package com.studytrack.app.util

/**
 * Keys used with NavController savedStateHandle to pass one-shot results back
 * to the previous screen (Navigation Component's recommended pattern).
 */
object NavResultKeys {

    /**
     * Set by AddEditTaskFragment when a task was successfully created from an
     * AI suggestion prefill; the value is the suggestionId. The AI chat screen
     * observes it to mark the suggestion card as accepted.
     */
    const val AI_SUGGESTION_CREATED = "ai_suggestion_created"
}
