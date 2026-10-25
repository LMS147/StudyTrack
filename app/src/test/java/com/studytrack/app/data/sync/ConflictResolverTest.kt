package com.studytrack.app.data.sync

import com.studytrack.app.data.local.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Last-write-wins arbitration.
 *
 * The property that matters most is the first one: **an unpushed local edit can
 * never be overwritten by a pull.** Every other rule is secondary to not losing
 * work the user has already done.
 */
class ConflictResolverTest {

    @Test
    fun `a row not cached locally always accepts the remote copy`() {
        assertTrue(
            ConflictResolver.remoteWins(
                localSyncStatus = null,
                localUpdatedAt = 0L,
                remoteUpdatedAt = 1_000L,
            )
        )
    }

    @Test
    fun `an unpushed local edit beats a newer remote copy`() {
        // Remote timestamp is newer, but the local edit has not been pushed
        // yet — the remote cannot possibly reflect it.
        assertFalse(
            ConflictResolver.remoteWins(
                localSyncStatus = SyncStatus.UPDATED,
                localUpdatedAt = 1_000L,
                remoteUpdatedAt = 9_999L,
            )
        )
    }

    @Test
    fun `an unpushed local create beats the remote`() {
        assertFalse(
            ConflictResolver.remoteWins(
                localSyncStatus = SyncStatus.CREATED,
                localUpdatedAt = 1_000L,
                remoteUpdatedAt = 9_999L,
            )
        )
    }

    @Test
    fun `an offline delete is never resurrected by a pull`() {
        assertFalse(
            ConflictResolver.remoteWins(
                localSyncStatus = SyncStatus.DELETED,
                localUpdatedAt = 1_000L,
                remoteUpdatedAt = 9_999L,
            )
        )
    }

    @Test
    fun `a settled local row yields to a newer remote copy`() {
        assertTrue(
            ConflictResolver.remoteWins(
                localSyncStatus = SyncStatus.SYNCED,
                localUpdatedAt = 1_000L,
                remoteUpdatedAt = 2_000L,
            )
        )
    }

    @Test
    fun `a settled local row keeps an older remote copy`() {
        assertFalse(
            ConflictResolver.remoteWins(
                localSyncStatus = SyncStatus.SYNCED,
                localUpdatedAt = 2_000L,
                remoteUpdatedAt = 1_000L,
            )
        )
    }

    @Test
    fun `a tie goes to the remote as the source of truth`() {
        assertTrue(
            ConflictResolver.remoteWins(
                localSyncStatus = SyncStatus.SYNCED,
                localUpdatedAt = 1_000L,
                remoteUpdatedAt = 1_000L,
            )
        )
    }

    @Test
    fun `an unknown stored status is treated as pending, not dropped`() {
        // SyncStatus.fromRaw maps garbage to UPDATED so a row written by a
        // future build is re-pushed rather than silently lost.
        assertEquals(SyncStatus.UPDATED, SyncStatus.fromRaw("SOMETHING_NEW"))
        assertEquals(SyncStatus.UPDATED, SyncStatus.fromRaw(null))
    }
}
