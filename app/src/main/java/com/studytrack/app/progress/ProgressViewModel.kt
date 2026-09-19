package com.studytrack.app.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.model.Progress
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.repository.ProgressRepository
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.data.repository.TaskRepository
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SubjectProgressUiModel(
    val subjectId: String,
    val subjectName: String,
    val completed: Int,
    val total: Int,
) {
    val percent: Int get() = if (total == 0) 0 else (completed * 100) / total
}

data class ProgressUiState(
    val loading: Boolean = true,
    val overallPercent: Int = 0,
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val totalPoints: Int = 0,
    val currentStreak: Int = 0,
    val subjectProgress: List<SubjectProgressUiModel> = emptyList(),
    val error: String? = null,
)

/**
 * Progress screen: GET /api/progress provides points/completed/streak; the
 * overall completion percentage and per-subject bars are computed client-side
 * from the cached task and subject lists.
 */
class ProgressViewModel(
    private val progressRepository: ProgressRepository,
    private val taskRepository: TaskRepository,
    private val subjectRepository: SubjectRepository,
) : ViewModel() {

    private val progress = MutableStateFlow<Progress?>(null)
    private val refreshing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<ProgressUiState> = combine(
        taskRepository.tasks,
        subjectRepository.subjects,
        progress,
        refreshing,
        errorMessage,
    ) { tasks: List<Task>, subjects, progressData, isRefreshing, error ->
        val completed = tasks.count { it.completed }
        val bySubject = tasks.groupBy { it.subjectId }
        ProgressUiState(
            loading = isRefreshing && tasks.isEmpty(),
            overallPercent = if (tasks.isEmpty()) 0 else (completed * 100) / tasks.size,
            totalTasks = tasks.size,
            completedTasks = completed,
            totalPoints = progressData?.totalPoints ?: 0,
            currentStreak = progressData?.currentStreak ?: 0,
            subjectProgress = subjects
                .map { subject ->
                    val subjectTasks = bySubject[subject.subjectId].orEmpty()
                    SubjectProgressUiModel(
                        subjectId = subject.subjectId,
                        subjectName = subject.subjectName,
                        completed = subjectTasks.count { it.completed },
                        total = subjectTasks.size,
                    )
                }
                .sortedByDescending { it.total },
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            val progressResult = progressRepository.getProgress()
            val tasksResult = taskRepository.refresh()
            val subjectsResult = subjectRepository.refresh()
            progress.value = (progressResult as? ApiResult.Success)?.data
            errorMessage.value = when {
                progressResult is ApiResult.Error -> progressResult.message
                tasksResult is ApiResult.Error -> tasksResult.message
                subjectsResult is ApiResult.Error -> subjectsResult.message
                else -> null
            }
            refreshing.value = false
        }
    }

    companion object {
        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProgressViewModel(
                    ServiceLocator.progressRepository,
                    ServiceLocator.taskRepository,
                    ServiceLocator.subjectRepository,
                )
            }
        }
    }
}
