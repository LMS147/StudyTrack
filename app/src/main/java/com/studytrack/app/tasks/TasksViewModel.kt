package com.studytrack.app.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.model.TaskType
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.data.repository.TaskRepository
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TasksUiState(
    val loading: Boolean = true,
    val items: List<TaskListItem> = emptyList(),
    val typeFilter: TaskType? = null,
    val priorityFilter: Priority? = null,
    val totalCount: Int = 0,
    val error: String? = null,
) {
    /** True when the student has tasks, but the current filters hide them all. */
    val filteredEmpty: Boolean get() = items.isEmpty() && totalCount > 0
}

/**
 * The Tasks tab: every task, newest deadlines first, with the two filter rows
 * from the design (task type + priority). Derived reactively from the
 * repository caches, so a task completed on another screen updates here too.
 */
class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val subjectRepository: SubjectRepository,
) : ViewModel() {

    /** Both filter rows in one flow: combine() is typed up to five sources. */
    private data class Filters(val type: TaskType? = null, val priority: Priority? = null)

    private val filters = MutableStateFlow(Filters())
    private val refreshing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<TasksUiState> = combine(
        taskRepository.tasks,
        subjectRepository.subjects,
        filters,
        refreshing,
        errorMessage,
    ) { tasks, subjects, activeFilters, isRefreshing, error ->
        val names = subjects.associate { it.subjectId to it.subjectName }
        val filtered = tasks
            .filter { activeFilters.type == null || it.taskType == activeFilters.type }
            .filter { activeFilters.priority == null || it.priority == activeFilters.priority }
            // Open tasks first, then by due date (undated last).
            .sortedWith(
                compareBy<Task> { it.completed }
                    .thenBy { it.dueDate ?: LATE_SORT_KEY }
            )
        TasksUiState(
            loading = isRefreshing && tasks.isEmpty(),
            items = filtered.map { task ->
                TaskListItem(task, task.subjectId?.let { names[it] })
            },
            typeFilter = activeFilters.type,
            priorityFilter = activeFilters.priority,
            totalCount = tasks.size,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            val tasksResult = taskRepository.refresh()
            if (subjectRepository.subjects.value.isEmpty()) subjectRepository.refresh()
            errorMessage.value = (tasksResult as? ApiResult.Error)?.message
            refreshing.value = false
        }
    }

    fun setTypeFilter(type: TaskType?) {
        filters.update { it.copy(type = type) }
    }

    fun setPriorityFilter(priority: Priority?) {
        filters.update { it.copy(priority = priority) }
    }

    fun toggleComplete(taskId: String, completed: Boolean) {
        viewModelScope.launch { taskRepository.setCompleted(taskId, completed) }
    }

    companion object {
        /** Sorts undated tasks after every dated one (ISO strings compare in order). */
        private const val LATE_SORT_KEY = "9999-12-31"

        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TasksViewModel(
                    ServiceLocator.taskRepository,
                    ServiceLocator.subjectRepository,
                )
            }
        }
    }
}
