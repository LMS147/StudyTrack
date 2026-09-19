package com.studytrack.app.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studytrack.app.R
import com.studytrack.app.databinding.ItemCalendarDayBinding
import java.time.LocalDate

/** One cell of the month grid: either a real day or a leading/trailing blank. */
sealed class DayCell {
    abstract val cellId: String

    data class Blank(override val cellId: String) : DayCell()

    data class Day(
        val date: LocalDate,
        val isToday: Boolean,
        val isSelected: Boolean,
        /** Priority colors of up to three tasks due this day. */
        @ColorRes val dotColors: List<Int> = emptyList(),
    ) : DayCell() {
        override val cellId: String get() = "day-$date"
    }
}

/**
 * Month grid: 7 columns (GridLayoutManager), locale-aware first day of week,
 * today ring, selected-day highlight and priority dots for days with tasks.
 */
class MonthGridAdapter(
    private val onDayClick: (LocalDate) -> Unit,
) : ListAdapter<DayCell, MonthGridAdapter.DayViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder =
        DayViewHolder(
            ItemCalendarDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DayViewHolder(
        private val binding: ItemCalendarDayBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(cell: DayCell) {
            when (cell) {
                is DayCell.Blank -> {
                    binding.root.isClickable = false
                    binding.root.background = null
                    binding.dayNumber.text = ""
                    binding.dotsRow.isVisible = false
                }
                is DayCell.Day -> {
                    val context = binding.root.context
                    binding.root.isClickable = true
                    binding.dayNumber.text = cell.date.dayOfMonth.toString()
                    binding.root.background = when {
                        cell.isSelected -> ContextCompat.getDrawable(
                            context, R.drawable.bg_day_selected
                        )
                        cell.isToday -> ContextCompat.getDrawable(context, R.drawable.bg_day_today)
                        else -> null
                    }
                    binding.dayNumber.setTextColor(
                        ContextCompat.getColor(
                            context,
                            if (cell.isSelected) R.color.white else R.color.text_primary
                        )
                    )

                    val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
                    binding.dotsRow.isVisible = cell.dotColors.isNotEmpty()
                    dots.forEachIndexed { index, dot ->
                        val colorRes = cell.dotColors.getOrNull(index)
                        dot.isVisible = colorRes != null
                        if (colorRes != null) {
                            dot.backgroundTintList = ContextCompat.getColorStateList(
                                context, colorRes
                            )
                        }
                    }

                    binding.root.setOnClickListener { onDayClick(cell.date) }
                }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DayCell>() {
            override fun areItemsTheSame(oldItem: DayCell, newItem: DayCell): Boolean =
                oldItem.cellId == newItem.cellId

            override fun areContentsTheSame(oldItem: DayCell, newItem: DayCell): Boolean =
                oldItem == newItem
        }
    }
}
