package com.studytrack.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.studytrack.app.data.local.SyncStatus

/**
 * Local mirror of [com.studytrack.app.data.model.Task].
 *
 * ## Per-account isolation
 *
 * `ownerUid` is **part of the primary key**, not merely a column that happens
 * to be filtered on. That is a deliberate structural guarantee: two accounts
 * cannot produce a row collision, and `OnConflictStrategy.REPLACE` can never
 * overwrite a *different* user's row — the keys simply differ. It also means
 * an accidentally unscoped write cannot silently land in someone else's data.
 *
 * Both indexes lead with `ownerUid` so that every scoped query is an index
 * range scan rather than a table scan, and so that a query which forgot the
 * UID predicate would be conspicuously slow.
 */
@Entity(
    tableName = "tasks",
    primaryKeys = ["ownerUid", "taskId"],
    indices = [
        Index(value = ["ownerUid", "syncStatus"]),
        Index(value = ["ownerUid", "dueDate"]),
    ],
)
data class TaskEntity(
    /** Firebase UID of the account that owns this row. Never blank. */
    @ColumnInfo(name = "ownerUid") val ownerUid: String,

    @ColumnInfo(name = "taskId") val taskId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "subjectId") val subjectId: String?,
    @ColumnInfo(name = "description") val description: String?,
    /** Stored as the [com.studytrack.app.data.model.TaskType] name. */
    @ColumnInfo(name = "taskType") val taskType: String,
    /** Stored as the [com.studytrack.app.data.model.Priority] name. */
    @ColumnInfo(name = "priority") val priority: String,
    /** ISO-8601 date-time, matching the wire format. */
    @ColumnInfo(name = "dueDate") val dueDate: String?,
    @ColumnInfo(name = "reminderDate") val reminderDate: String?,
    @ColumnInfo(name = "completed") val completed: Boolean,
    @ColumnInfo(name = "completedAt") val completedAt: String?,
    @ColumnInfo(name = "points") val points: Int,

    // ------------------------------------------------------------ sync bookkeeping

    @ColumnInfo(name = "syncStatus") val syncStatus: SyncStatus = SyncStatus.SYNCED,

    /**
     * Epoch millis of the last local write. Drives (a) the order pending
     * changes are pushed in, and (b) last-write-wins conflict resolution.
     */
    @ColumnInfo(name = "localUpdatedAt") val localUpdatedAt: Long,

    /**
     * Epoch millis of the row as the remote last knew it. Compared against the
     * remote's own timestamp on pull; see `ConflictResolver`.
     */
    @ColumnInfo(name = "remoteUpdatedAt") val remoteUpdatedAt: Long = 0L,
) {
    /** True while this row owes the remote a write. */
    val pendingSync: Boolean get() = syncStatus.isPending
}
