package com.studytrack.app.data.repository

import com.studytrack.app.data.model.Subject
import com.studytrack.app.data.model.SubjectPayload
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Subject CRUD surface.
 *
 * Implemented by [OfflineFirstSubjectRepository] — Room first, then the REST
 * API when one is configured, always scoped to the signed-in account's UID.
 * [com.studytrack.app.ServiceLocator] wires it up.
 */
interface SubjectRepository {

    /** Live list for the signed-in account. Empty while signed out. */
    val subjects: StateFlow<List<Subject>>

    /** Pull-through refresh; resolves from the cache when offline. */
    suspend fun refresh(): ApiResult<List<Subject>>

    suspend fun getSubject(subjectId: String): ApiResult<Subject>

    suspend fun create(payload: SubjectPayload): ApiResult<Subject>

    suspend fun update(subjectId: String, payload: SubjectPayload): ApiResult<Subject>

    suspend fun delete(subjectId: String): ApiResult<Unit>
}
