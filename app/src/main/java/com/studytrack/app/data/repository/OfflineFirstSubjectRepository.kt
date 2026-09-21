package com.studytrack.app.data.repository

import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.local.SyncStatus
import com.studytrack.app.data.local.entity.SubjectEntity
import com.studytrack.app.data.local.toEntity
import com.studytrack.app.data.local.toModel
import com.studytrack.app.data.local.toPayload
import com.studytrack.app.data.model.Subject
import com.studytrack.app.data.model.SubjectPayload
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.data.sync.ConflictResolver
import com.studytrack.app.data.sync.SyncOutcome
import com.studytrack.app.data.sync.SyncScheduler
import com.studytrack.app.data.sync.SyncableRepository
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/**
 * Offline-first subjects — same contract as [OfflineFirstTaskRepository]:
 * Room is the read path, writes land locally first and queue for the remote,
 * and every query is scoped to the active account's UID.
 *
 * Subjects are synced before tasks on purpose (see [com.studytrack.app.data.sync.SyncWorker]):
 * a task references a subject, so pushing subjects first means a freshly
 * created subject already exists remotely by the time its first task arrives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstSubjectRepository(
    private val db: StudyTrackDatabase,
    private val api: ApiService?,
    private val currentAccount: CurrentAccount,
    private val syncScheduler: SyncScheduler,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : SubjectRepository, SyncableRepository {

    override val hasRemote: Boolean get() = api != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val subjects: StateFlow<List<Subject>> = currentAccount.activeUid
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else db.subjectDao().observeForOwner(uid).map { rows -> rows.map { it.toModel() } }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override suspend fun refresh(): ApiResult<List<Subject>> {
        // Read path: nobody signed in means nothing to show, not a crash.
        val uid = currentAccount.uidOrNull() ?: return ApiResult.Success(emptyList())
        val dao = db.subjectDao()
        if (hasRemote) {
            val pull = pullRemote(uid)
            if (pull is SyncOutcome.Failure) {
                val cached = dao.getAllForOwner(uid).map { it.toModel() }
                return if (cached.isNotEmpty()) ApiResult.Success(cached)
                else ApiResult.Error(pull.message, pull.cause)
            }
        }
        return ApiResult.Success(dao.getAllForOwner(uid).map { it.toModel() })
    }

    override suspend fun getSubject(subjectId: String): ApiResult<Subject> {
        val uid = currentAccount.uidOrNull() ?: return ApiResult.Error("Not signed in")
        return db.subjectDao().getById(uid, subjectId)
            ?.let { ApiResult.Success(it.toModel()) }
            ?: ApiResult.Error("Subject not found")
    }

    override suspend fun create(payload: SubjectPayload): ApiResult<Subject> {
        val uid = currentAccount.requireUid()
        val now = clock()
        val subject = Subject(
            subjectId = UUID.randomUUID().toString(),
            subjectName = payload.subjectName,
            description = payload.description,
        )
        db.subjectDao().upsert(subject.toEntity(uid, initialStatus(SyncStatus.CREATED), now, now))
        afterLocalWrite()
        return ApiResult.Success(subject)
    }

    override suspend fun update(subjectId: String, payload: SubjectPayload): ApiResult<Subject> {
        val uid = currentAccount.requireUid()
        val dao = db.subjectDao()
        val existing = dao.getById(uid, subjectId) ?: return ApiResult.Error("Subject not found")
        val updated = existing.copy(
            subjectName = payload.subjectName,
            description = payload.description,
            syncStatus = nextWriteStatus(existing.syncStatus),
            localUpdatedAt = clock(),
        )
        dao.upsert(updated)
        afterLocalWrite()
        return ApiResult.Success(updated.toModel())
    }

    override suspend fun delete(subjectId: String): ApiResult<Unit> {
        val uid = currentAccount.requireUid()
        db.subjectDao().markDeleted(uid, subjectId, clock())
        afterLocalWrite()
        return ApiResult.Success(Unit)
    }

    // ------------------------------------------------------------- sync worker

    override suspend fun pushPending(ownerUid: String): SyncOutcome {
        val remote = api ?: return SyncOutcome.Skipped
        val dao = db.subjectDao()
        var pushed = 0

        for (row in dao.pendingForOwner(ownerUid)) {
            runCatching {
                when (row.syncStatus) {
                    SyncStatus.DELETED -> {
                        val response = remote.deleteSubject(row.subjectId)
                        if (!response.isSuccessful && response.code() != 404) {
                            error("delete failed (${response.code()})")
                        }
                        dao.purgeTombstone(ownerUid, row.subjectId)
                    }

                    SyncStatus.CREATED -> {
                        val saved = remote.createSubject(row.toPayload())
                        if (saved.subjectId != row.subjectId) {
                            dao.purgeTombstone(ownerUid, row.subjectId)
                            dao.upsert(
                                saved.toEntity(ownerUid, SyncStatus.SYNCED, clock(), clock())
                            )
                        } else {
                            dao.markSynced(ownerUid, row.subjectId, remoteUpdatedAt = clock())
                        }
                    }

                    SyncStatus.UPDATED -> {
                        remote.updateSubject(row.subjectId, row.toPayload())
                        dao.markSynced(ownerUid, row.subjectId, remoteUpdatedAt = clock())
                    }

                    SyncStatus.SYNCED -> Unit
                }
            }.onFailure { e ->
                if (e is CancellationException) throw e
                return SyncOutcome.Failure(
                    "Subject sync failed: ${e.message ?: e.javaClass.simpleName}", e
                )
            }
            pushed++
        }
        return SyncOutcome.Success(pushed = pushed)
    }

    override suspend fun pullRemote(ownerUid: String): SyncOutcome {
        val remote = api ?: return SyncOutcome.Skipped
        val dao = db.subjectDao()
        val fetchedAt = clock()

        val remoteSubjects = runCatching { remote.getSubjects() }.getOrElse { e ->
            if (e is CancellationException) throw e
            return SyncOutcome.Failure(
                "Could not reach the server: ${e.message ?: "network error"}", e
            )
        }

        val localRows = dao.getAllForOwner(ownerUid).associateBy { it.subjectId }
        val accepted = mutableListOf<SubjectEntity>()

        for (remoteSubject in remoteSubjects) {
            val local = localRows[remoteSubject.subjectId]
            if (ConflictResolver.remoteWins(local?.syncStatus, local?.localUpdatedAt ?: 0L, fetchedAt)) {
                accepted += remoteSubject.toEntity(
                    ownerUid = ownerUid,
                    syncStatus = SyncStatus.SYNCED,
                    localUpdatedAt = fetchedAt,
                    remoteUpdatedAt = fetchedAt,
                )
            }
        }

        dao.replaceOwnerSnapshot(
            remoteRows = accepted,
            ownerUid = ownerUid,
            keepSubjectIds = dao.pendingForOwner(ownerUid).map { it.subjectId },
        )
        return SyncOutcome.Success(pulled = accepted.size)
    }

    fun close() {
        scope.cancel()
    }

    private fun afterLocalWrite() {
        if (hasRemote) syncScheduler.requestSync()
    }

    private fun initialStatus(remoteStatus: SyncStatus): SyncStatus =
        if (hasRemote) remoteStatus else SyncStatus.SYNCED

    private fun nextWriteStatus(previous: SyncStatus): SyncStatus = when {
        !hasRemote -> SyncStatus.SYNCED
        previous == SyncStatus.CREATED -> SyncStatus.CREATED
        else -> SyncStatus.UPDATED
    }
}
