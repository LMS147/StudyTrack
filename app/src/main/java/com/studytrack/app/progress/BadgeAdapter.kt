package com.studytrack.app.progress

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studytrack.app.R
import com.studytrack.app.databinding.ItemBadgeBinding

/**
 * "BADGES" grid (3 columns). Earned tiles show full colour with a highlighted
 * border; unearned ones are greyed out so the goal is still readable.
 */
class BadgeAdapter : ListAdapter<BadgeUiModel, BadgeAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemBadgeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemBadgeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: BadgeUiModel) {
            val context = binding.root.context
            binding.badgeEmoji.text = item.emoji
            binding.badgeLabel.text = item.label

            val (surface, stroke, labelColor) = if (item.earned) {
                Triple(R.color.brand_container, R.color.brand_primary, R.color.brand_on_container)
            } else {
                Triple(R.color.surface_sunken, R.color.divider, R.color.text_secondary)
            }
            binding.badgeCard.setCardBackgroundColor(
                ContextCompat.getColor(context, surface)
            )
            binding.badgeCard.strokeColor = ContextCompat.getColor(context, stroke)
            binding.badgeEmoji.alpha = if (item.earned) 1f else 0.45f
            binding.badgeLabel.setTextColor(ContextCompat.getColor(context, labelColor))
            binding.badgeLabel.alpha = if (item.earned) 1f else 0.7f
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<BadgeUiModel>() {
            override fun areItemsTheSame(oldItem: BadgeUiModel, newItem: BadgeUiModel): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: BadgeUiModel, newItem: BadgeUiModel): Boolean =
                oldItem == newItem
        }
    }
}
