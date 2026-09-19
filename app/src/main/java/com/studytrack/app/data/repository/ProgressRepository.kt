package com.studytrack.app.data.repository

import com.studytrack.app.data.model.Progress
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.safeApiCall

class ProgressRepository(private val api: ApiService) {

    /** GET /api/progress — points, completed-task count and current streak. */
    suspend fun getProgress(): ApiResult<Progress> = safeApiCall {
        api.getProgress()
    }
}
