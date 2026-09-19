package com.studytrack.app.data.remote

import com.studytrack.app.util.TokenManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the current Firebase ID token as a Bearer token to every /api/
 * request. Requests without a signed-in user proceed unauthenticated and the
 * backend answers with 401.
 */
class AuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.startsWith("/api/")) {
            return chain.proceed(request)
        }
        val token = TokenManager.currentToken()
            ?: return chain.proceed(request)
        val authenticated = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authenticated)
    }
}
