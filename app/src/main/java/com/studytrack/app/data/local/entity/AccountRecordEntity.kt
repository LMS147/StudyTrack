package com.studytrack.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Registry of the accounts that have ever signed in **on this device**.
 *
 * This is the only table in the schema that is intentionally *not* filtered by
 * the active UID, and it is safe to read unscoped because it holds no study
 * data at all — just enough metadata to answer two questions before any
 * account's data is touched:
 *
 * 1. Has this account used this device before? (Decides whether an offline
 *    sign-in can be served from cache or must be refused — see
 *    [com.studytrack.app.auth.AccountSwitchGuard].)
 * 2. Has this account completed its first full remote fetch? (Decides whether
 *    the app must bootstrap before showing any screens.)
 *
 * Keeping `email` here is what makes the offline guard possible at all: the
 * guard has to decide *before* authenticating, and the only thing the user has
 * typed at that point is their email — the UID is not known until Firebase
 * answers, which offline it cannot.
 */
@Entity(tableName = "account_records")
data class AccountRecordEntity(
    /** Firebase UID. Primary key, and the `ownerUid` stamped onto that account's data rows. */
    @ColumnInfo(name = "ownerUid") val ownerUid: String,

    /**
     * Lowercased sign-in email, used to recognize an account before its UID is
     * known. Indexed because the offline guard looks up by email.
     */
    @ColumnInfo(name = "email") val email: String,

    @ColumnInfo(name = "displayName") val displayName: String?,

    /** Epoch millis of the last successful remote sync; 0 if never synced. */
    @ColumnInfo(name = "lastSyncedAt") val lastSyncedAt: Long = 0L,

    /**
     * True once a full remote fetch has populated this account's rows. Until
     * then the local cache is empty-or-partial and must not be presented as
     * authoritative — this is the flag that drives "fetch before showing any
     * screens" for a first-time account.
     */
    @ColumnInfo(name = "bootstrapped") val bootstrapped: Boolean = false,
)
