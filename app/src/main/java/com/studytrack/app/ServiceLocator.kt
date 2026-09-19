package com.studytrack.app

import android.content.Context
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.data.remote.GrokClient
import com.studytrack.app.data.remote.RetrofitClient
import com.studytrack.app.data.repository.AiBrain
import com.studytrack.app.data.repository.AiRepository
import com.studytrack.app.data.repository.GrokAiRepository
import com.studytrack.app.data.repository.AuthRepository
import com.studytrack.app.data.repository.CalendarRepository
import com.studytrack.app.data.repository.ProgressRepository
import com.studytrack.app.data.repository.SettingsRepository
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.data.repository.TaskRepository

/**
 * Minimal manual dependency container (chosen over a DI framework to keep the
 * dependency list exactly as specified). Initialized once from [StudyTrackApp].
 */
object ServiceLocator {

    private lateinit var appContext: Context

    val authRepository: AuthRepository by lazy { AuthRepository() }

    val apiService: ApiService by lazy { RetrofitClient.apiService }

    val subjectRepository: SubjectRepository by lazy { SubjectRepository(apiService) }

    val taskRepository: TaskRepository by lazy { TaskRepository(apiService) }

    val calendarRepository: CalendarRepository by lazy { CalendarRepository(apiService) }

    val progressRepository: ProgressRepository by lazy { ProgressRepository(apiService) }

    /**
     * The assistant's brain. If a Grok/xAI key is configured in
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

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
