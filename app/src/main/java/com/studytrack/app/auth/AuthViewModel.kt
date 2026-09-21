package com.studytrack.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.repository.AuthRepository
import com.studytrack.app.data.repository.SettingsRepository
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds login/registration call state for the auth screens.
 *
 * On success the fragment does not navigate itself — MainActivity observes the
 * FirebaseAuth state and routes to the dashboard, so there is exactly one
 * navigation path for "user just signed in".
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _loginState = MutableStateFlow<ApiResult<Unit>?>(null)
    val loginState: StateFlow<ApiResult<Unit>?> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow<ApiResult<Unit>?>(null)
    val registerState: StateFlow<ApiResult<Unit>?> = _registerState.asStateFlow()

    /** Google sign-in runs through the same login state (it IS a sign-in). */
    fun login(email: String, password: String) {
        if (_loginState.value == ApiResult.Loading) return
        viewModelScope.launch {
            _loginState.value = ApiResult.Loading
            _loginState.value = authRepository.login(email, password)
        }
    }

    /**
     * Creates the account, then stores the academic details collected by
     * steps 1–2 of the wizard. Those fields have no Firebase equivalent, so
     * they live in [SettingsRepository] alongside the rest of the profile.
     */
    fun register(
        name: String,
        email: String,
        password: String,
        studentId: String = "",
        institution: String = "",
        course: String = "",
        yearOfStudy: String = "",
    ) {
        if (_registerState.value == ApiResult.Loading) return
        viewModelScope.launch {
            _registerState.value = ApiResult.Loading
            val result = authRepository.register(name, email, password)
            if (result is ApiResult.Success) {
                settingsRepository.studentId = studentId
                settingsRepository.institution = institution
                settingsRepository.course = course
                settingsRepository.yearOfStudy = yearOfStudy
            }
            _registerState.value = result
        }
    }

    /** Exchanges a Google ID token for a Firebase session. */
    fun signInWithGoogle(idToken: String) {
        if (_loginState.value == ApiResult.Loading) return
        viewModelScope.launch {
            _loginState.value = ApiResult.Loading
            _loginState.value = authRepository.signInWithGoogle(idToken)
        }
    }

    /** Emails a password-reset link; the message is shown on the login screen. */
    fun sendPasswordReset(email: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            onResult(
                when (val result = authRepository.sendPasswordReset(email)) {
                    is ApiResult.Success -> PASSWORD_RESET_SENT
                    is ApiResult.Error -> result.message
                    ApiResult.Loading -> PASSWORD_RESET_SENT
                }
            )
        }
    }

    /** Clears a finished call so returning to the screen starts clean. */
    fun clearLoginState() {
        _loginState.value = null
    }

    companion object {
        const val PASSWORD_RESET_SENT =
            "If that email has an account, a reset link is on its way."

        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AuthViewModel(
                    ServiceLocator.authRepository,
                    ServiceLocator.settingsRepository,
                )
            }
        }
    }
}
