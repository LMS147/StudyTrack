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
    ) : ChatItem() {
        val effectiveDueDate: String? get() = suggestion.dueDate ?: userPickedDueDate
    }

    data object TypingIndicator : ChatItem() {
        override val itemId: String get() = "typing-indicator"
    }
}
