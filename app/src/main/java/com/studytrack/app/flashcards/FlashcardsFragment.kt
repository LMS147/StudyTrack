package com.studytrack.app.flashcards

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.os.bundleOf
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
import com.studytrack.app.databinding.FragmentFlashcardsBinding
import java.time.LocalDate
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FlashcardsFragment : Fragment() {
    private var _binding: FragmentFlashcardsBinding? = null
    private val binding get() = _binding!!
    private var filterId: String? = null
    private var items: List<FlashcardEntity> = emptyList()
    private var subjects: List<Subject> = emptyList()
    private var filterIds: List<String?> = listOf(null)
    private var updatingFilter = false
    private lateinit var cardsAdapter: FlashcardAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentFlashcardsBinding.inflate(inflater, container, false)
        filterId = savedInstanceState?.getString("filter")
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cardsAdapter = FlashcardAdapter { FlashcardEditorDialog.edit(it).show(childFragmentManager, "flashcard-editor") }
        binding.cardList.adapter = cardsAdapter
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.addCardButton.setOnClickListener { FlashcardEditorDialog.create(filterId).show(childFragmentManager, "flashcard-editor") }
        binding.subjectsButton.setOnClickListener { findNavController().navigate(R.id.action_flashcardsFragment_to_subjectsFragment) }
        binding.reviewButton.setOnClickListener {
            findNavController().navigate(R.id.action_flashcardsFragment_to_flashcardReviewFragment, bundleOf("subjectId" to filterId))
        }
        binding.subjectFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!updatingFilter) { filterId = filterIds.getOrNull(position); render() }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch { ServiceLocator.subjectRepository.refresh() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(ServiceLocator.flashcardRepository.cards, ServiceLocator.subjectRepository.subjects) { cards, subjects -> cards to subjects }
                    .collect { (cards, currentSubjects) ->
                        items = cards
                        if (subjects != currentSubjects || filterIds.size == 1) {
                            subjects = currentSubjects
                            updatingFilter = true
                            filterIds = listOf(null) + subjects.map { it.subjectId }
                            binding.subjectFilter.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item,
                                listOf(getString(R.string.flashcards_all_subjects)) + subjects.map { it.subjectName })
                            binding.subjectFilter.setSelection(filterIds.indexOf(filterId).coerceAtLeast(0))
                            updatingFilter = false
                        }
                        render()
                    }
            }
        }
    }
    private fun render() {
        val visible = items.filter { filterId == null || it.subjectId == filterId }
        val due = visible.count { ReviewSchedule.isDue(it.dueDate, LocalDate.now()) }
        cardsAdapter.subjectNames = subjects.associate { it.subjectId to it.subjectName }
        cardsAdapter.submitList(visible)
        binding.summaryText.text = getString(R.string.flashcards_summary, visible.size, due)
        binding.reviewButton.isEnabled = due > 0
        binding.addCardButton.isEnabled = subjects.isNotEmpty()
        binding.subjectsButton.isVisible = subjects.isEmpty()
        binding.emptyText.isVisible = visible.isEmpty()
        binding.emptyText.text = getString(if (subjects.isEmpty()) R.string.flashcards_need_subject else R.string.flashcards_empty)
    }
    override fun onResume() { super.onResume(); if (::cardsAdapter.isInitialized && _binding != null) render() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putString("filter", filterId) }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
