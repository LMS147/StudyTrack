package com.studytrack.app.timetable

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.local.entity.TimetableEntry
import com.studytrack.app.data.repository.TimetableRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TimetableStorageTest {
    private lateinit var db: StudyTrackDatabase
    private lateinit var account: CurrentAccount
    private lateinit var repo: TimetableRepository
    private val entry = TimetableEntry("alice", "same-id", "Lecture", null, 1, 540, 600,
        "Lab", "Lecturer", "2026-10-01", null)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), StudyTrackDatabase::class.java)
            .allowMainThreadQueries().build()
        account = CurrentAccount().apply { setSession("alice") }
        repo = TimetableRepository(db, account)
    }
    @After fun close() { db.close() }

    @Test fun `same IDs across accounts stay isolated and deletes are scoped`() = runBlocking {
        repo.save(entry)
        account.setSession("bob")
        repo.save(entry.copy(ownerUid = "bob", title = "Bob class"))
        val bob = withTimeout(5000) { repo.entries.first { it.isNotEmpty() } }
        assertEquals(listOf("Bob class"), bob.map { it.title })
        repo.delete(bob.single())
        assertTrue(db.timetableDao().all("bob").isEmpty())
        assertEquals("Lecture", db.timetableDao().all("alice").single().title)
        account.clearSession()
        assertTrue(repo.entries.first().isEmpty())
    }
    @Test fun `overlap is rejected and editing updates a single recurring record`() = runBlocking {
        repo.save(entry)
        try { repo.save(entry.copy(entryId = "conflict")); fail("overlap accepted")
        } catch (_: IllegalArgumentException) { }
        repo.save(entry.copy(title = "Updated", endMinute = 630))
        assertEquals(1, db.timetableDao().all("alice").size)
        assertEquals("Updated", db.timetableDao().all("alice").single().title)
    }
    @Test fun `an editor opened for another account cannot write after a switch`() = runBlocking {
        account.setSession("bob")
        try { repo.save(entry); fail("stale editor wrote data")
        } catch (_: IllegalArgumentException) { }
        assertTrue(db.timetableDao().all("alice").isEmpty())
        assertTrue(db.timetableDao().all("bob").isEmpty())
    }
}
