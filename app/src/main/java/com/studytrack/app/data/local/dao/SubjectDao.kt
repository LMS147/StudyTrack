package com.studytrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.studytrack.app.data.local.SyncStatus
import com.studytrack.app.data.local.entity.SubjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * Subject persistence, scoped to one Firebase UID.
 *
 * Same isolation contract as [TaskDao]: every method takes an `ownerUid`, no
 * unfiltered accessor exists, and `ownerUid` is part of the primary key.
 */
@Dao
interface SubjectDao {

    @Query(
        "SELECT * FROM subjects " +
            "WHERE ownerUid = :ownerUid AND syncStatus != 'DELETED' " +
            "ORDER BY subjectName ASC"
    )
    fun observeForOwner(ownerUid: String): Flow<List<SubjectEntity>>

    @Query(
        "SELECT * FROM subjects " +
            "WHERE ownerUid = :ownerUid AND syncStatus != 'DELETED'"
    )
    suspend fun getAllForOwner(ownerUid: String): List<SubjectEntity>

    @Query(
        "SELECT * FROM subjects " +
            "WHERE ownerUid = :ownerUid AND subjectId = :subjectId AND syncStatus != 'DELETED'"
    )
    suspend fun getById(ownerUid: String, subjectId: String): SubjectEntity?

    @Query(
        "SELECT COUNT(*) FROM tasks " +
            "WHERE ownerUid = :ownerUid AND subjectId = :subjectId AND syncStatus != 'DELETED'"
    )
    suspend fun taskCountForSubject(ownerUid: String, subjectId: String): Int

    @Upsert
    suspend fun upsert(subject: SubjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subjects: List<SubjectEntity>)

    @Query(
        "UPDATE subjects SET syncStatus = :syncStatus, remoteUpdatedAt = :remoteUpdatedAt " +
            "WHERE ownerUid = :ownerUid AND subjectId = :subjectId"
    )
    suspend fun markSynced(
        ownerUid: String,
        subjectId: String,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
        remoteUpdatedAt: Long,
    )

    @Query(
        "UPDATE subjects SET syncStatus = 'DELETED', localUpdatedAt = :updatedAt " +
            "WHERE ownerUid = :ownerUid AND subjectId = :subjectId"
    )
    suspend fun markDeleted(ownerUid: String, subjectId: String, updatedAt: Long)

    @Query("DELETE FROM subjects WHERE ownerUid = :ownerUid AND subjectId = :subjectId")
    suspend fun purgeTombstone(ownerUid: String, subjectId: String)

    @Query(
        "SELECT * FROM subjects " +
            "WHERE ownerUid = :ownerUid AND syncStatus != 'SYNCED' " +
            "ORDER BY localUpdatedAt ASC"
    )
    suspend fun pendingForOwner(ownerUid: String): List<SubjectEntity>

    /**
     * Applies a remote snapshot for one account without discarding local edits
     * that have not been pushed yet.
     */
    @Transaction
    suspend fun replaceOwnerSnapshot(remoteRows: List<SubjectEntity>, ownerUid: String, keepSubjectIds: List<String>) {
        if (keepSubjectIds.isEmpty()) {
            deleteSyncedForOwner(ownerUid)
        } else {
            deleteSyncedExcept(ownerUid, keepSubjectIds)
        }
        if (remoteRows.isNotEmpty()) insertAll(remoteRows)
    }

    @Query("DELETE FROM subjects WHERE ownerUid = :ownerUid AND syncStatus = 'SYNCED'")
    suspend fun deleteSyncedForOwner(ownerUid: String)

    /** Account-removal path only. See TaskDao.deleteAllForOwner. */
    @Query("DELETE FROM subjects WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForOwner(ownerUid: String)

    @Query(
        "DELETE FROM subjects WHERE ownerUid = :ownerUid AND syncStatus = 'SYNCED' " +
            "AND subjectId NOT IN (:keepSubjectIds)"
    )
    suspend fun deleteSyncedExcept(ownerUid: String, keepSubjectIds: List<String>)
}
