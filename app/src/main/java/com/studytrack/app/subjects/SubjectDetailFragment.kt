package com.studytrack.app.subjects

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
import com.studytrack.app.databinding.FragmentSubjectDetailBinding
import com.studytrack.app.tasks.TaskAdapter
import com.studytrack.app.tasks.TaskListItem
import com.studytrack.app.util.toast
import kotlinx.coroutines.launch

/**
 * A subject's tasks: header (name, description, progress) + the shared task
 * list + quick-add FAB that preselects this subject.
 */
class SubjectDetailFragment : Fragment() {

    private var _binding: FragmentSubjectDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SubjectDetailViewModel by viewModels { SubjectDetailViewModel.FACTORY }

    private lateinit var adapter: TaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSubjectDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = SubjectDetailFragmentArgs.fromBundle(requireArguments())

        adapter = TaskAdapter(
            onTaskClick = { task ->
                findNavController().navigate(
                    SubjectDetailFragmentDirections
                        .actionSubjectDetailFragmentToTaskDetailsFragment(task.taskId)
                )
            },
            onToggleComplete = { task ->
                viewModel.toggleComplete(task.taskId, !task.completed)
            },
        )
        binding.taskList.adapter = adapter
        binding.taskList.layoutManager = LinearLayoutManager(requireContext())

        binding.backButton.setOnClickListener { findNavController().popBackStack() }

        binding.addTaskFab.setOnClickListener {
            findNavController().navigate(
                SubjectDetailFragmentDirections
                    .actionSubjectDetailFragmentToAddEditTaskFragment(
                        defaultSubjectId = args.subjectId,
                    )
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> renderState(state) }
            }
        }

        viewModel.start(args.subjectId)
    }

    private fun renderState(state: SubjectDetailUiState) {
        val subject = state.subject

        binding.loadingProgress.isVisible = state.loading
        binding.emptyState.isVisible = subject != null && state.tasks.isEmpty() && !state.loading

        if (subject != null) {
            binding.subjectName.text = subject.subjectName
            binding.subjectAvatar.text =
                subject.subjectName.trim().take(1).uppercase().ifEmpty { "?" }
            val description = subject.description.orEmpty()
            binding.subjectDescription.isVisible = description.isNotBlank()
            binding.subjectDescription.text = description
        }

        binding.subjectProgress.max = 100
        binding.subjectProgress.progress = state.progressPercent
        binding.subjectProgressLabel.text = getString(
            R.string.subject_progress_label, state.completedCount, state.tasks.size
        )

        val subjectName = state.subject?.subjectName
        adapter.submitList(state.tasks.map { TaskListItem(it, subjectName) })

        if (state.error != null) {
            toast(state.error)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
