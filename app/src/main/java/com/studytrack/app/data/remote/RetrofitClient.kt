package com.studytrack.app.data.remote

//import com.google.firebase.BuildConfig
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton Retrofit setup.
 *
 * - [AuthInterceptor] attaches the Firebase ID token to every /api/ request;
 *   [TokenAuthenticator] force-refreshes and retries once on 401.
 * - kotlinx.serialization converter with:
 *     - unknown JSON keys ignored (backend can add fields safely),
 *     - nulls omitted from request bodies,
 *     - defaults (taskType/priority) always included,
 *     - unknown enum values coerced to their defaults.
 * - Full body logging in debug builds only.
 */
object RetrofitClient {

    /** Placeholder base URL — point this at your FastAPI instance. Trailing slash required. */
    const val BASE_URL = "https://api.studytrack.example.com/"

    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .authenticator(TokenAuthenticator())
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                        }
                    )
                }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
