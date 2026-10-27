package com.studytrack.app.flashcards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.studytrack.app.R
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.local.entity.FlashcardEntity
import com.studytrack.app.data.model.Subject
import com.studytrack.app.databinding.FragmentFlashcardReviewBinding
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FlashcardReviewFragment : Fragment() {
    private var _binding: FragmentFlashcardReviewBinding? = null
    private val binding get() = _binding!!
    private val reviewed = mutableSetOf<String>()
    private var currentId: String? = null
    private var revealed = false
    private var saving = false
    private var ownerUid: String? = null
    private var subjectId: String? = null
    private var items: List<FlashcardEntity> = emptyList()
    private var subjects: List<Subject> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFlashcardReviewBinding.inflate(inflater, container, false)
        ownerUid = savedInstanceState?.getString("owner") ?: ServiceLocator.currentAccount.uidOrNull()
        subjectId = arguments?.getString("subjectId")
        reviewed.addAll(savedInstanceState?.getStringArrayList("reviewed").orEmpty())
        currentId = savedInstanceState?.getString("current")
        revealed = savedInstanceState?.getBoolean("revealed") ?: false
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.revealButton.setOnClickListener { revealed = true; render() }
        binding.againButton.setOnClickListener { rate(ReviewRating.AGAIN) }
        binding.hardButton.setOnClickListener { rate(ReviewRating.HARD) }
        binding.easyButton.setOnClickListener { rate(ReviewRating.EASY) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(ServiceLocator.flashcardRepository.cards, ServiceLocator.subjectRepository.subjects) { cards, subjects -> cards to subjects }
                    .collect { (cards, currentSubjects) -> items = cards; subjects = currentSubjects; render() }
            }
        }
    }
    private fun render() {
        if (_binding == null) return
        val queue = items.filter { it.ownerUid == ownerUid && (subjectId == null || it.subjectId == subjectId) &&
            it.cardId !in reviewed && ReviewSchedule.isDue(it.dueDate, LocalDate.now()) }
            .sortedWith(compareBy({ it.dueDate }, { it.question }, { it.cardId }))
        val card = queue.firstOrNull()
        if (card != null && currentId != card.cardId) { currentId = card.cardId; revealed = false }
        binding.reviewCount.text = getString(R.string.flashcards_remaining, queue.size)
        binding.reviewCard.isVisible = card != null
        binding.doneText.isVisible = card == null
        binding.revealButton.isVisible = card != null && !revealed
        binding.revealButton.isEnabled = !saving
        binding.answerLabel.isVisible = card != null && revealed
        binding.answerText.isVisible = card != null && revealed
        binding.ratingLabel.isVisible = card != null && revealed
        binding.ratingButtons.isVisible = card != null && revealed
        binding.againButton.isEnabled = !saving
        binding.hardButton.isEnabled = !saving
        binding.easyButton.isEnabled = !saving
        if (card != null) {
            binding.reviewSubject.text = subjects.firstOrNull { it.subjectId == card.subjectId }?.subjectName
                ?: getString(R.string.flashcards_removed_subject)
            binding.questionText.text = card.question
            binding.answerText.text = card.answer
        }
    }
    private fun rate(rating: ReviewRating) {
        if (saving || !revealed) return
        val uid = ownerUid ?: return
        val cardId = currentId ?: return
        saving = true
        binding.errorText.text = ""
        render()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                ServiceLocator.flashcardRepository.rate(uid, cardId, rating)
                reviewed.add(cardId)
                currentId = null
                revealed = false
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                binding.errorText.text = error.message ?: getString(R.string.flashcards_save_error)
            } finally { saving = false; render() }
        }
    }
    override fun onResume() { super.onResume(); if (_binding != null) render() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("owner", ownerUid)
        outState.putString("current", currentId)
        outState.putBoolean("revealed", revealed)
        outState.putStringArrayList("reviewed", ArrayList(reviewed))
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
