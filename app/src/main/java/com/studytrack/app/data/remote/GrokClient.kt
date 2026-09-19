package com.studytrack.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Minimal OpenAI-compatible chat-completions client. Works with any provider
 * that follows the OpenAI SDK convention: a base URL that already includes
 * the version segment (e.g. https://api.groq.com/openai/v1/ or
 * https://api.x.ai/v1/) plus a "chat/completions" path.
 *
 * Deliberately separate from [RetrofitClient]: no Firebase auth interceptor
 * or token authenticator — this client only attaches the LLM API key.
 */
@Serializable
data class GrokChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class GrokChatRequest(
    val model: String,
    val messages: List<GrokChatMessage>,
    val temperature: Double = 0.4,
)

@Serializable
data class GrokChoice(
    val index: Int = 0,
    val message: GrokChatMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class GrokChatResponse(
    val id: String? = null,
    val model: String? = null,
    val choices: List<GrokChoice> = emptyList(),
)

interface GrokApiService {

    @POST("chat/completions")
    suspend fun chatCompletions(@Body request: GrokChatRequest): Response<GrokChatResponse>
}

object GrokClient {

    fun apiService(apiKey: String): GrokApiService =
        Retrofit.Builder()
            .baseUrl(BuildConfig.GROK_BASE_URL)
            .client(
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("Authorization", "Bearer $apiKey")
                                .header("Content-Type", "application/json")
                                .build()
                        )
                    }
                    .connectTimeout(20, TimeUnit.SECONDS)
                    // LLMs can take a while to generate a full reply.
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()
            )
            .addConverterFactory(
                RetrofitClient.json.asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create(GrokApiService::class.java)
}
