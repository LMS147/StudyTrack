package com.studytrack.app.data.local

/**
 * Local-vs-remote state of a single row, stored in the `syncStatus` column of
 * every scoped Room table.
 *
 * The lifecycle is:
 *
 * ```
 *   write while offline ──> CREATED / UPDATED / DELETED   (pending, UI already updated)
 *              │
 *              └── sync worker pushes ──> SYNCED
 *
 *   row pulled from the remote ──> SYNCED                  (nothing to push)
 * ```
 *
 * Ordering matters: the worker pushes pending rows in [localUpdatedAt] order so
 * that "create then delete" reaches the server in the order the user did it,
 * rather than racing into a resurrected row.
 */
enum class SyncStatus {
    /** Created on this device, never accepted by the remote. */
    CREATED,

    /** Exists remotely, edited on this device since the last successful sync. */
    UPDATED,

    /**
     * Deleted on this device. The row is kept (a tombstone) until the remote
     * confirms the delete, so an offline delete is not resurrected by the next
     * pull. Tombstones are only visible to the sync worker — every DAO read
     * filters them out.
     */
    DELETED,

    /** Local and remote agree; nothing to push. */
    SYNCED;

    /** True when the row still owes the remote a write. */
    val isPending: Boolean get() = this != SYNCED

    companion object {
        /**
         * Lenient parse for values read back out of SQLite. An unknown value
         * is treated as [UPDATED] — the conservative choice, because re-pushing
         * a row is harmless (last-write-wins) while dropping it loses data.
         */
        fun fromRaw(raw: String?): SyncStatus =
            entries.firstOrNull { it.name == raw } ?: UPDATED
    }
}
