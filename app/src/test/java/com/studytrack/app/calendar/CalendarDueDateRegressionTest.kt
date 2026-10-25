package com.studytrack.app.calendar

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.TaskPayload
import com.studytrack.app.data.repository.OfflineFirstCalendarRepository
import com.studytrack.app.data.repository.OfflineFirstSubjectRepository
import com.studytrack.app.data.repository.OfflineFirstTaskRepository
import com.studytrack.app.data.sync.SyncOutcome
import com.studytrack.app.data.sync.SyncScheduler
import com.studytrack.app.util.DateTimeUtils
import java.time.LocalDate
import java.time.LocalTime
import java.util.TimeZone
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
 * Regression tests for "a task doesn't appear on its due date in the Calendar".
 *
 * Two distinct guarantees are pinned here:
 *
 * 1. DATE MATCHING: the day a task lands on is derived from
 *    [DateTimeUtils.parseDate] — the app's single canonical parser — for every
 *    stored format (full date-time, date-only, UTC offset). The month-grid dot
 *    indicator and the selected-day list read the SAME `tasksByDate` map, so
 *    they cannot disagree.
 *
 * 2. LIVENESS (the actual field bug): the CalendarViewModel OBSERVES the live
 *    UID-scoped Room flow. The old implementation took a one-shot snapshot in
 *    `init`, and because the fragment's ViewModel survives bottom-nav
 *    navigation, a task created afterwards (Add/Edit Task screen, AI accept)
 *    never appeared — no list entry and no dot — until process death.
 *
 * Timezone is pinned to Africa/Johannesburg (UTC+2) so the offset-conversion
 * assertions are deterministic.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CalendarDueDateRegressionTest {

    private lateinit var db: StudyTrackDatabase
    private lateinit var currentAccount: CurrentAccount
    private lateinit var taskRepo: OfflineFirstTaskRepository
    private lateinit var subjectRepo: OfflineFirstSubjectRepository
    private lateinit var calendarRepo: OfflineFirstCalendarRepository
    private var savedTimeZone: TimeZone? = null

    private val alice = "uid-alice"

    @Before
    fun setUp() {
        savedTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Africa/Johannesburg"))
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
        calendarRepo = OfflineFirstCalendarRepository(
            db = db,
            currentAccount = currentAccount,
            pullTasks = { _ -> SyncOutcome.Skipped },
        )
        currentAccount.setSession(alice)
    }

    @After
    fun tearDown() {
        db.close()
        savedTimeZone?.let { TimeZone.setDefault(it) }
    }

    // ------------------------------------------------- date matching (bug 1a)

    @Test
    fun `a task stored with a time component shows on its due day, list and dot`() {
        // Exactly what AddEditTaskFragment stores: ISO local date-time.
        createTask("Essay draft", "2026-09-26T23:59:00")

        val vm = newViewModel()
        val dueDay = LocalDate.of(2026, 9, 26)
        vm.selectDate(dueDay)

        assertTrue(
            "task never appeared on its due day",
            pumpUntil { vm.state.value.selectedDateTasks.isNotEmpty() },
        )
        val state = vm.state.value
        assertEquals(
            listOf("Essay draft"),
            state.selectedDateTasks.map { it.title },
        )
        // The month-grid dot indicator reads this same map — one code path.
        assertEquals(
            listOf("Essay draft"),
            state.tasksByDate[dueDay].orEmpty().map { it.title },
        )
    }

    @Test
    fun `date-only and UTC-offset due dates land on the correct local day`() {
        createTask("Legacy note", "2026-09-26")
        // 22:30Z == 00:30 the NEXT day in Africa/Johannesburg (UTC+2).
        createTask("Night submission", "2026-09-26T22:30:00Z")

        val vm = newViewModel()

        assertTrue(
            "calendar never grouped the tasks",
            pumpUntil { vm.state.value.tasksByDate.size == 2 },
        )
        val byDate = vm.state.value.tasksByDate
        assertEquals(
            listOf("Legacy note"),
            byDate[LocalDate.of(2026, 9, 26)].orEmpty().map { it.title },
        )
        assertEquals(
            listOf("Night submission"),
            byDate[LocalDate.of(2026, 9, 27)].orEmpty().map { it.title },
        )
    }

    // ------------------------------------------- liveness (bug 1b — the fix)

    @Test
    fun `a task created after the calendar opened appears without a manual refresh`() {
        val vm = newViewModel()
        // Let the initial (empty) snapshot settle.
        assertTrue(
            "calendar never finished its first load",
            pumpUntil { !vm.state.value.loading },
        )

        // The field scenario: user opens Calendar, then creates a task due
        // tomorrow on the Add/Edit screen and comes back. No refresh() call.
        val tomorrow = LocalDate.now().plusDays(1)
        createTask(
            "Calculus problem set",
            DateTimeUtils.formatIso(tomorrow, LocalTime.of(17, 0)),
        )
        vm.selectDate(tomorrow)

        assertTrue(
            "task created after the calendar opened never appeared (stale snapshot)",
            pumpUntil { vm.state.value.selectedDateTasks.isNotEmpty() },
        )
        assertEquals(
            listOf("Calculus problem set"),
            vm.state.value.selectedDateTasks.map { it.title },
        )
    }

    // ------------------------------------------------------------- helpers

    private fun newViewModel() =
        CalendarViewModel(calendarRepo, subjectRepo, taskRepo)

    private fun createTask(title: String, dueDate: String) {
        runBlocking {
            taskRepo.create(
                TaskPayload(
                    title = title,
                    dueDate = dueDate,
                    priority = Priority.HIGH,
                )
            )
        }
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

    private object NoopSyncScheduler : SyncScheduler {
        override fun requestSync(reason: String) = Unit
        override fun requestImmediateSync(reason: String) = Unit
    }
}
