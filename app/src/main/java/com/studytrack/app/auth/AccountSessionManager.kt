package com.studytrack.app.auth

import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.local.entity.AccountRecordEntity
import com.studytrack.app.data.sync.ConnectivityMonitor
import com.studytrack.app.data.sync.SyncOutcome
import com.studytrack.app.data.sync.SyncScheduler
import com.studytrack.app.data.sync.SyncableRepository
import com.studytrack.app.util.ApiResult

/**
 * Owns every transition of the active account, and therefore every point where
 * one account's data could be shown to another.
 *
 * Nothing else in the app sets the active UID. Sign-in, sign-out, and restoring
 * a persisted session on launch all come through here, so the isolation rules
 * have exactly one implementation to audit.
 *
 * ## Sign-out deliberately keeps the cache
 *
 * [onSignedOut] clears the pointer and nothing else. Another account's rows —
 * and the account just signed out's own rows — stay in Room, so switching back
 * restores that workspace instantly instead of re-downloading it. Deleting on
 * sign-out would make multi-account use on one device useless offline.
 */
class AccountSessionManager(
    private val database: StudyTrackDatabase,
    private val currentAccount: CurrentAccount,
    private val connectivityMonitor: ConnectivityMonitor,
    private val syncScheduler: SyncScheduler,
    private val pushOrder: () -> List<SyncableRepository>,
    private val pullOrder: () -> List<SyncableRepository>,
    /**
     * Imports the pre-Room SharedPreferences store under the given UID, once
     * per device. Injected so this class stays free of Android `Context`.
     */
    private val migrateLegacyStore: suspend (String) -> Unit = {},
) {

    /**
     * Runs the isolation guard **before** authenticating.
     *
     * It has to run first: once Firebase has signed the user in, the previous
     * account's session is already gone and there is no clean way to undo a
     * switch that should never have been allowed.
     */
    suspend fun evaluateSignIn(email: String): AccountSwitchDecision {
        val accountDao = database.accountDao()
        val known = accountDao.getAll().map {
            KnownAccount(ownerUid = it.ownerUid, email = it.email, bootstrapped = it.bootstrapped)
        }

        // Recognizing "same account re-authenticating" needs the active
        // account's email, which only the registry knows.
        val activeUid = currentAccount.activeUid.value
        val activeEmail = activeUid?.let { accountDao.getByUid(it)?.email }
        val sameAccount = activeEmail != null &&
            AccountSwitchGuard.normalizeEmail(activeEmail) ==
            AccountSwitchGuard.normalizeEmail(email)

        return AccountSwitchGuard.evaluate(
            request = AccountSwitchRequest(email = email, isSameAccountAsActive = sameAccount),
            device = DeviceState(
                isOnline = connectivityMonitor.isOnline.value,
                activeUid = activeUid,
                knownAccounts = known,
            ),
        )
    }

    /**
     * Called after Firebase confirms a sign-in. Registers the account on this
     * device and points every query at it.
     *
     * @return true when the app must complete a remote fetch before showing any
     *   screens — i.e. this device has never fully loaded this account.
     */
    suspend fun onSignedIn(
        ownerUid: String,
        email: String?,
        displayName: String?,
    ): Boolean {
        val dao = database.accountDao()
        val normalizedEmail = email?.let { AccountSwitchGuard.normalizeEmail(it) }.orEmpty()
        val existing = dao.getByUid(ownerUid)

        // Upsert without clobbering sync history: an account returning to this
        // device must keep its `bootstrapped` flag, or it would be forced
        // through a full re-download every time.
        dao.upsert(
            AccountRecordEntity(
                ownerUid = ownerUid,
                email = normalizedEmail.ifEmpty { existing?.email.orEmpty() },
                displayName = displayName ?: existing?.displayName,
                lastSyncedAt = existing?.lastSyncedAt ?: 0L,
                bootstrapped = existing?.bootstrapped ?: false,
            )
        )

        currentAccount.setSession(ownerUid)

        // Pre-Room rows had no owner column; attribute them to this account.
        migrateLegacyStore(ownerUid)

        val needsBootstrap = existing?.bootstrapped != true
        if (needsBootstrap) {
            // Fetch inline. The caller holds the UI on a loading state until
            // this returns, so no screen is ever shown with another account's
            // data or with an empty workspace that is about to fill in.
            bootstrap(ownerUid)
        } else {
            syncScheduler.requestImmediateSync(SyncScheduler.REASON_AUTH)
        }
        return needsBootstrap
    }

    /**
     * Signs out of the local session.
     *
     * **Does not delete cached data** — see the class docs. The rows stay,
     * scoped to their own `ownerUid`, waiting for their owner to come back.
     */
    fun onSignedOut() {
        currentAccount.clearSession()
    }

    /**
     * Restores the persisted Firebase session on launch, without a network
     * round-trip. The account record is created if the device somehow has a
     * session for an account it never registered.
     */
    suspend fun restoreSession(ownerUid: String?, email: String?, displayName: String?) {
        if (ownerUid.isNullOrBlank()) {
            currentAccount.clearSession()
            return
        }
        val dao = database.accountDao()
        if (dao.getByUid(ownerUid) == null) {
            dao.upsert(
                AccountRecordEntity(
                    ownerUid = ownerUid,
                    email = email?.let { AccountSwitchGuard.normalizeEmail(it) }.orEmpty(),
                    displayName = displayName,
                )
            )
        }
        currentAccount.setSession(ownerUid)
        migrateLegacyStore(ownerUid)
    }

    /**
     * Full remote fetch for one account, then marks it usable offline.
     *
     * Only ever called with the UID that is about to become (or already is) the
     * active session.
     */
    suspend fun bootstrap(ownerUid: String): ApiResult<Unit> {
        if (!connectivityMonitor.isOnline.value) {
            return ApiResult.Error(AccountSwitchGuard.MESSAGE_OFFLINE_SWITCH)
        }

        var failure: SyncOutcome.Failure? = null
        for (repository in pullOrder()) {
            when (val outcome = repository.pullRemote(ownerUid)) {
                is SyncOutcome.Failure -> failure = failure ?: outcome
                else -> Unit
            }
        }

        return if (failure != null) {
            ApiResult.Error(failure.message, failure.cause)
        } else {
            database.accountDao().getByUid(ownerUid)?.let {
                database.accountDao().upsert(it.copy(bootstrapped = true))
            }
            // Anything the user wrote before the bootstrap finished still needs
            // to go out.
            syncScheduler.requestImmediateSync(SyncScheduler.REASON_AUTH)
            ApiResult.Success(Unit)
        }
    }

    /**
     * Permanently removes one account's cached data from this device.
     *
     * This is the *only* path allowed to delete another account's rows, and it
     * is not sign-out — it is an explicit "remove this account from this
     * device" action.
     */
    suspend fun removeAccountFromDevice(ownerUid: String) {
        database.taskDao().deleteAllForOwner(ownerUid)
        database.subjectDao().deleteAllForOwner(ownerUid)
        database.studySessionDao().deleteAllForOwner(ownerUid)
        database.progressDao().deleteForOwner(ownerUid)
        database.accountDao().deleteRecord(ownerUid)
        // If it was the active account, there is no session left to show.
        if (currentAccount.activeUid.value == ownerUid) currentAccount.clearSession()
    }

    /** Exposed for tests and diagnostics. */
    suspend fun knownAccounts(): List<AccountRecordEntity> = database.accountDao().getAll()

    /** True when a push is owed, used to decide whether sign-out can be instant. */
    suspend fun hasPendingWork(ownerUid: String): Boolean =
        pushOrder().any { it is SyncableRepository && it.hasRemote } &&
            database.taskDao().pendingForOwner(ownerUid).isNotEmpty()
}
