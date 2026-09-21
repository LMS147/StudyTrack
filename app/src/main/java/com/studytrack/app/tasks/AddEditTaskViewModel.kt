package com.studytrack.app.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.model.Subject
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.model.TaskPayload
import com.studytrack.app.data.model.TaskSuggestion
import com.studytrack.app.data.remote.RetrofitClient
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.data.repository.TaskRepository
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class TaskFormUiState(
    val loading: Boolean = true,
    /** Non-null when editing an existing task. */
    val editingTask: Task? = null,
    /** Non-null when the editor was opened to review an AI suggestion. */
    val prefill: TaskSuggestion? = null,
    val subjects: List<Subject> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

/**
 * Loads subjects (+ the edited task or AI-suggestion prefill) and performs the
 * create/update. Form field state itself lives in the fragment; this ViewModel
 * owns data loading and the save call state (Loading/Success/Error).
 */
class AddEditTaskViewModel(
    private val taskRepository: TaskRepository,
    private val subjectRepository: SubjectRepository,
    private val json: Json,
) : ViewModel() {

    private val _state = MutableStateFlow(TaskFormUiState())
    val state: StateFlow<TaskFormUiState> = _state.asStateFlow()

    private var started = false

    fun start(taskId: String?, prefillJson: String?) {
        if (started) return
        started = true

        viewModelScope.launch {
            subjectRepository.refresh()

            val prefill = parsePrefill(prefillJson)

            if (taskId != null) {
                when (val result = taskRepository.getTask(taskId)) {
                    is ApiResult.Success -> _state.update {
                        it.copy(
                            loading = false,
                            editingTask = result.data,
                            prefill = prefill,
                            subjects = subjectRepository.subjects.value,
                        )
                    }
                    is ApiResult.Error -> _state.update {
                        it.copy(loading = false, error = result.message)
                    }
                    ApiResult.Loading -> Unit
                }
            } else {
                _state.update {
                    it.copy(loading = false, prefill = prefill, subjects = subjectRepository.subjects.value)
                }
            }
        }
    }

    /** Creates (or updates, when editing) the task. */
    fun save(payload: TaskPayload) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val editingId = _state.value.editingTask?.taskId
            val result = if (editingId != null) {
                taskRepository.update(editingId, payload)
            } else {
                taskRepository.create(payload)
            }
            _state.update {
                when (result) {
                    is ApiResult.Success -> it.copy(saving = false, saved = true)
                    is ApiResult.Error -> it.copy(saving = false, error = result.message)
                    ApiResult.Loading -> it
                }
            }
        }
    }

    private fun parsePrefill(prefillJson: String?): TaskSuggestion? {
        if (prefillJson.isNullOrBlank()) return null
        return try {
            json.decodeFromString(TaskSuggestion.serializer(), prefillJson)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AddEditTaskViewModel(
                    ServiceLocator.taskRepository,
                    ServiceLocator.subjectRepository,
                    RetrofitClient.json,
                )
            }
        }
    }
}
