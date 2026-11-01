package com.studytrack.app.language

import com.studytrack.app.data.model.TaskAssistanceRequest
import com.studytrack.app.data.model.ChatTurn
import com.studytrack.app.data.remote.GrokApiService
import com.studytrack.app.data.remote.GrokChatMessage
import com.studytrack.app.data.remote.GrokChatRequest
import com.studytrack.app.data.remote.GrokChatResponse
import com.studytrack.app.data.remote.GrokChoice
import com.studytrack.app.data.repository.GrokAiRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Response

class AiLanguageTest {
    @Test fun `regional variants map to the supported app languages and unknown locales use English`() {
        for ((tag, expected) in mapOf("af-ZA" to "Afrikaans", "ja-JP" to "Japanese", "nso-ZA" to "Sepedi (Northern Sotho)", "tn-ZA" to "Setswana", "es-MX" to "Spanish", "fr-FR" to "French", "en" to "English", "de" to "English")) {
            assertEquals(expected, AppLanguage.name(tag))
        }
    }

    @Test fun `explicit app selection overrides old locale and device locale`() {
        assertEquals("en", AppLanguage.effectiveTag("en", "nso-ZA"))
        assertEquals("es", AppLanguage.effectiveTag("es", "nso-ZA"))
        assertEquals("af", AppLanguage.effectiveTag("af", "nso-ZA"))
        assertEquals("nso", AppLanguage.effectiveTag("", "nso-ZA"))
    }

    @Test fun `latest request repeats current language after Sepedi conversation`() = runBlocking {
        val requests = mutableListOf<GrokChatRequest>()
        val api = object : GrokApiService {
            override suspend fun chatCompletions(request: GrokChatRequest): Response<GrokChatResponse> {
                requests.add(request)
                return Response.success(GrokChatResponse(choices = listOf(GrokChoice(message = GrokChatMessage("assistant", "{\"replyText\":\"OK\",\"suggestions\":[]}")))))
            }
        }
        val brain = GrokAiRepository(api, "test")
        val history = listOf(ChatTurn("user", "Dumela"), ChatTurn("assistant", "Thobela"))
        for ((tag, language) in listOf("nso" to "Sepedi (Northern Sotho)", "en" to "English", "es" to "Spanish", "af" to "Afrikaans")) {
            brain.taskAssistance(TaskAssistanceRequest(message = "Hello", languageTag = tag, conversationHistory = history))
            val messages = requests.last().messages
            assertEquals("assistant", messages[messages.lastIndex - 1].role)
            assertEquals("Thobela", messages[messages.lastIndex - 1].content)
            assertEquals("user", messages.last().role)
            assertTrue(messages.last().content.startsWith("Hello"))
            assertTrue(messages.last().content.contains("Respond in $language"))
        }
    }

    @Test fun `Groq prompt switches language while retaining machine readable task fields`() = runBlocking {
        var sent: GrokChatRequest? = null
        val api = object : GrokApiService {
            override suspend fun chatCompletions(request: GrokChatRequest): Response<GrokChatResponse> {
                sent = request
                return Response.success(GrokChatResponse(choices = listOf(GrokChoice(message = GrokChatMessage("assistant", "{\"replyText\":\"Dumela\",\"suggestions\":[]}")))))
            }
        }
        val brain = GrokAiRepository(api, "test")
        for ((tag, language) in listOf("tn" to "Setswana", "ja" to "Japanese", "es" to "Spanish")) {
            brain.taskAssistance(TaskAssistanceRequest(message = "Hi", languageTag = tag))
            val prompt = sent!!.messages.first().content
            assertTrue(prompt.contains("in $language"))
            assertTrue(prompt.contains("taskType"))
            assertTrue(prompt.contains("priority"))
            assertTrue(prompt.contains("subject names exactly"))
        }
    }
}
