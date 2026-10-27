package com.studytrack.app.profile

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.studytrack.app.R

/** Shared by Profile and Settings so the language setting is reachable in both. */
object AppLanguagePicker {
    fun show(context: Context) {
        val tags = listOf("", "en", "af", "ja", "nso", "tn", "es", "fr")
        val names = arrayOf(
            context.getString(R.string.language_system), context.getString(R.string.language_english),
            context.getString(R.string.language_afrikaans), context.getString(R.string.language_japanese),
            context.getString(R.string.language_sepedi), context.getString(R.string.language_setswana),
            context.getString(R.string.language_spanish), context.getString(R.string.language_french),
        )
        val current = AppCompatDelegate.getApplicationLocales().get(0)?.language.orEmpty()
        val selected = tags.indexOf(current).coerceAtLeast(0)
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(names, selected) { dialog, which ->
                dialog.dismiss()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[which]))
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}
