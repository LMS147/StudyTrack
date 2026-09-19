package com.studytrack.app.profile

import android.content.Intent
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studytrack.app.BuildConfig
import com.studytrack.app.R
import com.studytrack.app.databinding.DialogEditProfileBinding
import com.studytrack.app.data.remote.RetrofitClient
import com.studytrack.app.databinding.FragmentProfileBinding
import com.studytrack.app.util.toast
import kotlinx.coroutines.launch

/**
 * Profile & Settings — opened from the Home avatar and closed with its back
 * arrow. Holds the student's details, headline stats and every preference.
 */
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

        binding.backButton.setOnClickListener { findNavController().navigateUp() }

        // The Subjects tab was replaced by Tasks in the design, so the
        // subject count is the way into the subject list.
        binding.statSubjectsCard.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_subjectsFragment)
        }

        binding.editPersonalButton.setOnClickListener { showEditPersonalDialog() }
        binding.editAvatarButton.setOnClickListener { showEditPersonalDialog() }

        wireSwitches()
        wireActions()

        binding.footerText.text =
            getString(R.string.profile_footer_format, BuildConfig.VERSION_NAME)
        binding.versionValue.text = BuildConfig.VERSION_NAME
        binding.apiEndpointValue.text = RetrofitClient.BASE_URL.ifBlank {
            getString(R.string.local_mode_label)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state -> renderState(state) }
                }
                launch {
                    viewModel.messages.collect { message ->
                        message?.let {
                            toast(it)
                            viewModel.clearMessage()
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ wiring

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

    private fun wireActions() {
        binding.changePasswordRow.setOnClickListener { confirmPasswordReset() }
        binding.exportDataRow.setOnClickListener { shareExport() }
        binding.helpSupportRow.setOnClickListener { showHelpDialog() }
        binding.privacyPolicyRow.setOnClickListener { showPrivacyDialog() }
        binding.logoutButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setPositiveButton(R.string.action_sign_out) { _, _ -> viewModel.logout() }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    // ------------------------------------------------------------------ render

    private fun renderState(state: ProfileUiState) {
        val personal = state.personal

        binding.profileAvatar.text = state.initials
        binding.profileName.text = personal.fullName.ifBlank {
            getString(R.string.greeting_fallback_name)
        }
        binding.profileStudentId.text = personal.studentId.ifBlank {
            getString(R.string.profile_value_empty)
        }

        binding.levelPill.text = getString(R.string.profile_level_pill_format, state.level)
        binding.xpPill.text = getString(R.string.profile_xp_pill_format, state.points)

        binding.statTasksDone.text = state.tasksDone.toString()
        binding.statSubjects.text = state.subjectCount.toString()
        binding.statStreak.text = getString(R.string.profile_streak_format, state.streak)

        binding.valueFullName.text = personal.fullName.ifBlank {
            getString(R.string.profile_value_empty)
        }
        binding.valueEmail.text = personal.email.ifBlank {
            getString(R.string.profile_value_empty)
        }
        binding.valueStudentId.text = personal.studentId.ifBlank {
            getString(R.string.profile_value_empty)
        }
        binding.valueInstitution.text = personal.institution.ifBlank {
            getString(R.string.profile_value_empty)
        }
        binding.valueCourse.text = personal.course.ifBlank {
            getString(R.string.profile_value_empty)
        }
        binding.valueYearOfStudy.text = personal.yearOfStudy.ifBlank {
            getString(R.string.profile_value_empty)
        }

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

    // ----------------------------------------------------------------- dialogs

    private fun showEditPersonalDialog() {
        val dialogBinding = DialogEditProfileBinding.inflate(layoutInflater)
        val personal = viewModel.state.value.personal
        dialogBinding.fullNameField.setText(personal.fullName)
        dialogBinding.studentIdField.setText(personal.studentId)
        dialogBinding.institutionField.setText(personal.institution)
        dialogBinding.courseField.setText(personal.course)
        dialogBinding.yearField.setText(personal.yearOfStudy)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_profile_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                viewModel.savePersonalInfo(
                    fullName = dialogBinding.fullNameField.text.toString(),
                    studentId = dialogBinding.studentIdField.text.toString(),
                    institution = dialogBinding.institutionField.text.toString(),
                    course = dialogBinding.courseField.text.toString(),
                    yearOfStudy = dialogBinding.yearField.text.toString(),
                )
            }
            .show()
    }

    private fun confirmPasswordReset() {
        val email = viewModel.state.value.personal.email
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_change_password)
            .setMessage(getString(R.string.password_reset_confirm, email))
            .setPositiveButton(R.string.action_send_link) { _, _ -> viewModel.sendPasswordReset() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** Shares the JSON export through any app that accepts text. */
    private fun shareExport() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_subject))
            putExtra(Intent.EXTRA_TEXT, viewModel.buildExportJson())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.action_export_data)))
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_help_support)
            .setMessage(R.string.help_support_body)
            .setPositiveButton(R.string.action_email_support) { _, _ ->
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = android.net.Uri.parse("mailto:$SUPPORT_EMAIL")
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.help_support_subject))
                }
                runCatching { startActivity(intent) }
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }

    private fun showPrivacyDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_privacy_policy)
            .setMessage(R.string.privacy_policy_body)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    /** True when the app is currently rendering the dark palette. */
    private fun isNightModeActive(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val SUPPORT_EMAIL = "support@studytrack.app"
    }
}
