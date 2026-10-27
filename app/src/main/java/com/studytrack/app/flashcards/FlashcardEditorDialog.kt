package com.studytrack.app.flashcards

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studytrack.app.R
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.local.entity.FlashcardEntity
import com.studytrack.app.databinding.DialogFlashcardEditorBinding
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class FlashcardEditorDialog : DialogFragment() {
    private var editor: DialogFlashcardEditorBinding? = null
    private lateinit var ownerUid: String
    private lateinit var cardId: String
    private var existing = false
    private var originalDue = ""
    private var interval = 0
    private var reviews = 0
    private var lastReview: String? = null
    private var subjectIds: List<String?> = emptyList()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val data = savedInstanceState ?: arguments ?: Bundle()
        ownerUid = data.getString("owner") ?: ServiceLocator.currentAccount.requireUid()
        cardId = data.getString("id") ?: UUID.randomUUID().toString()
        existing = data.getBoolean("existing")
        originalDue = data.getString("due") ?: LocalDate.now().toString()
        interval = data.getInt("interval")
        reviews = data.getInt("reviews")
        lastReview = data.getString("last")
        val b = DialogFlashcardEditorBinding.inflate(layoutInflater)
        editor = b
        b.questionInput.setText(data.getString("question").orEmpty())
        b.answerInput.setText(data.getString("answer").orEmpty())
        val subjects = ServiceLocator.subjectRepository.subjects.value
        subjectIds = listOf(null) + subjects.map { it.subjectId }
        b.subjectSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.flashcards_choose_subject)) + subjects.map { it.subjectName })
        b.subjectSpinner.setSelection(subjectIds.indexOf(data.getString("subject")).coerceAtLeast(0))
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing) R.string.flashcards_edit else R.string.flashcards_add)
            .setView(b.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .apply { if (existing) setNeutralButton(R.string.action_delete, null) }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val card = snapshot()
                dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = false
                lifecycleScope.launch {
                    try { ServiceLocator.flashcardRepository.save(card, isNew = !existing); dismiss()
                    } catch (error: CancellationException) { throw error
                    } catch (error: Exception) {
                        editor?.errorText?.text = error.message ?: getString(R.string.flashcards_save_error)
                        editor?.root?.post { editor?.root?.fullScroll(View.FOCUS_DOWN) }
                        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).isEnabled = true
                    }
                }
            }
            dialog.getButton(android.content.DialogInterface.BUTTON_NEUTRAL)?.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.flashcards_delete_title)
                    .setMessage(R.string.flashcards_delete_message)
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        lifecycleScope.launch {
                            try { ServiceLocator.flashcardRepository.delete(ownerUid, cardId); dismiss()
                            } catch (error: CancellationException) { throw error
                            } catch (error: Exception) { editor?.errorText?.text = error.message }
                        }
                    }.show()
            }
        }
        return dialog
    }
    private fun snapshot(): FlashcardEntity {
        val b = requireNotNull(editor)
        return FlashcardEntity(ownerUid, cardId, subjectIds.getOrNull(b.subjectSpinner.selectedItemPosition).orEmpty(),
            b.questionInput.text.toString(), b.answerInput.text.toString(), originalDue, interval, reviews, lastReview)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        editor?.let { outState.putAll(bundle(snapshot()).apply { putBoolean("existing", existing) }) }
    }
    override fun onDestroyView() { super.onDestroyView(); editor = null }
    companion object {
        fun create(subjectId: String?) = FlashcardEditorDialog().apply { arguments = Bundle().apply { putString("subject", subjectId) } }
        fun edit(card: FlashcardEntity) = FlashcardEditorDialog().apply { arguments = bundle(card).apply { putBoolean("existing", true) } }
        private fun bundle(card: FlashcardEntity) = Bundle().apply {
            putString("owner", card.ownerUid); putString("id", card.cardId); putString("subject", card.subjectId)
            putString("question", card.question); putString("answer", card.answer); putString("due", card.dueDate)
            putInt("interval", card.intervalDays); putInt("reviews", card.reviewCount); putString("last", card.lastReviewedOn)
        }
    }
}
