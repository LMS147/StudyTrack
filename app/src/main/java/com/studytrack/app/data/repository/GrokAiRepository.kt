package com.studytrack.app.data.repository

import com.studytrack.app.data.model.TaskAssistanceRequest
import com.studytrack.app.data.model.TaskAssistanceResponse
import com.studytrack.app.data.model.TaskSuggestion
import com.studytrack.app.data.remote.GrokApiService
import com.studytrack.app.data.remote.GrokChatMessage
import com.studytrack.app.data.remote.GrokChatRequest
import com.studytrack.app.data.remote.GrokChatResponse
import com.studytrack.app.data.remote.RetrofitClient
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.friendlyMessage
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.util.UUID

/**
 * [AiBrain] implementation that talks DIRECTLY to the xAI (Grok)
 * chat-completions API from the app, using an API key configured in
 * local.properties (exposed as [BuildConfig.GROK_API_KEY]).
 *
 * This mode exists so the assistant works during development / personal use
 * without a deployed StudyTrack backend. The same "brain contract" the
 * backend implements (docs/API_CONTRACT.md) is enforced here via the system
 * prompt: reply as one JSON object with a chat reply plus structured
 * suggestions, resolve relative dates against the device's today/timezone,
 * and never invent a date (null dueDate = the app asks the user).
 *
 * NOTE: the key ships inside the APK with this mode — fine for personal use,
 * not for a public release (see docs/ARCHITECTURE_DECISIONS.md §13).
 */
class GrokAiRepository(
    private val api: GrokApiService,
    private val model: String,
) : AiBrain {

    override suspend fun taskAssistance(request: TaskAssistanceRequest): ApiResult<TaskAssistanceResponse> = try {
        ApiResult.Success(callGrok(request))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ApiResult.Error(e.friendlyMessage(), e)
    }

    // ------------------------------------------------------------- network

    private suspend fun callGrok(request: TaskAssistanceRequest): TaskAssistanceResponse {
        val response: Response<GrokChatResponse> = api.chatCompletions(
            GrokChatRequest(
                model = model,
                messages = buildMessages(request),
            )
        )

        if (!response.isSuccessful) {
            val body = response.errorBody()?.string().orEmpty().take(400)
            val message = when (response.code()) {
                401, 403 ->
                    "Grok rejected the API key (${response.code()}). " +
                        "Check grok.apiKey in local.properties."
                429 -> "Grok is rate-limiting you (429). Wait a moment and try again."
                else -> "Grok API error ${response.code()}: $body"
            }
            // IllegalStateException flows through friendlyMessage() verbatim
            // (unlike IOException, which is mapped to a generic network error).
            throw IllegalStateException(message)
        }

        val content = response.body()?.choices?.firstOrNull()?.message?.content
            ?: throw IllegalStateException("Grok returned an empty reply. Try again.")
        return parseReply(content)
    }

    private fun buildMessages(request: TaskAssistanceRequest): List<GrokChatMessage> {
        val messages = mutableListOf(
            GrokChatMessage(role = "system", content = systemPrompt(request))
        )
        request.conversationHistory.forEach { turn ->
            messages.add(GrokChatMessage(role = turn.role, content = turn.content))
        }
        messages.add(GrokChatMessage(role = "user", content = request.message))
        return messages
    }

    // -------------------------------------------------------------- prompt

    private fun systemPrompt(request: TaskAssistanceRequest): String = buildString {
        append("You are StudyTrack's AI study assistant, running inside an Android app.\n")
        append("Current date: ${request.today ?: "unknown"} (timezone ${request.timezone ?: "unknown"}).\n")
        if (request.subjects.isNotEmpty()) {
            append("The student's existing subjects are: ")
            append(request.subjects.joinToString(", "))
            append(".\n")
        }
        request.taskContext?.let { ctx ->
            append("The student is asking about this existing task:")
            append(" id=${ctx.taskId ?: "?"}")
            ctx.title?.let { append(" title=\"$it\"") }
            ctx.dueDate?.let { append(" dueDate=$it") }
            append(".\n")
        }
        append(PROMPT_BODY)
    }

    private val PROMPT_BODY = """
You help students plan and organize their studying: suggest tasks, break big
goals into subtasks, and give brief study advice.

ALWAYS reply with ONE valid JSON object and nothing else — no markdown fences,
no commentary before or after. Exact shape:

{"replyText":"<short friendly reply shown as a chat bubble>","suggestions":[{"suggestionId":"sug-1","kind":"task","title":"...","description":"...","subjectId":null,"subjectName":"...","taskType":"Study","priority":"Medium","dueDate":"2026-09-24T17:00:00","reminderDate":null,"subtasks":[]}]}

Field rules:
- replyText: always present. Conversational, concise, encouraging.
- suggestions: usually []. Include only when the student is planning something
  concrete. At most 3 per reply.
- kind: "task", "breakdown" (a goal split into subtasks) or "study_plan".
- taskType: exactly one of "Assignment","Test","Exam","Project","Presentation","Study".
- priority: exactly one of "Low","Medium","High".
- dueDate, reminderDate and all subtask dates: ISO-8601 "yyyy-MM-ddTHH:mm:ss".
  Resolve relative dates ("tomorrow", "next Friday", "in 3 days") against the
  current date given above. If no time is given use 17:00 for due dates and
  09:00 for reminders. If you cannot confidently resolve the date, use null —
  the app will ask the student to pick one. NEVER guess or invent a date.
- subtasks: only for kind "breakdown"/"study_plan"; same field rules, never
  nested deeper than one level.
- subjectId: always null (the app resolves subjects itself). subjectName: when
  the suggestion clearly belongs to one of the student's existing subjects
  listed above, repeat that subject's name EXACTLY as written there (the app
  matches it back to the real subject); use a different name only when it is
  clearly a new subject; null when you cannot tell.
- The output must be valid JSON: double quotes, no trailing commas, no
  comments, no unescaped newlines inside strings.
    """.trimIndent()

    // ------------------------------------------------------------ parsing

    /**
     * The model is asked for pure JSON, but be defensive: extract the first
     * `{`…last `}` block and try to parse it. If anything fails, degrade
     * gracefully by showing the raw model output as a plain chat bubble.
     */
    private fun parseReply(content: String): TaskAssistanceResponse {
        val jsonText = extractJsonBlock(content)
        if (jsonText != null) {
            try {
                val parsed = RetrofitClient.json.decodeFromString(
                    TaskAssistanceResponse.serializer(),
                    jsonText,
                )
                return parsed.copy(
                    suggestions = parsed.suggestions.map(::ensureSuggestionId)
                )
            } catch (e: Exception) {
                // fall through to plain-text mode
            }
        }
        return TaskAssistanceResponse(
            replyText = content.trim(),
            suggestions = emptyList(),
        )
    }

    private fun extractJsonBlock(content: String): String? {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return content.substring(start, end + 1)
    }

    private fun ensureSuggestionId(suggestion: TaskSuggestion): TaskSuggestion =
        if (suggestion.suggestionId.isNullOrBlank()) {
            suggestion.copy(suggestionId = UUID.randomUUID().toString())
        } else {
            suggestion
        }
}
