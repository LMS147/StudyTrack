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

    private companion object {
        const val KEY_TASK_REMINDERS = "task_reminders_enabled"
        const val KEY_STREAK_REMINDERS = "streak_reminders_enabled"
        const val KEY_SHOW_AI_CARD = "show_ai_card_on_dashboard"
        const val KEY_DEFAULT_AI_PRIORITY = "default_ai_priority"
    }
}
