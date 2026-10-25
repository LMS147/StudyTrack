package com.studytrack.app.data.sync

import com.studytrack.app.data.local.SyncStatus

/**
 * Last-write-wins arbitration between a locally cached row and the copy the
 * remote returned.
 *
 * The rule set is deliberately small, because the project brief calls for
 * timestamp-based LWW and explicitly not a merge strategy:
 *
 * 1. **A pending local edit always wins.** It has not been pushed yet, so the
 *    remote's copy cannot possibly reflect it. Letting the remote overwrite it
 *    would silently delete the user's work — the single worst outcome in an
 *    offline-first app.
 * 2. Otherwise the **newer timestamp wins**, ties going to the remote (the
 *    remote is the source of truth, and a tie means the two already agree).
 * 3. **A local tombstone always wins over a remote row.** An offline delete
 *    must not be resurrected by the next pull; the tombstone is pushed first
 *    and only then purged.
 */
object ConflictResolver {

    /**
     * @param localSyncStatus the cached row's status, or `null` if it is not
     *   cached locally at all (a brand-new remote row, which always wins).
     * @param localUpdatedAt  epoch millis of the last local write; 0 when absent.
     * @param remoteUpdatedAt epoch millis the remote reports for its copy.
     * @return true when the remote copy should replace the cached row.
     */
    fun remoteWins(
        localSyncStatus: SyncStatus?,
        localUpdatedAt: Long,
        remoteUpdatedAt: Long,
    ): Boolean {
        // Not cached locally — nothing to protect.
        if (localSyncStatus == null) return true

        // An unpushed local delete outranks anything the remote still holds.
        if (localSyncStatus == SyncStatus.DELETED) return false

        // An unpushed local create/edit outranks the remote's stale copy.
        if (localSyncStatus.isPending) return false

        // Both sides are settled: newest timestamp wins, ties to the remote.
        return remoteUpdatedAt >= localUpdatedAt
    }
}
