package com.rewordly.app.domain.model

import java.time.DayOfWeek

data class UserSettings(
    val interfaceLanguage: InterfaceLanguage = InterfaceLanguage.DEFAULT,
    val learningLanguage: LearningLanguage = LearningLanguage.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dailyGoal: Int = DEFAULT_DAILY_GOAL,
    val notificationsEnabled: Boolean = false,
    val reminderHour: Int = DEFAULT_REMINDER_HOUR,
    val reminderMinute: Int = DEFAULT_REMINDER_MINUTE,
    /** Every day, or only on [reminderDays]. */
    val reminderFrequency: ReminderFrequency = ReminderFrequency.EVERY_DAY,
    /** Days the reminder may fire on; only consulted for [ReminderFrequency.SELECTED_DAYS]. */
    val reminderDays: Set<DayOfWeek> = ReminderPreferences.ALL_DAYS,
    val quietHoursEnabled: Boolean = false,
    val quietStartHour: Int = DEFAULT_QUIET_START_HOUR,
    val quietEndHour: Int = DEFAULT_QUIET_END_HOUR,
    /** Which notification kinds the user wants to receive. */
    val notificationKinds: Set<ReminderKind> = ReminderKind.DEFAULTS,
    val onboardingCompleted: Boolean = false,
    /** Estimated CEFR level, or null while the user has never estimated it. */
    val level: Difficulty? = null,
    val learningGoal: LearningGoal = LearningGoal.CASUAL,
    val interests: Set<TopicPreset> = emptySet(),
    /** How many words a single learning session shows; also caps the daily plan. */
    val sessionLength: Int = LearningProfile.DEFAULT_SESSION_LENGTH,
    /** Whether the optional profile setup was already offered. Never blocks the app. */
    val profileConfigured: Boolean = false,
) {
    /** The reminder configuration as one value, shared by the settings screen and the worker. */
    val reminderPreferences: ReminderPreferences
        get() = ReminderPreferences(
            enabled = notificationsEnabled,
            hour = reminderHour,
            minute = reminderMinute,
            frequency = reminderFrequency,
            days = reminderDays,
            quietHoursEnabled = quietHoursEnabled,
            quietStartHour = quietStartHour,
            quietEndHour = quietEndHour,
            kinds = notificationKinds,
        )

    companion object {
        const val DEFAULT_DAILY_GOAL = 10
        const val DEFAULT_REMINDER_HOUR = 19
        const val DEFAULT_REMINDER_MINUTE = 0
        const val DEFAULT_QUIET_START_HOUR = 22
        const val DEFAULT_QUIET_END_HOUR = 8
        val DAILY_GOAL_OPTIONS = listOf(5, 10, 15, 20, 30)
    }
}

/** Language of the app UI. Independent from [LearningLanguage]. */
enum class InterfaceLanguage(val tag: String) {
    RUSSIAN("ru"),
    ENGLISH("en"),
    ;

    companion object {
        val DEFAULT = RUSSIAN

        fun fromTag(tag: String?): InterfaceLanguage? = entries.firstOrNull { it.tag == tag }
    }
}

/** Language the user is learning. Add entries here to support more languages. */
enum class LearningLanguage(val tag: String) {
    ENGLISH("en"),
    ;

    companion object {
        val DEFAULT = ENGLISH

        fun fromTag(tag: String?): LearningLanguage? = entries.firstOrNull { it.tag == tag }
    }
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }
