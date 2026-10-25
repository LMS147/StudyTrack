package com.studytrack.app.ai

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.SubjectPayload
import com.studytrack.app.data.model.TaskAssistanceRequest
import com.studytrack.app.data.model.TaskAssistanceResponse
import com.studytrack.app.data.model.TaskPayload
import com.studytrack.app.data.model.TaskType
import com.studytrack.app.data.repository.AiBrain
import com.studytrack.app.data.repository.OfflineFirstSubjectRepository
import com.studytrack.app.data.repository.OfflineFirstTaskRepository
import com.studytrack.app.data.repository.SettingsRepository
import com.studytrack.app.data.sync.SyncScheduler
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.DateTimeUtils
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression tests for "the AI can't read/use due dates that are already on
 * the calendar".
 *
 * Root cause: the request body sent to the AI brain never contained the
 * student's task list — only the typed message, chat history, today/timezone,
 * subject names and an optional single-task context. The model therefore had
 * NO WAY to answer "what's due this week?".
 *
 * The fix computes the context on the client ([AiTaskContextBuilder]) from the
 * same UID-scoped Room cache the Calendar reads and compares dates with the
 * same canonical parser, so the AI's view of "due" can never drift from the
 * calendar's. These tests drive the real [AiAssistantViewModel] against
 * in-memory Room and capture the outgoing request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AiUpcomingTaskContextTest {

    private lateinit var db: StudyTrackDatabase
    private lateinit var currentAccount: CurrentAccount
    private lateinit var taskRepo: OfflineFirstTaskRepository
    private lateinit var subjectRepo: OfflineFirstSubjectRepository
    private lateinit var settings: SettingsRepository

    private val alice = "uid-alice"
    private val bob = "uid-bob"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StudyTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        currentAccount = CurrentAccount()
        taskRepo = OfflineFirstTaskRepository(
            db = db, api = null, currentAccount = currentAccount,
            syncScheduler = NoopSyncScheduler,
        )
        subjectRepo = OfflineFirstSubjectRepository(
            db = db, api = null, currentAccount = currentAccount,
            syncScheduler = NoopSyncScheduler,
        )
        settings = SettingsRepository(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `the AI request carries the student's upcoming tasks with subject and date`() {
        currentAccount.setSession(alice)
        val mathsId = createSubject("Mathematics")
        val tomorrow = LocalDate.now().plusDays(1)

        createTask(
            title = "Calculus test",
            dueDate = DateTimeUtils.formatIso(tomorrow, LocalTime.of(17, 0)),
            subjectId = mathsId,
        )
        // Out of the two-week window: must NOT be sent.
        createTask(
            title = "Far future thesis",
            dueDate = DateTimeUtils.formatIso(LocalDate.now().plusDays(90), LocalTime.of(9, 0)),
        )
        // Completed: must NOT be sent.
        runBlocking {
            val done = taskRepo.create(
                TaskPayload(
                    title = "Already done",
                    dueDate = DateTimeUtils.formatIso(tomorrow, LocalTime.of(8, 0)),
                )
            )
            (done as ApiResult.Success).let { taskRepo.setCompleted(it.data.taskId, true) }
        }

        val brain = CapturingBrain()
        val vm = newViewModel(brain)
        // Wait until the repositories' live flows have the rows (the VM reads
        // both `.value` snapshots when it builds the request).
        assertTrue(
            "task repository never observed the created tasks",
            pumpUntil { taskRepo.tasks.value.size == 3 && subjectRepo.subjects.value.isNotEmpty() },
        )

        vm.start(
            welcomeText = "welcome",
            emptyReplyText = "empty",
            addedViaEditorText = "added",
            confirmationTemplate = "added for %s",
            confirmationWithSubtasksTemplate = "added for %s with %d subtasks",
            confirmationSubjectSuffix = " under %s",
            initialPrompt = "what do I have due tomorrow?",
            contextTaskId = null,
        )

        val request = pumpUntilRequest(brain)
        val upcoming = request.upcomingTasks
        assertEquals(listOf("Calculus test"), upcoming.map { it.title })
        assertEquals(
            DateTimeUtils.formatIso(tomorrow, LocalTime.of(17, 0)),
            upcoming.first().dueDate,
        )
        assertEquals("Mathematics", upcoming.first().subjectName)
        assertEquals(Priority.MEDIUM.label, upcoming.first().priority)
    }

    @Test
    fun `the AI context never leaks the previous account's tasks`() {
        currentAccount.setSession(alice)
        createTask(
            title = "Alice essay",
            dueDate = DateTimeUtils.formatIso(LocalDate.now().plusDays(1), LocalTime.of(17, 0)),
        )
        assertTrue(
            "task repository never observed alice's task",
            pumpUntil { taskRepo.tasks.value.isNotEmpty() },
        )

        currentAccount.clearSession()
        currentAccount.setSession(bob)

        val brain = CapturingBrain()
        val vm = newViewModel(brain)
        assertTrue(
            "task repository never re-scoped to bob",
            pumpUntil { taskRepo.tasks.value.isEmpty() },
        )
        vm.start(
            welcomeText = "welcome",
            emptyReplyText = "empty",
            addedViaEditorText = "added",
            confirmationTemplate = "added for %s",
            confirmationWithSubtasksTemplate = "added for %s with %d subtasks",
            confirmationSubjectSuffix = " under %s",
            initialPrompt = "what's due this week?",
            contextTaskId = null,
        )

        val request = pumpUntilRequest(brain)
        assertTrue(
            "bob's AI request must not mention alice's tasks, got: " +
                request.upcomingTasks.map { it.title },
            request.upcomingTasks.none { it.title == "Alice essay" },
        )
        assertTrue(request.upcomingTasks.isEmpty())
    }

    // -------------------------------------------------- builder, pure logic

    @Test
    fun `the builder window keeps overdue and near tasks and caps the list`() {
        val today = LocalDate.of(2026, 9, 21)
        fun task(title: String, dayOffset: Long, completed: Boolean = false) =
            com.studytrack.app.data.model.Task(
                taskId = title,
                title = title,
                dueDate = DateTimeUtils.formatIso(today.plusDays(dayOffset), LocalTime.NOON),
                completed = completed,
            )

        // --- window: overdue through +14 days, open and dated only ---
        val windowed = AiTaskContextBuilder.upcoming(
            listOf(
                task("30 days overdue", -30),
                task("due tomorrow", 1),
                task("due in 14", 14),
                task("due in 15 — out", 15),
                task("undated backlog", 0).copy(dueDate = null),
                task("completed — out", 2, completed = true),
            ),
            today = today,
        )
        assertEquals(
            listOf("30 days overdue", "due tomorrow", "due in 14"),
            windowed.map { it.title },
        )

        // --- cap: never send more than LIMIT entries ---
        val capped = AiTaskContextBuilder.upcoming(
            (1..(AiTaskContextBuilder.LIMIT + 5)).map { task("filler $it", 3) },
            today = today,
        )
        assertEquals(AiTaskContextBuilder.LIMIT, capped.size)
    }

    // ------------------------------------------------------------- helpers

    private fun newViewModel(brain: AiBrain) =
        AiAssistantViewModel(brain, taskRepo, subjectRepo, settings)

    private fun createSubject(name: String): String =
        runBlocking {
            (subjectRepo.create(SubjectPayload(subjectName = name)) as ApiResult.Success)
                .data.subjectId
        }

    private fun createTask(title: String, dueDate: String, subjectId: String? = null) {
        runBlocking {
            taskRepo.create(
                TaskPayload(
                    title = title,
                    subjectId = subjectId,
                    taskType = TaskType.TEST,
                    dueDate = dueDate,
                )
            )
        }
    }

    private fun pumpUntilRequest(brain: CapturingBrain): TaskAssistanceRequest {
        assertTrue(
            "the AI request was never sent",
            pumpUntil { brain.lastRequest != null },
        )
        return brain.lastRequest!!
    }

    private fun pumpUntil(timeoutMs: Long = 10_000, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return condition()
    }

    private class CapturingBrain : AiBrain {
        @Volatile
        var lastRequest: TaskAssistanceRequest? = null

        override suspend fun taskAssistance(
            request: TaskAssistanceRequest,
        ): ApiResult<TaskAssistanceResponse> {
            lastRequest = request
            return ApiResult.Success(
                TaskAssistanceResponse(replyText = "You have a calculus test tomorrow.")
            )
        }
    }

    private object NoopSyncScheduler : SyncScheduler {
        override fun requestSync(reason: String) = Unit
        override fun requestImmediateSync(reason: String) = Unit
    }
}
