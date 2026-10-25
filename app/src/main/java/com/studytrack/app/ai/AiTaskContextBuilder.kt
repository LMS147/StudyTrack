package com.studytrack.app.ai

import com.studytrack.app.data.model.AiUpcomingTask
import com.studytrack.app.data.model.Task
import com.studytrack.app.util.DateTimeUtils
import java.time.LocalDate

/**
 * Builds the task context sent with every AI request (Bug: "the AI doesn't
 * know what's already on my calendar").
 *
 * This is the ONE canonical place that decides which tasks the assistant gets
 * to see, and it compares due dates with [DateTimeUtils.parseDate] — the same
 * parser the Calendar screen groups by. Storage format (full date-time,
 * date-only, or UTC-offset) therefore cannot make the AI's view of "due this
 * week" disagree with the calendar's.
 *
 * Window: every OPEN task that is overdue or due within [HORIZON_DAYS] days,
 * earliest first, capped at [LIMIT] entries so the prompt stays bounded.
 * Completed tasks and undated backlog tasks are never included.
 */
object AiTaskContextBuilder {

    const val HORIZON_DAYS = 14L
    const val LIMIT = 20

    fun upcoming(
        tasks: List<Task>,
        subjectNames: Map<String, String> = emptyMap(),
        today: LocalDate = DateTimeUtils.today(),
    ): List<AiUpcomingTask> =
        tasks.asSequence()
            .filter { !it.completed && !it.dueDate.isNullOrBlank() }
            .mapNotNull { task ->
                DateTimeUtils.parseDate(task.dueDate)?.let { date -> date to task }
            }
            .filter { (date, _) -> date <= today.plusDays(HORIZON_DAYS) }
            .sortedBy { (date, _) -> date }
            .take(LIMIT)
            .map { (_, task) ->
                AiUpcomingTask(
                    title = task.title,
                    // Stored ISO-8601, verbatim — the model is told today's
                    // date and can compare date parts itself.
                    dueDate = task.dueDate.orEmpty(),
                    subjectName = task.subjectId?.let { subjectNames[it] },
                    priority = task.priority.label,
                )
            }
            .toList()
}
