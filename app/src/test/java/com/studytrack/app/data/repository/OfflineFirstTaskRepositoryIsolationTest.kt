package com.studytrack.app.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.local.SyncStatus
import com.studytrack.app.data.model.CompleteTaskPayload
import com.studytrack.app.data.model.Progress
import com.studytrack.app.data.model.StudySessionPayload
import com.studytrack.app.data.model.Subject
import com.studytrack.app.data.model.SubjectPayload
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.model.TaskAssistanceRequest
import com.studytrack.app.data.model.TaskAssistanceResponse
import com.studytrack.app.data.model.TaskPayload
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.data.sync.SyncScheduler
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.io.IOException

/**
 * Isolation at the repository boundary — the layer the ViewModels actually use.
 *
 * The DAO tests prove the SQL is scoped. These prove the repository *picks the
 * right UID on its own*, that an account switch changes what is shown without
 * moving any rows, and that signing out does not delete anyone's cache.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application, not the manifest's StudyTrackApp: its onCreate
// calls FirebaseAuth.getInstance(), which needs a FirebaseApp that
// does not exist in a JVM test. Nothing under test needs it.
@Config(sdk = [34], application = Application::class)
class OfflineFirstTaskRepositoryIsolationTest {

    private lateinit var db: StudyTrackDatabase
    private lateinit var currentAccount: CurrentAccount
    private lateinit var api: FakeApiService
    private lateinit var repository: OfflineFirstTaskRepository

    private var now = 1_000L
    private val alice = "uid-alice"
    private val bob = "uid-bob"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StudyTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        currentAccount = CurrentAccount()
        api = FakeApiService()
        repository = OfflineFirstTaskRepository(
            db = db,
            api = api,
            currentAccount = currentAccount,
            syncScheduler = RecordingSyncScheduler(),
            clock = { now },
        )
    }

    @After
    fun tearDown() {
        repository.close()
        db.close()
    }

    // ----------------------------------------------------- writes are attributed

    @Test
    fun `a task created as one account is stored against that account's uid`() = runBlocking {
        currentAccount.setSession(alice)
        repository.create(TaskPayload(title = "Alice's essay"))

        currentAccount.setSession(bob)
        repository.create(TaskPayload(title = "Bob's essay"))

        val aliceRows = db.taskDao().getAllForOwner(alice).map { it.title }
        val bobRows = db.taskDao().getAllForOwner(bob).map { it.title }

        assertEquals(listOf("Alice's essay"), aliceRows)
        assertEquals(listOf("Bob's essay"), bobRows)
    }

    @Test
    fun `switching accounts changes what is visible without moving any rows`() = runBlocking {
        currentAccount.setSession(alice)
        repository.create(TaskPayload(title = "Alice's essay"))
        currentAccount.setSession(bob)
        repository.create(TaskPayload(title = "Bob's essay"))

        // The switch is a pointer move; both rows are still exactly where they
        // were written.
        currentAccount.setSession(alice)
        assertEquals(
            listOf("Alice's essay"),
            db.taskDao().getAllForOwner(alice).map { it.title },
        )
        assertEquals(
            listOf("Bob's essay"),
            db.taskDao().getAllForOwner(bob).map { it.title },
        )
    }

    @Test
    fun `one account cannot edit or complete another account's task`() = runBlocking {
        currentAccount.setSession(alice)
        val created = repository.create(TaskPayload(title = "Alice's essay"))
        val taskId = (created as ApiResult.Success).data.taskId

        currentAccount.setSession(bob)
        val update = repository.update(taskId, TaskPayload(title = "Hijacked"))
        val complete = repository.setCompleted(taskId, true)

        assertTrue("update must fail for another account's task", update is ApiResult.Error)
        assertTrue("complete must fail for another account's task", complete is ApiResult.Error)

        currentAccount.setSession(alice)
        val aliceRow = db.taskDao().getById(alice, taskId)
        assertEquals("Alice's essay", aliceRow?.title)
        assertEquals(false, aliceRow?.completed)
    }

    @Test
    fun `one account cannot delete another account's task`() = runBlocking {
        currentAccount.setSession(alice)
        val taskId = (repository.create(TaskPayload(title = "Alice's essay")) as ApiResult.Success)
            .data.taskId

        currentAccount.setSession(bob)
        repository.delete(taskId)

        currentAccount.setSession(alice)
        assertEquals(
            "Bob's delete must not tombstone Alice's row",
            1,
            db.taskDao().getAllForOwner(alice).size,
        )
    }

    // ------------------------------------------------------- sign-out behaviour

    @Test
    fun `signing out keeps every account's cached rows`() = runBlocking {
        currentAccount.setSession(alice)
        repository.create(TaskPayload(title = "Alice's essay"))
        currentAccount.setSession(bob)
        repository.create(TaskPayload(title = "Bob's essay"))

        // This is the rule: sign-out clears the pointer, not the cache.
        currentAccount.clearSession()

        assertEquals(1, db.taskDao().getAllForOwner(alice).size)
        assertEquals(1, db.taskDao().getAllForOwner(bob).size)

        // Returning to a previously used account restores it instantly.
        currentAccount.setSession(alice)
        assertEquals(
            listOf("Alice's essay"),
            db.taskDao().getAllForOwner(alice).map { it.title },
        )
    }

    @Test
    fun `a write with no active session throws instead of writing unscoped`() = runBlocking {
        currentAccount.clearSession()

        val result = runCatching { repository.create(TaskPayload(title = "Orphan")) }

        assertTrue(
            "must refuse rather than store a row with a blank ownerUid",
            result.exceptionOrNull() is IllegalStateException,
        )
        // Nothing was written for anyone.
        assertEquals(0, db.taskDao().getAllForOwner(alice).size)
        assertEquals(0, db.taskDao().getAllForOwner(bob).size)
    }

    // ------------------------------------------------------------ offline-first

    @Test
    fun `writes succeed with no connectivity and are queued as pending`() = runBlocking {
        api.failNextCalls = true
        currentAccount.setSession(alice)

        val result = repository.create(TaskPayload(title = "Written offline"))

        // The UI must update regardless of connectivity.
        assertTrue("offline write must succeed", result is ApiResult.Success)
        val row = db.taskDao().getAllForOwner(alice).single()
        assertEquals(SyncStatus.CREATED, row.syncStatus)
        assertTrue("the row is queued for the sync worker", row.pendingSync)
    }

    @Test
    fun `refresh falls back to the cache when the remote is unreachable`() = runBlocking {
        currentAccount.setSession(alice)
        repository.create(TaskPayload(title = "Cached task"))

        api.failNextCalls = true
        val refreshed = repository.refresh()

        assertTrue(refreshed is ApiResult.Success)
        assertEquals(
            listOf("Cached task"),
            (refreshed as ApiResult.Success).data.map { it.title },
        )
    }

    @Test
    fun `refresh reports an error only when there is no cache and no network`() = runBlocking {
        currentAccount.setSession(alice)
        api.failNextCalls = true

        val refreshed = repository.refresh()

        assertTrue(refreshed is ApiResult.Error)
    }

    // ----------------------------------------------------------- push ordering

    @Test
    fun `push marks rows synced and never touches another account's queue`() = runBlocking {
        currentAccount.setSession(alice)
        repository.create(TaskPayload(title = "Alice's essay"))
        currentAccount.setSession(bob)
        repository.create(TaskPayload(title = "Bob's essay"))

        val outcome = repository.pushPending(alice)

        assertTrue(
            db.taskDao().pendingForOwner(alice).isEmpty(),
        )
        assertEquals(
            "Bob's queue must be untouched by Alice's sync",
            1,
            db.taskDao().pendingForOwner(bob).size,
        )
        assertEquals(1, (outcome as com.studytrack.app.data.sync.SyncOutcome.Success).pushed)
    }

    // ------------------------------------------------------------------ fixtures

    private class RecordingSyncScheduler : SyncScheduler {
        val requests = mutableListOf<String>()
        override fun requestSync(reason: String) {
            requests += reason
        }

        override fun requestImmediateSync(reason: String) {
            requests += "immediate:$reason"
        }
    }

    /**
     * Minimal in-memory backend. Only the task endpoints do anything; the rest
     * throw so a test that accidentally reaches them fails loudly.
     */
    private class FakeApiService : ApiService {
        var failNextCalls = false
        val tasks = mutableListOf<Task>()
        val deleted = mutableListOf<String>()

        override suspend fun getTasks(): List<Task> {
            if (failNextCalls) throw IOException("offline")
            return tasks.toList()
        }

        override suspend fun createTask(payload: TaskPayload): Task {
            if (failNextCalls) throw IOException("offline")
            val task = Task(
                taskId = "remote-" + (tasks.size + 1),
                title = payload.title,
                subjectId = payload.subjectId,
                description = payload.description,
                taskType = payload.taskType,
                priority = payload.priority,
                dueDate = payload.dueDate,
                reminderDate = payload.reminderDate,
            )
            tasks += task
            return task
        }

        override suspend fun updateTask(id: String, payload: TaskPayload): Task {
            if (failNextCalls) throw IOException("offline")
            val existing = tasks.firstOrNull { it.taskId == id }
                ?: return Task(taskId = id, title = payload.title)
            val updated = existing.copy(
                title = payload.title,
                description = payload.description,
                taskType = payload.taskType,
                priority = payload.priority,
                dueDate = payload.dueDate,
                reminderDate = payload.reminderDate,
            )
            tasks[tasks.indexOf(existing)] = updated
            return updated
        }

        override suspend fun setTaskCompleted(id: String, payload: CompleteTaskPayload): Task {
            if (failNextCalls) throw IOException("offline")
            val existing = tasks.first { it.taskId == id }
            val updated = existing.copy(completed = payload.completed)
            tasks[tasks.indexOf(existing)] = updated
            return updated
        }

        override suspend fun deleteTask(id: String): Response<ResponseBody> {
            if (failNextCalls) throw IOException("offline")
            deleted += id
            tasks.removeAll { it.taskId == id }
            return Response.success("".toResponseBody(null))
        }

        override suspend fun getTask(id: String): Task =
            tasks.first { it.taskId == id }

        override suspend fun getCalendarTasks(): List<Task> = tasks.filter { it.dueDate != null }

        override suspend fun getProgress(): Progress = Progress()

        override suspend fun createSubject(payload: SubjectPayload): Subject = notNeeded()
        override suspend fun getSubjects(): List<Subject> = notNeeded()
        override suspend fun getSubject(id: String): Subject = notNeeded()
        override suspend fun updateSubject(id: String, payload: SubjectPayload): Subject = notNeeded()
        override suspend fun deleteSubject(id: String): Response<ResponseBody> = notNeeded()
        override suspend fun logStudySession(payload: StudySessionPayload): Response<ResponseBody> =
            notNeeded()

        override suspend fun taskAssistance(request: TaskAssistanceRequest): TaskAssistanceResponse =
            notNeeded()

        private fun notNeeded(): Nothing {
            // Assert.fail returns void, so it cannot be the body of a Nothing
            // function — throw instead.
            throw AssertionError(
                "FakeApiService: this endpoint should not be reached by these tests"
            )
        }
    }
}
