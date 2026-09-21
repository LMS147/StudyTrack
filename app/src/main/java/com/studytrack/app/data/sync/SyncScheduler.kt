package com.studytrack.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

/**
 * Asks for a background sync.
 *
 * An interface so the repositories can be tested without initializing
 * WorkManager — the isolation tests care about *which account's rows* get
 * touched, not about the work queue.
 */
interface SyncScheduler {

    /** Queues a sync if one is not already pending. */
    fun requestSync(reason: String = REASON_WRITE)

    /**
     * Replaces any queued sync and runs as soon as constraints allow. Used
     * after sign-in, where waiting behind a stale queued request would be
     * wrong.
     */
    fun requestImmediateSync(reason: String = REASON_AUTH)

    companion object {
        const val TAG = "studytrack-sync"
        const val UNIQUE_SYNC = "studytrack.sync.unique"
        const val REASON_WRITE = "local-write"
        const val REASON_AUTH = "auth-change"
    }
}

/**
 * WorkManager-backed scheduler.
 *
 * ## Retry behaviour
 *
 * Two constraints on the request do the retry work:
 *
 * - `NetworkType.CONNECTED` — WorkManager itself holds the request until
 *   connectivity returns, so "retry when the network comes back" needs no
 *   polling loop of our own.
 * - Exponential backoff — when the worker returns `Result.retry()` (a transient
 *   5xx or a dropped connection) WorkManager reschedules with growing delays
 *   instead of hammering a struggling backend.
 *
 * ## Why `ExistingWorkPolicy.KEEP`
 *
 * Every local write calls [requestSync], so a burst of edits would otherwise
 * queue a burst of workers all pushing the same queue. `KEEP` collapses them,
 * which is safe because the worker reads the pending set from the database
 * *when it runs* — it always picks up everything written since it was queued.
 */
class WorkManagerSyncScheduler(context: Context) : SyncScheduler {

    private val appContext = context.applicationContext

    override fun requestSync(reason: String) {
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(
                SyncScheduler.UNIQUE_SYNC,
                ExistingWorkPolicy.KEEP,
                buildRequest(),
            )
    }

    override fun requestImmediateSync(reason: String) {
        val workManager = WorkManager.getInstance(appContext)
        workManager.cancelUniqueWork(SyncScheduler.UNIQUE_SYNC)
        workManager.enqueueUniqueWork(
            SyncScheduler.UNIQUE_SYNC,
            ExistingWorkPolicy.REPLACE,
            buildRequest(),
        )
    }

    private fun buildRequest() = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            WorkRequest.MIN_BACKOFF_MILLIS,
            TimeUnit.MILLISECONDS,
        )
        .addTag(SyncScheduler.TAG)
        .build()
}
