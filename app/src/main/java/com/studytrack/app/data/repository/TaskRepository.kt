package com.studytrack.app.data.repository

import com.studytrack.app.data.model.CompleteTaskPayload
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.model.TaskPayload
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.safeApiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException

/**
 * Task CRUD surface. Two implementations:
 * - [RemoteTaskRepository]: the REST API (when api.baseUrl is configured)
 * - [LocalTaskRepository]: on-device storage (local mode, no backend)
 *
 * Also the single write path used by the AI chat to create accepted
 * suggestions.
 */
interface TaskRepository {

    val tasks: StateFlow<List<Task>>

    suspend fun refresh(): ApiResult<List<Task>>

    suspend fun getTask(taskId: String): ApiResult<Task>

    suspend fun create(payload: TaskPayload): ApiResult<Task>

    suspend fun update(taskId: String, payload: TaskPayload): ApiResult<Task>

    suspend fun delete(taskId: String): ApiResult<Unit>

    suspend fun setCompleted(taskId: String, completed: Boolean): ApiResult<Task>
}

/** Task CRUD against the REST API. */
class RemoteTaskRepository(private val api: ApiService) : TaskRepository {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    override val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    override suspend fun refresh(): ApiResult<List<Task>> = safeApiCall {
        api.getTasks().also { _tasks.value = it }
    }

    override suspend fun getTask(taskId: String): ApiResult<Task> = safeApiCall {
        api.getTask(taskId)
    }

    override suspend fun create(payload: TaskPayload): ApiResult<Task> = safeApiCall {
        val created = api.createTask(payload)
        refreshCacheBestEffort()
        created
    }

    override suspend fun update(taskId: String, payload: TaskPayload): ApiResult<Task> = safeApiCall {
        val updated = api.updateTask(taskId, payload)
        refreshCacheBestEffort()
        updated
    }

    override suspend fun delete(taskId: String): ApiResult<Unit> = safeApiCall {
        val response = api.deleteTask(taskId)
        if (!response.isSuccessful) throw HttpException(response)
        refreshCacheBestEffort()
        Unit
    }

    /** PATCH /api/tasks/{id}/complete — toggles completion state. */
    override suspend fun setCompleted(taskId: String, completed: Boolean): ApiResult<Task> = safeApiCall {
        val updated = api.setTaskCompleted(taskId, CompleteTaskPayload(completed))
        refreshCacheBestEffort()
        updated
    }

    private suspend fun refreshCacheBestEffort() {
        try {
            _tasks.value = api.getTasks()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // keep stale cache
        }
    }
}
