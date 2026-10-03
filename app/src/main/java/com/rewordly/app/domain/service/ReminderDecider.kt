package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.ProgressOverview
import com.rewordly.app.domain.model.ReminderKind
import com.rewordly.app.domain.model.ReminderPreferences
import com.rewordly.app.domain.model.UserSettings
import java.time.Instant
import java.time.ZoneId

/** Everything the decision needs, gathered from local data only. */
data class ReminderContext(
    val preferences: ReminderPreferences,
    val nowMillis: Long,
    val zone: ZoneId,
    /** Words whose review is due before the end of the local day. */
    val dueWords: Int,
    val wordsLearnedToday: Int,
    val dailyGoal: Int,
    /** Current streak in completed days. */
    val streakDays: Int,
    val lastNotifiedAt: Long?,
    /** Kinds already delivered today, so nothing is repeated. */
    val kindsNotifiedToday: Set<ReminderKind>,
) {
    /** Words still missing to reach today's goal; zero when the goal is met or unset. */
    val goalRemaining: Int get() = (dailyGoal - wordsLearnedToday).coerceAtLeast(0)

    val goalReached: Boolean get() = dailyGoal > 0 && wordsLearnedToday >= dailyGoal

    companion object {
        /**
         * Builds the context from the settings and the current progress overview, so the scheduled
         * worker and the after-session encouragement always evaluate the same facts.
         */
        fun of(
            settings: UserSettings,
            overview: ProgressOverview,
            nowMillis: Long,
            zone: ZoneId,
            lastNotifiedAt: Long?,
            kindsNotifiedToday: Set<ReminderKind>,
        ): ReminderContext = ReminderContext(
            preferences = settings.reminderPreferences,
            nowMillis = nowMillis,
            zone = zone,
            dueWords = overview.wordsDueToday,
            wordsLearnedToday = overview.today.wordsLearned,
            dailyGoal = overview.today.goal,
            streakDays = overview.streak.current,
            lastNotifiedAt = lastNotifiedAt,
            kindsNotifiedToday = kindsNotifiedToday,
        )
    }
}

sealed interface ReminderDecision {
    /** The most relevant thing to say right now. */
    data class Notify(val kind: ReminderKind) : ReminderDecision

    /** Nothing worth interrupting the user for. */
    data object Skip : ReminderDecision
}

/**
 * Decides whether to notify, and about what.
 *
 * The rules exist to keep reminders helpful rather than intrusive:
 * - at most one notification per scheduled run, chosen by usefulness rather than sent all at once;
 * - nothing inside quiet hours;
 * - nothing within [MIN_GAP_MILLIS] of the previous notification;
 * - nothing of a kind that was already sent today;
 * - nothing at all when there is nothing useful to say.
 *
 * This is pure logic on purpose: every rule above is covered by unit tests without Android.
 */
object ReminderDecider {
    /** Two notifications closer together than this are never sent. */
    const val MIN_GAP_MILLIS = 3 * 60 * 60 * 1000L

    fun decide(context: ReminderContext): ReminderDecision {
        if (!isAllowedNow(context)) return ReminderDecision.Skip
        val kind = ReminderKind.SCHEDULED.firstOrNull { candidate ->
            candidate in context.preferences.kinds &&
                candidate !in context.kindsNotifiedToday &&
                isUseful(candidate, context)
        }
        return kind?.let(ReminderDecision::Notify) ?: ReminderDecision.Skip
    }

    /**
     * Encouragement after the daily goal is completed. Uses the same gates as [decide], so it can
     * never arrive during quiet hours or right after another reminder.
     */
    fun decideEncouragement(context: ReminderContext): ReminderDecision {
        if (!isAllowedNow(context)) return ReminderDecision.Skip
        if (ReminderKind.SESSION_DONE !in context.preferences.kinds) return ReminderDecision.Skip
        if (ReminderKind.SESSION_DONE in context.kindsNotifiedToday) return ReminderDecision.Skip
        if (!context.goalReached) return ReminderDecision.Skip
        return ReminderDecision.Notify(ReminderKind.SESSION_DONE)
    }

    private fun isAllowedNow(context: ReminderContext): Boolean {
        if (!context.preferences.enabled) return false
        if (context.preferences.hasNoActiveDays) return false
        val now = Instant.ofEpochMilli(context.nowMillis).atZone(context.zone)
        if (context.preferences.quietHours?.contains(now.toLocalTime()) == true) return false
        if (!context.preferences.isActiveOn(now.dayOfWeek)) return false
        val last = context.lastNotifiedAt
        return last == null || context.nowMillis - last >= MIN_GAP_MILLIS
    }

    /** Whether the user's data gives this kind something meaningful to say. */
    private fun isUseful(kind: ReminderKind, context: ReminderContext): Boolean = when (kind) {
        ReminderKind.DUE_WORDS -> context.dueWords > 0
        ReminderKind.STREAK_AT_RISK ->
            context.streakDays >= ReminderKind.MIN_STREAK_TO_WARN && context.goalRemaining > 0
        ReminderKind.GOAL_INCOMPLETE -> context.goalRemaining > 0
        ReminderKind.DAILY -> true
        ReminderKind.SESSION_DONE -> context.goalReached
    }
}
