package com.studytrack.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiBackendConfigTest {
    @Test fun `saved AI backend overrides the build and task backend`() {
        assertEquals("https://saved.example/", AiBackendConfig.resolve(
            "https://saved.example", "https://build.example/", "https://tasks.example/"))
    }
    @Test fun `AI build address works while tasks stay local`() {
        assertEquals("https://ai.example/", AiBackendConfig.resolve("", "https://ai.example", ""))
    }
    @Test fun `existing app backend remains the AI fallback`() {
        assertEquals("https://tasks.example/", AiBackendConfig.resolve("", "", "https://tasks.example"))
    }
    @Test fun `no configuration remains unconfigured instead of contacting a placeholder`() {
        assertEquals("", AiBackendConfig.resolve("", "", ""))
    }
    @Test fun `unsafe and ambiguous addresses are rejected before auth tokens can be sent`() {
        listOf("http://example.com", "https://user:pass@example.com", "https://example.com?token=secret",
            "https://example.com/#fragment", "https://example.com/api/", "not a url")
            .forEach { assertNull(it, AiBackendConfig.normalize(it)) }
    }
    @Test fun `HTTPS roots are normalized with a trailing slash`() {
        assertEquals("https://example.com/", AiBackendConfig.normalize("  https://example.com  "))
    }
}
