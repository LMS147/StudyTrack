package com.studytrack.app.subjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.model.Subject
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.data.repository.TaskRepository
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SubjectDetailUiState(
    val loading: Boolean = true,
    val subject: Subject? = null,
    val tasks: List<Task> = emptyList(),
    val error: String? = null,
) {
    val completedCount: Int get() = tasks.count { it.completed }
    val progressPercent: Int
        get() = if (tasks.isEmpty()) 0 else (completedCount * 100) / tasks.size
}

/**
 * Subject Details: derives its content reactively from the subject and task
 * repository caches, so edits made elsewhere (or newly created tasks) show up
 * automatically when the screen resumes.
 */
class SubjectDetailViewModel(
    private val subjectRepository: SubjectRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    private val subjectId = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<SubjectDetailUiState> = combine(
        subjectRepository.subjects,
        taskRepository.tasks,
        subjectId,
        refreshing,
        errorMessage,
    ) { subjects, tasks, id, isRefreshing, error ->
        val subject = id?.let { wanted -> subjects.firstOrNull { it.subjectId == wanted } }
        SubjectDetailUiState(
            loading = isRefreshing && subjects.isEmpty(),
            subject = subject,
            tasks = tasks
                .filter { it.subjectId == id }
                .sortedWith(
                    compareBy<Task> { it.completed }.then(DUE_DATE_ORDER)
                ),
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubjectDetailUiState())

    private var started = false

    fun start(id: String) {
        if (started) return
        started = true
        subjectId.value = id
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            val subjectsResult = subjectRepository.refresh()
            val tasksResult = taskRepository.refresh()
            errorMessage.value = when {
                subjectsResult is ApiResult.Error -> subjectsResult.message
                tasksResult is ApiResult.Error -> tasksResult.message
                else -> null
            }
            refreshing.value = false
        }
    }

    fun toggleComplete(taskId: String, completed: Boolean) {
        viewModelScope.launch {
            taskRepository.setCompleted(taskId, completed)
        }
    }

    companion object {
        /** ISO-8601 strings sort lexicographically; nulls (undated) go last. */
        private val DUE_DATE_ORDER = Comparator<Task> { a, b ->
            val ad = a.dueDate
            val bd = b.dueDate
            when {
                ad == bd -> 0
                ad == null -> 1
                bd == null -> -1
                else -> ad.compareTo(bd)
            }
        }

        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SubjectDetailViewModel(
                    ServiceLocator.subjectRepository,
                    ServiceLocator.taskRepository,
                )
            }
        }
    }
}
