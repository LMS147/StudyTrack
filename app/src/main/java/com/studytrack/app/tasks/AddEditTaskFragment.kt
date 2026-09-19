package com.studytrack.app.tasks

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.studytrack.app.R
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.TaskPayload
import com.studytrack.app.data.model.TaskType
import com.studytrack.app.databinding.FragmentAddEditTaskBinding
import com.studytrack.app.util.DateTimeUtils
import com.studytrack.app.util.NavResultKeys
import com.studytrack.app.util.toast
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/**
 * Add/Edit task form.
 *
 * Sources of initial values (mutually exclusive):
 *  - args.taskId:   edit the existing task
 *  - args.prefillJson: prefill from an AI suggestion (the "Edit" action on a
 *    suggestion card); on save, reports success back to the chat screen via
 *    savedStateHandle with args.aiSuggestionId
 *  - args.defaultSubjectId / args.defaultDueDate: preselections when opened
 *    from Subject Details / the Calendar
 *
 * A due date is always required. Time is optional — a date-only due date is
 * stored as 23:59 (end of day) and a date-only reminder as 09:00.
 */
class AddEditTaskFragment : Fragment() {

    private var _binding: FragmentAddEditTaskBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddEditTaskViewModel by viewModels { AddEditTaskViewModel.FACTORY }

    // Form state (re-created on process death; the save itself is idempotent-ish
    // because it only happens on explicit user action).
    private var selectedSubjectId: String? = null
    private var selectedTaskType: TaskType = TaskType.STUDY
    private var dueDate: LocalDate? = null
    private var dueTime: LocalTime? = null
    private var reminderDate: LocalDate? = null
    private var reminderTime: LocalTime? = null

    private var populatedFromState = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = AddEditTaskFragmentArgs.fromBundle(requireArguments())

        binding.backButton.setOnClickListener { findNavController().popBackStack() }

        // Pre-fill a default due date (e.g. the selected calendar day).
        args.defaultDueDate?.let { DateTimeUtils.parseDate(it)?.let { date -> dueDate = date } }

        setupSubjectDropdown(args.defaultSubjectId)
        setupTaskTypeDropdown()
        setupPickers()
        setupReminderToggle()

        binding.saveButton.setOnClickListener {
            if (validate()) {
                viewModel.save(buildPayload())
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    renderState(state, args.aiSuggestionId)
                }
            }
        }

        viewModel.start(args.taskId, args.prefillJson)
    }

    // ------------------------------------------------------------- dropdowns

    private fun setupSubjectDropdown(defaultSubjectId: String?) {
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1)
        binding.subjectField.setAdapter(adapter)
        binding.subjectField.setOnItemClickListener { _, _, position, _ ->
            selectedSubjectId = if (position == 0) null else viewModel.state.value.subjects.getOrNull(position - 1)?.subjectId
        }
        // Default selection is applied once subjects load (renderState).
        if (defaultSubjectId != null) selectedSubjectId = defaultSubjectId
    }

    private fun setupTaskTypeDropdown() {
        val labels = TaskType.entries.map { it.label }
        binding.taskTypeField.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, labels)
        )
        binding.taskTypeField.setOnItemClickListener { _, _, position, _ ->
            selectedTaskType = TaskType.entries.getOrNull(position) ?: TaskType.STUDY
        }
        selectTaskType(TaskType.STUDY)
    }

    private fun selectTaskType(type: TaskType) {
        selectedTaskType = type
        binding.taskTypeField.setText(type.label, false)
    }

    // --------------------------------------------------------------- pickers

    private fun setupPickers() {
        binding.dueDateField.setOnClickListener { showDatePicker(isDue = true) }
        binding.dueTimeField.setOnClickListener { showTimePicker(isDue = true) }
        binding.reminderDateField.setOnClickListener { showDatePicker(isDue = false) }
        binding.reminderTimeField.setOnClickListener { showTimePicker(isDue = false) }

        binding.dueTimeLayout.setEndIconOnClickListener {
            dueTime = null
            renderDueTime()
        }
        binding.reminderTimeLayout.setEndIconOnClickListener {
            reminderTime = null
            renderReminderTime()
        }
    }

    private fun showDatePicker(isDue: Boolean) {
        val current = (if (isDue) dueDate else reminderDate) ?: DateTimeUtils.today()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(
                getString(
                    if (isDue) R.string.select_due_date else R.string.select_reminder_date
                )
            )
            .setSelection(DateTimeUtils.toUtcMillis(current))
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val picked = DateTimeUtils.fromUtcMillis(millis)
            if (isDue) {
                dueDate = picked
                binding.dueDateLayout.error = null
                renderDueDate()
            } else {
                reminderDate = picked
                binding.reminderDateLayout.error = null
                renderReminderDate()
            }
        }
        picker.show(childFragmentManager, if (isDue) "due_date" else "reminder_date")
    }

    private fun showTimePicker(isDue: Boolean) {
        val current = (if (isDue) dueTime else reminderTime) ?: LocalTime.of(17, 0)
        val is24Hour = DateFormat.is24HourFormat(requireContext())
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(if (is24Hour) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(current.hour)
            .setMinute(current.minute)
            .setTitleText(getString(R.string.select_time))
            .build()
        picker.addOnPositiveButtonClickListener {
            val picked = LocalTime.of(picker.hour, picker.minute)
            if (isDue) {
                dueTime = picked
                renderDueTime()
            } else {
                reminderTime = picked
                renderReminderTime()
            }
        }
        picker.show(childFragmentManager, if (isDue) "due_time" else "reminder_time")
    }

    private fun setupReminderToggle() {
        binding.reminderSwitch.setOnCheckedChangeListener { _, isChecked ->
            binding.reminderSection.isVisible = isChecked
        }
    }

    // -------------------------------------------------------------- rendering

    private fun renderDueDate() {
        binding.dueDateField.setText(dueDate?.let { DateTimeUtils.shortDate(DateTimeUtils.formatIso(it, LocalTime.MIDNIGHT)) }.orEmpty())
    }

    private fun renderDueTime() {
        binding.dueTimeField.setText(dueTime?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: getString(R.string.no_time_set))
    }

    private fun renderReminderDate() {
        binding.reminderDateField.setText(reminderDate?.let { DateTimeUtils.shortDate(DateTimeUtils.formatIso(it, LocalTime.MIDNIGHT)) }.orEmpty())
    }

    private fun renderReminderTime() {
        binding.reminderTimeField.setText(reminderTime?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: getString(R.string.no_time_set))
    }

    private fun renderState(state: TaskFormUiState, aiSuggestionId: String?) {
        val loading = state.loading
        binding.saveProgress.isVisible = state.saving
        binding.saveButton.isEnabled = !state.saving && !loading
        binding.titleField.isEnabled = !state.saving
        binding.descriptionField.isEnabled = !state.saving
        binding.subjectField.isEnabled = !state.saving
        binding.taskTypeField.isEnabled = !state.saving
        binding.dueDateField.isEnabled = !state.saving
        binding.dueTimeField.isEnabled = !state.saving
        binding.reminderSwitch.isEnabled = !state.saving

        if (state.error != null) {
            binding.errorText.text = state.error
            binding.errorText.isVisible = true
        } else {
            binding.errorText.isVisible = false
        }

        // Populate the form once data is available (edit task > AI prefill).
        if (!loading && !populatedFromState) {
            populatedFromState = true
            populateForm(state)
        }

        if (state.saved) {
            // Report back to the AI chat when this save came from a suggestion.
            if (aiSuggestionId != null) {
                findNavController().previousBackStackEntry?.savedStateHandle
                    ?.set(NavResultKeys.AI_SUGGESTION_CREATED, aiSuggestionId)
            }
            toast(getString(R.string.task_saved))
            findNavController().popBackStack()
        }
    }

    private fun populateForm(state: TaskFormUiState) {
        val task = state.editingTask
        val prefill = state.prefill

        // Subjects dropdown content
        val subjectNames = mutableListOf<String>()
        subjectNames.add(getString(R.string.label_no_subject))
        subjectNames.addAll(state.subjects.map { it.subjectName })
        (binding.subjectField.adapter as ArrayAdapter<String>).clear()
        (binding.subjectField.adapter as ArrayAdapter<String>).addAll(subjectNames)
        (binding.subjectField.adapter as ArrayAdapter<String>).notifyDataSetChanged()

        binding.screenTitle.setText(
            if (task != null) R.string.title_edit_task else R.string.title_add_task
        )
        binding.saveButton.setText(
            if (task != null) R.string.action_update_task else R.string.action_save_task
        )

        if (task != null) {
            binding.titleField.setText(task.title)
            binding.descriptionField.setText(task.description.orEmpty())

            selectedSubjectId = task.subjectId
            val subjectPosition = state.subjects
                .indexOfFirst { it.subjectId == task.subjectId }
                .let { if (it >= 0) it + 1 else 0 }
            binding.subjectField.setText(subjectNames.getOrElse(subjectPosition) { subjectNames[0] }, false)

            selectTaskType(task.taskType)

            DateTimeUtils.parseDateTime(task.dueDate)?.let { dateTime ->
                dueDate = dateTime.toLocalDate()
                val time = dateTime.toLocalTime()
                dueTime = if (time == LocalTime.MIDNIGHT) null else time
            }
            DateTimeUtils.parseDateTime(task.reminderDate)?.let { dateTime ->
                reminderDate = dateTime.toLocalDate()
                val time = dateTime.toLocalTime()
                reminderTime = if (time == LocalTime.MIDNIGHT) null else time
                binding.reminderSwitch.isChecked = true
            }

            when (task.priority) {
                Priority.LOW -> binding.priorityGroup.check(R.id.priorityLow)
                Priority.MEDIUM -> binding.priorityGroup.check(R.id.priorityMedium)
                Priority.HIGH -> binding.priorityGroup.check(R.id.priorityHigh)
            }
        } else if (prefill != null) {
            binding.titleField.setText(prefill.title)
            binding.descriptionField.setText(prefill.description.orEmpty())

            selectedSubjectId = prefill.subjectId
            val subjectPosition = state.subjects
                .indexOfFirst { it.subjectId == prefill.subjectId }
                .let { if (it >= 0) it + 1 else 0 }
            binding.subjectField.setText(subjectNames.getOrElse(subjectPosition) { subjectNames[0] }, false)

            selectTaskType(TaskType.fromRaw(prefill.taskType))

            DateTimeUtils.parseDateTime(prefill.dueDate)?.let { dateTime ->
                dueDate = dateTime.toLocalDate()
                val time = dateTime.toLocalTime()
                dueTime = if (time == LocalTime.MIDNIGHT) null else time
            }
            DateTimeUtils.parseDateTime(prefill.reminderDate)?.let { dateTime ->
                reminderDate = dateTime.toLocalDate()
                binding.reminderSwitch.isChecked = true
            }

            val priority = Priority.fromRaw(prefill.priority)
            when (priority) {
                Priority.LOW -> binding.priorityGroup.check(R.id.priorityLow)
                Priority.MEDIUM -> binding.priorityGroup.check(R.id.priorityMedium)
                Priority.HIGH -> binding.priorityGroup.check(R.id.priorityHigh)
            }
        } else {
            binding.priorityGroup.check(R.id.priorityMedium)
        }

        renderDueDate()
        renderDueTime()
        renderReminderDate()
        renderReminderTime()
        binding.reminderSection.isVisible = binding.reminderSwitch.isChecked
    }

    // ------------------------------------------------------------ save & validate

    private fun validate(): Boolean {
        var valid = true

        if (binding.titleField.text.toString().trim().isEmpty()) {
            binding.titleLayout.error = getString(R.string.error_title_required)
            valid = false
        } else {
            binding.titleLayout.error = null
        }

        if (dueDate == null) {
            binding.dueDateLayout.error = getString(R.string.error_due_date_required)
            valid = false
        } else {
            binding.dueDateLayout.error = null
        }

        if (binding.reminderSwitch.isChecked && reminderDate == null) {
            binding.reminderDateLayout.error = getString(R.string.error_due_date_required)
            valid = false
        } else {
            binding.reminderDateLayout.error = null
        }

        return valid
    }

    private fun buildPayload(): TaskPayload {
        val dateOnly = dueDate ?: DateTimeUtils.today()
        // Date-only due dates default to end-of-day; reminders to 09:00.
        val dueIso = DateTimeUtils.formatIso(dateOnly, dueTime ?: LocalTime.of(23, 59))
        val reminderIso = if (binding.reminderSwitch.isChecked && reminderDate != null) {
            DateTimeUtils.formatIso(reminderDate!!, reminderTime ?: LocalTime.of(9, 0))
        } else {
            null
        }

        return TaskPayload(
            title = binding.titleField.text.toString().trim(),
            description = binding.descriptionField.text.toString().trim().ifEmpty { null },
            subjectId = selectedSubjectId,
            taskType = selectedTaskType,
            priority = selectedPriority(),
            dueDate = dueIso,
            reminderDate = reminderIso,
        )
    }

    private fun selectedPriority(): Priority = when (binding.priorityGroup.checkedButtonId) {
        R.id.priorityLow -> Priority.LOW
        R.id.priorityHigh -> Priority.HIGH
        else -> Priority.MEDIUM
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
