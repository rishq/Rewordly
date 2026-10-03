package com.rewordly.app.domain.usecase

import com.rewordly.app.domain.model.QuietHours
import com.rewordly.app.domain.model.ReminderPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Computes when the next daily reminder should fire in the user's current zone.
 *
 * Everything is derived from local calendar dates and wall-clock times, never from a fixed number of
 * hours, so DST transitions and time-zone changes move the reminder with the user's clock instead of
 * drifting. WorkManager may deliver a little late under Doze; it never needs exact-alarm permissions.
 */
object ReminderTimeCalculator {

    /** Next occurrence of [hour]:[minute] strictly after [now], on any day. */
    fun nextTriggerMillis(now: Long, zone: ZoneId, hour: Int, minute: Int): Long {
        val time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val current = Instant.ofEpochMilli(now).atZone(zone)
        return candidateOn(current.toLocalDate(), time, zone, current).toInstant().toEpochMilli()
    }

    /**
     * Next occurrence that respects the user's active weekdays and quiet hours.
     *
     * A reminder that would land inside the quiet window is pushed to the end of that window, and the
     * resulting day is then re-checked against the active days. At most [MAX_DAYS_AHEAD] days are
     * searched: with an empty day set nothing would ever match, and the caller cancels instead.
     */
    fun nextTriggerMillis(now: Long, zone: ZoneId, preferences: ReminderPreferences): Long {
        val time = preferences.time
        val quiet = preferences.quietHours
        val current = Instant.ofEpochMilli(now).atZone(zone)
        var date = current.toLocalDate()

        repeat(MAX_DAYS_AHEAD) {
            val shifted = shiftOutOfQuietHours(time, quiet, date, zone)
            val shiftedDate = shifted.toLocalDate()
            val candidate = ZonedDateTime.of(shiftedDate, shifted.toLocalTime(), zone)
            if (candidate.toInstant().isAfter(current.toInstant()) && preferences.isActiveOn(shiftedDate.dayOfWeek)) {
                return candidate.toInstant().toEpochMilli()
            }
            date = date.plusDays(1)
        }
        // Unreachable for a non-empty day set; falls back to the plain next occurrence.
        return nextTriggerMillis(now, zone, time.hour, time.minute)
    }

    /** Moves [time] on [date] forward to the end of the quiet window when it falls inside it. */
    private fun shiftOutOfQuietHours(
        time: LocalTime,
        quiet: QuietHours?,
        date: LocalDate,
        zone: ZoneId,
    ): ZonedDateTime {
        val candidate = ZonedDateTime.of(date, time, zone)
        if (quiet == null || !quiet.contains(time)) return candidate
        val end = ZonedDateTime.of(date, quiet.end, zone)
        // A window that wraps midnight (22:00 - 08:00) ends on the following day.
        return if (quiet.startHour > quiet.endHour && time.hour >= quiet.startHour) end.plusDays(1) else end
    }

    private fun candidateOn(date: LocalDate, time: LocalTime, zone: ZoneId, current: ZonedDateTime): ZonedDateTime {
        val candidate = ZonedDateTime.of(date, time, zone)
        return if (candidate.toInstant().isAfter(current.toInstant())) {
            candidate
        } else {
            ZonedDateTime.of(date.plusDays(1), time, zone)
        }
    }

    /** Two weeks is more than enough to find an active weekday and stays cheap. */
    private const val MAX_DAYS_AHEAD = 14
}
