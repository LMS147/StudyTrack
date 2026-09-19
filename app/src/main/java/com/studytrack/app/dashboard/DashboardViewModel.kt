package com.studytrack.app.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.repository.AuthRepository
import com.studytrack.app.data.repository.SettingsRepository
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.data.repository.TaskRepository
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DashboardUiState(
    val loading: Boolean = true,
    val userName: String = "",
    val todayTotal: Int = 0,
    val todayCompleted: Int = 0,
    val dueToday: List<Task> = emptyList(),
    val overdue: List<Task> = emptyList(),
    val upcoming: List<Task> = emptyList(),
    val subjectNames: Map<String, String> = emptyMap(),
    val showAiCard: Boolean = true,
    val error: String? = null,
    /** Study points earned so far — the experience behind the level banner. */
    val points: Int = 0,
) {
    val todayProgressPercent: Int
        get() = if (todayTotal == 0) 0 else (todayCompleted * 100) / todayTotal

    val overdueCount: Int get() = overdue.size

    /** Scholar level: one level per [XP_PER_LEVEL] points, starting at 1. */
    val level: Int get() = points / XP_PER_LEVEL + 1

    /** XP earned inside the current level — what the banner's bar fills to. */
    val xpInLevel: Int get() = points % XP_PER_LEVEL

    val initials: String
        get() = userName.trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "?" }

    companion object {
        /** Points needed to advance one level (matches the design's 500). */
        const val XP_PER_LEVEL = 500
    }
}

/**
 * Aggregates the task and subject repository caches into the dashboard
 * summary: today's progress ring, tasks due today, overdue tasks and upcoming
 * deadlines.
 */
class DashboardViewModel(
    private val taskRepository: TaskRepository,
    private val subjectRepository: SubjectRepository,
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val today = MutableStateFlow(LocalDate.now())

    val state: StateFlow<DashboardUiState> = combine(
        taskRepository.tasks,
        subjectRepository.subjects,
        refreshing,
        errorMessage,
        today,
    ) { tasks, subjects, isRefreshing, error, todayDate ->
        val dated = tasks.filter { it.dueDate != null }
        DashboardUiState(
            loading = isRefreshing && tasks.isEmpty(),
            userName = authRepository.displayName(),
            todayTotal = dated.count { DateTimeUtils.parseDate(it.dueDate) == todayDate },
            todayCompleted = dated.count {
                DateTimeUtils.parseDate(it.dueDate) == todayDate && it.completed
            },
            dueToday = dated
                .filter { DateTimeUtils.parseDate(it.dueDate) == todayDate }
                .sortedBy { it.dueDate },
            overdue = dated
                .filter { !it.completed && DateTimeUtils.isOverdue(it.dueDate) }
                .sortedBy { it.dueDate },
            upcoming = dated
                .filter {
                    !it.completed &&
                        DateTimeUtils.parseDate(it.dueDate)?.isAfter(todayDate) == true
                }
                .sortedBy { it.dueDate }
                .take(8),
            subjectNames = subjects.associate { it.subjectId to it.subjectName },
            showAiCard = settingsRepository.showAiCardOnDashboard,
            error = error,
            points = tasks.filter { it.completed }.sumOf { it.points },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            val tasksResult = taskRepository.refresh()
            val subjectsResult = subjectRepository.refresh()
            errorMessage.value = when {
                tasksResult is ApiResult.Error -> tasksResult.message
                subjectsResult is ApiResult.Error -> subjectsResult.message
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
        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                DashboardViewModel(
                    ServiceLocator.taskRepository,
                    ServiceLocator.subjectRepository,
                    ServiceLocator.authRepository,
                    ServiceLocator.settingsRepository,
                )
            }
        }
    }
}
