package com.studytrack.app.timetable

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
import com.studytrack.app.ServiceLocator
import com.studytrack.app.databinding.FragmentTimetableBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TimetableFragment : Fragment() {
    private var _binding: FragmentTimetableBinding? = null
    private val binding get() = _binding!!
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTimetableBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = TimetableAdapter(true) { TimetableEditorDialog.edit(it).show(childFragmentManager, "class-editor") }
        binding.classList.adapter = adapter
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.addClassButton.setOnClickListener { TimetableEditorDialog.create().show(childFragmentManager, "class-editor") }
        viewLifecycleOwner.lifecycleScope.launch { ServiceLocator.subjectRepository.refresh() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(ServiceLocator.timetableRepository.entries, ServiceLocator.subjectRepository.subjects) { entries, subjects ->
                    entries to subjects.associate { it.subjectId to it.subjectName }
                }.collect { (entries, subjects) ->
                    adapter.subjectNames = subjects
                    adapter.submitList(entries.sortedWith(compareBy({ it.dayOfWeek }, { it.startMinute })))
                    binding.emptyTimetable.isVisible = entries.isEmpty()
                }
            }
        }
    }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
