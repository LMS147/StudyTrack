package com.studytrack.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.studytrack.app.data.local.SyncStatus

/**
 * Local mirror of [com.studytrack.app.data.model.Progress].
 *
 * Progress is a single aggregate per account, so `ownerUid` is the whole
 * primary key — there is at most one row per user, and reading progress is
 * always `WHERE ownerUid = ?`.
 */
@Entity(tableName = "progress")
data class ProgressEntity(
    /** Firebase UID of the account that owns this row. The entire primary key. */
    @PrimaryKey
    @ColumnInfo(name = "ownerUid") val ownerUid: String,

    @ColumnInfo(name = "totalPoints") val totalPoints: Int = 0,
    @ColumnInfo(name = "completedTasks") val completedTasks: Int = 0,
    @ColumnInfo(name = "currentStreak") val currentStreak: Int = 0,

    @ColumnInfo(name = "syncStatus") val syncStatus: SyncStatus = SyncStatus.SYNCED,
    @ColumnInfo(name = "localUpdatedAt") val localUpdatedAt: Long,
    @ColumnInfo(name = "remoteUpdatedAt") val remoteUpdatedAt: Long = 0L,
) {
    val pendingSync: Boolean get() = syncStatus.isPending
}
