package com.studytrack.app.util

import android.util.Patterns

object InputValidators {

    fun isValidEmail(email: String): Boolean =
        email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    /** At least [MIN_PASSWORD_LENGTH] characters — Firebase's own minimum. */
    fun isPasswordValid(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

    /** At least one digit, as required by the Create Account checklist. */
    fun containsNumber(password: String): Boolean = password.any { it.isDigit() }

    private const val MIN_PASSWORD_LENGTH = 6
}
