package com.studytrack.app.util

import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment

fun View.visible() {
    isVisible = true
}

fun View.gone() {
    isVisible = false
}

fun Fragment.toast(message: String) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
}

fun Fragment.hideKeyboard() {
    val imm = requireContext().getSystemService(InputMethodManager::class.java)
    imm?.hideSoftInputFromWindow(view?.windowToken, 0)
}
