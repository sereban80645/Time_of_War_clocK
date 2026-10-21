package com.timeofwar.clock

import java.time.LocalDateTime
import java.time.Period

/**
 * Single source of truth for both the app screen and the Wear OS Tile.
 * Day counts follow local calendar days and the event's local clock time,
 * avoiding DST-related 23/25-hour-day off-by-one errors.
 */
object CounterFormatter {
    fun format(start: LocalDateTime, now: LocalDateTime, daysOnly: Boolean, showHours: Boolean): String {
        val calendarDays = java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(), now.toLocalDate()).toInt()
        var hours = now.hour - start.hour
        if (now.minute < start.minute) hours--
        if (hours < 0) hours += 24

        if (daysOnly) {
            val days = calendarDays + if (now.toLocalTime().isBefore(start.toLocalTime())) 0 else 1
            return if (showHours) "$days д. $hours год." else "$days д."
        }

        var period = Period.between(start.toLocalDate(), now.toLocalDate())
        if (calendarDays > 0 && now.toLocalTime().isBefore(start.toLocalTime())) {
            period = period.minusDays(1)
            hours = (hours + 24) % 24
        }
        val base = "${period.years} р. ${period.months} міс. ${period.days} д."
        return if (showHours) "$base $hours год." else base
    }
}
