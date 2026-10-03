package com.rewordly.app.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * How often the daily reminder repeats.
 *
 * [SELECTED_DAYS] uses [ReminderPreferences.days]; [EVERY_DAY] ignores the day set so a user who
 * narrows the days and later switches back still finds their previous selection.
 */
enum class ReminderFrequency { EVERY_DAY, SELECTED_DAYS }

/**
 * What a reminder may talk about. Only one reminder is sent per scheduled run, so the kinds are
 * ordered by usefulness: the most relevant enabled kind wins (see `ReminderDecider`).
 */
enum class ReminderKind {
    /** Words whose review is due today. */
    DUE_WORDS,

    /** A streak of several days is about to break because today's goal is not met yet. */
    STREAK_AT_RISK,

    /** Today's goal has not been reached. */
    GOAL_INCOMPLETE,

    /** A plain "time to practise" nudge, used when there is nothing more specific to say. */
    DAILY,

    /** Sent right after the daily goal is completed, as encouragement. Never sent on a schedule. */
    SESSION_DONE,
    ;

    companion object {
        /** Everything except the encouragement, which the user opts into separately. */
        val SCHEDULED: List<ReminderKind> = listOf(DUE_WORDS, STREAK_AT_RISK, GOAL_INCOMPLETE, DAILY)

        /** Kinds enabled when the user has never touched the notification preferences. */
        val DEFAULTS: Set<ReminderKind> = setOf(DUE_WORDS, GOAL_INCOMPLETE, DAILY)

        /** A streak must be at least this long before it is worth warning about. */
        const val MIN_STREAK_TO_WARN = 2
    }
}

/**
 * A window in which no notification may be delivered, in the user's local time.
 *
 * The window may wrap midnight (for example 22:00 - 08:00), which is the common case.
 * Granularity is whole hours: minutes would add UI complexity without changing the outcome.
 */
data class QuietHours(val startHour: Int, val endHour: Int) {
    /** True when [time] falls inside the window. An empty window never matches. */
    fun contains(time: LocalTime): Boolean {
        if (startHour == endHour) return false
        val hour = time.hour
        return if (startHour < endHour) hour in startHour until endHour else hour >= startHour || hour < endHour
    }

    /** The first local time after the window, i.e. when reminders may be delivered again. */
    val end: LocalTime get() = LocalTime.of(endHour.coerceIn(0, 23), 0)

    val start: LocalTime get() = LocalTime.of(startHour.coerceIn(0, 23), 0)
}

/**
 * Everything the reminder scheduler and the notification decision need, as one value.
 *
 * This is a view over [UserSettings] rather than a second source of truth, so the settings screen
 * and the background worker can never disagree about what the user chose.
 */
data class ReminderPreferences(
    val enabled: Boolean = false,
    val hour: Int = UserSettings.DEFAULT_REMINDER_HOUR,
    val minute: Int = UserSettings.DEFAULT_REMINDER_MINUTE,
    val frequency: ReminderFrequency = ReminderFrequency.EVERY_DAY,
    val days: Set<DayOfWeek> = ALL_DAYS,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = UserSettings.DEFAULT_QUIET_START_HOUR,
    val quietEndHour: Int = UserSettings.DEFAULT_QUIET_END_HOUR,
    val kinds: Set<ReminderKind> = ReminderKind.DEFAULTS,
) {
    val time: LocalTime get() = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    /** The active quiet-hours window, or null when the feature is off. */
    val quietHours: QuietHours?
        get() = if (quietHoursEnabled) QuietHours(quietStartHour, quietEndHour) else null

    /** Whether a reminder may be delivered on [day] at all. */
    fun isActiveOn(day: DayOfWeek): Boolean = when (frequency) {
        ReminderFrequency.EVERY_DAY -> true
        ReminderFrequency.SELECTED_DAYS -> day in days
    }

    /** True when no day is selected, which would silently disable the reminder entirely. */
    val hasNoActiveDays: Boolean
        get() = frequency == ReminderFrequency.SELECTED_DAYS && days.isEmpty()

    companion object {
        val ALL_DAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet()
    }
}
