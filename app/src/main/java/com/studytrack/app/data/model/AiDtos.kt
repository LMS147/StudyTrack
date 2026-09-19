package com.studytrack.app.data.model

import kotlinx.serialization.Serializable

/** One turn of conversation context sent along with each AI request. */
@Serializable
data class ChatTurn(
    /** "user" or "assistant". */
    val role: String,
    val content: String,
)

/** Snapshot of the task an AI request is about (set when launched from Task Details). */
@Serializable
data class AiTaskContext(
    val taskId: String? = null,
    val title: String? = null,
    val dueDate: String? = null,
)

/**
 * Request body for POST /api/ai/task-assistance.
 *
 * `today` + `timezone` give the backend the device context it needs to resolve
 * relative dates ("next Friday", "in 3 days") authoritatively server-side —
 * the preferred approach (see docs/API_CONTRACT.md).
 */
@Serializable
data class TaskAssistanceRequest(
    val message: String,
    val conversationHistory: List<ChatTurn> = emptyList(),
    /** Device-local date, yyyy-MM-dd. */
    val today: String? = null,
    /** IANA timezone of the device, e.g. "Africa/Johannesburg". */
    val timezone: String? = null,
    val taskContext: AiTaskContext? = null,
)

/** Response for POST /api/ai/task-assistance. */
@Serializable
data class TaskAssistanceResponse(
    /** Plain-text part of the AI reply (always safe to render as a text bubble). */
    val replyText: String? = null,
    /** Structured, calendar-ready suggestions rendered as cards in the chat. */
    val suggestions: List<TaskSuggestion> = emptyList(),
)

/**
 * One AI-suggested task (or a subtask within a suggestion).
 *
 * Field names mirror the Task model. Dates are ISO-8601 strings resolved by
 * the BACKEND against the `today`/`timezone` context of the request. A null
 * `dueDate` means the AI could not confidently determine a date — the client
 * then shows an editable date field on the suggestion card and refuses to
 * accept the task until the user picks one. The client never guesses dates.
 */
@Serializable
data class TaskSuggestion(
    /** Client-side stable id for the card; generated if the backend omits it. */
    val suggestionId: String? = null,
    /** "task" | "breakdown" | "study_plan" — controls card framing only. */
    val kind: String = "task",
    val title: String = "",
    val description: String? = null,
    /** null when the AI couldn't tell which subject this belongs to. */
    val subjectId: String? = null,
    /** Convenience for display when the backend knows the subject name. */
    val subjectName: String? = null,
    /** Free-form string mapped leniently via [TaskType.fromRaw]. */
    val taskType: String? = null,
    /** Free-form string mapped leniently via [Priority.fromRaw]. */
    val priority: String? = null,
    /** ISO-8601 or null (= user must pick a date before accepting). */
    val dueDate: String? = null,
    val reminderDate: String? = null,
    /** For "breakdown"/"study_plan" suggestions: the proposed subtasks. */
    val subtasks: List<TaskSuggestion> = emptyList(),
)
