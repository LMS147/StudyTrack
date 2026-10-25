package com.studytrack.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.studytrack.app.data.local.SyncStatus

/**
 * Local mirror of [com.studytrack.app.data.model.Subject].
 *
 * Scoped per Firebase UID exactly like [TaskEntity] — `ownerUid` is part of the
 * primary key, so subject ids cannot collide across accounts and a REPLACE
 * conflict resolution can never touch another user's row.
 */
@Entity(
    tableName = "subjects",
    primaryKeys = ["ownerUid", "subjectId"],
    indices = [Index(value = ["ownerUid", "syncStatus"])],
)
data class SubjectEntity(
    /** Firebase UID of the account that owns this row. Never blank. */
    @ColumnInfo(name = "ownerUid") val ownerUid: String,

    @ColumnInfo(name = "subjectId") val subjectId: String,
    @ColumnInfo(name = "subjectName") val subjectName: String,
    @ColumnInfo(name = "description") val description: String?,

    @ColumnInfo(name = "syncStatus") val syncStatus: SyncStatus = SyncStatus.SYNCED,
    @ColumnInfo(name = "localUpdatedAt") val localUpdatedAt: Long,
    @ColumnInfo(name = "remoteUpdatedAt") val remoteUpdatedAt: Long = 0L,
) {
    val pendingSync: Boolean get() = syncStatus.isPending
}
