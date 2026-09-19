package com.studytrack.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.repository.AuthRepository
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
class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _loginState = MutableStateFlow<ApiResult<Unit>?>(null)
    val loginState: StateFlow<ApiResult<Unit>?> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow<ApiResult<Unit>?>(null)
    val registerState: StateFlow<ApiResult<Unit>?> = _registerState.asStateFlow()

    fun login(email: String, password: String) {
        if (_loginState.value == ApiResult.Loading) return
        viewModelScope.launch {
            _loginState.value = ApiResult.Loading
            _loginState.value = authRepository.login(email, password)
        }
    }

    fun register(name: String, email: String, password: String) {
        if (_registerState.value == ApiResult.Loading) return
        viewModelScope.launch {
            _registerState.value = ApiResult.Loading
            _registerState.value = authRepository.register(name, email, password)
        }
    }

    companion object {
        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(ServiceLocator.authRepository) }
        }
    }
}
