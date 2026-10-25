package com.studytrack.app.data.repository

import com.studytrack.app.data.model.Task
import com.studytrack.app.data.model.TaskPayload
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Task CRUD surface.
 *
 * Implemented by [OfflineFirstTaskRepository]: every read comes from the local
 * Room cache and every write lands there first, then queues for the REST API
 * when one is configured. With no `api.baseUrl` the cache *is* the source of
 * truth. All access is scoped to the signed-in account's UID.
 *
 * Also the single write path used by the AI chat to create accepted
 * suggestions.
 *
 * The previous remote-only implementation was removed rather than kept as an
 * alternative: two write paths meant two places to get per-account scoping
 * right, and the offline-first implementation already covers the online case.
 */
interface TaskRepository {

    /** Live list for the signed-in account. Empty while signed out. */
    val tasks: StateFlow<List<Task>>

    /**
     * Pull-through refresh. Offline this resolves from the cache instead of
     * failing, so screens keep working with no connectivity.
     */
    suspend fun refresh(): ApiResult<List<Task>>

    suspend fun getTask(taskId: String): ApiResult<Task>

    suspend fun create(payload: TaskPayload): ApiResult<Task>

    suspend fun update(taskId: String, payload: TaskPayload): ApiResult<Task>

    suspend fun delete(taskId: String): ApiResult<Unit>

    suspend fun setCompleted(taskId: String, completed: Boolean): ApiResult<Task>
}
