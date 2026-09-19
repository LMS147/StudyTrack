package com.studytrack.app.data.repository

import com.studytrack.app.data.model.Task
import com.studytrack.app.data.remote.ApiService
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.safeApiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Calendar data comes from GET /api/calendar, which returns the same Task
 * models (grouped client-side by dueDate). Kept as its own cache so the
 * calendar screen never has to mutate or be mutated by the task-editor flows.
 */
class CalendarRepository(private val api: ApiService) {

    private val _calendarTasks = MutableStateFlow<List<Task>>(emptyList())
    val calendarTasks: StateFlow<List<Task>> = _calendarTasks.asStateFlow()

    suspend fun refresh(): ApiResult<List<Task>> = safeApiCall {
        api.getCalendarTasks().also { _calendarTasks.value = it }
    }
}
