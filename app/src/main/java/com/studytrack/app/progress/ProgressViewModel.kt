package com.studytrack.app.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.model.Progress
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.model.TaskType
import com.studytrack.app.data.repository.ProgressRepository
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.data.repository.TaskRepository
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.DateTimeUtils
import com.studytrack.app.util.Levels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One "BY CATEGORY" row: a task type and how much of it is done. */
data class CategoryProgressUiModel(
    val label: String,
    val completed: Int,
    val total: Int,
) {
    val percent: Int get() = if (total == 0) 0 else (completed * 100) / total
}

/** One tile in the BADGES grid; unearned tiles render greyed out. */
data class BadgeUiModel(
    val id: String,
    val emoji: String,
    val label: String,
    val earned: Boolean,
)

data class ProgressUiState(
    val loading: Boolean = true,
    val points: Int = 0,
    val level: Int = 1,
    val xpInLevel: Int = 0,
    val overallPercent: Int = 0,
    val totalTasks: Int = 0,
    val completedTasks: Int = 0,
    val pendingTasks: Int = 0,
    val currentStreak: Int = 0,
    val categoryProgress: List<CategoryProgressUiModel> = emptyList(),
    val badges: List<BadgeUiModel> = emptyList(),
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
        // Points are summed client-side (the same rule the local repositories
        // apply: High 20 / Medium 10 / Low 5) so Home and Progress always agree.
        val points = tasks.filter { it.completed }.sumOf { it.points }
        ProgressUiState(
            loading = isRefreshing && tasks.isEmpty(),
            points = points,
            level = Levels.levelFor(points),
            xpInLevel = Levels.xpInLevel(points),
            overallPercent = if (tasks.isEmpty()) 0 else (completed * 100) / tasks.size,
            totalTasks = tasks.size,
            completedTasks = completed,
            pendingTasks = tasks.size - completed,
            currentStreak = progressData?.currentStreak ?: 0,
            categoryProgress = buildCategoryProgress(tasks),
            badges = buildBadges(tasks, points),
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    /**
     * "BY CATEGORY" rows — one per task type that actually has tasks, in the
     * enum's display order (Assignment, Test, Exam, Project, Presentation,
     * Study). Types with no tasks are omitted rather than shown as empty bars.
     */
    private fun buildCategoryProgress(tasks: List<Task>): List<CategoryProgressUiModel> =
        TaskType.entries.mapNotNull { type ->
            val ofType = tasks.filter { it.taskType == type }
            if (ofType.isEmpty()) {
                null
            } else {
                CategoryProgressUiModel(
                    label = "${type.emoji}  ${type.label}",
                    completed = ofType.count { it.completed },
                    total = ofType.size,
                )
            }
        }

    /**
     * Badge rules (deterministic and derivable from the task list alone, so
     * they work identically in local and backend mode):
     *   First Task     — at least one task completed
     *   Speed Runner   — a task finished before its due date
     *   Bookworm       — three or more completed Study tasks
     *   High Achiever  — ten or more tasks completed
     *   On Target      — five or more completed with nothing overdue
     *   Scholar Elite  — 1000 XP (level 3)
     */
    private fun buildBadges(tasks: List<Task>, points: Int): List<BadgeUiModel> {
        val completed = tasks.filter { it.completed }
        val finishedEarly = completed.count { task ->
            val done = DateTimeUtils.parseDateTime(task.completedAt)
            val due = DateTimeUtils.parseDateTime(task.dueDate)
            done != null && due != null && done.isBefore(due)
        }
        val hasOverdue = tasks.any { !it.completed && DateTimeUtils.isOverdue(it.dueDate) }

        return listOf(
            BadgeUiModel("first_task", "🔥", "First Task", completed.isNotEmpty()),
            BadgeUiModel("speed_runner", "⚡", "Speed Runner", finishedEarly > 0),
            BadgeUiModel(
                "bookworm", "📚", "Bookworm",
                completed.count { it.taskType == TaskType.STUDY } >= 3,
            ),
            BadgeUiModel("high_achiever", "🏆", "High Achiever", completed.size >= 10),
            BadgeUiModel(
                "on_target", "🎯", "On Target",
                completed.size >= 5 && !hasOverdue,
            ),
            BadgeUiModel("scholar_elite", "💎", "Scholar Elite", points >= 1000),
        )
    }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            val progressResult = progressRepository.getProgress()
            val tasksResult = taskRepository.refresh()
            val subjectsResult = subjectRepository.refresh()
            progress.value =
                if (progressResult is ApiResult.Success) progressResult.data else null
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
