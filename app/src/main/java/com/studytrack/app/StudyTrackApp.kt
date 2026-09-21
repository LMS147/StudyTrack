package com.studytrack.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.auth.FirebaseAuth
import com.studytrack.app.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application entry point. Initializes the service locator (manual DI container)
 * so ViewModels and repositories can resolve their dependencies, then applies
 * the saved theme choice.
 *
 * Also starts the two process-wide services the offline layer needs: the
 * connectivity monitor (which drives the sync queue and the offline
 * account-switch guard) and restoration of the persisted Firebase session into
 * [com.studytrack.app.auth.CurrentAccount], so the very first query is already
 * scoped to the right account instead of running before anyone knows who is
 * signed in.
 */
class StudyTrackApp : Application() {

    /**
     * Application-lifetime scope. Deliberately `SupervisorJob` + `Main`: a
     * failed session restore must not cancel the connectivity listener, and
     * `FirebaseAuth` callbacks are main-thread bound.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        applySavedThemeMode()

        ServiceLocator.connectivityMonitor.start()
        restorePersistedSession()
    }

    /**
     * Points the active-session holder at the account Firebase already restored
     * from its own persisted credentials — before any screen queries Room.
     *
     * Without this, the first repository call after a cold start would hit
     * `CurrentAccount.requireUid()` with no session set and throw, or (worse, if
     * it had a fallback) run unscoped.
     */
    private fun restorePersistedSession() {
        val user = FirebaseAuth.getInstance().currentUser
        appScope.launch {
            ServiceLocator.accountSessionManager.restoreSession(
                ownerUid = user?.uid,
                email = user?.email,
                displayName = user?.displayName,
            )
            if (user != null) {
                // Pick up anything queued while the app was closed.
                ServiceLocator.syncScheduler.requestSync()
            }
        }
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
