package com.studytrack.app.flashcards

import java.time.LocalDate

enum class ReviewRating { AGAIN, HARD, EASY }
data class ReviewResult(val dueDate: LocalDate, val intervalDays: Int)

/** Day-based spaced review. Again remains due today, but only once per active session. */
object ReviewSchedule {
    fun next(today: LocalDate, currentInterval: Int, rating: ReviewRating): ReviewResult {
        val interval = currentInterval.coerceIn(0, 180)
        val days = when (rating) {
            ReviewRating.AGAIN -> 0
            ReviewRating.HARD -> maxOf(1, interval / 2)
            ReviewRating.EASY -> if (interval < 3) 3 else (interval * 2).coerceAtMost(180)
        }
        return ReviewResult(today.plusDays(days.toLong()), days)
    }
    fun isDue(dueDate: String, today: LocalDate): Boolean = !LocalDate.parse(dueDate).isAfter(today)
}
