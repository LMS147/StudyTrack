package com.studytrack.app.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.studytrack.app.R
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.Task
import com.studytrack.app.databinding.FragmentCalendarBinding
import com.studytrack.app.tasks.TaskAdapter
import com.studytrack.app.tasks.TaskListItem
import com.studytrack.app.util.DateTimeUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Month-view calendar: locale-aware weekday header, selectable day cells with
 * priority dots, the selected day's task list below, and a quick-add FAB that
 * pre-fills the selected date.
 */
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CalendarViewModel by viewModels { CalendarViewModel.FACTORY }

    private lateinit var gridAdapter: MonthGridAdapter
    private lateinit var taskAdapter: TaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        gridAdapter = MonthGridAdapter(onDayClick = { viewModel.selectDate(it) })
        binding.monthGrid.adapter = gridAdapter
        binding.monthGrid.layoutManager = GridLayoutManager(requireContext(), DAYS_PER_WEEK)

        taskAdapter = TaskAdapter(
            onTaskClick = { task ->
                findNavController().navigate(
                    CalendarFragmentDirections.actionCalendarFragmentToTaskDetailsFragment(
                        task.taskId
                    )
                )
            },
            onToggleComplete = { task ->
                viewModel.toggleComplete(task.taskId, !task.completed)
            },
        )
        binding.dayTaskList.adapter = taskAdapter
        binding.dayTaskList.layoutManager = LinearLayoutManager(requireContext())

        binding.previousMonthButton.setOnClickListener { viewModel.previousMonth() }
        binding.nextMonthButton.setOnClickListener { viewModel.nextMonth() }
        binding.monthTitle.setOnClickListener { viewModel.selectDate(LocalDate.now()) }

        binding.addTaskFab.setOnClickListener {
            val selected = viewModel.state.value.selectedDate
            findNavController().navigate(
                CalendarFragmentDirections.actionCalendarFragmentToAddEditTaskFragment(
                    defaultDueDate = DateTimeUtils.isoDate(selected),
                )
            )
        }

        setupWeekdayHeader()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> renderState(state) }
            }
        }
    }

    /** Builds the weekday labels row respecting the locale's first day of week. */
    private fun setupWeekdayHeader() {
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val labels = (0 until DAYS_PER_WEEK).map { offset ->
            DayOfWeek.of(((firstDayOfWeek.value - 1 + offset) % DAYS_PER_WEEK) + 1)
                .getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
        binding.weekdayRow.removeAllViews()
        labels.forEach { label ->
            val textView = TextView(requireContext())
            textView.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
            textView.gravity = android.view.Gravity.CENTER
            textView.text = label
            textView.textSize = 12f
            textView.setTextColor(
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary)
            )
            binding.weekdayRow.addView(textView)
        }
    }

    private fun renderState(state: CalendarUiState) {
        binding.monthTitle.text = DateTimeUtils.monthYear(state.month)
        gridAdapter.submitList(buildCells(state))

        val selected = state.selectedDate
        binding.selectedDateTitle.text = getString(
            R.string.tasks_on_date,
            DateTimeUtils.fullDate(DateTimeUtils.formatIso(selected, java.time.LocalTime.MIDNIGHT)),
        )

        val tasks = state.selectedDateTasks
        binding.emptyDay.isVisible = tasks.isEmpty() && !state.loading
        binding.loadingProgress.isVisible = state.loading && tasks.isEmpty()

        val subjectNames = state.subjectNames
        taskAdapter.submitList(
            tasks.sortedBy { it.dueDate }.map { task ->
                TaskListItem(task, task.subjectId?.let { subjectNames[it] })
            }
        )
    }

    private fun buildCells(state: CalendarUiState): List<DayCell> {
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val first = state.month.atDay(1)
        val leading = (first.dayOfWeek.value - firstDayOfWeek.value + DAYS_PER_WEEK) % DAYS_PER_WEEK

        val cells = mutableListOf<DayCell>()
        repeat(leading) { cells += DayCell.Blank("blank-leading-$it") }

        val today = LocalDate.now()
        var date: LocalDate = first
        while (YearMonth.from(date) == state.month) {
            val dayTasks = state.tasksByDate[date].orEmpty()
            cells += DayCell.Day(
                date = date,
                isToday = date == today,
                isSelected = date == state.selectedDate,
                dotColors = dayTasks.take(3).map { priorityDotColor(it) },
            )
            date = date.plusDays(1)
        }
        while (cells.size % DAYS_PER_WEEK != 0) {
            cells += DayCell.Blank("blank-trailing-${cells.size}")
        }
        return cells
    }

    private fun priorityDotColor(task: Task): Int = when (task.priority) {
        Priority.LOW -> R.color.priority_low
        Priority.MEDIUM -> R.color.priority_medium
        Priority.HIGH -> R.color.priority_high
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val DAYS_PER_WEEK = 7
    }
}
