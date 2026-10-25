package com.studytrack.app.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.model.Task
import com.studytrack.app.data.repository.CalendarRepository
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.DateTimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val tasksByDate: Map<LocalDate, List<Task>> = emptyMap(),
    val subjectNames: Map<String, String> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
) {
    val selectedDateTasks: List<Task>
        get() = tasksByDate[selectedDate].orEmpty()
}

/**
 * Calendar state. GET /api/calendar returns the flat task list; grouping by
 * dueDate happens here, so a correctly-dated task automatically appears on
 * the right day (no separate calendar-entry type).
 */
class CalendarViewModel(
    private val calendarRepository: CalendarRepository,
    private val subjectRepository: SubjectRepository,
    private val taskRepository: com.studytrack.app.data.repository.TaskRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarUiState())
    val state: StateFlow<CalendarUiState> = _state.asStateFlow()

    init {
        // The calendar OBSERVES the live UID-scoped Room flow — a task saved
        // on any other screen (Add/Edit Task, AI assistant accept) appears
        // here, dots included, the moment Room emits. The old code took a
        // one-shot snapshot in init, and because this ViewModel survives
        // bottom-nav navigation, tasks created after the Calendar tab was
        // first opened never showed up at all.
        viewModelScope.launch {
            calendarRepository.calendarTasks.collect { tasks ->
                _state.update { it.copy(tasksByDate = groupByDate(tasks)) }
            }
        }
        viewModelScope.launch {
            subjectRepository.subjects.collect { subjects ->
                _state.update {
                    it.copy(subjectNames = subjects.associate { s -> s.subjectId to s.subjectName })
                }
            }
        }
        refresh()
    }

    /**
     * Pull-through refresh: fetches remote changes into Room. The screen's
     * list itself is driven by the observed flow above, so a successful pull
     * simply lands in Room and flows through — this only reports errors and
     * drives the loading flag.
     */
    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            if (subjectRepository.subjects.value.isEmpty()) {
                subjectRepository.refresh()
            }
            when (val result = calendarRepository.refresh()) {
                is ApiResult.Success -> _state.update {
                    it.copy(loading = false, error = null)
                }
                is ApiResult.Error -> _state.update {
                    it.copy(loading = false, error = result.message)
                }
                ApiResult.Loading -> Unit
            }
        }
    }

    fun previousMonth() {
        _state.update { it.copy(month = it.month.minusMonths(1)) }
    }

    fun nextMonth() {
        _state.update { it.copy(month = it.month.plusMonths(1)) }
    }

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date, month = YearMonth.from(date)) }
    }

    /**
     * Toggle completion via the Tasks repository. The write lands in Room and
     * the observed calendar flow re-emits on its own — no manual re-read.
     */
    fun toggleComplete(taskId: String, completed: Boolean) {
        viewModelScope.launch {
            when (val result = taskRepository.setCompleted(taskId, completed)) {
                is ApiResult.Success -> Unit
                is ApiResult.Error -> _state.update { it.copy(error = result.message) }
                ApiResult.Loading -> Unit
            }
        }
    }

    private fun groupByDate(tasks: List<Task>): Map<LocalDate, List<Task>> =
        tasks
            .mapNotNull { task ->
                DateTimeUtils.parseDate(task.dueDate)?.let { date -> date to task }
            }
            .groupBy({ (date, _) -> date }, { (_, task) -> task })

    companion object {
        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CalendarViewModel(
                    ServiceLocator.calendarRepository,
                    ServiceLocator.subjectRepository,
                    ServiceLocator.taskRepository,
                )
            }
        }
    }
}
