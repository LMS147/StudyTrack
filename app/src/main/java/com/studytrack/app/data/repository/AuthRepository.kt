package com.studytrack.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseNetworkException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.studytrack.app.util.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Email/password authentication against Firebase Auth.
 *
 * The REST API never sees passwords — the app exchanges Firebase credentials
 * for an ID token locally and attaches it to every API request (TokenManager +
 * AuthInterceptor).
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    val currentUser: FirebaseUser? get() = auth.currentUser

    fun isUserLoggedIn(): Boolean = auth.currentUser != null

    /** Best-effort display name (set at registration time). */
    fun displayName(): String = currentUser?.displayName.orEmpty()

    suspend fun login(email: String, password: String): ApiResult<Unit> = try {
        auth.signInWithEmailAndPassword(email, password).await()
        ApiResult.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ApiResult.Error(friendlyAuthError(e), e)
    }

    suspend fun register(name: String, email: String, password: String): ApiResult<Unit> = try {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val profileUpdate = UserProfileChangeRequest.Builder().setDisplayName(name).build()
        result.user?.updateProfile(profileUpdate)?.await()
        ApiResult.Success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ApiResult.Error(friendlyAuthError(e), e)
    }

    fun signOut() {
        auth.signOut()
    }
}

private fun friendlyAuthError(e: Exception): String = when (e) {
    is FirebaseAuthInvalidUserException -> "No account found for this email."
    is FirebaseAuthInvalidCredentialsException -> "Invalid email or password."
    is FirebaseAuthWeakPasswordException -> "That password is too weak."
    is FirebaseAuthUserCollisionException -> "An account with this email already exists."
    is FirebaseNetworkException -> "Network error — check your connection and try again."
    else -> e.message ?: "Authentication failed. Please try again."
}
