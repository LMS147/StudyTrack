package com.studytrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.studytrack.app.data.local.SyncStatus
import com.studytrack.app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Task persistence, scoped to one Firebase UID.
 *
 * ## There is deliberately no unfiltered accessor
 *
 * Not one method here can read or write tasks without an `ownerUid`. There is
 * no `getAllTasks()`, no `deleteAll()`, no `@Query("SELECT * FROM tasks")` —
 * the blanket query the isolation requirement forbids is not merely avoided,
 * it is unrepresentable. A caller that has no UID has no method to call.
 *
 * `tools/check_references.py` enforces this statically: it fails the build if
 * any `@Query` against a scoped table lacks an `ownerUid` predicate, so the
 * rule cannot be eroded by a later edit.
 *
 * ## Tombstones
 *
 * Deletes are soft. A row being deleted moves to [SyncStatus.DELETED] and
 * stays until the remote confirms, so an offline delete cannot be resurrected
 * by the next pull. Every user-facing read filters `syncStatus != 'DELETED'`;
 * only the sync worker asks for tombstones.
 */
@Dao
interface TaskDao {

    // ------------------------------------------------------------------ reads

    /**
     * Live list for one account, newest edits first, tombstones excluded.
     * This is what every task screen observes.
     */
    @Query(
        "SELECT * FROM tasks " +
            "WHERE ownerUid = :ownerUid AND syncStatus != 'DELETED' " +
            "ORDER BY completed ASC, dueDate IS NULL ASC, dueDate ASC, title ASC"
    )
    fun observeForOwner(ownerUid: String): Flow<List<TaskEntity>>

    /** Non-reactive variant used by the sync worker and one-shot lookups. */
    @Query(
        "SELECT * FROM tasks " +
            "WHERE ownerUid = :ownerUid AND syncStatus != 'DELETED'"
    )
    suspend fun getAllForOwner(ownerUid: String): List<TaskEntity>

    @Query(
        "SELECT * FROM tasks " +
            "WHERE ownerUid = :ownerUid AND taskId = :taskId AND syncStatus != 'DELETED'"
    )
    suspend fun getById(ownerUid: String, taskId: String): TaskEntity?

    /** Dated tasks for one account — the calendar month grid. */
    @Query(
        "SELECT * FROM tasks " +
            "WHERE ownerUid = :ownerUid AND syncStatus != 'DELETED' " +
            "AND dueDate IS NOT NULL " +
            "ORDER BY dueDate ASC"
    )
    fun observeDatedForOwner(ownerUid: String): Flow<List<TaskEntity>>

    /** Aggregate for the Progress screen, computed per account. */
    @Query(
        "SELECT COUNT(*) FROM tasks " +
            "WHERE ownerUid = :ownerUid AND syncStatus != 'DELETED' AND completed = 1"
    )
    suspend fun countCompletedForOwner(ownerUid: String): Int

    @Query(
        "SELECT COUNT(*) FROM tasks " +
            "WHERE ownerUid = :ownerUid AND syncStatus != 'DELETED'"
    )
    suspend fun countForOwner(ownerUid: String): Int

    // ----------------------------------------------------------------- writes

    /**
     * Insert or replace one row. Safe across accounts because `ownerUid` is
     * part of the primary key: this can only ever clobber the *same* user's
     * row.
     */
    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    /**
     * Marks a row synced and records the remote's timestamp, so the next pull
     * can tell a stale remote copy from a newer one.
     */
    @Query(
        "UPDATE tasks SET syncStatus = :syncStatus, remoteUpdatedAt = :remoteUpdatedAt " +
            "WHERE ownerUid = :ownerUid AND taskId = :taskId"
    )
    suspend fun markSynced(
        ownerUid: String,
        taskId: String,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
        remoteUpdatedAt: Long,
    )

    /** Soft-deletes: tombstones the row so the pending push can delete it remotely. */
    @Query(
        "UPDATE tasks SET syncStatus = 'DELETED', localUpdatedAt = :updatedAt " +
            "WHERE ownerUid = :ownerUid AND taskId = :taskId"
    )
    suspend fun markDeleted(ownerUid: String, taskId: String, updatedAt: Long)

    /**
     * Hard-deletes a tombstone once the remote has confirmed the delete.
     * Scoped by both keys, so it can never reach another account's row.
     */
    @Query("DELETE FROM tasks WHERE ownerUid = :ownerUid AND taskId = :taskId")
    suspend fun purgeTombstone(ownerUid: String, taskId: String)

    // ------------------------------------------------------------ sync worker

    /**
     * Everything this account owes the remote, oldest edit first so the user's
     * own ordering is preserved (create-then-delete must not invert).
     */
    @Query(
        "SELECT * FROM tasks " +
            "WHERE ownerUid = :ownerUid AND syncStatus != 'SYNCED' " +
            "ORDER BY localUpdatedAt ASC"
    )
    suspend fun pendingForOwner(ownerUid: String): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE ownerUid = :ownerUid")
    suspend fun countAllRowsForOwner(ownerUid: String): Int

    /**
     * Removes every row belonging to one account, pending rows included.
     * Reached only by the "remove this account from this device" path —
     * never by sign-out, which keeps the cache.
     */
    @Query("DELETE FROM tasks WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForOwner(ownerUid: String)

    /**
     * Replaces one account's snapshot from a remote pull while leaving pending
     * local edits untouched. Runs in one transaction so observers never see a
     * half-applied snapshot.
     */
    @Transaction
    suspend fun replaceOwnerSnapshot(remoteRows: List<TaskEntity>, ownerUid: String, keepTaskIds: List<String>) {
        if (keepTaskIds.isEmpty()) {
            deleteSyncedForOwner(ownerUid)
        } else {
            deleteSyncedExcept(ownerUid, keepTaskIds)
        }
        if (remoteRows.isNotEmpty()) insertAll(remoteRows)
    }

    @Query("DELETE FROM tasks WHERE ownerUid = :ownerUid AND syncStatus = 'SYNCED'")
    suspend fun deleteSyncedForOwner(ownerUid: String)

    @Query(
        "DELETE FROM tasks WHERE ownerUid = :ownerUid AND syncStatus = 'SYNCED' " +
            "AND taskId NOT IN (:keepTaskIds)"
    )
    suspend fun deleteSyncedExcept(ownerUid: String, keepTaskIds: List<String>)
}
