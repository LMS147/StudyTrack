package com.studytrack.app.data.remote

/** Saved device keys take priority over an existing personal local.properties build. */
object GroqConnectionConfig {
    fun key(saved: String, bundled: String): String = saved.trim().ifBlank { bundled.trim() }
    fun validDeviceKey(value: String): Boolean = value.startsWith("gsk_") &&
        value.length > 8 && value.none { it.isWhitespace() }
}
