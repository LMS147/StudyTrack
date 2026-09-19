package com.studytrack.app.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.model.AiTaskContext
import com.studytrack.app.data.model.ChatTurn
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.TaskPayload
import com.studytrack.app.data.model.TaskSuggestion
import com.studytrack.app.data.model.TaskType
import com.studytrack.app.data.repository.AiBrain
import com.studytrack.app.data.repository.SettingsRepository
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.data.repository.TaskRepository
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.UUID

/**
 * AI Assistant chat state machine.
 *
 * Every user message goes to the configured [AiBrain] together with the
 * conversation history and the device date/timezone so relative dates
 * ("next Friday") can be resolved into ISO-8601 — by the backend's
 * /api/ai/task-assistance endpoint when deployed, or by the direct
 * Grok/xAI brain when a key is configured in local.properties (see
 * docs/API_CONTRACT.md and docs/ARCHITECTURE_DECISIONS.md §13). When the backend returns a suggestion whose dueDate
 * is null (couldn't confidently resolve), the card shows an editable date
 * field and the client refuses to accept until the user picks one: it never
 * guesses dates.
 *
 * Accepting a suggestion creates the task(s) through the regular Tasks
 * repository — the chat has no other write path to the backend.
 */
class AiAssistantViewModel(
    private val aiBrain: AiBrain,
    private val taskRepository: TaskRepository,
    private val subjectRepository: SubjectRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatItem>>(emptyList())
    val messages: StateFlow<List<ChatItem>> = _messages.asStateFlow()

    private val _awaitingReply = MutableStateFlow(false)
    val awaitingReply: StateFlow<Boolean> = _awaitingReply.asStateFlow()

    private var taskContext: AiTaskContext? = null

    private var started = false
    private var initialPromptSent = false

    // UI text templates injected by the fragment (keeps resources out of the VM).
    private var emptyReplyText: String = ""
    private var addedViaEditorText: String = ""
    private var confirmationTemplate: String = ""
    private var confirmationWithSubtasksTemplate: String = ""

    /**
     * @param welcomeText        greeting bubble shown at the start of the chat
     * @param emptyReplyText     fallback when the API returns neither text nor suggestions
     * @param addedViaEditorText confirmation when a suggestion was created via the task editor
     * @param initialPrompt      optional prompt to auto-send (from Task Details shortcuts)
     * @param contextTaskId      optional task the conversation is about
     */
    fun start(
        welcomeText: String,
        emptyReplyText: String,
        addedViaEditorText: String,
        confirmationTemplate: String,
        confirmationWithSubtasksTemplate: String,
        initialPrompt: String?,
        contextTaskId: String?,
    ) {
        if (started) return
        started = true

        this.emptyReplyText = emptyReplyText
        this.addedViaEditorText = addedViaEditorText
        this.confirmationTemplate = confirmationTemplate
        this.confirmationWithSubtasksTemplate = confirmationWithSubtasksTemplate

        if (_messages.value.isEmpty()) {
            _messages.value = listOf(ChatItem.AiText(nextId("ai"), welcomeText))
        }

        if (contextTaskId != null) {
            viewModelScope.launch {
                taskContext = when (val result = taskRepository.getTask(contextTaskId)) {
                    is ApiResult.Success -> AiTaskContext(
                        taskId = result.data.taskId,
                        title = result.data.title,
                        dueDate = result.data.dueDate,
                    )
                    else -> AiTaskContext(taskId = contextTaskId)
                }
                if (subjectRepository.subjects.value.isEmpty()) {
                    subjectRepository.refresh()
                }
                sendInitialPrompt(initialPrompt)
            }
        } else {
            viewModelScope.launch {
                if (subjectRepository.subjects.value.isEmpty()) {
                    subjectRepository.refresh()
                }
                sendInitialPrompt(initialPrompt)
            }
        }
    }

    private fun sendInitialPrompt(prompt: String?) {
        if (prompt.isNullOrBlank() || initialPromptSent) return
        initialPromptSent = true
        sendMessage(prompt)
    }

    /** Sends a user message to the AI endpoint and renders the reply. */
    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _awaitingReply.value) return

        _awaitingReply.value = true
        append(ChatItem.UserMessage(nextId("user"), trimmed))
        append(ChatItem.TypingIndicator)

        viewModelScope.launch {
            // Conversation context (previous turns only; current message is
            // passed separately in the request body).
            val history = _messages.value.mapNotNull { item ->
                when (item) {
                    is ChatItem.UserMessage -> ChatTurn(role = "user", content = item.text)
                    is ChatItem.AiText -> ChatTurn(role = "assistant", content = item.text)
                    else -> null
                }
            }.dropLast(1)

            val request = com.studytrack.app.data.model.TaskAssistanceRequest(
                message = trimmed,
                conversationHistory = history,
                today = DateTimeUtils.todayIsoDate(),
                timezone = DateTimeUtils.timezoneId(),
                taskContext = taskContext,
            )

            when (val result = aiBrain.taskAssistance(request)) {
                is ApiResult.Success -> {
                    removeTypingIndicator()
                    val response = result.data
                    val replyText = response.replyText.orEmpty()
                    if (replyText.isNotBlank()) {
                        append(ChatItem.AiText(nextId("ai"), replyText))
                    } else if (response.suggestions.isEmpty()) {
                        append(ChatItem.AiText(nextId("ai"), emptyReplyText))
                    }
                    response.suggestions.forEach { raw ->
                        val suggestion = if (raw.suggestionId.isNullOrBlank()) {
                            raw.copy(suggestionId = UUID.randomUUID().toString())
                        } else {
                            raw
                        }
                        append(
                            ChatItem.Suggestion(
                                itemId = "sug-${suggestion.suggestionId}",
                                suggestion = suggestion,
                            )
                        )
                    }
                }
                is ApiResult.Error -> {
                    removeTypingIndicator()
                    append(ChatItem.AiText(nextId("ai"), result.message))
                }
                ApiResult.Loading -> Unit
            }
            _awaitingReply.value = false
        }
    }

    /** User picked a date on a suggestion card (backend couldn't resolve one). */
    fun setSuggestionDate(itemId: String, isoDate: String) {
        updateSuggestion(itemId) { item ->
            item.copy(userPickedDueDate = isoDate)
        }
    }

    /**
     * Accept: creates the task (and its subtasks) via the Tasks repository,
     * then posts a confirmation into the chat with the resolved date so the
     * user knows it landed on the calendar without switching screens.
     */
    fun acceptSuggestion(itemId: String) {
        val item = _messages.value.firstOrNull {
            it is ChatItem.Suggestion && it.itemId == itemId
        } as? ChatItem.Suggestion ?: return
        if (item.status != SuggestionStatus.PENDING) return

        val dueIso = item.effectiveDueDate ?: return // UI disables Accept without a date
        updateSuggestion(itemId) { it.copy(status = SuggestionStatus.ACCEPTING) }

        viewModelScope.launch {
            val result = taskRepository.create(item.suggestion.toPayload())
            when (result) {
                is ApiResult.Success -> {
                    var createdSubtasks = 0
                    item.suggestion.subtasks.forEach { subtask ->
                        // Subtasks without their own resolved date inherit the
                        // parent's due date (deterministic, documented rule).
                        val subResult = taskRepository.create(
                            subtask.toPayload(fallbackDueDate = dueIso)
                        )
                        if (subResult is ApiResult.Success) createdSubtasks++
                    }
                    updateSuggestion(itemId) { it.copy(status = SuggestionStatus.ACCEPTED) }

                    val confirmation = if (createdSubtasks == 0) {
                        String.format(confirmationTemplate, DateTimeUtils.fullDate(dueIso))
                    } else {
                        String.format(
                            confirmationWithSubtasksTemplate,
                            DateTimeUtils.fullDate(dueIso),
                            createdSubtasks,
                        )
                    }
                    append(ChatItem.AiText(nextId("sys"), confirmation))
                }
                is ApiResult.Error -> {
                    updateSuggestion(itemId) { it.copy(status = SuggestionStatus.PENDING) }
                    append(ChatItem.AiText(nextId("sys"), result.message))
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun rejectSuggestion(itemId: String) {
        updateSuggestion(itemId) { item ->
            if (item.status == SuggestionStatus.PENDING) {
                item.copy(status = SuggestionStatus.REJECTED)
            } else {
                item
            }
        }
    }

    /**
     * Called when the task editor reports that a suggestion was created via
     * the "Edit" flow (AddEditTaskFragment -> savedStateHandle result).
     */
    fun markAcceptedFromEditor(itemId: String) {
        val item = _messages.value.firstOrNull { it.itemId == itemId }
        if (item !is ChatItem.Suggestion) return
        if (item.status == SuggestionStatus.ACCEPTED) return

        updateSuggestion(itemId) { it.copy(status = SuggestionStatus.ACCEPTED) }
        append(
            ChatItem.AiText(
                nextId("sys"),
                "✓ \"${item.suggestion.title}\" — $addedViaEditorText",
            )
        )
    }

    // --------------------------------------------------------------- internals

    private fun TaskSuggestion.toPayload(fallbackDueDate: String? = null): TaskPayload =
        TaskPayload(
            title = title,
            description = description,
            subjectId = subjectId,
            taskType = TaskType.fromRaw(taskType),
            priority = Priority.fromRaw(priority),
            dueDate = dueDate ?: fallbackDueDate,
            reminderDate = reminderDate,
        )

    /** End-of-day for user-picked date-only suggestions (mirrors the task editor). */
    fun userPickedIsoDate(date: java.time.LocalDate): String =
        DateTimeUtils.formatIso(date, LocalTime.of(23, 59))

    private fun append(item: ChatItem) {
        _messages.update { it + item }
    }

    private fun removeTypingIndicator() {
        _messages.update { list -> list.filterNot { it is ChatItem.TypingIndicator } }
    }

    private inline fun updateSuggestion(itemId: String, transform: (ChatItem.Suggestion) -> ChatItem.Suggestion) {
        _messages.update { list ->
            list.map { item ->
                if (item is ChatItem.Suggestion && item.itemId == itemId) transform(item) else item
            }
        }
    }

    private fun nextId(prefix: String): String = "$prefix-${idCounter++}"

    private var idCounter: Long = 0

    companion object {
        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AiAssistantViewModel(
                    ServiceLocator.aiBrain,
                    ServiceLocator.taskRepository,
                    ServiceLocator.subjectRepository,
                    ServiceLocator.settingsRepository,
                )
            }
        }
    }
}
