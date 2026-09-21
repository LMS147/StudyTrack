package com.studytrack.app.data.model

import kotlinx.serialization.Serializable

/** Response model for GET /api/progress. */
@Serializable
data class Progress(
    val totalPoints: Int = 0,
    val completedTasks: Int = 0,
    val currentStreak: Int = 0,
)
