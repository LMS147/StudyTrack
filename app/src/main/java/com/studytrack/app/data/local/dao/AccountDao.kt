package com.studytrack.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.studytrack.app.data.local.entity.AccountRecordEntity

/**
 * The device's account registry.
 *
 * **This is the one DAO whose queries are not filtered by the active UID, and
 * that is deliberate.** `account_records` stores no study data — only UIDs,
 * emails and sync bookkeeping — and its whole purpose is to answer questions
 * *about other accounts* before any of their data is touched. The offline
 * account-switch guard cannot work if it is only allowed to see the currently
 * signed-in account.
 *
 * The isolation rule still holds where it matters: this table has no columns
 * for tasks, subjects, progress or sessions, so no amount of unscoped reading
 * here can surface another user's study data.
 */
@Dao
interface AccountDao {

    @Query("SELECT * FROM account_records WHERE ownerUid = :ownerUid")
    suspend fun getByUid(ownerUid: String): AccountRecordEntity?

    /**
     * Lookup by lowercased email. This is how an account is recognized before
     * authentication — offline, the UID is unknowable until Firebase answers.
     */
    @Query("SELECT * FROM account_records WHERE email = :email")
    suspend fun getByEmail(email: String): AccountRecordEntity?

    @Query("SELECT * FROM account_records ORDER BY lastSyncedAt DESC")
    suspend fun getAll(): List<AccountRecordEntity>

    /** Every UID that has ever signed in on this device. */
    @Query("SELECT ownerUid FROM account_records")
    suspend fun allUids(): List<String>

    @Upsert
    suspend fun upsert(record: AccountRecordEntity)

    @Query("UPDATE account_records SET lastSyncedAt = :syncedAt WHERE ownerUid = :ownerUid")
    suspend fun setLastSyncedAt(ownerUid: String, syncedAt: Long)

    @Query("UPDATE account_records SET bootstrapped = :bootstrapped WHERE ownerUid = :ownerUid")
    suspend fun setBootstrapped(ownerUid: String, bootstrapped: Boolean)

    /**
     * Removes an account from the registry **and all of its cached data**.
     *
     * This is *not* sign-out — signing out keeps the cache so the account can
     * return to it instantly. This is the "remove this account from this
     * device" path (a future Profile action), and it is the only place in the
     * app permitted to delete another account's rows.
     */
    @Query("DELETE FROM account_records WHERE ownerUid = :ownerUid")
    suspend fun deleteRecord(ownerUid: String)
}
