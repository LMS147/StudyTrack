package com.studytrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.studytrack.app.data.local.SyncStatus
import com.studytrack.app.data.local.entity.StudySessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Study-session persistence, scoped to one Firebase UID.
 *
 * Sessions are write-only against the API today, so the interesting direction
 * here is the push queue: an offline session is stored immediately and replayed
 * when connectivity returns, keyed by a stable on-device id so a retried push
 * cannot log it twice.
 */
@Dao
interface StudySessionDao {

    @Query(
        "SELECT * FROM study_sessions " +
            "WHERE ownerUid = :ownerUid AND syncStatus != 'DELETED' " +
            "ORDER BY localUpdatedAt DESC"
    )
    fun observeForOwner(ownerUid: String): Flow<List<StudySessionEntity>>

    @Query(
        "SELECT * FROM study_sessions " +
            "WHERE ownerUid = :ownerUid AND sessionId = :sessionId"
    )
    suspend fun getById(ownerUid: String, sessionId: String): StudySessionEntity?

    @Upsert
    suspend fun upsert(session: StudySessionEntity)

    @Query(
        "SELECT * FROM study_sessions " +
            "WHERE ownerUid = :ownerUid AND syncStatus != 'SYNCED' " +
            "ORDER BY localUpdatedAt ASC"
    )
    suspend fun pendingForOwner(ownerUid: String): List<StudySessionEntity>

    @Query(
        "UPDATE study_sessions SET syncStatus = :syncStatus, remoteUpdatedAt = :remoteUpdatedAt " +
            "WHERE ownerUid = :ownerUid AND sessionId = :sessionId"
    )
    suspend fun markSynced(
        ownerUid: String,
        sessionId: String,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
        remoteUpdatedAt: Long,
    )

    @Query(
        "DELETE FROM study_sessions WHERE ownerUid = :ownerUid AND sessionId = :sessionId"
    )
    suspend fun purge(ownerUid: String, sessionId: String)

    /** Account-removal path only. See TaskDao.deleteAllForOwner. */
    @Query("DELETE FROM study_sessions WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForOwner(ownerUid: String)

    @Query(
        "SELECT COUNT(*) FROM study_sessions " +
            "WHERE ownerUid = :ownerUid AND syncStatus != 'DELETED'"
    )
    suspend fun countForOwner(ownerUid: String): Int
}
