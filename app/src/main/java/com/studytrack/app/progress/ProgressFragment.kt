package com.studytrack.app.progress

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.studytrack.app.R
import com.studytrack.app.databinding.FragmentProgressBinding
import com.studytrack.app.util.toast
import kotlinx.coroutines.launch

class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProgressViewModel by viewModels { ProgressViewModel.FACTORY }

    private lateinit var adapter: SubjectProgressAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SubjectProgressAdapter()
        binding.subjectProgressList.adapter = adapter
        binding.subjectProgressList.layoutManager = LinearLayoutManager(requireContext())

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: ProgressUiState) {
        binding.loadingProgress.isVisible = state.loading

        binding.overallRing.setProgress(state.overallPercent, true)
        binding.overallPercentText.text =
            getString(R.string.percent_format, state.overallPercent)
        binding.overallSummaryText.text = getString(
            R.string.progress_completed_of, state.completedTasks, state.totalTasks
        )

        binding.statPointsValue.text = state.totalPoints.toString()
        binding.statCompletedValue.text = state.completedTasks.toString()
        binding.statStreakValue.text = state.currentStreak.toString()

        adapter.submitList(state.subjectProgress)
        binding.emptySubjects.isVisible = state.subjectProgress.isEmpty()

        if (state.error != null && !state.loading) {
            toast(state.error)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
