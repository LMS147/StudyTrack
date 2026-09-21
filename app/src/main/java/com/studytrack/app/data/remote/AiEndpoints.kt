package com.studytrack.app.data.remote

/**
 * Which OpenAI-compatible LLM endpoint to talk to, and with which model.
 *
 * The app's "AI brain" is deliberately provider-agnostic (any OpenAI-style
 * chat-completions service), so the only thing that differs between providers
 * is the base URL and a sensible default model. This object works out both from
 * the API key's well-known prefix, unless the user has explicitly overridden
 * them in local.properties.
 *
 * ## Why the prefix matters
 *
 * A Groq key starts with `gsk_`, an xAI key with `xai`. If someone drops in a
 * Groq key but the app silently kept pointing at xAI's URL (the historical
 * default), every request would 401 and read like a broken key. Deriving the
 * endpoint from the key makes a single-line config just work.
 *
 * Pure JVM, no Android imports, so the mapping is unit-testable.
 */
object AiEndpoints {

    /** Groq (https://console.groq.com). Current production models, verified. */
    val GROQ = AiEndpoint(
        baseUrl = "https://api.groq.com/openai/v1/",
        defaultModel = "openai/gpt-oss-120b",
    )

    /** xAI Grok (https://console.x.ai). */
    val XAI = AiEndpoint(
        baseUrl = "https://api.x.ai/v1/",
        defaultModel = "grok-4",
    )

    /** Picks a provider from the API key's prefix. */
    fun forKey(apiKey: String): AiEndpoint = when {
        apiKey.trim().startsWith("gsk_") -> GROQ
        else -> XAI
    }

    /**
     * Final endpoint to use. Explicit non-blank values from local.properties
     * always win, so an advanced user can still pin any provider/model.
     */
    fun resolve(apiKey: String, explicitBaseUrl: String, explicitModel: String): AiEndpoint {
        val defaults = forKey(apiKey)
        return AiEndpoint(
            baseUrl = explicitBaseUrl.takeIf { it.isNotBlank() } ?: defaults.baseUrl,
            model = explicitModel.takeIf { it.isNotBlank() } ?: defaults.defaultModel,
        )
    }
}

/** A concrete LLM endpoint: where to send chat-completions, and a default model. */
data class AiEndpoint(
    val baseUrl: String,
    val defaultModel: String,
)
