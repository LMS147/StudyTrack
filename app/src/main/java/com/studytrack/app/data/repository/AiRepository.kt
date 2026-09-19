package com.studytrack.app.data.repository

import com.studytrack.app.data.model.TaskAssistanceRequest
import com.studytrack.app.data.model.TaskAssistanceResponse
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.safeApiCall

/**
 * The app's AI "brain" — one method, two implementations:
 * - [AiRepository]: POST /api/ai/task-assistance on the StudyTrack backend
 *   (preferred when a backend is deployed; keys stay server-side).
 * - [GrokAiRepository]: calls the xAI (Grok) chat-completions API directly
 *   from the app using a key from local.properties — for development and
 *   personal use without a backend.
 *
 * [ServiceLocator] picks the implementation at startup based on whether a
 * Grok key is configured.
 */
interface AiBrain {
    suspend fun taskAssistance(request: TaskAssistanceRequest): ApiResult<TaskAssistanceResponse>
}

/**
 * POST /api/ai/task-assistance. Everything the assistant creates goes through
 * the normal TaskRepository afterwards.
 */
class AiRepository(private val api: ApiService) : AiBrain {

    override suspend fun taskAssistance(request: TaskAssistanceRequest): ApiResult<TaskAssistanceResponse> =
        safeApiCall {
            api.taskAssistance(request)
        }
}
