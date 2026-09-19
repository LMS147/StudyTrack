package com.studytrack.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.studytrack.app.data.repository.SettingsRepository

/**
 * Application entry point. Initializes the service locator (manual DI container)
 * so ViewModels and repositories can resolve their dependencies, then applies
 * the saved theme choice.
 */
class StudyTrackApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        applySavedThemeMode()
    }

    /**
     * Applies the light/dark choice saved on the Profile screen *before* any
     * Activity is created, so the very first frame already uses the right
     * colours (no light flash on launch). Nothing saved yet = follow the
     * system theme, which is also what a fresh install gets.
     */
    private fun applySavedThemeMode() {
        AppCompatDelegate.setDefaultNightMode(
            when (ServiceLocator.settingsRepository.darkMode) {
                SettingsRepository.DARK_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                SettingsRepository.DARK_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }
}
