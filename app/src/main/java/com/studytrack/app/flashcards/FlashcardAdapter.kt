package com.studytrack.app.flashcards

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studytrack.app.R
import com.studytrack.app.data.local.entity.FlashcardEntity
import com.studytrack.app.databinding.ItemFlashcardBinding
import java.time.LocalDate

class FlashcardAdapter(private val onEdit: (FlashcardEntity) -> Unit) :
    ListAdapter<FlashcardEntity, FlashcardAdapter.Holder>(object : DiffUtil.ItemCallback<FlashcardEntity>() {
        override fun areItemsTheSame(a: FlashcardEntity, b: FlashcardEntity) = a.ownerUid == b.ownerUid && a.cardId == b.cardId
        override fun areContentsTheSame(a: FlashcardEntity, b: FlashcardEntity) = a == b
    }) {
    var subjectNames: Map<String, String> = emptyMap()
        set(value) { if (field != value) { field = value; notifyItemRangeChanged(0, itemCount) } }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemFlashcardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    inner class Holder(private val binding: ItemFlashcardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(card: FlashcardEntity) {
            val context = binding.root.context
            binding.cardSubject.text = subjectNames[card.subjectId] ?: context.getString(R.string.flashcards_removed_subject)
            binding.cardQuestion.text = card.question
            binding.cardDue.text = if (ReviewSchedule.isDue(card.dueDate, LocalDate.now())) context.getString(R.string.flashcards_due_today)
                else context.getString(R.string.flashcards_due_date, card.dueDate)
            binding.root.setOnClickListener { onEdit(card) }
        }
    }
}
