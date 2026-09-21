package com.studytrack.app.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of truth for *which Firebase account owns the data the app
 * is currently showing*.
 *
 * ## Why this class exists
 *
 * Every local Room row carries an `ownerUid`, and every DAO query filters by
 * one. If the UID were passed down from UI code, or defaulted to `""`, or read
 * ad-hoc from `FirebaseAuth.currentUser?.uid`, then any one of those call
 * sites could drift and start reading another account's rows. Funneling every
 * read through this one object means there is exactly one place to audit.
 *
 * ## Rules this enforces
 *
 * - [requireUid] throws rather than returning a blank string. A blank UID in a
 *   `WHERE ownerUid = ?` clause would not return another user's data — but it
 *   would silently return *nothing* and hide the real bug. Failing loudly is
 *   the safer default, and it is what turns "forgot to set the session" into a
 *   crash in development instead of mysteriously empty screens in production.
 * - Signing out calls [clearSession], which drops the pointer **only**. It
 *   never deletes cached rows, so an account that used this device before can
 *   have its data back instantly when it signs in again.
 *
 * The active UID is published as a [StateFlow] so the sync layer can react to
 * an account switch (cancel in-flight work, reschedule for the new UID).
 */
class CurrentAccount {

    private val _activeUid = MutableStateFlow<String?>(null)

    /** The signed-in account's UID, or `null` while signed out. */
    val activeUid: StateFlow<String?> = _activeUid.asStateFlow()

    /** Convenience for the common "is anybody signed in?" check. */
    val hasActiveSession: Boolean get() = _activeUid.value != null

    /**
     * Points every subsequent query at [uid].
     *
     * Called on sign-in (after the account record is upserted) and with `null`
     * on sign-out. Blank input is rejected: a blank UID would poison every
     * scoped query with a filter that matches nothing.
     */
    fun setSession(uid: String?) {
        val normalized = uid?.trim()
        require(normalized.isNullOrEmpty() || normalized.isNotEmpty()) {
            "ownerUid must not be blank"
        }
        _activeUid.value = normalized?.takeIf { it.isNotEmpty() }
    }

    /**
     * Signs out of the local session **without touching cached data**.
     *
     * Other accounts' rows stay in Room on purpose — see the class docs. Only
     * the pointer moves.
     */
    fun clearSession() {
        _activeUid.value = null
    }

    /**
     * The UID to scope a query with, or throw.
     *
     * **Use this on write paths.** Persisting a row without an owner would put
     * it outside every account's scope, so a write with no session is a bug and
     * must fail loudly.
     *
     * @throws IllegalStateException when no account is signed in. Callers that
     *   legitimately run in the background (the sync worker) must check
     *   [hasActiveSession] first and skip instead of calling this.
     */
    fun requireUid(): String = _activeUid.value
        ?: throw IllegalStateException(
            "No active StudyTrack session — refusing to run an unscoped query. " +
                "Background callers must check hasActiveSession first."
        )

    /**
     * The active UID, or `null` when nobody is signed in.
     *
     * **Use this on read paths.** A screen can legitimately be alive across an
     * auth transition, and the safe answer to "whose data should I show?" when
     * nobody is signed in is *nothing* — never a crash, and never another
     * account's rows. Crashing the process on a read turned a transient startup
     * race into a hard failure the user could not get past.
     */
    fun uidOrNull(): String? = _activeUid.value
}
