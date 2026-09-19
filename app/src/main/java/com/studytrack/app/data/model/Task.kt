package com.studytrack.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TaskType(val label: String) {
    @SerialName("Assignment") ASSIGNMENT("Assignment"),
    @SerialName("Test") TEST("Test"),
    @SerialName("Exam") EXAM("Exam"),
    @SerialName("Project") PROJECT("Project"),
    @SerialName("Presentation") PRESENTATION("Presentation"),
    @SerialName("Study") STUDY("Study");

    companion object {
        /**
         * Lenient parse for AI suggestions, where `taskType` arrives as a
         * free-form string. Falls back to STUDY when unrecognized.
         */
        fun fromRaw(raw: String?): TaskType = entries.firstOrNull {
            it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true)
        } ?: STUDY
    }
}

@Serializable
enum class Priority(val label: String) {
    @SerialName("Low") LOW("Low"),
    @SerialName("Medium") MEDIUM("Medium"),
    @SerialName("High") HIGH("High");

    companion object {
        fun fromRaw(raw: String?): Priority = entries.firstOrNull {
            it.label.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true)
        } ?: MEDIUM
    }
}

/**
 * Wire model for GET/PUT /api/tasks. Date fields are ISO-8601 strings
 * (e.g. "2026-09-24T17:00:00"); parsing/formatting lives in [com.studytrack.app.util.DateTimeUtils].
 */
@Serializable
data class Task(
    val taskId: String,
    val title: String,
    val subjectId: String? = null,
    val description: String? = null,
    val taskType: TaskType = TaskType.STUDY,
    val priority: Priority = Priority.MEDIUM,
    /** ISO-8601 date-time. Null only for undated backlog tasks. */
    val dueDate: String? = null,
    /** ISO-8601 date-time. */
    val reminderDate: String? = null,
    val completed: Boolean = false,
    /** ISO-8601 date-time set when the task was completed. */
    val completedAt: String? = null,
    val points: Int = 0,
)

/** Request body for POST /api/tasks and PUT /api/tasks/{id}. */
@Serializable
data class TaskPayload(
    val title: String,
    val subjectId: String? = null,
    val description: String? = null,
    val taskType: TaskType = TaskType.STUDY,
    val priority: Priority = Priority.MEDIUM,
    val dueDate: String? = null,
    val reminderDate: String? = null,
)

/** Request body for PATCH /api/tasks/{id}/complete. */
@Serializable
data class CompleteTaskPayload(val completed: Boolean = true)
