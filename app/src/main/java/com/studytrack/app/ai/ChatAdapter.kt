package com.studytrack.app.ai

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studytrack.app.R
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.TaskType
import com.studytrack.app.databinding.ItemMessageAiBinding
import com.studytrack.app.databinding.ItemMessageUserBinding
import com.studytrack.app.databinding.ItemSuggestionCardBinding
import com.studytrack.app.databinding.ItemTypingBinding
import com.studytrack.app.util.DateTimeUtils

/**
 * Chat adapter with four view types: user bubbles (right), AI text bubbles
 * (left), structured suggestion cards and the typing indicator. Chat history
 * lives in the ViewModel; this list is diffed by itemId.
 */
class ChatAdapter(
    private val listener: Listener,
) : ListAdapter<ChatItem, RecyclerView.ViewHolder>(DIFF) {

    interface Listener {
        fun onAcceptSuggestion(item: ChatItem.Suggestion)
        fun onEditSuggestion(item: ChatItem.Suggestion)
        fun onRejectSuggestion(item: ChatItem.Suggestion)
        fun onPickSuggestionDate(item: ChatItem.Suggestion)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatItem.UserMessage -> TYPE_USER
        is ChatItem.AiText -> TYPE_AI_TEXT
        is ChatItem.Suggestion -> TYPE_SUGGESTION
        ChatItem.TypingIndicator -> TYPE_TYPING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserViewHolder(
                ItemMessageUserBinding.inflate(inflater, parent, false)
            )
            TYPE_AI_TEXT -> AiTextViewHolder(
                ItemMessageAiBinding.inflate(inflater, parent, false)
            )
            TYPE_SUGGESTION -> SuggestionViewHolder(
                ItemSuggestionCardBinding.inflate(inflater, parent, false)
            )
            else -> TypingViewHolder(
                ItemTypingBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatItem.UserMessage -> (holder as UserViewHolder).bind(item)
            is ChatItem.AiText -> (holder as AiTextViewHolder).bind(item)
            is ChatItem.Suggestion -> (holder as SuggestionViewHolder).bind(item)
            ChatItem.TypingIndicator -> Unit // static layout
        }
    }

    class UserViewHolder(
        private val binding: ItemMessageUserBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatItem.UserMessage) {
            binding.messageText.text = item.text
        }
    }

    class AiTextViewHolder(
        private val binding: ItemMessageAiBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatItem.AiText) {
            binding.messageText.text = item.text
        }
    }

    class TypingViewHolder(
        binding: ItemTypingBinding,
    ) : RecyclerView.ViewHolder(binding.root)

    inner class SuggestionViewHolder(
        private val binding: ItemSuggestionCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatItem.Suggestion) {
            val context = binding.root.context
            val suggestion = item.suggestion

            binding.suggestionTitle.text = suggestion.title

            val description = suggestion.description.orEmpty()
            binding.suggestionDescription.isVisible = description.isNotBlank()
            binding.suggestionDescription.text = description

            binding.taskTypeChip.text = TaskType.fromRaw(suggestion.taskType).label

            val priority = Priority.fromRaw(suggestion.priority)
            binding.priorityChip.text = priority.label
            val (containerColor, foregroundColor) = when (priority) {
                Priority.LOW -> R.color.priority_low_container to R.color.priority_low
                Priority.MEDIUM -> R.color.priority_medium_container to R.color.priority_medium
                Priority.HIGH -> R.color.priority_high_container to R.color.priority_high
            }
            binding.priorityChip.backgroundTintList =
                ContextCompat.getColorStateList(context, containerColor)
            binding.priorityChip.setTextColor(ContextCompat.getColor(context, foregroundColor))

            val subjectLabel = suggestion.subjectName ?: suggestion.subjectId
            binding.subjectChip.isVisible = !subjectLabel.isNullOrBlank()
            binding.subjectChip.text = subjectLabel.orEmpty()

            // Date: resolved value or an editable "Set date" field.
            val dueDate = item.effectiveDueDate
            if (dueDate != null) {
                binding.suggestionDateValue.text = DateTimeUtils.shortDateTime(dueDate)
                binding.suggestionNeedsDate.isVisible = false
            } else {
                binding.suggestionDateValue.text =
                    context.getString(R.string.suggestion_set_date)
                binding.suggestionNeedsDate.isVisible = true
            }
            binding.suggestionDateValue.setOnClickListener { listener.onPickSuggestionDate(item) }

            // Subtask preview rows.
            binding.subtasksContainer.removeAllViews()
            suggestion.subtasks.forEach { subtask ->
                val row = TextView(context)
                row.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                val due = subtask.dueDate
                row.text = if (due != null) {
                    "• ${subtask.title} — ${DateTimeUtils.shortDateTime(due)}"
                } else {
                    "• ${subtask.title}"
                }
                row.textSize = 13f
                row.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                binding.subtasksContainer.addView(row)
            }
            binding.subtasksContainer.isVisible = suggestion.subtasks.isNotEmpty()

            // Status-dependent rendering.
            when (item.status) {
                SuggestionStatus.PENDING -> {
                    binding.actionsRow.isVisible = true
                    binding.statusLabel.isVisible = false
                    binding.suggestionCard.alpha = 1f
                    val canAccept = dueDate != null
                    binding.acceptButton.isEnabled = canAccept
                    binding.acceptButton.alpha = if (canAccept) 1f else 0.5f
                    binding.editButton.isEnabled = true
                    binding.rejectButton.isEnabled = true
                }
                SuggestionStatus.ACCEPTING -> {
                    binding.actionsRow.isVisible = true
                    binding.statusLabel.isVisible = false
                    binding.suggestionCard.alpha = 1f
                    binding.acceptButton.isEnabled = false
                    binding.editButton.isEnabled = false
                    binding.rejectButton.isEnabled = false
                }
                SuggestionStatus.ACCEPTED -> {
                    binding.actionsRow.isVisible = false
                    binding.statusLabel.isVisible = true
                    binding.statusLabel.text =
                        context.getString(R.string.suggestion_accepted)
                    binding.statusLabel.setTextColor(
                        ContextCompat.getColor(context, R.color.success)
                    )
                    binding.suggestionCard.alpha = 0.85f
                }
                SuggestionStatus.REJECTED -> {
                    binding.actionsRow.isVisible = false
                    binding.statusLabel.isVisible = true
                    binding.statusLabel.text =
                        context.getString(R.string.suggestion_rejected)
                    binding.statusLabel.setTextColor(
                        ContextCompat.getColor(context, R.color.text_secondary)
                    )
                    binding.suggestionCard.alpha = 0.55f
                }
            }

            binding.acceptButton.setOnClickListener { listener.onAcceptSuggestion(item) }
            binding.editButton.setOnClickListener { listener.onEditSuggestion(item) }
            binding.rejectButton.setOnClickListener { listener.onRejectSuggestion(item) }
        }
    }

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_AI_TEXT = 1
        private const val TYPE_SUGGESTION = 2
        private const val TYPE_TYPING = 3

        private val DIFF = object : DiffUtil.ItemCallback<ChatItem>() {
            override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean =
                oldItem.itemId == newItem.itemId

            override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean =
                oldItem == newItem
        }
    }
}
