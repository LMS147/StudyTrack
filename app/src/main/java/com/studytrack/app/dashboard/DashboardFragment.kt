package com.studytrack.app.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.studytrack.app.R
import com.studytrack.app.databinding.FragmentDashboardBinding
import com.studytrack.app.tasks.TaskListItem
import com.studytrack.app.util.DateTimeUtils
import com.studytrack.app.util.toast
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels { DashboardViewModel.FACTORY }

    private lateinit var adapter: DashboardAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = DashboardAdapter(
            onTaskClick = { task ->
                findNavController().navigate(
                    DashboardFragmentDirections
                        .actionDashboardFragmentToTaskDetailsFragment(task.taskId)
                )
            },
            onToggleComplete = { task ->
                viewModel.toggleComplete(task.taskId, !task.completed)
            },
        )
        binding.dashboardList.adapter = adapter
        binding.dashboardList.layoutManager = LinearLayoutManager(requireContext())

        binding.addTaskFab.setOnClickListener {
            findNavController().navigate(
                DashboardFragmentDirections.actionDashboardFragmentToAddEditTaskFragment()
            )
        }

        // The avatar is the app's route to Profile & Settings (it is not a
        // bottom-nav tab in the design).
        binding.profileAvatarButton.setOnClickListener {
            findNavController().navigate(R.id.action_global_profileFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: DashboardUiState) {
        binding.loadingProgress.isVisible = state.loading

        binding.greetingText.text = getString(
            greetingResForHour(),
            state.userName.ifBlank { getString(R.string.greeting_fallback_name) }
        )
        binding.dateText.text = DateTimeUtils.todayLabel()

        // Stat cards + gamification banner.
        binding.profileAvatarButton.text = state.initials
        binding.statDueToday.text = state.dueToday.size.toString()
        binding.statOverdue.text = state.overdueCount.toString()
        binding.statPoints.text = state.points.toString()

        binding.levelTitle.text = getString(R.string.home_level_format, state.level)
        binding.levelXpText.text = getString(
            R.string.home_level_xp_format,
            state.xpInLevel,
            DashboardUiState.XP_PER_LEVEL,
            state.level + 1,
        )
        binding.levelProgress.max = DashboardUiState.XP_PER_LEVEL
        binding.levelProgress.setProgressCompat(state.xpInLevel, true)

        adapter.submitList(buildItems(state))

        if (state.error != null && !state.loading) {
            toast(state.error)
        }
    }

    private fun buildItems(state: DashboardUiState): List<DashboardItem> {
        val items = mutableListOf<DashboardItem>()
        val subjectNames = state.subjectNames

        items += DashboardItem.Header(
            itemId = "header-today",
            title = getString(R.string.section_due_today),
            count = state.dueToday.size,
        )
        if (state.dueToday.isEmpty()) {
            items += DashboardItem.Empty("empty-today", getString(R.string.empty_due_today))
        } else {
            items += state.dueToday.map { task ->
                DashboardItem.TaskRow(TaskListItem(task, task.subjectId?.let { subjectNames[it] }))
            }
        }

        if (state.overdue.isNotEmpty()) {
            items += DashboardItem.Header(
                itemId = "header-overdue",
                title = getString(R.string.section_overdue),
                count = state.overdue.size,
            )
            items += state.overdue.map { task ->
                DashboardItem.TaskRow(TaskListItem(task, task.subjectId?.let { subjectNames[it] }))
            }
        }

        items += DashboardItem.Header(
            itemId = "header-upcoming",
            title = getString(R.string.section_upcoming),
            count = state.upcoming.size,
        )
        if (state.upcoming.isEmpty()) {
            items += DashboardItem.Empty("empty-upcoming", getString(R.string.empty_upcoming))
        } else {
            items += state.upcoming.map { task ->
                DashboardItem.TaskRow(TaskListItem(task, task.subjectId?.let { subjectNames[it] }))
            }
        }

        return items
    }

    private fun greetingResForHour(): Int {
        // Hour is only used to pick the greeting; no Calendar import needed for logic.
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..16 -> R.string.greeting_afternoon
            in 17..21 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
