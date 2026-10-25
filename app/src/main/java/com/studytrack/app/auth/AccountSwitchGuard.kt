package com.studytrack.app.auth

/**
 * Decides what a sign-in may do to the local cache, before any data is read.
 *
 * This is the gate that makes multi-account use of one device safe. It answers
 * one question: *can this account be served from what is already on this
 * device, must it be fetched first, or must the switch be refused?*
 *
 * ## Why it is a plain Kotlin object with no Android imports
 *
 * The per-account isolation rules are the riskiest logic in this feature — a
 * mistake shows one student another student's tasks. Keeping the decision in a
 * dependency-free class means it is exhaustively unit-testable on the JVM (see
 * `AccountSwitchGuardTest`) rather than only exercisable through an emulator.
 * The Android layers call into it; they do not reimplement it.
 */
object AccountSwitchGuard {

    /**
     * Shown verbatim when an account switch is refused because the device is
     * offline and has no cached data for that account.
     */
    const val MESSAGE_OFFLINE_SWITCH =
        "Can't switch accounts while offline — connect to the internet first"

    /**
     * Evaluates a sign-in attempt.
     *
     * @param request the account being signed into.
     * @param device  connectivity plus what this device already knows.
     */
    fun evaluate(request: AccountSwitchRequest, device: DeviceState): AccountSwitchDecision {
        // Re-authenticating the account that is already active is not a switch.
        // Its rows are already scoped to it, so there is nothing to protect.
        if (request.isSameAccountAsActive) {
            return AccountSwitchDecision.AllowFromCache(reason = "already-active-account")
        }

        val normalized = normalizeEmail(request.email)
        val known = device.knownAccounts.firstOrNull {
            normalizeEmail(it.email) == normalized
        }

        if (!device.isOnline) {
            // Offline there is no way to prove who this account is or to fetch
            // anything, so the only safe source is a cache this device already
            // holds *and* has fully populated for that account.
            return when {
                known == null -> AccountSwitchDecision.BlockedOffline(
                    message = MESSAGE_OFFLINE_SWITCH,
                    reason = "account-unseen-on-device",
                )

                !known.bootstrapped -> AccountSwitchDecision.BlockedOffline(
                    // Same refusal, different cause: the account signed in here
                    // before but its first full fetch never completed, so the
                    // cache is empty or partial. Showing it would look like the
                    // account has no tasks — exactly the "empty or wrong data"
                    // outcome this guard exists to prevent.
                    message = MESSAGE_OFFLINE_SWITCH,
                    reason = "account-cache-incomplete",
                )

                else -> AccountSwitchDecision.AllowFromCache(
                    reason = "cached-for-known-account",
                    matchedUid = known.ownerUid,
                )
            }
        }

        // Online: an account this device has never seen (or never finished
        // loading) must be fully fetched before any screen is shown, so the
        // user is never briefly shown an empty or stale workspace.
        return if (known == null || !known.bootstrapped) {
            AccountSwitchDecision.AllowWithBootstrap(
                reason = if (known == null) "first-time-account" else "cache-incomplete",
                matchedUid = known?.ownerUid,
            )
        } else {
            AccountSwitchDecision.AllowFromCache(
                reason = "cached-for-known-account",
                matchedUid = known.ownerUid,
            )
        }
    }

    /** Lowercase + trim, so `A@B.com` and ` a@b.COM ` are the same account. */
    fun normalizeEmail(email: String): String = email.trim().lowercase()
}

/** The account a sign-in attempt is asking for. */
data class AccountSwitchRequest(
    val email: String,
    /** True when this is the already-signed-in account re-authenticating. */
    val isSameAccountAsActive: Boolean = false,
)

/** Connectivity plus everything this device already knows about its accounts. */
data class DeviceState(
    val isOnline: Boolean,
    val activeUid: String?,
    val knownAccounts: List<KnownAccount> = emptyList(),
)

/** The slice of `account_records` the guard needs. */
data class KnownAccount(
    val ownerUid: String,
    val email: String,
    /** True once a full remote fetch has populated this account's rows. */
    val bootstrapped: Boolean,
)

/** What the sign-in flow is permitted to do next. */
sealed interface AccountSwitchDecision {

    /** Machine-readable cause, for tests and diagnostics. Never shown to users. */
    val reason: String

    /**
     * Serve this account straight from the local cache. No blocking fetch
     * needed — this is what makes returning to a previously used account feel
     * instant.
     */
    data class AllowFromCache(
        override val reason: String,
        val matchedUid: String? = null,
    ) : AccountSwitchDecision

    /**
     * Allowed, but the app must complete a full remote fetch for this account
     * **before showing any screens**. Used for an account this device has not
     * seen before.
     */
    data class AllowWithBootstrap(
        override val reason: String,
        val matchedUid: String? = null,
    ) : AccountSwitchDecision

    /**
     * Refuse the switch. The user stays signed in as they are; no other
     * account's data is read, shown, or deleted.
     */
    data class BlockedOffline(
        val message: String,
        override val reason: String,
    ) : AccountSwitchDecision
}
