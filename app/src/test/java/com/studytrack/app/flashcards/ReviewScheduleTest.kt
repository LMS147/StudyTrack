package com.studytrack.app.flashcards

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReviewScheduleTest {
    private val today = LocalDate.parse("2026-10-27")
    @Test fun `first ratings schedule again today hard tomorrow and easy in three days`() {
        assertEquals(today, ReviewSchedule.next(today, 0, ReviewRating.AGAIN).dueDate)
        assertEquals(today.plusDays(1), ReviewSchedule.next(today, 0, ReviewRating.HARD).dueDate)
        assertEquals(today.plusDays(3), ReviewSchedule.next(today, 0, ReviewRating.EASY).dueDate)
    }
    @Test fun `hard reduces an established interval while easy expands and caps it`() {
        assertEquals(5, ReviewSchedule.next(today, 10, ReviewRating.HARD).intervalDays)
        assertEquals(20, ReviewSchedule.next(today, 10, ReviewRating.EASY).intervalDays)
        assertEquals(180, ReviewSchedule.next(today, 120, ReviewRating.EASY).intervalDays)
        assertEquals(0, ReviewSchedule.next(today, 50, ReviewRating.AGAIN).intervalDays)
    }
    @Test fun `due includes overdue and today but not future cards`() {
        assertTrue(ReviewSchedule.isDue("2026-10-20", today))
        assertTrue(ReviewSchedule.isDue("2026-10-27", today))
        assertFalse(ReviewSchedule.isDue("2026-10-28", today))
    }
}
