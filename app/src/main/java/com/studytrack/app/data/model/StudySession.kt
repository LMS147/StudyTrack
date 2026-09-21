package com.studytrack.app.data.model

import kotlinx.serialization.Serializable

/**
 * Request body for POST /api/study-sessions.
 *
 * The endpoint is wired into [com.studytrack.app.data.remote.ApiService] and
 * the repository layer but does not have a dedicated screen yet — it is ready
 * for future "log study time" UI.
 */
@Serializable
data class StudySessionPayload(
    val subjectId: String? = null,
    val taskId: String? = null,
    val durationMinutes: Int,
    /** ISO-8601 date-time; defaults to "now" server-side when omitted. */
    val studiedAt: String? = null,
    val notes: String? = null,
)
