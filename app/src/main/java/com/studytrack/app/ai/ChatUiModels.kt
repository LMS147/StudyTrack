package com.studytrack.app.ai

import com.studytrack.app.data.model.TaskSuggestion

/** Lifecycle of a suggestion card in the chat. */
enum class SuggestionStatus { PENDING, ACCEPTING, ACCEPTED, REJECTED }

/**
 * In-memory chat history (kept in the ViewModel so scrolling back through the
 * conversation works; intentionally not persisted yet).
 */
sealed class ChatItem {
    abstract val itemId: String

    data class UserMessage(
        override val itemId: String,
        val text: String,
    ) : ChatItem()

    data class AiText(
        override val itemId: String,
        val text: String,
    ) : ChatItem()

    /**
     * A structured, calendar-ready suggestion rendered as a card with
     * Accept / Edit / Reject actions. When the backend could not confidently
     * resolve a date, [TaskSuggestion.dueDate] is null and the user must pick
     * one on the card ([userPickedDueDate]) before accepting — the client
     * never guesses dates.
     */
    data class Suggestion(
        override val itemId: String,
        val suggestion: TaskSuggestion,
        val userPickedDueDate: String? = null,
        val status: SuggestionStatus = SuggestionStatus.PENDING,
        /**
         * Subject matched against the student's real subjects when the card
         * was created (from the AI's subjectId, or its subjectName). Null when
         * nothing matched — the card then invites the user to pick one.
         */
        val resolvedSubjectId: String? = null,
        val resolvedSubjectName: String? = null,
        /** Set when the user attaches the suggestion to a subject on the card. */
        val userPickedSubjectId: String? = null,
        val userPickedSubjectName: String? = null,
    ) : ChatItem() {
        val effectiveDueDate: String? get() = suggestion.dueDate ?: userPickedDueDate

        /**
         * Subject this suggestion will be filed under: an explicit user pick
         * beats the auto-resolved match, which beats the raw id the AI/backend
         * supplied (kept so a backend that knows real ids still works).
         */
        val effectiveSubjectId: String?
            get() = userPickedSubjectId ?: resolvedSubjectId ?: suggestion.subjectId

        /** What the card shows on the subject chip. */
        val displaySubjectName: String? get() = userPickedSubjectName ?: resolvedSubjectName
    }

    data object TypingIndicator : ChatItem() {
        override val itemId: String get() = "typing-indicator"
    }
}
