package com.studytrack.app.calendar

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studytrack.app.R
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.Task
import com.studytrack.app.databinding.ItemCalendarTaskBinding
import com.studytrack.app.tasks.TaskListItem

/**
 * The selected day's tasks on the Calendar screen: type icon, title over the
 * subject, and the priority pill — the row style the calendar design uses
 * (tapping a row opens Task Details, where the task can be completed).
 */
class CalendarTaskAdapter(
    private val onTaskClick: (Task) -> Unit,
) : ListAdapter<TaskListItem, CalendarTaskAdapter.DayTaskViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayTaskViewHolder =
        DayTaskViewHolder(
            ItemCalendarTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: DayTaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DayTaskViewHolder(
        private val binding: ItemCalendarTaskBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TaskListItem) {
            val context = binding.root.context
            val task = item.task

            binding.calendarTaskIcon.text = task.taskType.emoji
            binding.calendarTaskTitle.text = task.title
            binding.calendarTaskSubject.isVisible = !item.subjectName.isNullOrBlank()
            binding.calendarTaskSubject.text = item.subjectName.orEmpty()

            val (containerColor, foregroundColor) = when (task.priority) {
                Priority.LOW -> R.color.priority_low_container to R.color.priority_low
                Priority.MEDIUM -> R.color.priority_medium_container to R.color.priority_medium
                Priority.HIGH -> R.color.priority_high_container to R.color.priority_high
            }
            binding.calendarTaskPriority.text = task.priority.label
            binding.calendarTaskPriority.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, containerColor))
            binding.calendarTaskPriority.setTextColor(
                ContextCompat.getColor(context, foregroundColor)
            )

            binding.root.setOnClickListener { onTaskClick(task) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TaskListItem>() {
            override fun areItemsTheSame(oldItem: TaskListItem, newItem: TaskListItem): Boolean =
                oldItem.task.taskId == newItem.task.taskId

            override fun areContentsTheSame(oldItem: TaskListItem, newItem: TaskListItem): Boolean =
                oldItem == newItem
        }
    }
}
