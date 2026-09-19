package com.studytrack.app.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.repository.AuthRepository
import com.studytrack.app.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProfileUiState(
    val loading: Boolean = true,
    val displayName: String = "",
    val email: String = "",
    val taskReminders: Boolean = true,
    val streakReminders: Boolean = true,
    val showAiCard: Boolean = true,
    val defaultAiPriority: String = "Medium",
    val pomodoroSound: Boolean = true,
)

/**
 * Profile screen: account summary, notification preferences, AI preferences
 * (persisted via [SettingsRepository]) and the logout action. Sign-out is
 * observed centrally by MainActivity's auth-state listener.
 */
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val user = authRepository.currentUser
        val fallbackName = user?.email
            ?.substringBefore('@')
            ?.replaceFirstChar { it.uppercaseChar() }
            .orEmpty()
        _state.update {
            it.copy(
                loading = false,
                displayName = authRepository.displayName().ifBlank { fallbackName },
                email = user?.email.orEmpty(),
                taskReminders = settingsRepository.taskRemindersEnabled,
                streakReminders = settingsRepository.streakRemindersEnabled,
                showAiCard = settingsRepository.showAiCardOnDashboard,
                defaultAiPriority = settingsRepository.defaultAiPriority,
                pomodoroSound = settingsRepository.pomodoroSoundEnabled,
            )
        }
    }

    fun setTaskReminders(enabled: Boolean) {
        settingsRepository.taskRemindersEnabled = enabled
        _state.update { it.copy(taskReminders = enabled) }
    }

    fun setStreakReminders(enabled: Boolean) {
        settingsRepository.streakRemindersEnabled = enabled
        _state.update { it.copy(streakReminders = enabled) }
    }

    fun setShowAiCard(enabled: Boolean) {
        settingsRepository.showAiCardOnDashboard = enabled
        _state.update { it.copy(showAiCard = enabled) }
    }

    fun setDefaultAiPriority(raw: String) {
        settingsRepository.defaultAiPriority = raw
        _state.update { it.copy(defaultAiPriority = raw) }
    }

    fun setPomodoroSound(enabled: Boolean) {
        settingsRepository.pomodoroSoundEnabled = enabled
        _state.update { it.copy(pomodoroSound = enabled) }
    }

    /**
     * Persists an explicit light/dark choice. The caller (ProfileFragment) is
     * responsible for switching the app theme via AppCompatDelegate — the
     * ViewModel only owns the preference, and "follow the system" stays the
     * state until the user touches this switch.
     */
    fun setDarkMode(enabled: Boolean) {
        settingsRepository.darkMode = if (enabled) {
            SettingsRepository.DARK_MODE_DARK
        } else {
            SettingsRepository.DARK_MODE_LIGHT
        }
    }

    /** MainActivity's auth-state listener navigates back to the login screen. */
    fun logout() {
        authRepository.signOut()
    }

    companion object {
        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProfileViewModel(
                    ServiceLocator.authRepository,
                    ServiceLocator.settingsRepository,
                )
            }
        }
    }
}
