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
import com.studytrack.app.R
import com.studytrack.app.databinding.FragmentLoginBinding
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.InputValidators
import com.studytrack.app.util.hideKeyboard
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AuthViewModel by viewModels { AuthViewModel.FACTORY }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginButton.setOnClickListener {
            hideKeyboard()
            if (validate()) {
                viewModel.login(
                    email = binding.emailField.text.toString().trim(),
                    password = binding.passwordField.text.toString()
                )
            }
        }

        // Note: navigation to the Register screen is wired in Step 3 once the
        // navigation graph exists.

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loginState.collect { state -> renderState(state) }
            }
        }
    }

    private fun validate(): Boolean {
        var valid = true

        if (!InputValidators.isValidEmail(binding.emailField.text.toString().trim())) {
            binding.emailLayout.error = getString(R.string.error_email_invalid)
            valid = false
        } else {
            binding.emailLayout.error = null
        }

        if (binding.passwordField.text.toString().isEmpty()) {
            binding.passwordLayout.error = getString(R.string.error_password_required)
            valid = false
        } else {
            binding.passwordLayout.error = null
        }

        return valid
    }

    private fun renderState(state: ApiResult<Unit>?) {
        val loading = state is ApiResult.Loading
        binding.loginProgress.isVisible = loading
        binding.loginButton.isEnabled = !loading
        binding.emailLayout.isEnabled = !loading
        binding.passwordLayout.isEnabled = !loading

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
