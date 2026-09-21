package com.studytrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.studytrack.app.data.local.entity.ProgressEntity

/**
 * Progress persistence, scoped to one Firebase UID.
 *
 * `ownerUid` is the entire primary key here, so "read my progress" is
 * necessarily `WHERE ownerUid = ?` — there is no row to read otherwise.
 */
@Dao
interface ProgressDao {

    @Query("SELECT * FROM progress WHERE ownerUid = :ownerUid")
    suspend fun getForOwner(ownerUid: String): ProgressEntity?

    @Upsert
    suspend fun upsert(progress: ProgressEntity)

    @Query(
        "UPDATE progress SET syncStatus = 'SYNCED', remoteUpdatedAt = :remoteUpdatedAt " +
            "WHERE ownerUid = :ownerUid"
    )
    suspend fun markSynced(ownerUid: String, remoteUpdatedAt: Long)

    /** True when this account's progress still owes the remote a write. */
    @Query("SELECT EXISTS(SELECT 1 FROM progress WHERE ownerUid = :ownerUid AND syncStatus != 'SYNCED')")
    suspend fun hasPendingForOwner(ownerUid: String): Boolean

    @Query("DELETE FROM progress WHERE ownerUid = :ownerUid")
    suspend fun deleteForOwner(ownerUid: String)
}
