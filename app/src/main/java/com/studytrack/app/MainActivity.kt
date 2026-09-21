package com.studytrack.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.auth.FirebaseAuth
import com.studytrack.app.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    /** Scope for moving the account session before navigation. */
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The five bottom-nav tabs (Home, Tasks, Calendar, Progress, AI). Profile
     * & Settings is deliberately NOT in this set: it is pushed from the Home
     * avatar and closes with its back arrow, like the reference design.
     */
    private val topLevelDestinations = setOf(
        R.id.dashboardFragment,
        R.id.tasksFragment,
        R.id.calendarFragment,
        R.id.progressFragment,
        R.id.aiAssistantFragment
    )

    /**
     * Central auth routing: any sign-in lands on the dashboard, any sign-out on
     * the login screen. Fragments never navigate on auth events themselves, so
     * there is exactly one place that owns these transitions.
     *
     * The account session is moved **before** navigation, so no screen can
     * query Room while the active UID still points at the previous account.
     */
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        activityScope.launch {
            if (user != null) {
                ServiceLocator.accountSessionManager.onSignedIn(
                    ownerUid = user.uid,
                    email = user.email,
                    displayName = user.displayName,
                )
            } else {
                // Clears the pointer only — cached rows for every account stay
                // in Room so a returning account is restored instantly.
                ServiceLocator.accountSessionManager.onSignedOut()
            }
            routeForAuthState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHost.navController

        // Conditional start destination based on the Firebase session.
        // The graph is set programmatically (not via app:navGraph) so it is
        // inflated only once with the right entry point.
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(
            if (ServiceLocator.authRepository.isUserLoggedIn()) R.id.dashboardFragment
            else R.id.loginFragment
        )
        navController.setGraph(graph, null)

        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Bottom nav is only visible on the six top-level destinations
            // (hidden on login/register and on pushed detail/editor screens).
            binding.bottomNav.isVisible = destination.id in topLevelDestinations
        }
    }

    override fun onStart() {
        super.onStart()
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
    }

    override fun onDestroy() {
        activityScope.cancel()
        super.onDestroy()
    }

    private fun routeForAuthState() {
        if (!::navController.isInitialized) return
        val loggedIn = ServiceLocator.authRepository.isUserLoggedIn()
        val currentId = navController.currentDestination?.id ?: return
        val onAuthScreen =
            currentId == R.id.loginFragment || currentId == R.id.registerFragment
        try {
            if (loggedIn && onAuthScreen) {
                navController.navigate(R.id.action_global_dashboardFragment)
            } else if (!loggedIn && !onAuthScreen) {
                navController.navigate(R.id.action_global_loginFragment)
            }
        } catch (e: IllegalStateException) {
            // Navigation can transiently reject calls mid-transition (e.g. an
            // auth callback racing a fragment transaction); the next auth
            // state change re-routes, so this is safe to swallow.
        } catch (e: IllegalArgumentException) {
            // Destination not resolvable from the current state — same as above.
        }
    }
}
