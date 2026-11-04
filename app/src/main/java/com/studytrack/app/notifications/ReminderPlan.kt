package com.studytrack.app.notifications

import com.studytrack.app.data.local.entity.TaskEntity
import com.studytrack.app.data.local.entity.TimetableEntry
import java.time.*

data class Reminder(val key: String, val at: Long, val title: String, val message: String)

object ReminderPlan {
    fun build(tasks: List<TaskEntity>, classes: List<TimetableEntry>, now: Long,
              zone: ZoneId = ZoneId.systemDefault()): List<Reminder> {
        val result = mutableListOf<Reminder>()
        fun epoch(value: String?): Long? = value?.let {
            runCatching { LocalDateTime.parse(it).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDate.parse(it).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
        }
        tasks.filterNot { it.completed }.forEach { task ->
            val due = epoch(task.dueDate)
            val at = epoch(task.reminderDate) ?: due?.minus(60 * 60_000L)
            if (at != null && (due == null || due > now)) {
                result += Reminder("task:${task.taskId}:$at", at, task.title, "Task deadline reminder")
            }
        }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        classes.forEach { entry ->
            val first = runCatching { LocalDate.parse(entry.startsOn) }.getOrNull() ?: return@forEach
            val last = entry.endsOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            for (offset in 0L..8L) {
                val date = maxOf(today, first).plusDays(offset)
                if (date.dayOfWeek.value != entry.dayOfWeek || date < first || (last != null && date > last)) continue
                val start = date.atStartOfDay().plusMinutes(entry.startMinute.toLong())
                    .atZone(zone).toInstant().toEpochMilli()
                if (start <= now) continue
                val at = start - 15 * 60_000L
                result += Reminder("class:${entry.entryId}:$at", at, entry.title,
                    "Upcoming class" + entry.venue.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty())
            }
        }
        // A short late-delivery window handles device sleep without flooding old reminders.
        return result.filter { it.at >= now - 60 * 60_000L }.sortedBy { it.at }
    }
}
