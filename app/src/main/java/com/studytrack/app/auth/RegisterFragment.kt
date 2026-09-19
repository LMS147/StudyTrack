package com.studytrack.app.auth

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
import androidx.navigation.fragment.findNavController
import com.studytrack.app.R
import com.studytrack.app.databinding.FragmentRegisterBinding
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.InputValidators
import com.studytrack.app.util.hideKeyboard
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels { AuthViewModel.FACTORY }

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

        binding.registerButton.setOnClickListener {
            hideKeyboard()
            if (validate()) {
                viewModel.register(
                    name = binding.nameField.text.toString().trim(),
                    email = binding.emailField.text.toString().trim(),
                    password = binding.passwordField.text.toString()
                )
            }
        }

        binding.loginLink.setOnClickListener {
            findNavController().navigate(RegisterFragmentDirections.actionRegisterFragmentToLoginFragment())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.registerState.collect { state -> renderState(state) }
            }
        }
    }

    private fun validate(): Boolean {
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

        val password = binding.passwordField.text.toString()
        if (!InputValidators.isPasswordValid(password)) {
            binding.passwordLayout.error = getString(R.string.error_password_short)
            valid = false
        } else {
            binding.passwordLayout.error = null
        }

        if (binding.confirmField.text.toString() != password) {
            binding.confirmLayout.error = getString(R.string.error_password_mismatch)
            valid = false
        } else {
            binding.confirmLayout.error = null
        }

        return valid
    }

    private fun renderState(state: ApiResult<Unit>?) {
        val loading = state is ApiResult.Loading
        binding.registerProgress.isVisible = loading
        binding.registerButton.isEnabled = !loading
        binding.nameLayout.isEnabled = !loading
        binding.emailLayout.isEnabled = !loading
        binding.passwordLayout.isEnabled = !loading
        binding.confirmLayout.isEnabled = !loading

        if (state is ApiResult.Error) {
            binding.errorText.text = state.message
            binding.errorText.isVisible = true
        } else {
            binding.errorText.isVisible = false
        }
        // On Success: no navigation here — MainActivity's FirebaseAuth
        // AuthStateListener routes to the dashboard (single navigation path).
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
