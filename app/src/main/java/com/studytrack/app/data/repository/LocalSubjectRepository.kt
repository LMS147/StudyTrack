package com.studytrack.app.data.repository

import com.studytrack.app.data.model.Subject
import com.studytrack.app.data.model.SubjectPayload
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

/** [SubjectRepository] backed by [LocalStore] — no backend required. */
class LocalSubjectRepository(
    private val store: LocalStore,
) : SubjectRepository {

    override val subjects: StateFlow<List<Subject>> = store.subjects

    override suspend fun refresh(): ApiResult<List<Subject>> =
        ApiResult.Success(store.subjects.value)

    override suspend fun getSubject(subjectId: String): ApiResult<Subject> =
        store.subjects.value.firstOrNull { it.subjectId == subjectId }
            ?.let { ApiResult.Success(it) }
            ?: ApiResult.Error("Subject not found")

    override suspend fun create(payload: SubjectPayload): ApiResult<Subject> {
        val subject = Subject(
            subjectId = UUID.randomUUID().toString(),
            subjectName = payload.subjectName,
            description = payload.description,
        )
        store.mutateSubjects { it.add(subject) }
        return ApiResult.Success(subject)
    }

    override suspend fun update(
        subjectId: String,
        payload: SubjectPayload,
    ): ApiResult<Subject> {
        var updated: Subject? = null
        store.mutateSubjects { list ->
            val index = list.indexOfFirst { it.subjectId == subjectId }
            if (index >= 0) {
                updated = list[index].copy(
                    subjectName = payload.subjectName,
                    description = payload.description,
                )
                list[index] = updated!!
            }
        }
        return updated?.let { ApiResult.Success(it) }
            ?: ApiResult.Error("Subject not found")
    }

    /**
     * Local equivalent of the backend's archive: the subject disappears from
     * the list; tasks keep their subjectId (rendered as "No subject"), which
     * mirrors the REST contract's history-preserving semantics.
     */
    override suspend fun delete(subjectId: String): ApiResult<Unit> {
        store.mutateSubjects { list ->
            list.removeAll { it.subjectId == subjectId }
        }
        return ApiResult.Success(Unit)
    }
}
