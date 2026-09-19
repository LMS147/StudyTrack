package com.studytrack.app.tasks

import android.content.res.ColorStateList
import android.graphics.Paint
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.studytrack.app.R
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.Task
import com.studytrack.app.databinding.ItemTaskBinding
import com.studytrack.app.util.DateTimeUtils

/**
 * Shared binding logic for the task row layout (item_task.xml), used by
 * [TaskAdapter] and the Dashboard's sectioned list so both render identically.
 */
object TaskRowBinder {

    fun bind(
        binding: ItemTaskBinding,
        item: TaskListItem,
        onTaskClick: (Task) -> Unit,
        onToggleComplete: (Task) -> Unit,
    ) {
        val task = item.task
        val context = binding.root.context

        binding.taskTitle.text = task.title
        binding.taskTitle.paintFlags = if (task.completed) {
            binding.taskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            binding.taskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        renderPriority(binding, task.priority)

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

    private fun renderPriority(binding: ItemTaskBinding, priority: Priority) {
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
