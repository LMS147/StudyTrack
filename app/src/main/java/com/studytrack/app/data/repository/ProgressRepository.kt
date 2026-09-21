package com.studytrack.app.data.repository

import com.studytrack.app.data.model.Progress
import com.studytrack.app.util.ApiResult

/**
 * Progress surface.
 *
 * Implemented by [OfflineFirstProgressRepository]: cached per account in Room,
 * refreshed from `GET /api/progress` when online, and derived from the cached
 * tasks when neither the cache nor the network can answer — so the Progress
 * screen never shows a misleading zero after working offline.
 */
interface ProgressRepository {

    suspend fun getProgress(): ApiResult<Progress>
}
