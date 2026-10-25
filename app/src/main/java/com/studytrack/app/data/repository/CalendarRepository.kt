package com.studytrack.app.data.repository

import com.studytrack.app.data.model.Task
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.flow.StateFlow

/**
 * Calendar data surface.
 *
 * Implemented by [OfflineFirstCalendarRepository]. Calendar entries are dated
 * tasks, so it reads the same UID-scoped Room table as the task screens rather
 * than keeping a second copy that could drift out of sync.
 */
interface CalendarRepository {

    /** Dated tasks for the signed-in account, ordered by due date. */
    val calendarTasks: StateFlow<List<Task>>

    /** Pull-through refresh; resolves from the cache when offline. */
    suspend fun refresh(): ApiResult<List<Task>>
}
