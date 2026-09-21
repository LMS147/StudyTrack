package com.studytrack.app.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studytrack.app.data.model.Task
import com.studytrack.app.databinding.ItemDashboardEmptyBinding
import com.studytrack.app.databinding.ItemDashboardHeaderBinding
import com.studytrack.app.databinding.ItemTaskBinding
import com.studytrack.app.tasks.TaskListItem
import com.studytrack.app.tasks.TaskRowBinder

/**
 * The dashboard list is a sectioned RecyclerView: headers ("Due today",
 * "Overdue", "Upcoming deadlines"), task rows (reusing the shared item_task
 * layout) and empty-section placeholders.
 */
sealed class DashboardItem {
    abstract val itemId: String

    data class Header(
        override val itemId: String,
        val title: String,
        val count: Int,
    ) : DashboardItem()

    data class TaskRow(val item: TaskListItem) : DashboardItem() {
        override val itemId: String get() = "task-${item.task.taskId}"
    }

    data class Empty(
        override val itemId: String,
        val message: String,
    ) : DashboardItem()
}

class DashboardAdapter(
    private val onTaskClick: (Task) -> Unit,
    private val onToggleComplete: (Task) -> Unit,
) : ListAdapter<DashboardItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DashboardItem.Header -> TYPE_HEADER
        is DashboardItem.TaskRow -> TYPE_TASK
        is DashboardItem.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                ItemDashboardHeaderBinding.inflate(inflater, parent, false)
            )
            TYPE_TASK -> TaskViewHolder(
                ItemTaskBinding.inflate(inflater, parent, false),
                onTaskClick,
                onToggleComplete,
            )
            else -> EmptyViewHolder(
                ItemDashboardEmptyBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is DashboardItem.Header -> (holder as HeaderViewHolder).bind(item)
            is DashboardItem.TaskRow -> (holder as TaskViewHolder).bind(item.item)
            is DashboardItem.Empty -> (holder as EmptyViewHolder).bind(item)
        }
    }

    class HeaderViewHolder(
        private val binding: ItemDashboardHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DashboardItem.Header) {
            binding.headerTitle.text = item.title
            binding.headerCount.text = item.count.toString()
        }
    }

    class TaskViewHolder(
        private val binding: ItemTaskBinding,
        private val onTaskClick: (Task) -> Unit,
        private val onToggleComplete: (Task) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: TaskListItem) =
            TaskRowBinder.bind(binding, item, onTaskClick, onToggleComplete)
    }

    class EmptyViewHolder(
        private val binding: ItemDashboardEmptyBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DashboardItem.Empty) {
            binding.emptyMessage.text = item.message
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_TASK = 1
        private const val TYPE_EMPTY = 2

        private val DIFF = object : DiffUtil.ItemCallback<DashboardItem>() {
            override fun areItemsTheSame(oldItem: DashboardItem, newItem: DashboardItem): Boolean =
                oldItem.itemId == newItem.itemId

            override fun areContentsTheSame(oldItem: DashboardItem, newItem: DashboardItem): Boolean =
                oldItem == newItem
        }
    }
}
