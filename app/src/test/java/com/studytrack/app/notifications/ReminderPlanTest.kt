package com.studytrack.app.notifications

import com.studytrack.app.data.local.entity.TimetableEntry
import com.studytrack.app.data.local.entity.TaskEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderPlanTest {
    private val zone = ZoneId.of("Africa/Johannesburg")
    private fun at(raw: String) = LocalDateTime.parse(raw).atZone(zone).toInstant().toEpochMilli()
    private val now = at("2026-11-04T08:00:00")
    private val task = TaskEntity("alice", "t1", "Exam", null, null, "EXAM", "High",
        "2026-11-04T10:00:00", null, false, null, 0, localUpdatedAt = 0)
    private val lesson = TimetableEntry("alice", "c1", "Maths", null, 3, 600, 660,
        "Room 1", "", "2026-11-01", null)

    @Test fun `explicit reminders override deadline fallback and offset times work`() {
        assertEquals(at("2026-11-04T09:00:00"), ReminderPlan.build(listOf(task), emptyList(), now, zone).single().at)
        val explicit = task.copy(reminderDate = "2026-11-04T06:30:00Z")
        assertEquals(at("2026-11-04T08:30:00"), ReminderPlan.build(listOf(explicit), emptyList(), now, zone).single().at)
    }
    @Test fun `completed expired and undated tasks never notify`() {
        val rows = listOf(task.copy(completed = true), task.copy(dueDate = "2026-11-03T12:00:00"),
            task.copy(dueDate = null))
        assertTrue(ReminderPlan.build(rows, emptyList(), now, zone).isEmpty())
    }
    @Test fun `class alerts precede start and future terms remain scheduled`() {
        val alerts = ReminderPlan.build(emptyList(), listOf(lesson), now, zone)
        assertEquals(at("2026-11-04T09:45:00"), alerts.first().at)
        val future = lesson.copy(startsOn = "2027-01-01")
        assertEquals(at("2027-01-06T09:45:00"), ReminderPlan.build(emptyList(), listOf(future), now, zone).first().at)
    }
    @Test fun `term end is inclusive and edited class changes occurrence key`() {
        assertEquals(1, ReminderPlan.build(emptyList(), listOf(lesson.copy(endsOn = "2026-11-04")), now, zone).size)
        assertTrue(ReminderPlan.build(emptyList(), listOf(lesson.copy(endsOn = "2026-11-03")), now, zone).isEmpty())
        assertNotEquals(ReminderPlan.build(emptyList(), listOf(lesson), now, zone).first().key,
            ReminderPlan.build(emptyList(), listOf(lesson.copy(startMinute = 630)), now, zone).first().key)
    }
    @Test fun `stale reminders are skipped and weekly recurrence advances`() {
        val later = at("2026-11-04T11:00:00")
        assertEquals(at("2026-11-11T09:45:00"), ReminderPlan.build(emptyList(), listOf(lesson), later, zone).first().at)
        assertTrue(ReminderPlan.build(listOf(task.copy(reminderDate = "2026-11-03T08:00:00")), emptyList(), now, zone).isEmpty())
    }
}
