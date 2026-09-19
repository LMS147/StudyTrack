package com.studytrack.app.data.repository

import com.studytrack.app.data.model.Task
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.flow.StateFlow

/**
 * [CalendarRepository] for local mode: the calendar grid is derived directly
 * from the local task store (the screen groups tasks by dueDate client-side
 * anyway, exactly like it does with backend data).
 */
class LocalCalendarRepository(
    private val store: LocalStore,
) : CalendarRepository {

    override val calendarTasks: StateFlow<List<Task>> = store.tasks

    override suspend fun refresh(): ApiResult<List<Task>> =
        ApiResult.Success(store.tasks.value)
}
