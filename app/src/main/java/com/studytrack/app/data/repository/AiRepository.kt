package com.studytrack.app.data.repository

import com.studytrack.app.data.model.TaskAssistanceRequest
import com.studytrack.app.data.model.TaskAssistanceResponse
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.safeApiCall

/**
 * POST /api/ai/task-assistance. The only AI entry point — everything the
 * assistant creates goes through the normal TaskRepository afterwards.
 */
class AiRepository(private val api: ApiService) {

    suspend fun taskAssistance(request: TaskAssistanceRequest): ApiResult<TaskAssistanceResponse> =
        safeApiCall {
            api.taskAssistance(request)
        }
}
