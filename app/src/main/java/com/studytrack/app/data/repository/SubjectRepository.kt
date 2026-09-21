package com.studytrack.app.data.repository

import com.studytrack.app.data.model.Subject
import com.studytrack.app.data.model.SubjectPayload
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.safeApiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException

/**
 * Subject CRUD surface. Two implementations:
 * - [RemoteSubjectRepository]: the REST API (when api.baseUrl is configured)
 * - [LocalSubjectRepository]: on-device storage (local mode, no backend)
 *
 * [com.studytrack.app.ServiceLocator] picks one at startup.
 */
interface SubjectRepository {

    val subjects: StateFlow<List<Subject>>

    suspend fun refresh(): ApiResult<List<Subject>>

    suspend fun getSubject(subjectId: String): ApiResult<Subject>

    suspend fun create(payload: SubjectPayload): ApiResult<Subject>

    suspend fun update(subjectId: String, payload: SubjectPayload): ApiResult<Subject>

    suspend fun delete(subjectId: String): ApiResult<Unit>
}

/**
 * Subject CRUD against the REST API, with an in-memory cache exposed as a
 * [StateFlow] so any screen (and the AI chat) can observe the current list.
 */
class RemoteSubjectRepository(private val api: ApiService) : SubjectRepository {

    private val _subjects = MutableStateFlow<List<Subject>>(emptyList())
    override val subjects: StateFlow<List<Subject>> = _subjects.asStateFlow()

    override suspend fun refresh(): ApiResult<List<Subject>> = safeApiCall {
        api.getSubjects().also { _subjects.value = it }
    }

    override suspend fun getSubject(subjectId: String): ApiResult<Subject> = safeApiCall {
        api.getSubject(subjectId)
    }

    override suspend fun create(payload: SubjectPayload): ApiResult<Subject> = safeApiCall {
        val created = api.createSubject(payload)
        refreshCacheBestEffort()
        created
    }

    override suspend fun update(subjectId: String, payload: SubjectPayload): ApiResult<Subject> = safeApiCall {
        val updated = api.updateSubject(subjectId, payload)
        refreshCacheBestEffort()
        updated
    }

    override suspend fun delete(subjectId: String): ApiResult<Unit> = safeApiCall {
        val response = api.deleteSubject(subjectId)
        if (!response.isSuccessful) throw HttpException(response)
        refreshCacheBestEffort()
        Unit
    }

    /**
     * After a successful mutation the list cache is re-fetched so every
     * observing screen stays in sync. Failure here is non-fatal — the mutation
     * already succeeded and the next explicit refresh() surfaces errors.
     */
    private suspend fun refreshCacheBestEffort() {
        try {
            _subjects.value = api.getSubjects()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // keep stale cache
        }
    }
}
