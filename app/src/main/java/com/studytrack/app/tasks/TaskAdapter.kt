package com.studytrack.app.tasks

import android.graphics.Paint
import android.graphics.ColorStateList
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
import com.studytrack.app.databinding.ItemTaskBinding
import com.studytrack.app.util.DateTimeUtils

/** A task plus the display name of its subject (null when none/unlinked). */
data class TaskListItem(
    val task: Task,
    val subjectName: String? = null,
)

/**
 * Shared task row used on the Dashboard, Calendar day list and Subject
 * Details. ListAdapter + DiffUtil; the wrapped [TaskListItem] carries the
 * subject name so the diff also catches subject renames.
 */
class TaskAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onToggleComplete: (Task) -> Unit,
) : ListAdapter<TaskListItem, TaskAdapter.TaskViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder =
        TaskViewHolder(ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(
        private val binding: ItemTaskBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TaskListItem) {
            val task = item.task
            val context = binding.root.context

            binding.taskTitle.text = task.title
            binding.taskTitle.paintFlags = if (task.completed) {
                binding.taskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.taskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            renderPriority(task.priority)

            binding.taskDueDate.text = DateTimeUtils.shortDateTime(task.dueDate)
            val overdue = !task.completed && DateTimeUtils.isOverdue(task.dueDate)
            binding.taskDueDate.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (overdue) R.color.overdue_red else R.color.text_secondary
                )
            )

            binding.taskSubject.isVisible = !item.subjectName.isNullOrBlank()
            binding.taskSubject.text = item.subjectName.orEmpty()

            binding.taskCompletedIcon.setImageResource(
                if (task.completed) R.drawable.ic_check_circle else R.drawable.ic_radio_unchecked
            )
            binding.taskCompletedIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    context,
                    if (task.completed) R.color.success else R.color.divider
                )
            )
            binding.taskCompletedIcon.setOnClickListener { onToggleComplete(task) }

            binding.root.setOnClickListener { onTaskClick(task) }
        }

        private fun renderPriority(priority: Priority) {
            val context = binding.root.context
            val (containerColor, foregroundColor) = when (priority) {
                Priority.LOW -> R.color.priority_low_container to R.color.priority_low
                Priority.MEDIUM -> R.color.priority_medium_container to R.color.priority_medium
                Priority.HIGH -> R.color.priority_high_container to R.color.priority_high
            }
            binding.taskPriorityChip.text = priority.label
            binding.taskPriorityChip.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, containerColor))
            binding.taskPriorityChip.setTextColor(ContextCompat.getColor(context, foregroundColor))
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
