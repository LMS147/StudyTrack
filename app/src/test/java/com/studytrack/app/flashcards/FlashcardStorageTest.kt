package com.studytrack.app.flashcards

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.local.entity.FlashcardEntity
import com.studytrack.app.data.local.entity.SubjectEntity
import com.studytrack.app.data.repository.FlashcardRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
class FlashcardStorageTest {
    private lateinit var db: StudyTrackDatabase
    private lateinit var account: CurrentAccount
    private lateinit var repo: FlashcardRepository
    private val today = LocalDate.parse("2026-10-27")
    private val alice = FlashcardEntity("alice", "same-id", "subject", "Question", "Answer", today.toString(), 0, 0, null)
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), StudyTrackDatabase::class.java)
            .allowMainThreadQueries().build()
        account = CurrentAccount().apply { setSession("alice") }
        repo = FlashcardRepository(db, account)
        runBlocking {
            db.subjectDao().upsert(SubjectEntity("alice", "subject", "Maths", null, localUpdatedAt = 1))
            db.subjectDao().upsert(SubjectEntity("bob", "subject", "Biology", null, localUpdatedAt = 1))
        }
    }
    @After fun close() { db.close() }
    @Test fun `same card IDs remain account scoped and deletes cannot remove another account`() = runBlocking {
        repo.save(alice, true)
        account.setSession("bob")
        repo.save(alice.copy(ownerUid = "bob", question = "Bob question"), true)
        val bobCards = withTimeout(5000) { repo.cards.first { it.isNotEmpty() } }
        assertEquals(listOf("Bob question"), bobCards.map { it.question })
        repo.delete("bob", "same-id")
        assertNull(db.flashcardDao().get("bob", "same-id"))
        assertEquals("Question", db.flashcardDao().get("alice", "same-id")!!.question)
        account.clearSession()
        assertTrue(repo.cards.first().isEmpty())
    }
    @Test fun `rating updates due date and progress and rejects rating before due`() = runBlocking {
        repo.save(alice, true)
        repo.rate("alice", "same-id", ReviewRating.EASY, today)
        val stored = db.flashcardDao().get("alice", "same-id")!!
        assertEquals("2026-10-30", stored.dueDate)
        assertEquals(3, stored.intervalDays)
        assertEquals(1, stored.reviewCount)
        assertEquals(today.toString(), stored.lastReviewedOn)
        try { repo.rate("alice", "same-id", ReviewRating.HARD, today); fail("future card rated")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun `a stale editor preserves a concurrent review and cannot resurrect a deleted card`() = runBlocking {
        repo.save(alice, true)
        repo.rate("alice", "same-id", ReviewRating.EASY, today)
        repo.save(alice.copy(question = "Edited"), false)
        assertEquals("2026-10-30", db.flashcardDao().get("alice", "same-id")!!.dueDate)
        repo.delete("alice", "same-id")
        try { repo.save(alice.copy(question = "Old draft"), false); fail("deleted card resurrected")
        } catch (_: IllegalArgumentException) { }
        assertNull(db.flashcardDao().get("alice", "same-id"))
    }
    @Test fun `account switch blocks edits and rating through stale screens`() = runBlocking {
        repo.save(alice, true)
        account.setSession("bob")
        try { repo.save(alice.copy(question = "Wrong"), false); fail("cross-account save")
        } catch (_: IllegalArgumentException) { }
        try { repo.rate("alice", "same-id", ReviewRating.AGAIN, today); fail("cross-account rating")
        } catch (_: IllegalArgumentException) { }
        assertEquals("Question", db.flashcardDao().get("alice", "same-id")!!.question)
    }
    @Test fun `cards require a question answer and a subject owned by the account`() = runBlocking {
        for (bad in listOf(alice.copy(question = " "), alice.copy(answer = " "), alice.copy(subjectId = "missing"))) {
            try { repo.save(bad, true); fail("invalid card accepted")
            } catch (_: IllegalArgumentException) { }
        }
        assertNull(db.flashcardDao().get("alice", "same-id"))
    }
}
