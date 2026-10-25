package com.studytrack.app.data.local.entity

import androidx.room.Entity

/** One weekly class. Dates are inclusive ISO dates; minutes use local wall-clock time. */
@Entity(tableName = "timetable_entries", primaryKeys = ["ownerUid", "entryId"])
data class TimetableEntry(
    @androidx.room.ColumnInfo(name = "ownerUid") val ownerUid: String,
    val entryId: String,
    val title: String,
    val subjectId: String?,
    val dayOfWeek: Int,
    val startMinute: Int,
    val endMinute: Int,
    val venue: String,
    val lecturer: String,
    val startsOn: String,
    val endsOn: String?,
)
