package com.studytrack.app.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class GroqConnectionTest {
    @Test fun `saved key wins and a local build key remains a fallback`() {
        assertEquals("gsk_device", GroqConnectionConfig.key(" gsk_device ", "gsk_build"))
        assertEquals("gsk_build", GroqConnectionConfig.key("", " gsk_build "))
        assertEquals("", GroqConnectionConfig.key("", ""))
    }
    @Test fun `device key input rejects URLs non Groq credentials and whitespace`() {
        assertTrue(GroqConnectionConfig.validDeviceKey("gsk_test_key"))
        listOf("", "gsk_", "xai_test_key", "https://example.com", "gsk_with space")
            .forEach { assertFalse(it, GroqConnectionConfig.validDeviceKey(it)) }
    }
    @Test fun `direct client sends Groq bearer auth to chat completions without Firebase`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"role":"assistant","content":"Study one topic at a time."}}]}"""))
            val api = GrokClient.apiService("gsk_fixture_only", server.url("/openai/v1/").toString())
            val response = api.chatCompletions(GrokChatRequest(
                model = AiEndpoints.GROQ.model,
                messages = listOf(GrokChatMessage("user", "Help me study"))))
            assertTrue(response.isSuccessful)
            assertEquals("Study one topic at a time.", response.body()!!.choices.first().message!!.content)
            val request = server.takeRequest()
            assertEquals("/openai/v1/chat/completions", request.path)
            assertEquals("Bearer gsk_fixture_only", request.getHeader("Authorization"))
            assertTrue(request.body.readUtf8().contains("Help me study"))
        } finally { server.shutdown() }
    }
}
