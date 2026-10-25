package com.studytrack.app.timetable

import com.studytrack.app.data.local.entity.TimetableEntry
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TimetableRulesTest {
    private val monday = TimetableEntry("alice", "a", "Programming", null, 1, 540, 600,
        "Lab 1", "Lecturer", "2026-10-01", "2026-11-02")

    @Test fun `weekly occurrences respect weekday and inclusive term bounds`() {
        assertTrue(TimetableRules.occursOn(monday, LocalDate.parse("2026-10-05")))
        assertTrue(TimetableRules.occursOn(monday, LocalDate.parse("2026-11-02")))
        assertFalse(TimetableRules.occursOn(monday, LocalDate.parse("2026-09-28")))
        assertFalse(TimetableRules.occursOn(monday, LocalDate.parse("2026-11-09")))
        assertFalse(TimetableRules.occursOn(monday, LocalDate.parse("2026-10-06")))
    }
    @Test fun `unbounded classes recur next year without creating individual task rows`() {
        assertTrue(TimetableRules.occursOn(monday.copy(endsOn = null), LocalDate.parse("2027-01-04")))
    }
    @Test fun `adjacent classes are allowed but partial and contained overlaps are conflicts`() {
        assertFalse(TimetableRules.overlaps(monday, monday.copy(startMinute = 600, endMinute = 660)))
        assertTrue(TimetableRules.overlaps(monday, monday.copy(startMinute = 570, endMinute = 630)))
        assertTrue(TimetableRules.overlaps(monday, monday.copy(startMinute = 550, endMinute = 590)))
    }
    @Test fun `different accounts weekdays and nonintersecting terms do not conflict`() {
        assertFalse(TimetableRules.overlaps(monday, monday.copy(ownerUid = "bob")))
        assertFalse(TimetableRules.overlaps(monday, monday.copy(dayOfWeek = 2)))
        assertFalse(TimetableRules.overlaps(monday, monday.copy(startsOn = "2026-11-03", endsOn = null)))
        assertFalse(TimetableRules.overlaps(monday.copy(endsOn = "2026-10-06"),
            monday.copy(startsOn = "2026-10-06", endsOn = "2026-10-07")))
    }
    @Test fun `invalid or occurrence free schedules cannot be saved`() {
        listOf(monday.copy(title = " "), monday.copy(dayOfWeek = 0),
            monday.copy(endMinute = 540), monday.copy(endMinute = 500),
            monday.copy(startsOn = "2026-12-01"),
            monday.copy(startsOn = "2026-10-06", endsOn = "2026-10-07"))
            .forEach { entry ->
                try { TimetableRules.validate(entry); fail("invalid schedule accepted: $entry")
                } catch (_: IllegalArgumentException) { }
            }
    }
}
