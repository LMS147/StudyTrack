package com.studytrack.app.subjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.model.Subject
import com.studytrack.app.data.model.SubjectPayload
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.data.repository.TaskRepository
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A subject plus its completion stats computed from the cached task list. */
data class SubjectWithProgress(
    val subject: Subject,
    val totalTasks: Int,
    val completedTasks: Int,
) {
    val progressPercent: Int
        get() = if (totalTasks == 0) 0 else (completedTasks * 100) / totalTasks
}

class SubjectsViewModel(
    private val subjectRepository: SubjectRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val subjectsWithProgress: StateFlow<List<SubjectWithProgress>> =
        combine(subjectRepository.subjects, taskRepository.tasks) { subjects, tasks ->
            subjects.map { subject ->
                val subjectTasks = tasks.filter { it.subjectId == subject.subjectId }
                SubjectWithProgress(
                    subject = subject,
                    totalTasks = subjectTasks.size,
                    completedTasks = subjectTasks.count { it.completed },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loadState = MutableStateFlow<ApiResult<Unit>?>(null)
    val loadState: StateFlow<ApiResult<Unit>?> = _loadState.asStateFlow()

    private val _saveState = MutableStateFlow<ApiResult<Unit>?>(null)
    val saveState: StateFlow<ApiResult<Unit>?> = _saveState.asStateFlow()

    private val _deleteState = MutableStateFlow<ApiResult<Unit>?>(null)
    val deleteState: StateFlow<ApiResult<Unit>?> = _deleteState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_loadState.value == ApiResult.Loading) return
        viewModelScope.launch {
            _loadState.value = ApiResult.Loading
            val subjectsResult = subjectRepository.refresh()
            val tasksResult = taskRepository.refresh()
            _loadState.value = when {
                subjectsResult is ApiResult.Error ->
                    ApiResult.Error(subjectsResult.message, subjectsResult.cause)
                tasksResult is ApiResult.Error ->
                    ApiResult.Error(tasksResult.message, tasksResult.cause)
                else -> ApiResult.Success(Unit)
            }
        }
    }

    fun createSubject(name: String, description: String?) {
        viewModelScope.launch {
            _saveState.value = ApiResult.Loading
            _saveState.value = subjectRepository.create(SubjectPayload(name, description)).asUnit()
        }
    }

    fun updateSubject(subjectId: String, name: String, description: String?) {
        viewModelScope.launch {
            _saveState.value = ApiResult.Loading
            _saveState.value =
                subjectRepository.update(subjectId, SubjectPayload(name, description)).asUnit()
        }
    }

    fun deleteSubject(subjectId: String) {
        viewModelScope.launch {
            _deleteState.value = subjectRepository.delete(subjectId)
        }
    }

    private fun <T> ApiResult<T>.asUnit(): ApiResult<Unit> = when (this) {
        is ApiResult.Success -> ApiResult.Success(Unit)
        is ApiResult.Error -> ApiResult.Error(message, cause)
        ApiResult.Loading -> ApiResult.Loading
    }

    companion object {
        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SubjectsViewModel(
                    ServiceLocator.subjectRepository,
                    ServiceLocator.taskRepository,
                )
            }
        }
    }
}
