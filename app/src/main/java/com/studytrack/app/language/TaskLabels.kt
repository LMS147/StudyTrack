package com.studytrack.app.language

import androidx.annotation.StringRes
import com.studytrack.app.R
import com.studytrack.app.data.model.Priority
import com.studytrack.app.data.model.TaskType

/** UI labels only; canonical values remain unchanged for persistence and AI. */
@StringRes fun TaskType.labelResource(): Int = when (this) {
    TaskType.ASSIGNMENT -> R.string.task_type_assignment
    TaskType.TEST -> R.string.task_type_test
    TaskType.EXAM -> R.string.task_type_exam
    TaskType.PROJECT -> R.string.task_type_project
    TaskType.PRESENTATION -> R.string.task_type_presentation
    TaskType.STUDY -> R.string.task_type_study
}
@StringRes fun Priority.labelResource(): Int = when (this) {
    Priority.LOW -> R.string.priority_low
    Priority.MEDIUM -> R.string.priority_medium
    Priority.HIGH -> R.string.priority_high
}
