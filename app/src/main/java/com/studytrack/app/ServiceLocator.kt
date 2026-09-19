package com.studytrack.app

import android.content.Context
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.data.remote.GrokClient
import com.studytrack.app.data.remote.RetrofitClient
import com.studytrack.app.data.repository.AiBrain
import com.studytrack.app.data.repository.AiRepository
import com.studytrack.app.data.repository.CalendarRepository
import com.studytrack.app.data.repository.GrokAiRepository
import com.studytrack.app.data.repository.LocalCalendarRepository
import com.studytrack.app.data.repository.LocalProgressRepository
import com.studytrack.app.data.repository.LocalStore
import com.studytrack.app.data.repository.LocalSubjectRepository
import com.studytrack.app.data.repository.LocalTaskRepository
import com.studytrack.app.data.repository.ProgressRepository
import com.studytrack.app.data.repository.RemoteCalendarRepository
import com.studytrack.app.data.repository.RemoteProgressRepository
import com.studytrack.app.data.repository.RemoteSubjectRepository
import com.studytrack.app.data.repository.RemoteTaskRepository
import com.studytrack.app.data.repository.SettingsRepository
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.data.repository.TaskRepository

/**
 * Minimal manual dependency container (chosen over a DI framework to keep the
 * dependency list exactly as specified). Initialized once from [StudyTrackApp].
 *
 * DATA MODE is decided here, once, at startup:
 * - api.baseUrl set (local.properties)  → REST repositories against the backend
 * - api.baseUrl blank (default)         → LOCAL MODE: everything persists
 *   on-device via [LocalStore]; no backend is ever contacted
 */
object ServiceLocator {

    private lateinit var appContext: Context

    /** True when no backend is configured — repositories run on-device. */
    val isLocalMode: Boolean get() = RetrofitClient.BASE_URL.isBlank()

    val authRepository: com.studytrack.app.data.repository.AuthRepository by lazy {
        com.studytrack.app.data.repository.AuthRepository()
    }

    private val localStore: LocalStore by lazy { LocalStore(appContext) }

    val subjectRepository: SubjectRepository by lazy {
        if (isLocalMode) LocalSubjectRepository(localStore)
        else RemoteSubjectRepository(apiService)
    }

    val taskRepository: TaskRepository by lazy {
        if (isLocalMode) LocalTaskRepository(localStore)
        else RemoteTaskRepository(apiService)
    }

    val calendarRepository: CalendarRepository by lazy {
        if (isLocalMode) LocalCalendarRepository(localStore)
        else RemoteCalendarRepository(apiService)
    }

    val progressRepository: ProgressRepository by lazy {
        if (isLocalMode) LocalProgressRepository(localStore)
        else RemoteProgressRepository(apiService)
    }

    /**
     * The assistant's brain. If a Groq/xAI key is configured in
     * local.properties (BuildConfig.GROK_API_KEY), chat directly with the LLM;
     * otherwise use the StudyTrack backend endpoint.
     */
    val aiBrain: AiBrain by lazy {
        if (BuildConfig.GROK_API_KEY.isNotBlank()) {
            GrokAiRepository(GrokClient.apiService(BuildConfig.GROK_API_KEY))
        } else {
            AiRepository(apiService)
        }
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext)
    }

    /** Only touch this in remote mode (Retrofit rejects a blank base URL). */
    val apiService: ApiService by lazy {
        check(!isLocalMode) { "apiService requested in local mode — no backend configured" }
        RetrofitClient.apiService
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** AI brain used when local mode has no LLM key configured. */
    private object NoBrainConfigured : AiBrain {
        override suspend fun taskAssistance(
            request: com.studytrack.app.data.model.TaskAssistanceRequest,
        ): com.studytrack.app.util.ApiResult<com.studytrack.app.data.model.TaskAssistanceResponse> =
            com.studytrack.app.util.ApiResult.Error(
                "No AI configured. Add grok.apiKey to local.properties " +
                    "(Groq/xAI key — see local.properties.example), or set " +
                    "api.baseUrl to your StudyTrack backend."
            )
    }
}
