package com.studytrack.app.timetable

import com.studytrack.app.data.local.entity.TimetableEntry
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

object TimetableRules {
    fun validate(entry: TimetableEntry) {
        require(entry.title.isNotBlank()) { "Enter a class title." }
        require(entry.dayOfWeek in 1..7) { "Choose a day of the week." }
        require(entry.startMinute in 0..1439 && entry.endMinute in 1..1439 &&
            entry.endMinute > entry.startMinute) { "End time must be after start time on the same day." }
        val start = LocalDate.parse(entry.startsOn)
        val end = entry.endsOn?.let(LocalDate::parse)
        require(end == null || !end.isBefore(start)) { "Term end must be on or after the start date." }
        val first = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(entry.dayOfWeek)))
        require(end == null || !first.isAfter(end)) { "The date range must include the selected weekday." }
    }

    fun occursOn(entry: TimetableEntry, date: LocalDate): Boolean =
        date.dayOfWeek.value == entry.dayOfWeek && !date.isBefore(LocalDate.parse(entry.startsOn)) &&
            (entry.endsOn == null || !date.isAfter(LocalDate.parse(entry.endsOn)))

    fun overlaps(a: TimetableEntry, b: TimetableEntry): Boolean {
        if (a.ownerUid != b.ownerUid || a.dayOfWeek != b.dayOfWeek ||
            a.startMinute >= b.endMinute || b.startMinute >= a.endMinute) return false
        val start = maxOf(LocalDate.parse(a.startsOn), LocalDate.parse(b.startsOn))
        val end = minOf(a.endsOn?.let(LocalDate::parse) ?: LocalDate.MAX,
            b.endsOn?.let(LocalDate::parse) ?: LocalDate.MAX)
        if (start.isAfter(end)) return false
        val first = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(a.dayOfWeek)))
        return !first.isAfter(end)
    }
}
