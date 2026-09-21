package com.studytrack.app.data.repository

import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.local.SyncStatus
import com.studytrack.app.data.local.applyPayload
import com.studytrack.app.data.local.entity.TaskEntity
import com.studytrack.app.data.local.toEntity
import com.studytrack.app.data.local.toModel
import com.studytrack.app.data.local.toPayload
import com.studytrack.app.data.model.CompleteTaskPayload
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.model.TaskPayload
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.data.sync.ConflictResolver
import com.studytrack.app.data.sync.SyncOutcome
import com.studytrack.app.data.sync.SyncScheduler
import com.studytrack.app.data.sync.SyncableRepository
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.DateTimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime
import java.util.UUID

/**
 * Offline-first tasks: Room is the read path, the REST API is the remote.
 *
 * ## Read path
 *
 * [tasks] is a live query on `tasks WHERE ownerUid = :uid`. When the active
 * account changes, `flatMapLatest` tears down the old query and starts a new
 * one scoped to the new UID — so the UI cannot keep showing the previous
 * account's rows for even one frame. There is no in-memory list to forget to
 * clear, which is the classic source of cross-account leakage.
 *
 * ## Write path
 *
 * Every mutation writes to Room first and returns immediately. The row is
 * stamped `CREATED` / `UPDATED` / `DELETED` and a background sync is requested;
 * the UI updates whether or not the device is online. In local mode (no
 * `api.baseUrl`) rows are written as `SYNCED` because the cache *is* the source
 * of truth and there is nothing to push to.
 *
 * ## Scope
 *
 * `ownerUid` is never a parameter of the public methods. It is read from
 * [CurrentAccount] inside each one, so no caller — fragment, ViewModel, or the
 * AI chat's write path — can pass the wrong account.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstTaskRepository(
    private val db: StudyTrackDatabase,
    private val api: ApiService?,
    private val currentAccount: CurrentAccount,
    private val syncScheduler: SyncScheduler,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : TaskRepository, SyncableRepository {

    override val hasRemote: Boolean get() = api != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val tasks: StateFlow<List<Task>> = currentAccount.activeUid
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else db.taskDao().observeForOwner(uid).map { rows -> rows.map { it.toModel() } }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Pull-through refresh.
     *
     * Offline-first means a failed fetch is not an error to the user: the
     * cached rows are returned instead. Only when there is *no* cache and *no*
     * connectivity does this surface an error, because then there is genuinely
     * nothing to show.
     */
    override suspend fun refresh(): ApiResult<List<Task>> {
        // Read path: nobody signed in means nothing to show, not a crash.
        val uid = currentAccount.uidOrNull() ?: return ApiResult.Success(emptyList())
        val dao = db.taskDao()

        if (hasRemote) {
            val pull = pullRemote(uid)
            if (pull is SyncOutcome.Failure) {
                val cached = dao.getAllForOwner(uid).map { it.toModel() }
                return if (cached.isNotEmpty()) {
                    ApiResult.Success(cached)
                } else {
                    ApiResult.Error(pull.message, pull.cause)
                }
            }
        }
        return ApiResult.Success(dao.getAllForOwner(uid).map { it.toModel() })
    }

    override suspend fun getTask(taskId: String): ApiResult<Task> {
        val uid = currentAccount.uidOrNull() ?: return ApiResult.Error("Not signed in")
        return db.taskDao().getById(uid, taskId)
            ?.let { ApiResult.Success(it.toModel()) }
            ?: ApiResult.Error("Task not found")
    }

    override suspend fun create(payload: TaskPayload): ApiResult<Task> {
        val uid = currentAccount.requireUid()
        val now = clock()
        val task = Task(
            taskId = UUID.randomUUID().toString(),
            title = payload.title,
            subjectId = payload.subjectId,
            description = payload.description,
            taskType = payload.taskType,
            priority = payload.priority,
            dueDate = payload.dueDate,
            reminderDate = payload.reminderDate,
        )
        db.taskDao().upsert(
            task.toEntity(uid, initialStatus(SyncStatus.CREATED), now, now)
        )
        afterLocalWrite()
        return ApiResult.Success(task)
    }

    override suspend fun update(taskId: String, payload: TaskPayload): ApiResult<Task> {
        val uid = currentAccount.requireUid()
        val dao = db.taskDao()
        val existing = dao.getById(uid, taskId) ?: return ApiResult.Error("Task not found")
        // A row that has never been pushed stays CREATED — the remote has not
        // heard of it yet, so a PUT would 404. In local mode nothing is ever
        // pending, so the row goes straight to SYNCED.
        val updated = existing.applyPayload(payload, clock()).copy(
            syncStatus = nextWriteStatus(existing.syncStatus),
        )
        dao.upsert(updated)
        afterLocalWrite()
        return ApiResult.Success(updated.toModel())
    }

    /** Soft delete: tombstones the row so the queued push can delete it remotely. */
    override suspend fun delete(taskId: String): ApiResult<Unit> {
        val uid = currentAccount.requireUid()
        db.taskDao().markDeleted(uid, taskId, clock())
        afterLocalWrite()
        return ApiResult.Success(Unit)
    }

    override suspend fun setCompleted(taskId: String, completed: Boolean): ApiResult<Task> {
        val uid = currentAccount.requireUid()
        val dao = db.taskDao()
        val existing = dao.getById(uid, taskId) ?: return ApiResult.Error("Task not found")
        val updated = existing.copy(
            completed = completed,
            completedAt = if (completed) DateTimeUtils.formatIso(LocalDateTime.now()) else null,
            points = when {
                !completed -> 0
                existing.points > 0 -> existing.points
                else -> pointsFor(Priority.fromRaw(existing.priority))
            },
            // Completion goes out via the PATCH endpoint, not PUT, so the row
            // is marked UPDATED and the push path sends both.
            syncStatus = nextWriteStatus(existing.syncStatus),
            localUpdatedAt = clock(),
        )
        dao.upsert(updated)
        afterLocalWrite()
        return ApiResult.Success(updated.toModel())
    }

    // ------------------------------------------------------------- sync worker

    override suspend fun pushPending(ownerUid: String): SyncOutcome {
        val remote = api ?: return SyncOutcome.Skipped
        val dao = db.taskDao()
        var pushed = 0

        for (row in dao.pendingForOwner(ownerUid)) {
            val result = runCatching {
                when (row.syncStatus) {
                    SyncStatus.DELETED -> {
                        val response = remote.deleteTask(row.taskId)
                        if (!response.isSuccessful && response.code() != 404) {
                            error("delete failed (${response.code()})")
                        }
                        // 404 means it is already gone remotely — the tombstone
                        // has done its job either way, so purge it.
                        dao.purgeTombstone(ownerUid, row.taskId)
                    }

                    SyncStatus.CREATED -> {
                        val saved = remote.createTask(row.toPayload())
                        if (saved.taskId != row.taskId) {
                            // The backend assigned its own id: adopt it and
                            // drop the provisional local row.
                            dao.purgeTombstone(ownerUid, row.taskId)
                            dao.upsert(
                                saved.toEntity(
                                    ownerUid = ownerUid,
                                    syncStatus = SyncStatus.SYNCED,
                                    localUpdatedAt = clock(),
                                    remoteUpdatedAt = clock(),
                                )
                            )
                        } else {
                            dao.markSynced(ownerUid, row.taskId, remoteUpdatedAt = clock())
                        }
                    }

                    SyncStatus.UPDATED -> {
                        val saved = remote.updateTask(row.taskId, row.toPayload())
                        // Completion is a separate endpoint; sending it only
                        // when it actually differs avoids a needless write.
                        if (saved.completed != row.completed) {
                            remote.setTaskCompleted(
                                row.taskId,
                                CompleteTaskPayload(row.completed)
                            )
                        }
                        dao.markSynced(ownerUid, row.taskId, remoteUpdatedAt = clock())
                    }

                    SyncStatus.SYNCED -> Unit
                }
            }

            result.onFailure { e ->
                if (e is CancellationException) throw e
                return SyncOutcome.Failure(
                    "Task sync failed: ${e.message ?: e.javaClass.simpleName}", e
                )
            }
            pushed++
        }
        return SyncOutcome.Success(pushed = pushed)
    }

    /**
     * Applies the remote snapshot to one account only.
     *
     * The wire `Task` model carries no server-side `updatedAt`, so true
     * timestamp LWW against the remote is not expressible yet. What this
     * implements is the safe subset: **an unpushed local edit or delete always
     * wins**, and for rows both sides already agree on the remote is
     * authoritative. See `docs/OFFLINE_SYNC.md` for how adding a server
     * timestamp upgrades this to full LWW.
     */
    override suspend fun pullRemote(ownerUid: String): SyncOutcome {
        val remote = api ?: return SyncOutcome.Skipped
        val dao = db.taskDao()
        val fetchedAt = clock()

        val remoteTasks = runCatching { remote.getTasks() }
            .getOrElse { e ->
                if (e is CancellationException) throw e
                return SyncOutcome.Failure(
                    "Could not reach the server: ${e.message ?: "network error"}", e
                )
            }

        val localRows = dao.getAllForOwner(ownerUid).associateBy { it.taskId }
        val accepted = mutableListOf<TaskEntity>()

        for (remoteTask in remoteTasks) {
            val local = localRows[remoteTask.taskId]
            val wins = ConflictResolver.remoteWins(
                localSyncStatus = local?.syncStatus,
                localUpdatedAt = local?.localUpdatedAt ?: 0L,
                remoteUpdatedAt = fetchedAt,
            )
            if (wins) {
                accepted += remoteTask.toEntity(
                    ownerUid = ownerUid,
                    syncStatus = SyncStatus.SYNCED,
                    localUpdatedAt = fetchedAt,
                    remoteUpdatedAt = fetchedAt,
                )
            }
        }

        // Rows with unpushed local edits are preserved through the replace.
        // replaceOwnerSnapshot is itself a @Transaction, so observers never see
        // the half-applied snapshot in between.
        val pendingIds = dao.pendingForOwner(ownerUid).map { it.taskId }
        dao.replaceOwnerSnapshot(
            remoteRows = accepted,
            ownerUid = ownerUid,
            keepTaskIds = pendingIds,
        )

        return SyncOutcome.Success(pulled = accepted.size)
    }

    /** Releases the scope; only used by tests. */
    fun close() {
        scope.cancel()
    }

    private fun afterLocalWrite() {
        if (hasRemote) syncScheduler.requestSync()
    }

    /**
     * In local mode there is no remote, so a row is never "pending" — marking
     * it CREATED would queue work that can never be pushed.
     */
    private fun initialStatus(remoteStatus: SyncStatus): SyncStatus =
        if (hasRemote) remoteStatus else SyncStatus.SYNCED

    /**
     * Status to stamp after editing an existing row. A row the remote has never
     * seen stays [SyncStatus.CREATED] (a PUT would 404); anything else becomes
     * [SyncStatus.UPDATED]; in local mode nothing is ever pending.
     */
    private fun nextWriteStatus(previous: SyncStatus): SyncStatus = when {
        !hasRemote -> SyncStatus.SYNCED
        previous == SyncStatus.CREATED -> SyncStatus.CREATED
        else -> SyncStatus.UPDATED
    }

    private fun pointsFor(priority: Priority): Int = when (priority) {
        Priority.LOW -> 5
        Priority.MEDIUM -> 10
        Priority.HIGH -> 20
    }
}
