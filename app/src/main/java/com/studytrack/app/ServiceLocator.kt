package com.studytrack.app

import android.content.Context
import com.studytrack.app.auth.AccountSessionManager
import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.data.remote.GrokClient
import com.studytrack.app.data.remote.RetrofitClient
import com.studytrack.app.data.repository.AiBrain
import com.studytrack.app.data.repository.AiRepository
import com.studytrack.app.data.repository.CalendarRepository
import com.studytrack.app.data.repository.GrokAiRepository
import com.studytrack.app.data.repository.OfflineFirstCalendarRepository
import com.studytrack.app.data.repository.OfflineFirstProgressRepository
import com.studytrack.app.data.repository.OfflineFirstStudySessionRepository
import com.studytrack.app.data.repository.OfflineFirstSubjectRepository
import com.studytrack.app.data.repository.OfflineFirstTaskRepository
import com.studytrack.app.data.repository.ProgressRepository
import com.studytrack.app.data.repository.SettingsRepository
import com.studytrack.app.data.repository.StudySessionRepository
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.data.repository.TaskRepository
import com.studytrack.app.data.sync.ConnectivityMonitor
import com.studytrack.app.data.sync.SyncScheduler
import com.studytrack.app.data.sync.WorkManagerSyncScheduler
import com.studytrack.app.data.sync.SyncableRepository

/**
 * Minimal manual dependency container (chosen over a DI framework to keep the
 * dependency list exactly as specified). Initialized once from [StudyTrackApp].
 *
 * ## Data mode
 *
 * `api.baseUrl` (local.properties) still decides whether there is a backend,
 * but it is no longer a fork between two repository implementations. Every
 * repository is now offline-first over Room; `api.baseUrl` only decides whether
 * they are handed an [ApiService] to sync against:
 *
 * - `api.baseUrl` set   → Room cache + sync queue against the REST backend
 * - `api.baseUrl` blank → Room cache only; rows are written already `SYNCED`
 *   because there is no remote to owe anything to
 *
 * That unification is deliberate. Two parallel storage stacks was how a
 * per-account leak would eventually creep in — only one of them would get the
 * UID filter.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    /** True when no backend is configured — the local cache is the truth. */
    val isLocalMode: Boolean get() = RetrofitClient.BASE_URL.isBlank()

    // ------------------------------------------------------------- core plumbing

    /** The local SQLite database: offline cache and sync queue. */
    val database: StudyTrackDatabase by lazy { StudyTrackDatabase.getInstance(appContext) }

    /**
     * Which account's data the app is showing. Every scoped query reads the UID
     * from here, never from a call site.
     */
    val currentAccount: CurrentAccount by lazy { CurrentAccount() }

    val connectivityMonitor: ConnectivityMonitor by lazy { ConnectivityMonitor(appContext) }

    val syncScheduler: SyncScheduler by lazy { WorkManagerSyncScheduler(appContext) }

    /** Owns the sign-in / sign-out transitions that move the active UID. */
    val accountSessionManager: AccountSessionManager by lazy {
        AccountSessionManager(
            database = database,
            currentAccount = currentAccount,
            connectivityMonitor = connectivityMonitor,
            syncScheduler = syncScheduler,
            // Lambdas, not the lists directly: pushOrder/pullOrder are lazy and
            // depend on the repositories, which depend on this object.
            pushOrder = { pushOrder },
            pullOrder = { pullOrder },
            migrateLegacyStore = { uid ->
                legacyStoreMigration.migrateOnce(uid, database.taskDao(), database.subjectDao())
            },
        )
    }

    private val legacyStoreMigration: com.studytrack.app.data.local.LegacyStoreMigration by lazy {
        com.studytrack.app.data.local.LegacyStoreMigration(appContext)
    }

    // --------------------------------------------------------------- repositories

    val authRepository: com.studytrack.app.data.repository.AuthRepository by lazy {
        com.studytrack.app.data.repository.AuthRepository()
    }

    val subjectRepository: SubjectRepository by lazy {
        OfflineFirstSubjectRepository(
            db = database,
            api = remoteApiOrNull(),
            currentAccount = currentAccount,
            syncScheduler = syncScheduler,
        )
    }

    val taskRepository: TaskRepository by lazy {
        OfflineFirstTaskRepository(
            db = database,
            api = remoteApiOrNull(),
            currentAccount = currentAccount,
            syncScheduler = syncScheduler,
        )
    }

    val calendarRepository: CalendarRepository by lazy {
        OfflineFirstCalendarRepository(
            db = database,
            currentAccount = currentAccount,
            // Calendar entries are dated tasks, so it shares the task pull
            // rather than writing remote tasks into Room a second time.
            pullTasks = { uid -> (taskRepository as SyncableRepository).pullRemote(uid) },
        )
    }

    val progressRepository: ProgressRepository by lazy {
        OfflineFirstProgressRepository(
            db = database,
            api = remoteApiOrNull(),
            currentAccount = currentAccount,
        )
    }

    val studySessionRepository: StudySessionRepository by lazy {
        OfflineFirstStudySessionRepository(
            db = database,
            api = remoteApiOrNull(),
            currentAccount = currentAccount,
            syncScheduler = syncScheduler,
        )
    }

    // ------------------------------------------------------------------ sync order

    /**
     * Push order. Subjects precede tasks because a task carries a `subjectId`,
     * so the subject must exist remotely before its first task is created.
     */
    val pushOrder: List<SyncableRepository> by lazy {
        listOf(
            subjectRepository as SyncableRepository,
            taskRepository as SyncableRepository,
            studySessionRepository as SyncableRepository,
        )
    }

    /** Pull order. Progress is derived server-side, so it is pulled last. */
    val pullOrder: List<SyncableRepository> by lazy {
        listOf(
            subjectRepository as SyncableRepository,
            taskRepository as SyncableRepository,
            progressRepository as SyncableRepository,
        )
    }

    /**
     * Records a completed sync for one account. Flipping `bootstrapped` is what
     * later allows that account to sign in offline and be served from cache
     * instead of being refused.
     */
    suspend fun markAccountSynced(ownerUid: String) {
        val dao = database.accountDao()
        val now = System.currentTimeMillis()
        dao.getByUid(ownerUid)?.let { record ->
            dao.upsert(record.copy(lastSyncedAt = now, bootstrapped = true))
        } ?: dao.setLastSyncedAt(ownerUid, now)
    }

    // ------------------------------------------------------------------- AI + misc

    /**
     * The assistant's brain. If a Groq/xAI key is configured in
     * local.properties (BuildConfig.GROK_API_KEY), chat directly with the LLM;
     * otherwise use the StudyTrack backend endpoint.
     */
    val aiBrain: AiBrain by lazy {
        if (BuildConfig.GROK_API_KEY.isNotBlank()) {
            GrokAiRepository(GrokClient.apiService(BuildConfig.GROK_API_KEY))
        } else {
            AiRepository(apiService)
        }
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext)
    }

    /** Only touch this in remote mode (Retrofit rejects a blank base URL). */
    val apiService: ApiService by lazy {
        check(!isLocalMode) { "apiService requested in local mode — no backend configured" }
        RetrofitClient.apiService
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** The backend client, or `null` when running without one. */
    private fun remoteApiOrNull(): ApiService? = if (isLocalMode) null else apiService

    /** AI brain used when local mode has no LLM key configured. */
    private object NoBrainConfigured : AiBrain {
        override suspend fun taskAssistance(
            request: com.studytrack.app.data.model.TaskAssistanceRequest,
        ): com.studytrack.app.util.ApiResult<com.studytrack.app.data.model.TaskAssistanceResponse> =
            com.studytrack.app.util.ApiResult.Error(
                "No AI configured. Add grok.apiKey to local.properties " +
                    "(Groq/xAI key — see local.properties.example), or set " +
                    "api.baseUrl to your StudyTrack backend."
            )
    }
}
