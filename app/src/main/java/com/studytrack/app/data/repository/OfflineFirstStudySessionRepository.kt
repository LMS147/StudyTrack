package com.studytrack.app.data.repository

import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.local.SyncStatus
import com.studytrack.app.data.local.entity.StudySessionEntity
import com.studytrack.app.data.local.toPayload
import com.studytrack.app.data.model.StudySessionPayload
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.data.sync.SyncOutcome
import com.studytrack.app.data.sync.SyncScheduler
import com.studytrack.app.data.sync.SyncableRepository
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.studytrack.app.data.local.toModel
import java.util.UUID

/**
 * Sessions the user has logged.
 *
 * There is no read endpoint on the API today, so this is a pure write queue:
 * a session logged offline is stored against the active account's UID and
 * replayed later. The on-device `sessionId` is generated once and reused as the
 * idempotency key, so a retried push cannot log the same session twice.
 */
interface StudySessionRepository {
    /** Sessions for the active account, newest first. */
    fun observeSessions(): Flow<List<StudySessionEntity>>

    suspend fun logSession(payload: StudySessionPayload): ApiResult<StudySessionEntity>
}

class OfflineFirstStudySessionRepository(
    private val db: StudyTrackDatabase,
    private val api: ApiService?,
    private val currentAccount: CurrentAccount,
    private val syncScheduler: SyncScheduler,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : StudySessionRepository, SyncableRepository {

    override val hasRemote: Boolean get() = api != null

    /**
     * Scoped to whichever account is active. The Flow completes with an empty
     * list while signed out rather than emitting another account's rows.
     */
    override fun observeSessions(): Flow<List<StudySessionEntity>> {
        val uid = currentAccount.activeUid.value
            ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return db.studySessionDao().observeForOwner(uid)
    }

    override suspend fun logSession(payload: StudySessionPayload): ApiResult<StudySessionEntity> {
        val uid = currentAccount.requireUid()
        val now = clock()
        val entity = StudySessionEntity(
            ownerUid = uid,
            sessionId = UUID.randomUUID().toString(),
            subjectId = payload.subjectId,
            taskId = payload.taskId,
            durationMinutes = payload.durationMinutes,
            studiedAt = payload.studiedAt,
            notes = payload.notes,
            syncStatus = if (hasRemote) SyncStatus.CREATED else SyncStatus.SYNCED,
            localUpdatedAt = now,
            remoteUpdatedAt = now,
        )
        db.studySessionDao().upsert(entity)
        if (hasRemote) syncScheduler.requestSync()
        return ApiResult.Success(entity)
    }

    override suspend fun pushPending(ownerUid: String): SyncOutcome {
        val remote = api ?: return SyncOutcome.Skipped
        val dao = db.studySessionDao()
        var pushed = 0
        for (row in dao.pendingForOwner(ownerUid)) {
            runCatching {
                when (row.syncStatus) {
                    SyncStatus.DELETED -> dao.purge(ownerUid, row.sessionId)
                    else -> {
                        val response = remote.logStudySession(row.toPayload())
                        if (!response.isSuccessful) error("log failed (${response.code()})")
                        dao.markSynced(ownerUid, row.sessionId, remoteUpdatedAt = clock())
                    }
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                return SyncOutcome.Failure(
                    "Study session sync failed: ${e.message ?: e.javaClass.simpleName}", e
                )
            }
            pushed++
        }
        return SyncOutcome.Success(pushed = pushed)
    }

    /** The API has no session read endpoint, so there is nothing to pull. */
    override suspend fun pullRemote(ownerUid: String): SyncOutcome = SyncOutcome.Skipped
}
