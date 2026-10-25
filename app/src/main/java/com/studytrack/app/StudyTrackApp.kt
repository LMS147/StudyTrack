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
     * from its own persisted credentials.
     *
     * ## The UID is set synchronously, on purpose
     *
     * `FirebaseAuth.currentUser` is available immediately — Firebase restores it
     * from disk before `Application.onCreate` runs. The Room-dependent work
     * (upserting the account record, importing legacy rows) is genuinely async,
     * and it used to be that the UID was set only *after* that completed. That
     * produced a crash on cold start: `MainActivity` saw a logged-in user and
     * routed to the Dashboard, whose `refresh()` called `requireUid()` before
     * the coroutine had finished, and the app died with "No active StudyTrack
     * session".
     *
     * So the pointer moves here, synchronously, and only the bookkeeping is
     * deferred. No screen can ever query Room before the UID is known.
     */
    private fun restorePersistedSession() {
        val user = FirebaseAuth.getInstance().currentUser

        // Synchronous: this must be true before onCreate returns.
        ServiceLocator.currentAccount.setSession(user?.uid)

        if (user == null) return

        appScope.launch {
            // Ensures the account record exists so this device can recognize
            // the account later (offline sign-in, instant cache restore).
            ServiceLocator.accountSessionManager.restoreSession(
                ownerUid = user.uid,
                email = user.email,
                displayName = user.displayName,
            )
            // Pick up anything queued while the app was closed.
            ServiceLocator.syncScheduler.requestSync()
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
