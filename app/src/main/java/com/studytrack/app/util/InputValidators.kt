package com.studytrack.app.util

import android.util.Patterns

object InputValidators {

    fun isValidEmail(email: String): Boolean =
        email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    fun isPasswordValid(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

    private const val MIN_PASSWORD_LENGTH = 6
}
