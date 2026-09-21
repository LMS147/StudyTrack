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
 * One of the student's open tasks, sent with every AI request so the model can
 * answer "what's due this week?" and plan around existing deadlines.
 *
 * Computed on the CLIENT from the same UID-scoped Room cache the Calendar
 * reads (see [com.studytrack.app.ai.AiTaskContextBuilder]) — in local mode the
 * device is the only place these tasks exist, and in direct-LLM mode there is
 * no backend to fetch them. Dates are passed through in their stored ISO-8601
 * form; day-level comparisons use [com.studytrack.app.util.DateTimeUtils],
 * the app's single canonical date parser.
 */
@Serializable
data class AiUpcomingTask(
    val title: String,
    /** ISO-8601 date or date-time, exactly as stored on the task. */
    val dueDate: String,
    val subjectName: String? = null,
    /** "Low" | "Medium" | "High". */
    val priority: String? = null,
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
    /**
     * Names of the student's existing subjects. Sent so the AI attributes a
     * suggestion to a subject that actually exists (the app matches the
     * returned subjectName back to a real Subject). Without this the model
     * guesses a name like "Maths" for a subject actually called "Mathematics",
     * the match fails, and the accepted task lands under no subject at all.
     */
    val subjects: List<String> = emptyList(),
    val taskContext: AiTaskContext? = null,
    /**
     * The student's open tasks that are overdue or due within the next two
     * weeks (bounded, client-computed). Without this the model has no way to
     * know what is already on the calendar. Backends must treat it as optional
     * (older builds omit it) and include it in the prompt when present.
     */
    val upcomingTasks: List<AiUpcomingTask> = emptyList(),
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
