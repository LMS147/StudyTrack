package com.studytrack.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Subject(
    val subjectId: String,
    val subjectName: String,
    val description: String? = null,
)

/** Request body for POST /api/subjects and PUT /api/subjects/{id}. */
@Serializable
data class SubjectPayload(
    val subjectName: String,
    val description: String? = null,
)
