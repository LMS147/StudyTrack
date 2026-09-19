package com.studytrack.app.tasks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studytrack.app.data.model.Task
import com.studytrack.app.databinding.ItemTaskBinding

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

        fun bind(item: TaskListItem) =
            TaskRowBinder.bind(binding, item, onTaskClick, onToggleComplete)
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
