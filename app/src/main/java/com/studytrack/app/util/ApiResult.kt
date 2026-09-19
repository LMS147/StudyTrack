package com.studytrack.app.util

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/**
 * Wrapper for API/auth call states exposed by ViewModels to the UI.
 */
sealed interface ApiResult<out T> {
    data object Loading : ApiResult<Nothing>
    data class Success<out T>(val data: T) : ApiResult<T>
    data class Error(val message: String, val cause: Throwable? = null) : ApiResult<Nothing>
}

/**
 * Runs an API call and maps any failure to a user-friendly [ApiResult.Error],
 * rethrowing [CancellationException] so structured concurrency stays intact.
 */
suspend fun <T> safeApiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    ApiResult.Error(e.friendlyMessage(), e)
}

fun Throwable.friendlyMessage(): String = when (this) {
    is HttpException -> when (val code = code()) {
        400 -> "Bad request (400)"
        401 -> "Your session expired — please log in again"
        403 -> "You don't have permission to do that (403)"
        404 -> "Not found (404)"
        in 500..599 -> "Server error ($code)"
        else -> "Request failed ($code)"
    }
    is IOException -> "Network error — check your connection and try again"
    else -> message ?: "Something went wrong. Please try again."
}
