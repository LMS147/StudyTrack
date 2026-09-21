package com.studytrack.app.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.data.repository.TaskRepository
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TaskDetailsUiState(
    val loading: Boolean = true,
    val task: Task? = null,
    val subjectName: String? = null,
    val toggling: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null,
)

class TaskDetailsViewModel(
    private val taskRepository: TaskRepository,
    private val subjectRepository: SubjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TaskDetailsUiState())
    val state: StateFlow<TaskDetailsUiState> = _state.asStateFlow()

    private var loadedTaskId: String? = null

    fun load(taskId: String) {
        if (loadedTaskId == taskId) return
        loadedTaskId = taskId

        viewModelScope.launch {
            if (subjectRepository.subjects.value.isEmpty()) {
                subjectRepository.refresh()
            }
            when (val result = taskRepository.getTask(taskId)) {
                is ApiResult.Success -> {
                    val subjectName = subjectRepository.subjects.value
                        .firstOrNull { it.subjectId == result.data.subjectId }
                        ?.subjectName
                    _state.value = TaskDetailsUiState(
                        loading = false,
                        task = result.data,
                        subjectName = subjectName,
                    )
                }
                is ApiResult.Error -> _state.update { it.copy(loading = false, error = result.message) }
                ApiResult.Loading -> Unit
            }
        }
    }

    /** PATCH /api/tasks/{id}/complete — toggles the completed flag. */
    fun toggleComplete() {
        val task = _state.value.task ?: return
        if (_state.value.toggling) return
        viewModelScope.launch {
            _state.update { it.copy(toggling = true) }
            when (val result = taskRepository.setCompleted(task.taskId, !task.completed)) {
                is ApiResult.Success -> _state.update {
                    it.copy(toggling = false, task = result.data)
                }
                is ApiResult.Error -> _state.update {
                    it.copy(toggling = false, error = result.message)
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun delete() {
        val task = _state.value.task ?: return
        if (_state.value.deleting) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true) }
            when (val result = taskRepository.delete(task.taskId)) {
                is ApiResult.Success -> _state.update { it.copy(deleting = false, deleted = true) }
                is ApiResult.Error -> _state.update {
                    it.copy(deleting = false, error = result.message)
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    companion object {
        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TaskDetailsViewModel(
                    ServiceLocator.taskRepository,
                    ServiceLocator.subjectRepository,
                )
            }
        }
    }
}
