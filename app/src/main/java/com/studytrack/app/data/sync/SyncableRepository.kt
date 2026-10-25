package com.studytrack.app.data.sync

/** Result of one push or pull pass for a single account. */
sealed interface SyncOutcome {
    data class Success(val pushed: Int = 0, val pulled: Int = 0) : SyncOutcome

    /**
     * Transient failure. The worker maps this to `Result.retry()` so
     * WorkManager reschedules with backoff rather than dropping the queue.
     */
    data class Failure(val message: String, val cause: Throwable? = null) : SyncOutcome

    /** No remote is configured (local mode) — the cache *is* the truth. */
    data object Skipped : SyncOutcome
}

/**
 * The sync half of an offline-first repository.
 *
 * The public repository interfaces ([com.studytrack.app.data.repository.TaskRepository]
 * and friends) are unchanged, so screens and ViewModels need no edits; this is
 * implemented alongside them and consumed only by [SyncWorker].
 *
 * Every method takes an explicit `ownerUid`. The worker resolves it once from
 * [com.studytrack.app.auth.CurrentAccount] at the start of a run and passes it
 * down, so a sync can never touch more than one account's rows — and cannot
 * silently switch accounts halfway through a pass.
 */
interface SyncableRepository {

    /** False in local mode, where there is no remote to sync against. */
    val hasRemote: Boolean

    /** Pushes this account's pending rows, oldest edit first. */
    suspend fun pushPending(ownerUid: String): SyncOutcome

    /** Pulls the remote snapshot for this account and applies it per-UID. */
    suspend fun pullRemote(ownerUid: String): SyncOutcome
}
