package com.studytrack.app.ai

import android.app.Application
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.studytrack.app.R
import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.model.TaskAssistanceRequest
import com.studytrack.app.data.model.TaskAssistanceResponse
import com.studytrack.app.data.model.TaskSuggestion
import com.studytrack.app.data.repository.AiBrain
import com.studytrack.app.data.repository.OfflineFirstSubjectRepository
import com.studytrack.app.data.repository.OfflineFirstTaskRepository
import com.studytrack.app.data.repository.SettingsRepository
import com.studytrack.app.data.sync.SyncScheduler
import com.studytrack.app.util.ApiResult
import android.view.ContextThemeWrapper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import kotlinx.coroutines.runBlocking

/**
 * Regression tests for two field-reported bugs in the AI assistant:
 *
 * 1. "AI replies render as right-aligned user bubbles." These tests inflate the
 *    real row layouts through the real [ChatAdapter] and assert, per item, which
 *    side the row lands on and whether the bot avatar is present: user rows are
 *    gravity=end with no avatar, AI rows are gravity=start with the avatar, and
 *    the bound text is the item's own text (not a neighbour's).
 *
 * 2. "A second account can't accept AI-suggested tasks." These tests drive the
 *    real [AiAssistantViewModel] against a real in-memory Room database: sign in
 *    as Alice, chat, accept a suggestion, then sign out, sign in as Bob, chat,
 *    accept — and assert the accepted rows land under the UID that was signed in
 *    at accept time, never under the other account's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AiAssistantChatRegressionTest {

    private lateinit var db: StudyTrackDatabase
    private lateinit var currentAccount: CurrentAccount

    private val alice = "uid-alice"
    private val bob = "uid-bob"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, StudyTrackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        currentAccount = CurrentAccount()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ------------------------------------------------------------ bug 1: sides

    @Test
    fun `user rows inflate right-aligned and AI rows left-aligned with the avatar`() {
        val themed = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_StudyTrack,
        )
        val parent = FrameLayout(themed)
        val adapter = ChatAdapter(NoopListener)

        val items = listOf(
            ChatItem.UserMessage("u-1", "Help me prioritise my tasks"),
            ChatItem.AiText("a-1", "Here is what I'd tackle first."),
        )
        submitAndWait(adapter, items)

        // Position 0: the user's message — right-aligned, single bubble, no avatar.
        val userHolder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(0))
        adapter.onBindViewHolder(userHolder, 0)
        val userRow = userHolder.itemView as LinearLayout
        assertEquals(Gravity.END, userRow.gravity)
        assertEquals(1, userRow.childCount)
        assertEquals(
            "Help me prioritise my tasks",
            userRow.findViewById<android.widget.TextView>(R.id.messageText).text.toString(),
        )

        // Position 1: the AI's reply — left-aligned, avatar first, own text bound.
        val aiHolder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(1))
        adapter.onBindViewHolder(aiHolder, 1)
        val aiRow = aiHolder.itemView as LinearLayout
        assertEquals(Gravity.START, aiRow.gravity)
        assertEquals(2, aiRow.childCount)
        assertTrue(
            "AI row must start with the bot avatar",
            aiRow.getChildAt(0) is ImageView,
        )
        assertEquals(
            "Here is what I'd tackle first.",
            aiRow.findViewById<android.widget.TextView>(R.id.messageText).text.toString(),
        )
    }

    @Test
    fun `a full exchange renders user right then AI left then suggestion card`() {
        val themed = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_StudyTrack,
        )
        val parent = FrameLayout(themed)
        val adapter = ChatAdapter(NoopListener)

        val items = listOf(
            ChatItem.AiText("a-0", "welcome"),
            ChatItem.UserMessage("u-1", "plan my week"),
            ChatItem.AiText("a-2", "done — one suggestion below"),
            ChatItem.Suggestion(
                itemId = "sug-1",
                suggestion = TaskSuggestion(
                    suggestionId = "sug-1",
                    title = "Draft the essay",
                    taskType = "Assignment",
                    priority = "Medium",
                    dueDate = "2026-09-25T17:00:00",
                ),
            ),
        )
        submitAndWait(adapter, items)

        val sides = (0 until adapter.itemCount).map { pos ->
            val holder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(pos))
            adapter.onBindViewHolder(holder, pos)
            pos to holder.itemView
        }

        assertEquals(Gravity.START, (sides[0].second as LinearLayout).gravity)
        assertEquals(Gravity.END, (sides[1].second as LinearLayout).gravity)
        assertEquals(Gravity.START, (sides[2].second as LinearLayout).gravity)
        // The suggestion card is its own layout with Accept/Reject actions.
        assertNotNull(sides[3].second.findViewById<View>(R.id.acceptButton))
        assertNotNull(sides[3].second.findViewById<View>(R.id.rejectButton))
    }

    // ------------------------------------------- bug 2: accept under the right uid

    @Test
    fun `accepting a suggestion files the task under the account signed in at accept time`() {
        val taskRepo = OfflineFirstTaskRepository(
            db = db, api = null, currentAccount = currentAccount,
            syncScheduler = NoopSyncScheduler,
        )
        val subjectRepo = OfflineFirstSubjectRepository(
            db = db, api = null, currentAccount = currentAccount,
            syncScheduler = NoopSyncScheduler,
        )
        val settings = SettingsRepository(ApplicationProvider.getApplicationContext())

        // ---------- account A ----------
        currentAccount.setSession(alice)
        val vmAlice = AiAssistantViewModel(
            FakeBrain("Alice's essay plan"), taskRepo, subjectRepo, settings,
        )
        startAndChat(vmAlice)
        val aliceSuggestion = latestSuggestion(vmAlice)
        vmAlice.acceptSuggestion(aliceSuggestion.itemId)
        idleMain()

        assertEquals(
            listOf("Alice's essay plan"),
            titlesOf(alice),
        )
        assertEquals(
            SuggestionStatus.ACCEPTED,
            latestSuggestion(vmAlice).status,
        )

        // ---------- switch to account B ----------
        currentAccount.clearSession()
        currentAccount.setSession(bob)

        val vmBob = AiAssistantViewModel(
            FakeBrain("Bob's revision session"), taskRepo, subjectRepo, settings,
        )
        startAndChat(vmBob)
        val bobSuggestion = latestSuggestion(vmBob)
        vmBob.acceptSuggestion(bobSuggestion.itemId)
        idleMain()

        // Bob's accepted task is stored under BOB's uid…
        assertEquals(
            listOf("Bob's revision session"),
            titlesOf(bob),
        )
        // …the card visibly accepted (a silent no-op is exactly the bug)…
        assertEquals(SuggestionStatus.ACCEPTED, latestSuggestion(vmBob).status)
        // …and Alice's row never moved into Bob's scope, nor Bob's into Alice's.
        assertEquals(
            listOf("Alice's essay plan"),
            titlesOf(alice),
        )
    }

    @Test
    fun `an accepted suggestion never lands under a previous account's uid`() {
        val taskRepo = OfflineFirstTaskRepository(
            db = db, api = null, currentAccount = currentAccount,
            syncScheduler = NoopSyncScheduler,
        )
        val subjectRepo = OfflineFirstSubjectRepository(
            db = db, api = null, currentAccount = currentAccount,
            syncScheduler = NoopSyncScheduler,
        )
        val settings = SettingsRepository(ApplicationProvider.getApplicationContext())

        currentAccount.setSession(alice)
        val vmAlice = AiAssistantViewModel(
            FakeBrain("first account task"), taskRepo, subjectRepo, settings,
        )
        startAndChat(vmAlice)
        vmAlice.acceptSuggestion(latestSuggestion(vmAlice).itemId)
        idleMain()

        currentAccount.clearSession()
        currentAccount.setSession(bob)
        val vmBob = AiAssistantViewModel(
            FakeBrain("second account task"), taskRepo, subjectRepo, settings,
        )
        startAndChat(vmBob)
        vmBob.acceptSuggestion(latestSuggestion(vmBob).itemId)
        idleMain()

        // The decisive assertion: no row owned by Alice carries Bob's title and
        // vice versa — a stale-UID write would show up right here.
        val aliceTitles = titlesOf(alice)
        val bobTitles = titlesOf(bob)
        assertTrue("Alice must not own Bob's task", "second account task" !in aliceTitles)
        assertTrue("Bob must not own Alice's task", "first account task" !in bobTitles)
        assertEquals(listOf("first account task"), aliceTitles)
        assertEquals(listOf("second account task"), bobTitles)
    }

    // ------------------------------------------------------------- helpers

    private fun startAndChat(vm: AiAssistantViewModel) {
        vm.start(
            welcomeText = "welcome",
            emptyReplyText = "empty",
            addedViaEditorText = "added",
            confirmationTemplate = "added for %s",
            confirmationWithSubtasksTemplate = "added for %s with %d subtasks",
            confirmationSubjectSuffix = " under %s",
            initialPrompt = null,
            contextTaskId = null,
        )
        idleMain()
        vm.sendMessage("help me plan")
        idleMain()
    }

    private fun latestSuggestion(vm: AiAssistantViewModel): ChatItem.Suggestion =
        vm.messages.value.last { it is ChatItem.Suggestion } as ChatItem.Suggestion

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun titlesOf(uid: String): List<String> =
        runBlocking { db.taskDao().getAllForOwner(uid).map { it.title } }

    /**
     * Drains the shadow main looper until [condition] holds. The accept path
     * suspends inside OfflineFirstTaskRepository.create (withContext IO), so a
     * single idle() can run before the write finishes.
     */
    private fun pumpUntil(condition: () -> Boolean, timeoutMs: Long = 10_000): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return condition()
    }

    private fun submitAndWait(adapter: ChatAdapter, items: List<ChatItem>) {
        adapter.submitList(items)
        val deadline = System.currentTimeMillis() + 5_000
        while (adapter.itemCount != items.size && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertEquals(items.size, adapter.itemCount)
    }

    /** A brain that always answers with one dated suggestion (title parameterised). */
    private class FakeBrain(private val suggestionTitle: String) : AiBrain {
        override suspend fun taskAssistance(
            request: TaskAssistanceRequest,
        ): ApiResult<TaskAssistanceResponse> = ApiResult.Success(
            TaskAssistanceResponse(
                replyText = "Sure — here is a suggestion.",
                suggestions = listOf(
                    TaskSuggestion(
                        suggestionId = "sug-$suggestionTitle",
                        title = suggestionTitle,
                        taskType = "Study",
                        priority = "Medium",
                        dueDate = "2026-09-25T17:00:00",
                    ),
                ),
            ),
        )
    }

    private object NoopSyncScheduler : SyncScheduler {
        override fun requestSync(reason: String) = Unit
        override fun requestImmediateSync(reason: String) = Unit
    }

    private object NoopListener : ChatAdapter.Listener {
        override fun onAcceptSuggestion(item: ChatItem.Suggestion) = Unit
        override fun onEditSuggestion(item: ChatItem.Suggestion) = Unit
        override fun onRejectSuggestion(item: ChatItem.Suggestion) = Unit
        override fun onPickSuggestionDate(item: ChatItem.Suggestion) = Unit
        override fun onPickSuggestionSubject(item: ChatItem.Suggestion) = Unit
    }
}
