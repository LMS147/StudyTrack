package com.studytrack.app.data.repository

import com.studytrack.app.auth.CurrentAccount
import com.studytrack.app.data.local.StudyTrackDatabase
import com.studytrack.app.data.local.toModel
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.sync.SyncOutcome
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Offline-first calendar.
 *
 * The calendar has no storage of its own: its entries *are* dated tasks, so it
 * reads the same UID-scoped `tasks` table the task screens use and simply
 * filters to rows with a `dueDate`. That is why there is no calendar entity in
 * the schema — a correctly dated task appears on the right day automatically,
 * online or off.
 *
 * Remote refresh is delegated to the task pull (injected as [pullTasks]) rather
 * than re-implementing snapshot logic here, so there is exactly one code path
 * that writes remote tasks into Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstCalendarRepository(
    private val db: StudyTrackDatabase,
    private val currentAccount: CurrentAccount,
    private val pullTasks: suspend (ownerUid: String) -> SyncOutcome,
) : CalendarRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val calendarTasks: StateFlow<List<Task>> = currentAccount.activeUid
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else db.taskDao().observeDatedForOwner(uid).map { rows -> rows.map { it.toModel() } }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Pull-through refresh with the same offline-first contract as the tasks:
     * a failed pull falls back to whatever dated tasks are cached, and only
     * reports an error when there is nothing cached to show.
     */
    override suspend fun refresh(): ApiResult<List<Task>> {
        val uid = currentAccount.requireUid()
        val pull = pullTasks(uid)
        if (pull is SyncOutcome.Failure) {
            val cached = db.taskDao().getAllForOwner(uid)
                .filter { it.dueDate != null }
                .map { it.toModel() }
            return if (cached.isNotEmpty()) ApiResult.Success(cached)
            else ApiResult.Error(pull.message, pull.cause)
        }
        return ApiResult.Success(
            db.taskDao().getAllForOwner(uid).filter { it.dueDate != null }.map { it.toModel() }
        )
    }

    fun close() {
        scope.cancel()
    }
}
