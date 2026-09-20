package com.studytrack.app.tasks

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
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.TaskType
import com.studytrack.app.databinding.FragmentTasksBinding
import com.studytrack.app.util.toast
import kotlinx.coroutines.launch

/**
 * Tasks tab: the flat list of every task with the two filter rows from the
 * design (type + priority). Rows are the shared item_task layout, so they match
 * the Dashboard and Subject Details exactly.
 */
class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TasksViewModel by viewModels { TasksViewModel.FACTORY }

    private lateinit var adapter: TaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = TaskAdapter(
            onTaskClick = { task ->
                findNavController().navigate(
                    TasksFragmentDirections
                        .actionTasksFragmentToTaskDetailsFragment(task.taskId)
                )
            },
            onToggleComplete = { task ->
                viewModel.toggleComplete(task.taskId, !task.completed)
            },
        )
        binding.tasksList.adapter = adapter
        binding.tasksList.layoutManager = LinearLayoutManager(requireContext())

        binding.addTaskFab.setOnClickListener {
            findNavController().navigate(
                TasksFragmentDirections.actionTasksFragmentToAddEditTaskFragment()
            )
        }

        // One row covers both: the task types, then "Completed".
        binding.typeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.typeChipCompleted -> viewModel.setCompletedOnly(true)
                R.id.typeChipAssignment -> viewModel.setTypeFilter(TaskType.ASSIGNMENT)
                R.id.typeChipTest -> viewModel.setTypeFilter(TaskType.TEST)
                R.id.typeChipExam -> viewModel.setTypeFilter(TaskType.EXAM)
                R.id.typeChipProject -> viewModel.setTypeFilter(TaskType.PROJECT)
                R.id.typeChipPresentation -> viewModel.setTypeFilter(TaskType.PRESENTATION)
                R.id.typeChipStudy -> viewModel.setTypeFilter(TaskType.STUDY)
                else -> viewModel.setTypeFilter(null) // "All"
            }
        }

        binding.priorityChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.setPriorityFilter(
                when (checkedIds.firstOrNull()) {
                    R.id.priorityChipHigh -> Priority.HIGH
                    R.id.priorityChipMedium -> Priority.MEDIUM
                    R.id.priorityChipLow -> Priority.LOW
                    else -> null // "All"
                }
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: TasksUiState) {
        binding.tasksSubtitle.text = getString(
            R.string.tasks_pending_completed,
            state.pendingCount,
            state.completedCount,
        )
        binding.loadingProgress.isVisible = state.loading
        binding.emptyState.isVisible = state.filteredEmpty && !state.loading
        adapter.submitList(state.items)

        if (state.error != null && !state.loading) {
            toast(state.error)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
