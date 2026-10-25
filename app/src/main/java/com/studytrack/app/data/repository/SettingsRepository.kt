package com.studytrack.app.data.repository

import android.content.Context

/**
 * Local app settings (SharedPreferences). Notification preferences and AI
 * preferences live entirely client-side for now; the reminder master switches
 * are ready to gate future WorkManager-based notifications.
 */
class SettingsRepository(context: Context) {

    private val prefs =
        context.getSharedPreferences("studytrack_settings", Context.MODE_PRIVATE)

    var taskRemindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_TASK_REMINDERS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_TASK_REMINDERS, value).apply()
        }

    var streakRemindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_STREAK_REMINDERS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_STREAK_REMINDERS, value).apply()
        }

    /** Whether the AI assistant shortcut card is shown on the Dashboard. */
    var showAiCardOnDashboard: Boolean
        get() = prefs.getBoolean(KEY_SHOW_AI_CARD, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_AI_CARD, value).apply()
        }

    /** Priority applied to AI suggestions that don't specify one ("Low|Medium|High"). */
    var defaultAiPriority: String
        get() = prefs.getString(KEY_DEFAULT_AI_PRIORITY, "Medium").orEmpty().ifBlank { "Medium" }
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_AI_PRIORITY, value).apply()
        }

    // ------------------------------------------------- personal information
    // Student details live on-device only (no backend field for them); the
    // name/email come from Firebase and are shown read-only alongside.

    var studentId: String
        get() = prefs.getString(KEY_STUDENT_ID, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_STUDENT_ID, value).apply()

    var institution: String
        get() = prefs.getString(KEY_INSTITUTION, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_INSTITUTION, value).apply()

    var course: String
        get() = prefs.getString(KEY_COURSE, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_COURSE, value).apply()

    var yearOfStudy: String
        get() = prefs.getString(KEY_YEAR_OF_STUDY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_YEAR_OF_STUDY, value).apply()

    // ------------------------------------------------------- notifications

    /** Master switch for push notifications. */
    var pushNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_PUSH_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_PUSH_NOTIFICATIONS, value).apply()

    /** Morning summary of the day's workload. */
    var dailyDigestEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAILY_DIGEST, true)
        set(value) = prefs.edit().putBoolean(KEY_DAILY_DIGEST, value).apply()

    /** Weekly progress summary. */
    var weeklyReportEnabled: Boolean
        get() = prefs.getBoolean(KEY_WEEKLY_REPORT, true)
        set(value) = prefs.edit().putBoolean(KEY_WEEKLY_REPORT, value).apply()

    /**
     * Pomodoro sound preference. The toggle exists (and its state is
     * remembered) ahead of the focus timer itself: the timer will read this
     * when it is built, so the choice already survives restarts.
     */
    var pomodoroSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_POMODORO_SOUND, true)
        set(value) {
            prefs.edit().putBoolean(KEY_POMODORO_SOUND, value).apply()
        }

    /**
     * Theme choice: [DARK_MODE_SYSTEM] (default, follows the phone), or an
     * explicit [DARK_MODE_LIGHT] / [DARK_MODE_DARK] picked on the Profile
     * screen. Stored as a string so "follow the system" stays expressible —
     * a plain boolean could not distinguish it from "explicitly light".
     * [com.studytrack.app.StudyTrackApp] applies it on every launch.
     */
    var darkMode: String
        get() = prefs.getString(KEY_DARK_MODE, DARK_MODE_SYSTEM).orEmpty()
            .ifBlank { DARK_MODE_SYSTEM }
        set(value) {
            prefs.edit().putString(KEY_DARK_MODE, value).apply()
        }

    companion object {
        /** Follow the phone's system theme (first-launch default). */
        const val DARK_MODE_SYSTEM = "system"

        /** Force light, regardless of the system theme. */
        const val DARK_MODE_LIGHT = "light"

        /** Force dark, regardless of the system theme. */
        const val DARK_MODE_DARK = "dark"

        private const val KEY_TASK_REMINDERS = "task_reminders_enabled"
        private const val KEY_STREAK_REMINDERS = "streak_reminders_enabled"
        private const val KEY_SHOW_AI_CARD = "show_ai_card_on_dashboard"
        private const val KEY_DEFAULT_AI_PRIORITY = "default_ai_priority"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_POMODORO_SOUND = "pomodoro_sound_enabled"
        private const val KEY_STUDENT_ID = "student_id"
        private const val KEY_INSTITUTION = "institution"
        private const val KEY_COURSE = "course"
        private const val KEY_YEAR_OF_STUDY = "year_of_study"
        private const val KEY_PUSH_NOTIFICATIONS = "push_notifications_enabled"
        private const val KEY_DAILY_DIGEST = "daily_digest_enabled"
        private const val KEY_WEEKLY_REPORT = "weekly_report_enabled"
    }
}
