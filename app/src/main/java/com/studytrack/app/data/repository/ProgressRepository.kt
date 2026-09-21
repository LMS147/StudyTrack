package com.studytrack.app.data.repository

import com.studytrack.app.data.model.Progress
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.safeApiCall

/**
 * Progress surface. Two implementations:
 * - [RemoteProgressRepository]: GET /api/progress (when api.baseUrl is set)
 * - [LocalProgressRepository]: computed on-device from the local task store
 */
interface ProgressRepository {

    suspend fun getProgress(): ApiResult<Progress>
}

class RemoteProgressRepository(private val api: ApiService) : ProgressRepository {

    /** GET /api/progress — points, completed-task count and current streak. */
    override suspend fun getProgress(): ApiResult<Progress> = safeApiCall {
        api.getProgress()
    }
}
