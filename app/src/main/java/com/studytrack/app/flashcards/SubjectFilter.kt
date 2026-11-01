package com.studytrack.app.flashcards

/** Keep a selection only while its subject still exists. Null means all subjects. */
internal fun validSubjectFilter(selectedId: String?, subjectIds: Collection<String>): String? =
    selectedId?.takeIf { it in subjectIds }
