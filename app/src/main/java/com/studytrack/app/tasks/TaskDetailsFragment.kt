package com.studytrack.app.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studytrack.app.R
import com.studytrack.app.data.model.Priority
import com.studytrack.app.databinding.FragmentTaskDetailsBinding
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.DateTimeUtils
import com.studytrack.app.util.toast
import kotlinx.coroutines.launch

class TaskDetailsFragment : Fragment() {

    private var _binding: FragmentTaskDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TaskDetailsViewModel by viewModels { TaskDetailsViewModel.FACTORY }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTaskDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = TaskDetailsFragmentArgs.fromBundle(requireArguments())

        binding.backButton.setOnClickListener { findNavController().popBackStack() }

        binding.completeButton.setOnClickListener { viewModel.toggleComplete() }

        binding.editButton.setOnClickListener {
            findNavController().navigate(
                TaskDetailsFragmentDirections.actionTaskDetailsFragmentToAddEditTaskFragment(
                    taskId = args.taskId
                )
            )
        }

        binding.deleteButton.setOnClickListener { confirmDelete() }

        binding.aiBreakDownButton.setOnClickListener { openAiAssistant(AiMode.BREAK_DOWN) }
        binding.aiStudyPlanButton.setOnClickListener { openAiAssistant(AiMode.STUDY_PLAN) }
        binding.aiExplainButton.setOnClickListener { openAiAssistant(AiMode.EXPLAIN) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> renderState(state) }
            }
        }

        viewModel.load(args.taskId)
    }

    private enum class AiMode { BREAK_DOWN, STUDY_PLAN, EXPLAIN }

    /**
     * Opens the AI Assistant tab pre-seeded with a prompt about this task and
     * the task id as context (sent as `taskContext` with the request).
     */
    private fun openAiAssistant(mode: AiMode) {
        val task = viewModel.state.value.task ?: return
        val prompt = when (mode) {
            AiMode.BREAK_DOWN -> "Break \"${task.title}\" into subtasks"
            AiMode.STUDY_PLAN -> "Create a study plan for \"${task.title}\""
            AiMode.EXPLAIN -> "Explain the task \"${task.title}\""
        }
        findNavController().navigate(
            TaskDetailsFragmentDirections.actionTaskDetailsFragmentToAiAssistantFragment(
                initialPrompt = prompt,
                contextTaskId = task.taskId,
            )
        )
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_task_confirm_title))
            .setMessage(getString(R.string.delete_task_confirm_message))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.delete() }
            .show()
    }

    private fun renderState(state: TaskDetailsUiState) {
        val task = state.task
        binding.loadingProgress.isVisible = state.loading
        binding.completeButton.isEnabled = task != null && !state.toggling && !state.deleting
        binding.editButton.isEnabled = task != null && !state.deleting
        binding.deleteButton.isEnabled = task != null && !state.deleting
        binding.aiCard.isEnabled = task != null

        if (state.error != null) {
            toast(state.error)
        }

        if (state.deleted) {
            toast(getString(R.string.task_deleted))
            findNavController().popBackStack()
            return
        }

        if (task == null) return

        binding.taskTitle.text = task.title

        binding.typeChip.text = task.taskType.label
        binding.priorityChip.text = task.priority.label
        val (containerColor, foregroundColor) = when (task.priority) {
            Priority.LOW -> R.color.priority_low_container to R.color.priority_low
            Priority.MEDIUM -> R.color.priority_medium_container to R.color.priority_medium
            Priority.HIGH -> R.color.priority_high_container to R.color.priority_high
        }
        binding.priorityChip.backgroundTintList =
            ContextCompat.getColorStateList(requireContext(), containerColor)
        binding.priorityChip.setTextColor(ContextCompat.getColor(requireContext(), foregroundColor))

        if (task.completed) {
            binding.completedBadge.isVisible = true
            binding.completedBadge.text = getString(
                R.string.label_completed_on,
                DateTimeUtils.shortDateTime(task.completedAt ?: task.dueDate),
            )
            binding.completeButton.setText(R.string.action_mark_incomplete)
        } else {
            binding.completedBadge.isVisible = false
            binding.completeButton.setText(R.string.action_mark_complete)
        }

        binding.subjectValue.text = state.subjectName ?: getString(R.string.label_no_subject)
        binding.dueValue.text = DateTimeUtils.shortDateTime(task.dueDate)

        val relative = DateTimeUtils.relativeLabel(task.dueDate)
        binding.relativeDueValue.isVisible =
            relative != null && !task.completed && DateTimeUtils.isOverdue(task.dueDate)
        binding.relativeDueValue.text = relative.orEmpty()

        val reminder = task.reminderDate
        binding.reminderRow.isVisible = reminder != null
        binding.reminderValue.text = DateTimeUtils.shortDateTime(reminder)

        binding.pointsValue.text =
            getString(R.string.label_points) + ": " + task.points

        val description = task.description.orEmpty()
        binding.descriptionValue.isVisible = description.isNotBlank()
        binding.descriptionValue.text = description
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
