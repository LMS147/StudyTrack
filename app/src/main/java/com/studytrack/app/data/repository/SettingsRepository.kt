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

    private companion object {
        const val KEY_TASK_REMINDERS = "task_reminders_enabled"
        const val KEY_STREAK_REMINDERS = "streak_reminders_enabled"
        const val KEY_SHOW_AI_CARD = "show_ai_card_on_dashboard"
        const val KEY_DEFAULT_AI_PRIORITY = "default_ai_priority"
        const val KEY_DARK_MODE = "dark_mode"
    }

    companion object {
        /** Follow the phone's system theme (first-launch default). */
        const val DARK_MODE_SYSTEM = "system"

        /** Force light, regardless of the system theme. */
        const val DARK_MODE_LIGHT = "light"

        /** Force dark, regardless of the system theme. */
        const val DARK_MODE_DARK = "dark"
    }
}
