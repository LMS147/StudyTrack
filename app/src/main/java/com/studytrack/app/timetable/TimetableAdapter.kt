package com.studytrack.app.timetable

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studytrack.app.R
import com.studytrack.app.data.local.entity.TimetableEntry
import com.studytrack.app.databinding.ItemTimetableClassBinding
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

class TimetableAdapter(private val showDay: Boolean, private val onClick: (TimetableEntry) -> Unit) :
    ListAdapter<TimetableEntry, TimetableAdapter.Holder>(object : DiffUtil.ItemCallback<TimetableEntry>() {
        override fun areItemsTheSame(a: TimetableEntry, b: TimetableEntry) = a.ownerUid == b.ownerUid && a.entryId == b.entryId
        override fun areContentsTheSame(a: TimetableEntry, b: TimetableEntry) = a == b
    }) {
    var subjectNames: Map<String, String> = emptyMap()
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }
        }
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(
        ItemTimetableClassBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    inner class Holder(private val binding: ItemTimetableClassBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: TimetableEntry) {
            val context = binding.root.context
            val time = "${time(entry.startMinute)} – ${time(entry.endMinute)}"
            binding.classTime.text = if (showDay) "${DayOfWeek.of(entry.dayOfWeek).getDisplayName(TextStyle.FULL, Locale.getDefault())} · $time" else time
            binding.classTitle.text = entry.title
            binding.classDetails.text = listOfNotNull(subjectNames[entry.subjectId], entry.venue.takeIf { it.isNotBlank() }, entry.lecturer.takeIf { it.isNotBlank() }).joinToString(" · ")
            binding.classDetails.isVisible = binding.classDetails.text.isNotBlank()
            binding.classTerm.isVisible = showDay
            binding.classTerm.text = context.getString(R.string.timetable_term_format, entry.startsOn,
                entry.endsOn ?: context.getString(R.string.timetable_no_end))
            binding.root.setOnClickListener { onClick(entry) }
        }
    }
    companion object {
        fun time(minutes: Int): String = LocalTime.of(minutes / 60, minutes % 60).toString()
    }
}
