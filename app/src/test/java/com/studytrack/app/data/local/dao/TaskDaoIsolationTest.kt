package com.studytrack.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.local.SyncStatus
import com.studytrack.app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Per-account isolation, verified against a real SQLite database.
 *
 * Robolectric runs these on the JVM, so the actual Room-generated DAO
 * implementation and the real SQL it emits are what get exercised — not a
 * reimplementation of the queries. If a `WHERE ownerUid = :ownerUid` were ever
 * dropped from one of these methods, the corresponding test here fails.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskDaoIsolationTest {

    private lateinit var db: StudyTrackDatabase
    private lateinit var dao: TaskDao

    private val alice = "uid-alice"
    private val bob = "uid-bob"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StudyTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.taskDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun task(
        ownerUid: String,
        taskId: String,
        title: String = taskId,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
        dueDate: String? = null,
        localUpdatedAt: Long = 1_000L,
    ) = TaskEntity(
        ownerUid = ownerUid,
        taskId = taskId,
        title = title,
        subjectId = null,
        description = null,
        taskType = "STUDY",
        priority = "MEDIUM",
        dueDate = dueDate,
        reminderDate = null,
        completed = false,
        completedAt = null,
        points = 0,
        syncStatus = syncStatus,
        localUpdatedAt = localUpdatedAt,
        remoteUpdatedAt = 0L,
    )

    // ------------------------------------------------------- the core guarantee

    @Test
    fun `observing one account never returns another account's tasks`() = runBlocking {
        dao.upsert(task(alice, "a1", "Alice's essay"))
        dao.upsert(task(alice, "a2", "Alice's exam"))
        dao.upsert(task(bob, "b1", "Bob's essay"))

        val aliceSees = dao.observeForOwner(alice).first().map { it.title }
        val bobSees = dao.observeForOwner(bob).first().map { it.title }

        assertEquals(listOf("Alice's essay", "Alice's exam"), aliceSees.sorted())
        assertEquals(listOf("Bob's essay"), bobSees)
        assertTrue("Alice must not see Bob's row", aliceSees.none { it.startsWith("Bob") })
    }

    @Test
    fun `getById cannot fetch another account's task even with the right id`() {
        // The isolation guarantee must hold for direct id lookups too, which is
        // where a missing filter is easiest to miss.
        runBlocking { dao.upsert(task(bob, "shared-id", "Bob's private task")) }

        val stolen = runBlocking { dao.getById(alice, "shared-id") }

        assertNull("Alice must not be able to read Bob's task by id", stolen)
    }

    @Test
    fun `the same taskId under two accounts stores two independent rows`() {
        // ownerUid is part of the primary key, so this is an insert, not an
        // overwrite — the structural guarantee behind the whole feature.
        runBlocking {
            dao.upsert(task(alice, "same-id", "Alice's version"))
            dao.upsert(task(bob, "same-id", "Bob's version"))
        }

        assertEquals(1, runBlocking { dao.countAllRowsForOwner(alice) })
        assertEquals(1, runBlocking { dao.countAllRowsForOwner(bob) })
        assertEquals("Alice's version", runBlocking { dao.getById(alice, "same-id") }?.title)
        assertEquals("Bob's version", runBlocking { dao.getById(bob, "same-id") }?.title)
    }

    // ------------------------------------------------------------- scoped writes

    @Test
    fun `markDeleted tombstones only the named account's row`() = runBlocking {
        dao.upsert(task(alice, "same-id", "Alice's"))
        dao.upsert(task(bob, "same-id", "Bob's"))

        dao.markDeleted(alice, "same-id", updatedAt = 2_000L)

        assertNull("Alice's row is tombstoned", dao.getById(alice, "same-id"))
        assertEquals("Bob's row is untouched", "Bob's", dao.getById(bob, "same-id")?.title)
    }

    @Test
    fun `replaceOwnerSnapshot leaves the other account completely alone`() = runBlocking {
        dao.upsert(task(alice, "a1", "Alice old"))
        dao.upsert(task(bob, "b1", "Bob's"))

        // Alice's remote snapshot now contains a different row.
        dao.replaceOwnerSnapshot(
            remoteRows = listOf(task(alice, "a2", "Alice new")),
            ownerUid = alice,
            keepTaskIds = emptyList(),
        )

        assertEquals(listOf("Alice new"), dao.getAllForOwner(alice).map { it.title })
        assertEquals(listOf("Bob's"), dao.getAllForOwner(bob).map { it.title })
    }

    @Test
    fun `replaceOwnerSnapshot preserves pending local edits`() = runBlocking {
        dao.upsert(task(alice, "edited", "Alice's offline edit", SyncStatus.UPDATED))
        dao.upsert(task(alice, "settled", "Alice's synced row", SyncStatus.SYNCED))

        // The remote does not know about either row yet.
        dao.replaceOwnerSnapshot(
            remoteRows = emptyList(),
            ownerUid = alice,
            keepTaskIds = listOf("edited"),
        )

        val remaining = dao.getAllForOwner(alice).map { it.title }
        assertTrue("the unpushed edit must survive a pull", "Alice's offline edit" in remaining)
        assertTrue("the settled row is replaced by the snapshot", "Alice's synced row" !in remaining)
    }

    @Test
    fun `deleteAllForOwner removes one account and nothing else`() = runBlocking {
        dao.upsert(task(alice, "a1"))
        dao.upsert(task(bob, "b1"))
        dao.upsert(task(bob, "b2", "Bob's pending", SyncStatus.CREATED))

        dao.deleteAllForOwner(alice)

        assertEquals(0, dao.countAllRowsForOwner(alice))
        assertEquals(2, dao.countAllRowsForOwner(bob))
    }

    // -------------------------------------------------------------- sync queue

    @Test
    fun `the pending queue contains only the active account's rows`() = runBlocking {
        dao.upsert(task(alice, "a1", syncStatus = SyncStatus.CREATED))
        dao.upsert(task(alice, "a2", syncStatus = SyncStatus.SYNCED))
        dao.upsert(task(bob, "b1", syncStatus = SyncStatus.UPDATED))

        val alicePending = dao.pendingForOwner(alice).map { it.taskId }
        val bobPending = dao.pendingForOwner(bob).map { it.taskId }

        assertEquals(listOf("a1"), alicePending)
        assertEquals(listOf("b1"), bobPending)
    }

    @Test
    fun `pending rows are pushed oldest edit first`() = runBlocking {
        dao.upsert(task(alice, "third", localUpdatedAt = 3_000L, syncStatus = SyncStatus.UPDATED))
        dao.upsert(task(alice, "first", localUpdatedAt = 1_000L, syncStatus = SyncStatus.CREATED))
        dao.upsert(task(alice, "second", localUpdatedAt = 2_000L, syncStatus = SyncStatus.UPDATED))

        // Create-then-delete must not invert, or the row comes back to life.
        assertEquals(
            listOf("first", "second", "third"),
            dao.pendingForOwner(alice).map { it.taskId },
        )
    }

    // ---------------------------------------------------------------- tombstones

    @Test
    fun `tombstoned rows are hidden from every read path`() = runBlocking {
        dao.upsert(task(alice, "gone", dueDate = "2026-10-01T09:00:00"))
        dao.upsert(task(alice, "kept", dueDate = "2026-10-02T09:00:00"))
        dao.markDeleted(alice, "gone", updatedAt = 2_000L)

        assertTrue(dao.getAllForOwner(alice).none { it.taskId == "gone" })
        assertTrue(dao.observeForOwner(alice).first().none { it.taskId == "gone" })
        assertTrue(dao.observeDatedForOwner(alice).first().none { it.taskId == "gone" })
        assertEquals(1, dao.countForOwner(alice))
        assertEquals(0, dao.countCompletedForOwner(alice))
        // The worker can still see it, which is what lets the delete be pushed.
        assertEquals(listOf("gone"), dao.pendingForOwner(alice).map { it.taskId })
    }

    @Test
    fun `purgeTombstone cannot reach another account's row`() = runBlocking {
        dao.upsert(task(alice, "same-id"))
        dao.upsert(task(bob, "same-id"))

        dao.purgeTombstone(alice, "same-id")

        assertEquals(0, dao.countAllRowsForOwner(alice))
        assertEquals(1, dao.countAllRowsForOwner(bob))
    }

    // ------------------------------------------------------------------ calendar

    @Test
    fun `the dated-task query is scoped and skips undated rows`() = runBlocking {
        dao.upsert(task(alice, "a1", dueDate = "2026-10-01T09:00:00"))
        dao.upsert(task(alice, "a2", dueDate = null))
        dao.upsert(task(bob, "b1", dueDate = "2026-10-01T09:00:00"))

        assertEquals(listOf("a1"), dao.observeDatedForOwner(alice).first().map { it.taskId })
    }

    // ------------------------------------------------- an account with no data

    @Test
    fun `an account that has never synced sees an empty list, never someone else's`() =
        runBlocking {
            dao.upsert(task(alice, "a1"))

            val carolSees = dao.observeForOwner("uid-carol").first()

            assertTrue("a brand-new account must see nothing at all", carolSees.isEmpty())
        }
}
