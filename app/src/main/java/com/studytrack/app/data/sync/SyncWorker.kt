package com.studytrack.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.studytrack.app.ServiceLocator

/**
 * Pushes queued local changes and pulls remote ones, for **one** account.
 *
 * ## The account is resolved once, at the top
 *
 * `doWork` reads the active UID a single time and threads that same value
 * through every repository call. A worker that re-read the current account
 * per repository could straddle a sign-out/sign-in and end up pushing one
 * account's edits under another's session — the exact class of bug the
 * isolation requirement exists to prevent. Resolving once makes that
 * structurally impossible.
 *
 * If nobody is signed in, the worker does nothing and reports success. It does
 * not fall back to "any" account's data.
 *
 * ## Ordering
 *
 * Subjects are pushed before tasks because a task carries a `subjectId`;
 * creating the subject first means the task never references something the
 * remote has not heard of. Within a table, rows go out in `localUpdatedAt`
 * order so create-then-delete cannot invert into a resurrected row.
 *
 * ## Failure handling
 *
 * Any transient failure returns `Result.retry()`. WorkManager then applies the
 * exponential backoff configured by [SyncScheduler], and the pending rows are
 * still in Room — nothing was consumed optimistically, so a retry re-attempts
 * exactly the rows that have not been confirmed.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val account = ServiceLocator.currentAccount

        // Signed out: nothing to sync, and definitely not another account's data.
        if (!account.hasActiveSession) return Result.success()
        val uid = account.requireUid()

        val locator = ServiceLocator

        // 1. Push, subjects first.
        for (repository in locator.pushOrder) {
            if (repository.pushPending(uid) is SyncOutcome.Failure) {
                return Result.retry()
            }
        }

        // 2. Pull. A failed pull is not fatal to the push that just succeeded,
        //    but the run as a whole still retries so the cache catches up.
        var pullFailed = false
        for (repository in locator.pullOrder) {
            if (repository.pullRemote(uid) is SyncOutcome.Failure) pullFailed = true
        }

        if (pullFailed) return Result.retry()

        // 3. Record that this account is now fully synced on this device. This
        //    is what flips the account from "needs bootstrap" to "can be
        //    served from cache", which is what lets it sign in offline later.
        locator.markAccountSynced(uid)

        return Result.success()
    }
}
