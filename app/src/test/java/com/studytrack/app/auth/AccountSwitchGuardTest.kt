package com.studytrack.app.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offline account-switch rules, exhaustively.
 *
 * These are plain JVM tests on purpose: [AccountSwitchGuard] has no Android
 * imports, so the riskiest decision in the offline feature is covered without
 * an emulator and runs on every CI build.
 *
 * The matrix being pinned down:
 *
 * | online | account known on device | bootstrapped | outcome        |
 * |--------|-------------------------|--------------|----------------|
 * | no     | no                      | –            | **BLOCKED**    |
 * | no     | yes                     | no           | **BLOCKED**    |
 * | no     | yes                     | yes          | allow, cache   |
 * | yes    | no                      | –            | allow, fetch   |
 * | yes    | yes                     | no           | allow, fetch   |
 * | yes    | yes                     | yes          | allow, cache   |
 */
class AccountSwitchGuardTest {

    private val alice = KnownAccount(ownerUid = "uid-alice", email = "alice@school.edu", bootstrapped = true)
    private val bobSeeded = KnownAccount(ownerUid = "uid-bob", email = "bob@school.edu", bootstrapped = false)
    private val known = listOf(alice, bobSeeded)

    // ----------------------------------------------------------------- offline

    @Test
    fun `offline switch to an account never seen on this device is blocked`() {
        val decision = AccountSwitchGuard.evaluate(
            request = AccountSwitchRequest(email = "carol@school.edu"),
            device = DeviceState(isOnline = false, activeUid = "uid-alice", knownAccounts = known),
        )

        val blocked = decision as AccountSwitchDecision.BlockedOffline
        assertEquals("account-unseen-on-device", blocked.reason)
        assertEquals(AccountSwitchGuard.MESSAGE_OFFLINE_SWITCH, blocked.message)
    }

    @Test
    fun `offline block message is the exact wording required`() {
        val decision = AccountSwitchGuard.evaluate(
            request = AccountSwitchRequest(email = "stranger@elsewhere.com"),
            device = DeviceState(isOnline = false, activeUid = null, knownAccounts = emptyList()),
        ) as AccountSwitchDecision.BlockedOffline

        assertEquals(
            "Can't switch accounts while offline — connect to the internet first",
            decision.message,
        )
    }

    @Test
    fun `offline switch to a known-but-never-bootstrapped account is also blocked`() {
        // The account signed in here before, but its first full fetch never
        // finished — so there is no cache to restore and showing an empty
        // workspace would look like data loss.
        val decision = AccountSwitchGuard.evaluate(
            request = AccountSwitchRequest(email = "bob@school.edu"),
            device = DeviceState(isOnline = false, activeUid = "uid-alice", knownAccounts = known),
        ) as AccountSwitchDecision.BlockedOffline

        assertEquals("account-cache-incomplete", decision.reason)
    }

    @Test
    fun `offline switch to a fully cached account is allowed from cache`() {
        val decision = AccountSwitchGuard.evaluate(
            request = AccountSwitchRequest(email = "alice@school.edu"),
            device = DeviceState(isOnline = false, activeUid = "uid-bob", knownAccounts = known),
        )

        val allowed = decision as AccountSwitchDecision.AllowFromCache
        assertEquals("cached-for-known-account", allowed.reason)
        assertEquals("uid-alice", allowed.matchedUid)
    }

    @Test
    fun `offline first-ever login on a fresh device is blocked`() {
        // Nobody has ever signed in here, so there is nothing cached for anyone.
        val decision = AccountSwitchGuard.evaluate(
            request = AccountSwitchRequest(email = "alice@school.edu"),
            device = DeviceState(isOnline = false, activeUid = null, knownAccounts = emptyList()),
        )

        assertTrue(decision is AccountSwitchDecision.BlockedOffline)
    }

    // ------------------------------------------------------------------ online

    @Test
    fun `online switch to an unseen account requires a bootstrap fetch`() {
        val decision = AccountSwitchGuard.evaluate(
            request = AccountSwitchRequest(email = "carol@school.edu"),
            device = DeviceState(isOnline = true, activeUid = "uid-alice", knownAccounts = known),
        ) as AccountSwitchDecision.AllowWithBootstrap

        assertEquals("first-time-account", decision.reason)
    }

    @Test
    fun `online switch to a known but unbootstrapped account requires a fetch`() {
        val decision = AccountSwitchGuard.evaluate(
            request = AccountSwitchRequest(email = "bob@school.edu"),
            device = DeviceState(isOnline = true, activeUid = "uid-alice", knownAccounts = known),
        ) as AccountSwitchDecision.AllowWithBootstrap

        assertEquals("cache-incomplete", decision.reason)
        assertEquals("uid-bob", decision.matchedUid)
    }

    @Test
    fun `online switch to a fully cached account is served from cache`() {
        val decision = AccountSwitchGuard.evaluate(
            request = AccountSwitchRequest(email = "alice@school.edu"),
            device = DeviceState(isOnline = true, activeUid = "uid-bob", knownAccounts = known),
        ) as AccountSwitchDecision.AllowFromCache

        assertEquals("uid-alice", decision.matchedUid)
    }

    // ------------------------------------------------------- not actually a switch

    @Test
    fun `re-authenticating the already active account is never blocked`() {
        // Offline, same account: its rows are already scoped to it, so refusing
        // would lock the user out of their own cached data for no reason.
        val decision = AccountSwitchGuard.evaluate(
            request = AccountSwitchRequest(email = "bob@school.edu", isSameAccountAsActive = true),
            device = DeviceState(isOnline = false, activeUid = "uid-bob", knownAccounts = known),
        ) as AccountSwitchDecision.AllowFromCache

        assertEquals("already-active-account", decision.reason)
    }

    // ------------------------------------------------------------- email matching

    @Test
    fun `email matching ignores case and surrounding whitespace`() {
        // Firebase returns lowercase emails, but the user types whatever they
        // like; a mismatch here would wrongly report an unseen account.
        val decision = AccountSwitchGuard.evaluate(
            request = AccountSwitchRequest(email = "  ALICE@School.EDU "),
            device = DeviceState(isOnline = false, activeUid = "uid-bob", knownAccounts = known),
        )

        assertTrue(decision is AccountSwitchDecision.AllowFromCache)
    }

    @Test
    fun `normalizeEmail lowercases and trims`() {
        assertEquals("a@b.com", AccountSwitchGuard.normalizeEmail("  A@B.COM  "))
    }
}
