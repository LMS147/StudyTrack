package com.studytrack.app.progress

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studytrack.app.R
import com.studytrack.app.databinding.ItemSubjectProgressBinding

/** Per-subject progress bars on the Progress screen. */
class SubjectProgressAdapter :
    ListAdapter<SubjectProgressUiModel, SubjectProgressAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemSubjectProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemSubjectProgressBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubjectProgressUiModel) {
            binding.subjectName.text = item.subjectName
            binding.percentText.text =
                binding.root.context.getString(R.string.percent_format, item.percent)
            binding.countText.text = binding.root.context.getString(
                R.string.progress_completed_of, item.completed, item.total
            )
            binding.progressBar.max = 100
            binding.progressBar.progress = item.percent
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SubjectProgressUiModel>() {
            override fun areItemsTheSame(
                oldItem: SubjectProgressUiModel,
                newItem: SubjectProgressUiModel,
            ): Boolean = oldItem.subjectId == newItem.subjectId

            override fun areContentsTheSame(
                oldItem: SubjectProgressUiModel,
                newItem: SubjectProgressUiModel,
            ): Boolean = oldItem == newItem
        }
    }
}
