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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.studytrack.app.R
import com.studytrack.app.databinding.FragmentProgressBinding
import com.studytrack.app.util.Levels
import com.studytrack.app.util.toast
import kotlinx.coroutines.launch

/**
 * Progress tab: XP/level banner, completion stats, per-category bars and the
 * badge grid — everything derived from the task and subject caches.
 */
class ProgressFragment : Fragment() {

    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProgressViewModel by viewModels { ProgressViewModel.FACTORY }

    private lateinit var categoryAdapter: CategoryProgressAdapter
    private lateinit var badgeAdapter: BadgeAdapter

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

        categoryAdapter = CategoryProgressAdapter()
        binding.categoryProgressList.adapter = categoryAdapter
        binding.categoryProgressList.layoutManager = LinearLayoutManager(requireContext())

        badgeAdapter = BadgeAdapter()
        binding.badgeGrid.adapter = badgeAdapter
        binding.badgeGrid.layoutManager = GridLayoutManager(requireContext(), BADGE_COLUMNS)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: ProgressUiState) {
        binding.loadingProgress.isVisible = state.loading

        binding.xpValue.text = state.points.toString()
        binding.levelValue.text = getString(R.string.progress_level_format, state.level)
        binding.levelProgress.max = Levels.XP_PER_LEVEL
        binding.levelProgress.setProgressCompat(state.xpInLevel, true)
        binding.levelStartLabel.text = getString(R.string.progress_level_short_format, state.level)
        binding.levelEndLabel.text =
            getString(R.string.progress_level_short_format, state.level + 1)
        binding.xpUntilNextLevel.text = getString(
            R.string.progress_xp_until_format,
            Levels.XP_PER_LEVEL - state.xpInLevel,
            state.level + 1,
        )

        binding.statCompletedValue.text = state.completedTasks.toString()
        binding.statPendingValue.text = state.pendingTasks.toString()
        binding.statPercentValue.text = getString(R.string.percent_format, state.overallPercent)

        categoryAdapter.submitList(state.categoryProgress)
        binding.emptyCategories.isVisible = state.categoryProgress.isEmpty()

        badgeAdapter.submitList(state.badges)

        if (state.error != null && !state.loading) {
            toast(state.error)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val BADGE_COLUMNS = 3
    }
}
