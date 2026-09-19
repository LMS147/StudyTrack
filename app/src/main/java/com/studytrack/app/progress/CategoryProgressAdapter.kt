package com.studytrack.app.progress

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studytrack.app.R
import com.studytrack.app.databinding.ItemCategoryProgressBinding

/** "BY CATEGORY" rows: one labelled progress bar per task type. */
class CategoryProgressAdapter :
    ListAdapter<CategoryProgressUiModel, CategoryProgressAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemCategoryProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemCategoryProgressBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CategoryProgressUiModel) {
            binding.categoryLabel.text = item.label
            binding.categoryCount.text = binding.root.context.getString(
                R.string.progress_completed_of, item.completed, item.total
            )
            binding.categoryProgress.max = 100
            binding.categoryProgress.setProgressCompat(item.percent, true)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CategoryProgressUiModel>() {
            override fun areItemsTheSame(
                oldItem: CategoryProgressUiModel,
                newItem: CategoryProgressUiModel,
            ): Boolean = oldItem.label == newItem.label

            override fun areContentsTheSame(
                oldItem: CategoryProgressUiModel,
                newItem: CategoryProgressUiModel,
            ): Boolean = oldItem == newItem
        }
    }
}
