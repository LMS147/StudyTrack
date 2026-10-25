package com.studytrack.app.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Accept hosted HTTPS API roots only, without embedded credentials or query strings. */
object AiBackendConfig {
    fun resolve(saved: String, build: String, appBackend: String): String =
        normalize(saved.ifBlank { build }.ifBlank { appBackend }).orEmpty()

    fun normalize(raw: String): String? {
        val url = raw.trim().toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null || url.encodedPath != "/") return null
        return url.toString().trimEnd('/') + "/"
    }
}
