package com.studytrack.app.timetable

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studytrack.app.R
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.local.entity.TimetableEntry
import com.studytrack.app.databinding.DialogTimetableEditorBinding
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class TimetableEditorDialog : DialogFragment() {
    private var editor: DialogTimetableEditorBinding? = null
    private var startMinute = 540
    private var endMinute = 600
    private var startsOn = LocalDate.now().toString()
    private var endsOn: String? = null
    private var selectedSubjectId: String? = null
    private var subjectIds: List<String?> = emptyList()
    private lateinit var ownerUid: String
    private lateinit var entryId: String

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val data = savedInstanceState ?: arguments ?: Bundle()
        ownerUid = data.getString("owner") ?: ServiceLocator.currentAccount.requireUid()
        entryId = data.getString("id") ?: UUID.randomUUID().toString()
        startMinute = data.getInt("start", 540)
        endMinute = data.getInt("end", 600)
        startsOn = data.getString("from") ?: LocalDate.now().toString()
        endsOn = data.getString("until")
        selectedSubjectId = data.getString("subject")
        val b = DialogTimetableEditorBinding.inflate(layoutInflater)
        editor = b
        b.titleInput.setText(data.getString("title").orEmpty())
        b.venueInput.setText(data.getString("venue").orEmpty())
        b.lecturerInput.setText(data.getString("lecturer").orEmpty())
        val subjects = ServiceLocator.subjectRepository.subjects.value
        subjectIds = listOf(null) + subjects.map { it.subjectId }
        b.subjectSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.timetable_no_subject)) + subjects.map { it.subjectName })
        b.subjectSpinner.setSelection(subjectIds.indexOf(selectedSubjectId).coerceAtLeast(0))
        b.daySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
            (1..7).map { DayOfWeek.of(it).getDisplayName(TextStyle.FULL, Locale.getDefault()) })
        b.daySpinner.setSelection(data.getInt("day", LocalDate.parse(startsOn).dayOfWeek.value) - 1)
        b.startTimeButton.setOnClickListener { pickTime(true) }
        b.endTimeButton.setOnClickListener { pickTime(false) }
        b.startDateButton.setOnClickListener { pickDate(false) }
        b.endDateButton.setOnClickListener { pickDate(true) }
        b.clearEndButton.setOnClickListener { endsOn = null; renderTimes() }
        renderTimes()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (data.getBoolean("existing")) R.string.timetable_edit else R.string.timetable_add)
            .setView(b.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .apply {
                if (data.getBoolean("existing")) setNeutralButton(R.string.action_delete, null)
            }.create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val entry = snapshot()
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = false
                lifecycleScope.launch {
                    try {
                        ServiceLocator.timetableRepository.save(entry)
                        dismiss()
                    } catch (error: CancellationException) { throw error
                    } catch (error: Exception) {
                        editor?.errorText?.text = error.message ?: getString(R.string.timetable_save_error)
                        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = true
                    }
                }
            }
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.timetable_delete_title)
                    .setMessage(R.string.timetable_delete_message)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        lifecycleScope.launch {
                            try { ServiceLocator.timetableRepository.delete(snapshot()); dismiss()
                            } catch (error: CancellationException) { throw error
                            } catch (error: Exception) { editor?.errorText?.text = error.message }
                        }
                    }.show()
            }
        }
        return dialog
    }

    private fun snapshot(): TimetableEntry {
        val b = requireNotNull(editor)
        return TimetableEntry(ownerUid, entryId, b.titleInput.text.toString(),
            subjectIds.getOrNull(b.subjectSpinner.selectedItemPosition), b.daySpinner.selectedItemPosition + 1,
            startMinute, endMinute, b.venueInput.text.toString(), b.lecturerInput.text.toString(), startsOn, endsOn)
    }

    private fun renderTimes() {
        editor?.let {
            it.startTimeButton.text = TimetableAdapter.time(startMinute)
            it.endTimeButton.text = TimetableAdapter.time(endMinute)
            it.startDateButton.text = startsOn
            it.endDateButton.text = endsOn ?: getString(R.string.timetable_no_end)
        }
    }

    private fun pickTime(start: Boolean) {
        val value = if (start) startMinute else endMinute
        TimePickerDialog(requireContext(), { _, hour, minute ->
            if (start) startMinute = hour * 60 + minute else endMinute = hour * 60 + minute
            renderTimes()
        }, value / 60, value % 60, android.text.format.DateFormat.is24HourFormat(requireContext())).show()
    }

    private fun pickDate(end: Boolean) {
        val current = if (end) endsOn ?: startsOn else startsOn
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(LocalDate.parse(current).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()).build()
        picker.addOnPositiveButtonClickListener {
            val value = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
            if (end) endsOn = value else startsOn = value
            renderTimes()
        }
        picker.show(childFragmentManager, "class-date")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        editor?.let { outState.putAll(bundle(snapshot()).apply {
            putBoolean("existing", arguments?.getBoolean("existing") ?: false)
        }) }
    }

    override fun onDestroyView() { super.onDestroyView(); editor = null }

    companion object {
        fun create(date: LocalDate = LocalDate.now()) = TimetableEditorDialog().apply {
            arguments = Bundle().apply { putString("from", date.toString()) }
        }
        fun edit(entry: TimetableEntry) = TimetableEditorDialog().apply {
            arguments = bundle(entry).apply { putBoolean("existing", true) }
        }
        private fun bundle(entry: TimetableEntry) = Bundle().apply {
            putString("owner", entry.ownerUid); putString("id", entry.entryId); putString("title", entry.title)
            putString("subject", entry.subjectId); putInt("day", entry.dayOfWeek)
            putInt("start", entry.startMinute); putInt("end", entry.endMinute)
            putString("venue", entry.venue); putString("lecturer", entry.lecturer)
            putString("from", entry.startsOn); putString("until", entry.endsOn)
        }
    }
}
