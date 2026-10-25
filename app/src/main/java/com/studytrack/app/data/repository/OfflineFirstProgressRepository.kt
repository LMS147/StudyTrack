package com.studytrack.app.data.repository

import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.local.toModel
import com.studytrack.app.data.local.toEntity
import com.studytrack.app.data.model.Progress
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.data.sync.SyncOutcome
import com.studytrack.app.data.sync.SyncableRepository
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.CancellationException

/**
 * Offline-first progress.
 *
 * Progress is a server-computed aggregate, so there is nothing to *push* — the
 * points and streak rules live on the backend. What this adds is a per-account
 * cache so the Progress screen renders instantly and still shows the last known
 * numbers with no connectivity, plus a locally derived fallback computed from
 * the cached tasks when neither the cache nor the network can answer.
 *
 * The fallback is what keeps offline mode honest: rather than showing zeroes
 * (which would look like the user lost all their XP), it recomputes from the
 * tasks actually on the device.
 */
class OfflineFirstProgressRepository(
    private val db: StudyTrackDatabase,
    private val api: ApiService?,
    private val currentAccount: CurrentAccount,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ProgressRepository, SyncableRepository {

    override val hasRemote: Boolean get() = api != null

    override suspend fun getProgress(): ApiResult<Progress> {
        // Read path: nobody signed in means no progress to show, not a crash.
        val uid = currentAccount.uidOrNull() ?: return ApiResult.Success(Progress())

        if (hasRemote) {
            when (val pull = pullRemote(uid)) {
                is SyncOutcome.Failure -> {
                    // Offline: fall back to the cached aggregate, then to a
                    // locally derived one.
                    db.progressDao().getForOwner(uid)?.let {
                        return ApiResult.Success(it.toModel())
                    }
                    return ApiResult.Success(computeFromLocalTasks(uid))
                }

                else -> Unit
            }
        }

        // Local mode, or a successful pull: read the cached row.
        db.progressDao().getForOwner(uid)?.let { return ApiResult.Success(it.toModel()) }
        val derived = computeFromLocalTasks(uid)
        db.progressDao().upsert(derived.toEntity(uid, clock()))
        return ApiResult.Success(derived)
    }

    /** Progress is server-derived, so there is never a pending local push. */
    override suspend fun pushPending(ownerUid: String): SyncOutcome = SyncOutcome.Skipped

    override suspend fun pullRemote(ownerUid: String): SyncOutcome {
        val remote = api ?: return SyncOutcome.Skipped
        val fetchedAt = clock()
        val progress = runCatching { remote.getProgress() }.getOrElse { e ->
            if (e is CancellationException) throw e
            return SyncOutcome.Failure(
                "Could not reach the server: ${e.message ?: "network error"}", e
            )
        }
        db.progressDao().upsert(progress.toEntity(ownerUid, fetchedAt, fetchedAt))
        return SyncOutcome.Success(pulled = 1)
    }

    /**
     * Derives progress from this account's cached tasks. Uses the same points
     * rule as the backend (High 20 / Medium 10 / Low 5), so the offline number
     * matches what the server will report once connectivity returns.
     */
    private suspend fun computeFromLocalTasks(ownerUid: String): Progress {
        val tasks = db.taskDao().getAllForOwner(ownerUid)
        val completed = tasks.filter { it.completed }
        return Progress(
            totalPoints = completed.sumOf { it.points },
            completedTasks = completed.size,
            currentStreak = 0, // streak needs server-side date history
        )
    }
}
