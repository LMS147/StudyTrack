package com.studytrack.app.subjects

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studytrack.app.R
import com.studytrack.app.data.model.Subject
import com.studytrack.app.databinding.ItemSubjectBinding

/**
 * List adapter for the Subjects screen. Uses ListAdapter + DiffUtil; the list
 * items ([SubjectWithProgress]) are computed in the ViewModel from the
 * Subject and Task repository caches.
 */
class SubjectAdapter(
    private val onSubjectClick: (SubjectWithProgress) -> Unit,
    private val onEditSubject: (Subject) -> Unit,
    private val onDeleteSubject: (Subject) -> Unit,
) : ListAdapter<SubjectWithProgress, SubjectAdapter.SubjectViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder =
        SubjectViewHolder(
            ItemSubjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SubjectViewHolder(
        private val binding: ItemSubjectBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubjectWithProgress) {
            val subject = item.subject

            binding.subjectAvatar.text =
                subject.subjectName.trim().take(1).uppercase().ifEmpty { "?" }

            binding.subjectName.text = subject.subjectName

            val description = subject.description.orEmpty()
            binding.subjectDescription.isVisible = description.isNotBlank()
            binding.subjectDescription.text = description

            binding.subjectProgress.max = 100
            binding.subjectProgress.progress = item.progressPercent
            binding.subjectProgressLabel.text = binding.root.context.getString(
                R.string.subject_progress_label, item.completedTasks, item.totalTasks
            )

            binding.root.setOnClickListener { onSubjectClick(item) }

            binding.subjectOptions.setOnClickListener {
                val popup = PopupMenu(binding.root.context, binding.subjectOptions)
                popup.menuInflater.inflate(R.menu.menu_subject_actions, popup.menu)
                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_edit -> {
                            onEditSubject(subject)
                            true
                        }
                        R.id.action_delete -> {
                            onDeleteSubject(subject)
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SubjectWithProgress>() {
            override fun areItemsTheSame(
                oldItem: SubjectWithProgress,
                newItem: SubjectWithProgress,
            ): Boolean = oldItem.subject.subjectId == newItem.subject.subjectId

            override fun areContentsTheSame(
                oldItem: SubjectWithProgress,
                newItem: SubjectWithProgress,
            ): Boolean = oldItem == newItem
        }
    }
}
