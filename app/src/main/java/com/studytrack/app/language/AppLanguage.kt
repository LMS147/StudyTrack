package com.studytrack.app.language

import java.util.Locale

/** Resolve the app's effective locale to a supported AI response language. */
object AppLanguage {
    private val names = mapOf(
        "en" to "English", "af" to "Afrikaans", "ja" to "Japanese",
        "nso" to "Sepedi (Northern Sotho)", "tn" to "Setswana",
        "es" to "Spanish", "fr" to "French",
    )
    fun supportedTag(tag: String?): String {
        val language = Locale.forLanguageTag(tag.orEmpty()).language.lowercase(Locale.ROOT)
        return language.takeIf { it in names } ?: "en"
    }
    fun name(tag: String?): String = names.getValue(supportedTag(tag))
}
