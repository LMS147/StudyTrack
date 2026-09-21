package com.studytrack.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live "do we have usable connectivity?" signal.
 *
 * Two consumers need it:
 *
 * - [com.studytrack.app.auth.AccountSwitchGuard], which refuses an account
 *   switch when offline and no cache exists for that account.
 * - The repositories, which decide whether a write can be pushed now or must
 *   sit in the queue.
 *
 * Connectivity is treated as *validated* internet, not merely "a network
 * interface exists" — a Wi-Fi captive portal or an airplane-mode radio reports
 * a network but cannot reach the API, and misreading that would make the app
 * claim a sync it cannot perform.
 */
class ConnectivityMonitor(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager

    private val _isOnline = MutableStateFlow(readCurrentState())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onLost(network: Network) = publish()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = publish()

        private fun publish() {
            _isOnline.value = readCurrentState()
        }
    }

    private var registered = false

    /** Idempotent; called once from the Application. */
    fun start() {
        if (registered) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { connectivityManager.registerNetworkCallback(request, callback) }
            .onSuccess { registered = true }
        _isOnline.value = readCurrentState()
    }

    fun stop() {
        if (!registered) return
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        registered = false
    }

    private fun readCurrentState(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
