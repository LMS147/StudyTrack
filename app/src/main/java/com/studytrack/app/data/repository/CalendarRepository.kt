package com.studytrack.app.data.repository

import com.studytrack.app.data.model.Task
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Calendar data surface. Two implementations:
 * - [RemoteCalendarRepository]: GET /api/calendar (when api.baseUrl is set)
 * - [LocalCalendarRepository]: derives the month grid from the local task
 *   store (local mode, no backend)
 */
interface CalendarRepository {

    val calendarTasks: StateFlow<List<Task>>

    suspend fun refresh(): ApiResult<List<Task>>
}

/**
 * Calendar data comes from GET /api/calendar, which returns the same Task
 * models (grouped client-side by dueDate). Kept as its own cache so the
 * calendar screen never has to mutate or be mutated by the task-editor flows.
 */
class RemoteCalendarRepository(private val api: ApiService) : CalendarRepository {

    private val _calendarTasks = MutableStateFlow<List<Task>>(emptyList())
    override val calendarTasks: StateFlow<List<Task>> = _calendarTasks.asStateFlow()

    override suspend fun refresh(): ApiResult<List<Task>> = safeApiCall {
        api.getCalendarTasks().also { _calendarTasks.value = it }
    }
}
