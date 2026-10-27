package com.studytrack.app.profile

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.studytrack.app.R
import com.studytrack.app.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

/** Preferences use the same persisted settings as Profile, on their own screen. */
class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ProfileViewModel by viewModels { ProfileViewModel.FACTORY }
    private var updatingUi = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        wireSwitches()
        binding.languageButton.setOnClickListener { AppLanguagePicker.show(requireContext()) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { renderState(it) }
            }
        }
    }

    private fun wireSwitches() {
        // Dark Mode: the switch reflects what is on screen right now; flipping
        // it stores an explicit choice and re-themes the whole app (the Activity
        // recreates itself, so the new palette applies immediately).
        binding.darkModeSwitch.isChecked = isNightModeActive()
        binding.darkModeSwitch.setOnCheckedChangeListener { _, checked ->
            if (updatingUi) return@setOnCheckedChangeListener
            viewModel.setDarkMode(checked)
            AppCompatDelegate.setDefaultNightMode(
                if (checked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        binding.pushNotificationsSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) viewModel.setPushNotifications(checked)
        }
        binding.deadlineRemindersSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) viewModel.setDeadlineReminders(checked)
        }
        binding.dailyDigestSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) viewModel.setDailyDigest(checked)
        }
        binding.weeklyReportSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) viewModel.setWeeklyReport(checked)
        }
        binding.showAiCardSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) viewModel.setShowAiCard(checked)
        }
        binding.pomodoroSoundSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) viewModel.setPomodoroSound(checked)
        }

        binding.aiPriorityGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (updatingUi || !isChecked) return@addOnButtonCheckedListener
            val raw = when (checkedId) {
                R.id.aiPriorityLow -> "Low"
                R.id.aiPriorityMedium -> "Medium"
                R.id.aiPriorityHigh -> "High"
                else -> return@addOnButtonCheckedListener
            }
            viewModel.setDefaultAiPriority(raw)
        }
    }

    private fun renderState(state: ProfileUiState) {
        updatingUi = true
        binding.pushNotificationsSwitch.isChecked = state.pushNotifications
        binding.deadlineRemindersSwitch.isChecked = state.deadlineReminders
        binding.dailyDigestSwitch.isChecked = state.dailyDigest
        binding.weeklyReportSwitch.isChecked = state.weeklyReport
        binding.showAiCardSwitch.isChecked = state.showAiCard
        binding.pomodoroSoundSwitch.isChecked = state.pomodoroSound
        val checkedId = when (state.defaultAiPriority.lowercase()) {
            "low" -> R.id.aiPriorityLow
            "high" -> R.id.aiPriorityHigh
            else -> R.id.aiPriorityMedium
        }
        if (binding.aiPriorityGroup.checkedButtonId != checkedId) {
            binding.aiPriorityGroup.check(checkedId)
        }
        updatingUi = false
    }

    /** True when the app is currently rendering the dark palette. */
    private fun isNightModeActive(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
