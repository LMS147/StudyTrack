package com.studytrack.app.data.repository

import com.studytrack.app.data.model.Progress
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.DateTimeUtils
import java.time.LocalDate

/**
 * [ProgressRepository] for local mode: points come from the per-task points
 * awarded at completion time, and the streak counts consecutive days (ending
 * today or yesterday) with at least one task completed.
 */
class LocalProgressRepository(
    private val store: LocalStore,
) : ProgressRepository {

    override suspend fun getProgress(): ApiResult<Progress> {
        val tasks = store.tasks.value
        val completed = tasks.filter { it.completed }
        return ApiResult.Success(
            Progress(
                totalPoints = completed.sumOf { it.points },
                completedTasks = completed.size,
                currentStreak = currentStreak(completed),
            )
        )
    }

    private fun currentStreak(completedTasks: List<com.studytrack.app.data.model.Task>): Int {
        val completedDays = completedTasks
            .mapNotNull { DateTimeUtils.parseDate(it.completedAt) }
            .toSet()
        if (completedDays.isEmpty()) return 0

        // A streak survives until the end of yesterday if today has nothing yet.
        var day = LocalDate.now()
        if (day !in completedDays) {
            day = day.minusDays(1)
        }
        var streak = 0
        while (day in completedDays) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }
}
