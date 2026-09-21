package com.studytrack.app.util

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * Fetches the current Firebase user's ID token.
 *
 * The backend expects this token as a `Authorization: Bearer <token>` header on
 * every API request (see [com.studytrack.app.data.remote.AuthInterceptor]).
 * Firebase SDK keeps its own in-memory cache, so [currentToken] is cheap unless
 * [forceRefresh] is used — which the OkHttp [okhttp3.Authenticator] does after
 * a 401 to retry with a freshly minted token.
 *
 * This runs on OkHttp worker threads via [runBlocking], which is the expected
 * blocking context inside interceptors/authenticators.
 */
object TokenManager {

    /**
     * Returns the current ID token, or null when the user is signed out or the
     * refresh failed.
     */
    fun currentToken(forceRefresh: Boolean = false): String? = runBlocking {
        val user = FirebaseAuth.getInstance().currentUser ?: return@runBlocking null
        try {
            user.getIdToken(forceRefresh).await().token
        } catch (e: Exception) {
            null
        }
    }
}
