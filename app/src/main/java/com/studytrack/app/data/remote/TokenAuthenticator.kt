package com.studytrack.app.data.remote

import com.studytrack.app.util.TokenManager
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * When the backend answers 401 (Firebase ID tokens live ~1 hour), forces a
 * token refresh and retries the request exactly once. Gives up if a retry
 * already happened or the user is signed out.
 */
class TokenAuthenticator : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null
        val freshToken = TokenManager.currentToken(forceRefresh = true) ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $freshToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
