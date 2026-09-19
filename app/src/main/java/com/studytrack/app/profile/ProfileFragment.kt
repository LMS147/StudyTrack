package com.studytrack.app.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studytrack.app.BuildConfig
import com.studytrack.app.R
import com.studytrack.app.data.remote.RetrofitClient
import com.studytrack.app.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels { ProfileViewModel.FACTORY }

    /** Guards against programmatic switch updates re-triggering listeners. */
    private var updatingUi = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.logoutButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(R.string.action_logout) { _, _ -> viewModel.logout() }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }

        binding.taskRemindersSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) viewModel.setTaskReminders(checked)
        }
        binding.streakRemindersSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) viewModel.setStreakReminders(checked)
        }
        binding.showAiCardSwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingUi) viewModel.setShowAiCard(checked)
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: ProfileUiState) {
        binding.profileAvatar.text =
            state.displayName.take(1).uppercase().ifBlank { "?" }
        binding.profileName.text =
            state.displayName.ifBlank { getString(R.string.greeting_fallback_name) }
        binding.profileEmail.text = state.email

        updatingUi = true
        binding.taskRemindersSwitch.isChecked = state.taskReminders
        binding.streakRemindersSwitch.isChecked = state.streakReminders
        binding.showAiCardSwitch.isChecked = state.showAiCard
        val checkedId = when (state.defaultAiPriority.lowercase()) {
            "low" -> R.id.aiPriorityLow
            "high" -> R.id.aiPriorityHigh
            else -> R.id.aiPriorityMedium
        }
        if (binding.aiPriorityGroup.checkedButtonId != checkedId) {
            binding.aiPriorityGroup.check(checkedId)
        }
        updatingUi = false

        binding.versionValue.text = BuildConfig.VERSION_NAME
        binding.apiEndpointValue.text = RetrofitClient.BASE_URL.ifBlank {
            getString(R.string.local_mode_label)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
