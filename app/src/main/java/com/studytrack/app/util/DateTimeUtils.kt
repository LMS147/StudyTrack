package com.studytrack.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Central place for the app's ISO-8601 wire format <-> display conversions.
 *
 * Wire format (see docs/API_CONTRACT.md): ISO-8601 local date-time strings
 * like "2026-09-24T17:00:00". Values with an explicit offset (e.g. "...Z")
 * are accepted and converted to the device timezone on read.
 */
object DateTimeUtils {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
    private val fullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")
    private val monthYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    // ------------------------------------------------------------- now & basics

    fun today(): LocalDate = LocalDate.now()

    /** Device-local date in yyyy-MM-dd, sent to the AI endpoint as context. */
    fun todayIsoDate(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    /** Any date as yyyy-MM-dd (e.g. the calendar quick-add default due date). */
    fun isoDate(date: LocalDate): String = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

    /** IANA id of the device timezone, sent to the AI endpoint as context. */
    fun timezoneId(): String = ZoneId.systemDefault().id

    // --------------------------------------------------------------- formatting

    fun formatIso(date: LocalDate, time: LocalTime): String =
        LocalDateTime.of(date, time).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    fun formatIso(dateTime: LocalDateTime): String =
        dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    // ----------------------------------------------------------------- parsing

    /**
     * Parses ISO-8601 flexibly: date-time with or without seconds, with
     * offset ("...Z" / "+02:00" — converted to device time), or plain dates
     * ("2026-09-24" — start of day). Returns null for anything unparseable.
     */
    fun parseDateTime(raw: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(raw)
        } catch (e: DateTimeParseException) {
            try {
                OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            } catch (e2: DateTimeParseException) {
                try {
                    LocalDate.parse(raw).atStartOfDay()
                } catch (e3: DateTimeParseException) {
                    null
                }
            }
        }
    }

    fun parseDate(raw: String?): LocalDate? = parseDateTime(raw)?.toLocalDate()

    // -------------------------------------------------------------- displaying

    /** "Fri, 14 Nov" */
    fun shortDate(raw: String?): String =
        parseDateTime(raw)?.format(shortDateFormatter) ?: "—"

    /**
     * "Fri, 14 Nov · 17:00". A time of exactly 00:00 is treated as a date-only
     * value and the time part is omitted.
     */
    fun shortDateTime(raw: String?): String {
        val dateTime = parseDateTime(raw) ?: return "—"
        return if (dateTime.toLocalTime() == LocalTime.MIDNIGHT) {
            dateTime.format(shortDateFormatter)
        } else {
            dateTime.format(shortDateFormatter) + " · " + dateTime.format(timeFormatter)
        }
    }

    /** "Friday, 14 November" — used in AI chat confirmations. */
    fun fullDate(raw: String?): String =
        parseDateTime(raw)?.format(fullDateFormatter) ?: "—"

    /** "17:00" for the time-only fields of the task editor. */
    fun timeOnly(raw: String?): String =
        parseDateTime(raw)?.format(timeFormatter) ?: ""

    fun monthYear(month: YearMonth): String = month.format(monthYearFormatter)

    /** "Saturday, 19 September" — the dashboard date line. */
    fun todayLabel(): String = LocalDate.now().format(fullDateFormatter)

    /**
     * Relative label for due dates: "Today" / "Tomorrow" / "In N days" /
     * "N days overdue". Null when undated.
     */
    fun relativeLabel(raw: String?): String? {
        val date = parseDate(raw) ?: return null
        val today = LocalDate.now()
        return when {
            date == today -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            date.isAfter(today) -> "In ${date.toEpochDay() - today.toEpochDay()} days"
            else -> "${today.toEpochDay() - date.toEpochDay()} days overdue"
        }
    }

    fun isToday(raw: String?): Boolean = parseDate(raw) == LocalDate.now()

    fun isOverdue(raw: String?): Boolean {
        val date = parseDate(raw) ?: return false
        return date.isBefore(LocalDate.now())
    }

    // ------------------------------------------------- MaterialDatePicker glue

    /** MaterialDatePicker selections are UTC epoch millis; convert to date. */
    fun fromUtcMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    fun toUtcMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
