package com.studytrack.app.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButtonToggleGroup
import com.studytrack.app.R
import com.studytrack.app.databinding.FragmentRegisterBinding
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.InputValidators
import com.studytrack.app.util.hideKeyboard
import kotlinx.coroutines.launch

/**
 * Create Account as a three-step wizard — Personal, Academic, Security — with
 * the design's stepper. All three steps are sections of this one screen (no
 * fragment per step), so the entered details simply stay in their fields as the
 * user moves back and forth.
 *
 * The account itself is created at the end of step 3; the academic details have
 * no Firebase equivalent and are stored with the rest of the profile.
 */
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels { AuthViewModel.FACTORY }

    private var step = 1
    private var yearOfStudy: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backButton.setOnClickListener {
            if (step > 1) showStep(step - 1) else findNavController().navigateUp()
        }

        binding.continuePersonalButton.setOnClickListener {
            hideKeyboard()
            if (validatePersonalStep()) showStep(2)
        }

        binding.continueAcademicButton.setOnClickListener {
            hideKeyboard()
            if (validateAcademicStep()) showStep(3)
        }

        binding.createAccountButton.setOnClickListener {
            hideKeyboard()
            if (validateSecurityStep()) {
                viewModel.register(
                    name = binding.nameField.text.toString().trim(),
                    email = binding.emailField.text.toString().trim(),
                    password = binding.passwordField.text.toString(),
                    studentId = binding.studentIdField.text.toString().trim(),
                    institution = binding.institutionField.text.toString().trim(),
                    course = binding.courseField.text.toString().trim(),
                    yearOfStudy = yearOfStudy.orEmpty(),
                )
            }
        }

        binding.loginLink.setOnClickListener {
            findNavController().navigate(RegisterFragmentDirections.actionRegisterFragmentToLoginFragment())
        }

        // Year of study: the two toggle rows act as one choice, so picking in
        // one row clears the other (MaterialButtonToggleGroup is per-row only).
        binding.yearGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                clearOtherYearGroup(group)
                yearOfStudy = yearLabel(checkedId)
            }
        }
        binding.yearGroup2.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                clearOtherYearGroup(group)
                yearOfStudy = yearLabel(checkedId)
            }
        }

        // Live password checklist.
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) = updateChecklist()
        }
        binding.passwordField.addTextChangedListener(watcher)
        binding.confirmField.addTextChangedListener(watcher)

        updateChecklist()
        showStep(1)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.registerState.collect { state -> renderState(state) }
            }
        }
    }

    // ------------------------------------------------------------------ steps

    private fun showStep(target: Int) {
        step = target
        binding.stepPersonal.isVisible = target == 1
        binding.stepAcademic.isVisible = target == 2
        binding.stepSecurity.isVisible = target == 3
        binding.signInRow.isVisible = target == 1
        binding.stepSubtitle.text = getString(R.string.register_step_format, target)

        val activeColor = ContextCompat.getColor(requireContext(), R.color.brand_primary)
        val doneColor = ContextCompat.getColor(requireContext(), R.color.success)
        val pendingColor = ContextCompat.getColor(requireContext(), R.color.divider)
        val mutedText = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        val bars = listOf(binding.stepBar1, binding.stepBar2, binding.stepBar3)
        val labels = listOf(binding.stepLabel1, binding.stepLabel2, binding.stepLabel3)
        bars.forEachIndexed { index, bar ->
            bar.setBackgroundColor(
                when {
                    index + 1 < target -> doneColor
                    index + 1 == target -> activeColor
                    else -> pendingColor
                }
            )
        }
        labels.forEachIndexed { index, label ->
            val isDone = index + 1 < target
            val isActive = index + 1 == target
            label.setTextColor(if (isActive) activeColor else if (isDone) doneColor else mutedText)
        }
        binding.stepLabel1.text = if (target > 1) {
            getString(R.string.register_step_done_format, getString(R.string.register_step_personal))
        } else {
            getString(R.string.register_step_personal)
        }
        binding.stepLabel2.text = if (target > 2) {
            getString(R.string.register_step_done_format, getString(R.string.register_step_academic))
        } else {
            getString(R.string.register_step_academic)
        }
        binding.stepLabel3.text = getString(R.string.register_step_security)
    }

    // ------------------------------------------------------------- validation

    private fun validatePersonalStep(): Boolean {
        var valid = true

        if (binding.nameField.text.toString().trim().isEmpty()) {
            binding.nameLayout.error = getString(R.string.error_name_required)
            valid = false
        } else {
            binding.nameLayout.error = null
        }

        if (!InputValidators.isValidEmail(binding.emailField.text.toString().trim())) {
            binding.emailLayout.error = getString(R.string.error_email_invalid)
            valid = false
        } else {
            binding.emailLayout.error = null
        }

        if (binding.studentIdField.text.toString().trim().isEmpty()) {
            binding.studentIdLayout.error = getString(R.string.error_student_id_required)
            valid = false
        } else {
            binding.studentIdLayout.error = null
        }

        return valid
    }

    private fun validateAcademicStep(): Boolean {
        var valid = true

        if (binding.institutionField.text.toString().trim().isEmpty()) {
            binding.institutionLayout.error = getString(R.string.error_institution_required)
            valid = false
        } else {
            binding.institutionLayout.error = null
        }

        if (binding.courseField.text.toString().trim().isEmpty()) {
            binding.courseLayout.error = getString(R.string.error_course_required)
            valid = false
        } else {
            binding.courseLayout.error = null
        }

        if (yearOfStudy.isNullOrBlank()) {
            binding.continueAcademicButton.text = getString(R.string.error_year_required)
            valid = false
        } else {
            binding.continueAcademicButton.text = getString(R.string.action_continue_arrow)
        }

        return valid
    }

    private fun validateSecurityStep(): Boolean {
        val password = binding.passwordField.text.toString()
        val confirm = binding.confirmField.text.toString()
        updateChecklist()

        if (!InputValidators.isPasswordValid(password)) {
            binding.passwordLayout.error = getString(R.string.error_password_short)
            return false
        }
        if (!InputValidators.containsNumber(password)) {
            binding.passwordLayout.error = getString(R.string.error_password_needs_number)
            return false
        }
        binding.passwordLayout.error = null

        if (confirm != password) {
            binding.confirmLayout.error = getString(R.string.error_password_mismatch)
            return false
        }
        binding.confirmLayout.error = null
        return true
    }

    /** Ticks the three checklist dots and gates the Create Account button. */
    private fun updateChecklist() {
        val password = binding.passwordField.text.toString()
        val confirm = binding.confirmField.text.toString()
        val lengthOk = InputValidators.isPasswordValid(password)
        val numberOk = InputValidators.containsNumber(password)
        val matchOk = password.isNotEmpty() && password == confirm

        tick(binding.ruleLengthDot, lengthOk)
        tick(binding.ruleNumberDot, numberOk)
        tick(binding.ruleMatchDot, matchOk)

        binding.createAccountButton.isEnabled = lengthOk && numberOk && matchOk
        binding.createAccountButton.alpha = if (binding.createAccountButton.isEnabled) 1f else 0.5f
    }

    private fun tick(dot: View, satisfied: Boolean) {
        dot.setBackgroundResource(
            if (satisfied) R.drawable.bg_step_dot_done else R.drawable.bg_step_dot_empty
        )
    }

    // ------------------------------------------------------------ year group

    private fun clearOtherYearGroup(active: MaterialButtonToggleGroup) {
        val other = if (active === binding.yearGroup) binding.yearGroup2 else binding.yearGroup
        if (other.checkedButtonId != View.NO_ID) other.clearChecked()
    }

    private fun yearLabel(checkedId: Int): String = getString(
        when (checkedId) {
            R.id.year1 -> R.string.year_1
            R.id.year2 -> R.string.year_2
            R.id.year3 -> R.string.year_3
            R.id.year4 -> R.string.year_4
            R.id.yearHonours -> R.string.year_honours
            else -> R.string.year_masters
        }
    )

    // ----------------------------------------------------------------- render

    private fun renderState(state: ApiResult<Unit>?) {
        val loading = state is ApiResult.Loading
        binding.registerProgress.isVisible = loading
        binding.continuePersonalButton.isEnabled = !loading
        binding.continueAcademicButton.isEnabled = !loading

        if (state is ApiResult.Error) {
            binding.errorText.text = state.message
            binding.errorText.isVisible = true
        } else {
            binding.errorText.isVisible = false
        }
        // The Create Account button is gated by the checklist, not by loading.
        updateChecklist()
        if (loading) binding.createAccountButton.isEnabled = false
        // On Success: no navigation here — MainActivity's FirebaseAuth
        // AuthStateListener routes to the dashboard (single navigation path).
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
