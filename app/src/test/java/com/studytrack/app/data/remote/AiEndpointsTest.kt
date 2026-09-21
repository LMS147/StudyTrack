package com.studytrack.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Provider inference for the LLM brain.
 *
 * The failure this pins down: dropping a Groq key (`gsk_…`) into a build that
 * still pointed at xAI's URL makes every request 401 and looks like a bad key.
 * The endpoint must follow the key.
 */
class AiEndpointsTest {

    @Test
    fun `a gsk_ key selects Groq`() {
        assertEquals(AiEndpoints.GROQ, AiEndpoints.forKey("gsk_some_groq_key"))
    }

    @Test
    fun `a non-gsk key falls back to xAI`() {
        assertEquals(AiEndpoints.XAI, AiEndpoints.forKey("xai_some_key"))
        assertEquals(AiEndpoints.XAI, AiEndpoints.forKey(""))
    }

    @Test
    fun `resolve uses the inferred Groq endpoint and model when nothing is pinned`() {
        val endpoint = AiEndpoints.resolve("gsk_key", explicitBaseUrl = "", explicitModel = "")
        assertEquals("https://api.groq.com/openai/v1/", endpoint.baseUrl)
        assertEquals("openai/gpt-oss-120b", endpoint.model)
    }

    @Test
    fun `explicit values always win over inference`() {
        val endpoint = AiEndpoints.resolve(
            apiKey = "gsk_key",
            explicitBaseUrl = "https://my.proxy.example/v1/",
            explicitModel = "openai/gpt-oss-20b",
        )
        assertEquals("https://my.proxy.example/v1/", endpoint.baseUrl)
        assertEquals("openai/gpt-oss-20b", endpoint.model)
    }

    @Test
    fun `an xAI key keeps the xAI endpoint when nothing is pinned`() {
        val endpoint = AiEndpoints.resolve("xai_key", explicitBaseUrl = "", explicitModel = "")
        assertEquals("https://api.x.ai/v1/", endpoint.baseUrl)
        assertEquals("grok-4", endpoint.model)
    }
}
