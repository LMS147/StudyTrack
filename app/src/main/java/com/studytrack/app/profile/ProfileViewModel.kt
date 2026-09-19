package com.studytrack.app.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.studytrack.app.ServiceLocator
import com.studytrack.app.data.repository.AuthRepository
import com.studytrack.app.data.repository.ProgressRepository
import com.studytrack.app.data.repository.SettingsRepository
import com.studytrack.app.data.repository.SubjectRepository
import com.studytrack.app.data.repository.TaskRepository
import com.studytrack.app.util.ApiResult
import com.studytrack.app.util.Levels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editable personal details, as shown in the PERSONAL INFORMATION card. */
data class PersonalInfo(
    val fullName: String = "",
    val email: String = "",
    val studentId: String = "",
    val institution: String = "",
    val course: String = "",
    val yearOfStudy: String = "",
)

data class ProfileUiState(
    val loading: Boolean = true,
    val personal: PersonalInfo = PersonalInfo(),
    val points: Int = 0,
    val level: Int = 1,
    val tasksDone: Int = 0,
    val subjectCount: Int = 0,
    val streak: Int = 0,
    // Notification preferences
    val pushNotifications: Boolean = true,
    val deadlineReminders: Boolean = true,
    val dailyDigest: Boolean = true,
    val weeklyReport: Boolean = true,
    // AI preferences
    val showAiCard: Boolean = true,
    val defaultAiPriority: String = "Medium",
    // App preferences
    val pomodoroSound: Boolean = true,
) {
    val initials: String
        get() = personal.fullName.trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "?" }
}

/**
 * Profile & Settings: the student's details (name/email from Firebase, the
 * academic fields stored on-device), the headline stats, and every preference
 * the app has — notifications, AI, theme and the Pomodoro sound.
 */
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository,
    private val taskRepository: TaskRepository,
    private val subjectRepository: SubjectRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    /** One-shot messages for the screen to surface (toasts/dialogs). */
    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    init {
        loadPreferences()
        refreshStats()
    }

    private fun loadPreferences() {
        val user = authRepository.currentUser
        val fallbackName = user?.email
            ?.substringBefore('@')
            ?.replaceFirstChar { it.uppercaseChar() }
            .orEmpty()
        _state.update {
            it.copy(
                personal = PersonalInfo(
                    fullName = authRepository.displayName().ifBlank { fallbackName },
                    email = user?.email.orEmpty(),
                    studentId = settingsRepository.studentId,
                    institution = settingsRepository.institution,
                    course = settingsRepository.course,
                    yearOfStudy = settingsRepository.yearOfStudy,
                ),
                pushNotifications = settingsRepository.pushNotificationsEnabled,
                deadlineReminders = settingsRepository.taskRemindersEnabled,
                dailyDigest = settingsRepository.dailyDigestEnabled,
                weeklyReport = settingsRepository.weeklyReportEnabled,
                showAiCard = settingsRepository.showAiCardOnDashboard,
                defaultAiPriority = settingsRepository.defaultAiPriority,
                pomodoroSound = settingsRepository.pomodoroSoundEnabled,
            )
        }
    }

    /** Headline stats: tasks done, subject count, streak, XP/level. */
    private fun refreshStats() {
        viewModelScope.launch {
            val tasksResult = taskRepository.refresh()
            val subjectsResult = subjectRepository.refresh()
            val progressResult = progressRepository.getProgress()

            val tasks = (tasksResult as? ApiResult.Success)?.data.orEmpty()
            val subjects = (subjectsResult as? ApiResult.Success)?.data.orEmpty()
            val progress = (progressResult as? ApiResult.Success)?.data
            val points = tasks.filter { it.completed }.sumOf { it.points }

            _state.update {
                it.copy(
                    loading = false,
                    tasksDone = tasks.count { task -> task.completed },
                    subjectCount = subjects.size,
                    streak = progress?.currentStreak ?: fallbackStreak(tasks),
                    points = points,
                    level = Levels.levelFor(points),
                )
            }
        }
    }

    /**
     * Streak fallback for when the progress endpoint is unavailable: counts
     * consecutive days ending today (or yesterday, so a streak stays alive
     * until the day is over) with at least one task completed.
     */
    private fun fallbackStreak(tasks: List<com.studytrack.app.data.model.Task>): Int {
        val days = tasks.filter { it.completed }
            .mapNotNull { com.studytrack.app.util.DateTimeUtils.parseDate(it.completedAt) }
            .toSet()
        if (days.isEmpty()) return 0
        var cursor = java.time.LocalDate.now()
        if (cursor !in days) cursor = cursor.minusDays(1)
        var streak = 0
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun refresh() = refreshStats()

    fun savePersonalInfo(
        fullName: String,
        studentId: String,
        institution: String,
        course: String,
        yearOfStudy: String,
    ) {
        settingsRepository.studentId = studentId.trim()
        settingsRepository.institution = institution.trim()
        settingsRepository.course = course.trim()
        settingsRepository.yearOfStudy = yearOfStudy.trim()
        viewModelScope.launch {
            if (fullName.isNotBlank() && fullName != _state.value.personal.fullName) {
                authRepository.updateDisplayName(fullName.trim())
            }
            loadPreferences()
            _messages.value = "Profile updated"
        }
    }

    // ------------------------------------------------------------ preferences

    fun setPushNotifications(enabled: Boolean) {
        settingsRepository.pushNotificationsEnabled = enabled
        _state.update { it.copy(pushNotifications = enabled) }
    }

    fun setDeadlineReminders(enabled: Boolean) {
        settingsRepository.taskRemindersEnabled = enabled
        _state.update { it.copy(deadlineReminders = enabled) }
    }

    fun setDailyDigest(enabled: Boolean) {
        settingsRepository.dailyDigestEnabled = enabled
        _state.update { it.copy(dailyDigest = enabled) }
    }

    fun setWeeklyReport(enabled: Boolean) {
        settingsRepository.weeklyReportEnabled = enabled
        _state.update { it.copy(weeklyReport = enabled) }
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

    // ----------------------------------------------------------------- actions

    /** Sends a Firebase password-reset email to the signed-in address. */
    fun sendPasswordReset() {
        val email = _state.value.personal.email
        if (email.isBlank()) {
            _messages.value = "No email address on this account."
            return
        }
        viewModelScope.launch {
            _messages.value = when (val result = authRepository.sendPasswordReset(email)) {
                is ApiResult.Success -> "Password reset link sent to $email"
                is ApiResult.Error -> result.message
                ApiResult.Loading -> null
            }
        }
    }

    /**
     * Everything the app stores about the student as pretty-printed JSON —
     * the same payload shape the local store persists, so it can be read
     * (or re-imported) later.
     */
    fun buildExportJson(): String {
        val subjects = subjectRepository.subjects.value
        val tasks = taskRepository.tasks.value
        val personal = _state.value.personal
        val json = com.studytrack.app.data.remote.RetrofitClient.json
        return buildString {
            appendLine("{")
            appendLine("  \"exportedAt\": \"${com.studytrack.app.util.DateTimeUtils.todayIsoDate()}\",")
            appendLine("  \"profile\": {")
            appendLine("    \"fullName\": \"${personal.fullName}\",")
            appendLine("    \"email\": \"${personal.email}\",")
            appendLine("    \"studentId\": \"${personal.studentId}\",")
            appendLine("    \"institution\": \"${personal.institution}\",")
            appendLine("    \"course\": \"${personal.course}\",")
            appendLine("    \"yearOfStudy\": \"${personal.yearOfStudy}\"")
            appendLine("  },")
            appendLine("  \"points\": ${_state.value.points},")
            runCatching {
                appendLine(
                    "  \"subjects\": " +
                        json.encodeToString(
                            kotlinx.serialization.builtins.ListSerializer(
                                com.studytrack.app.data.model.Subject.serializer()
                            ),
                            subjects,
                        ) + ","
                )
                appendLine(
                    "  \"tasks\": " +
                        json.encodeToString(
                            kotlinx.serialization.builtins.ListSerializer(
                                com.studytrack.app.data.model.Task.serializer()
                            ),
                            tasks,
                        )
                )
            }.onFailure { appendLine("  \"error\": \"could not serialize tasks\"") }
            appendLine("}")
        }
    }

    /** Called once a one-shot message has been shown. */
    fun clearMessage() {
        _messages.value = null
    }

    fun logout() {
        authRepository.signOut()
    }

    companion object {
        val FACTORY: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProfileViewModel(
                    ServiceLocator.authRepository,
                    ServiceLocator.settingsRepository,
                    ServiceLocator.taskRepository,
                    ServiceLocator.subjectRepository,
                    ServiceLocator.progressRepository,
                )
            }
        }
    }
}
