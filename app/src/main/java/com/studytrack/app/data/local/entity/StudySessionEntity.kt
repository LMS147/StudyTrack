package com.studytrack.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.studytrack.app.data.local.SyncStatus

/**
 * Local mirror of a logged study session.
 *
 * The API only exposes `POST /api/study-sessions` (see
 * [com.studytrack.app.data.model.StudySessionPayload]) and has no read
 * endpoint yet, so `sessionId` is generated on-device and becomes the
 * idempotency key when the row is finally pushed. Keeping the generated id
 * stable across retries is what stops one offline session from being logged
 * several times if a push is retried after a timeout.
 *
 * Scoped per UID like every other table.
 */
@Entity(
    tableName = "study_sessions",
    primaryKeys = ["ownerUid", "sessionId"],
    indices = [Index(value = ["ownerUid", "syncStatus"])],
)
data class StudySessionEntity(
    /** Firebase UID of the account that owns this row. Never blank. */
    @ColumnInfo(name = "ownerUid") val ownerUid: String,

    @ColumnInfo(name = "sessionId") val sessionId: String,
    @ColumnInfo(name = "subjectId") val subjectId: String?,
    @ColumnInfo(name = "taskId") val taskId: String?,
    @ColumnInfo(name = "durationMinutes") val durationMinutes: Int,
    /** ISO-8601 date-time. */
    @ColumnInfo(name = "studiedAt") val studiedAt: String?,
    @ColumnInfo(name = "notes") val notes: String?,

    @ColumnInfo(name = "syncStatus") val syncStatus: SyncStatus = SyncStatus.SYNCED,
    @ColumnInfo(name = "localUpdatedAt") val localUpdatedAt: Long,
    @ColumnInfo(name = "remoteUpdatedAt") val remoteUpdatedAt: Long = 0L,
) {
    val pendingSync: Boolean get() = syncStatus.isPending
}
