package com.rewordly.app.domain.model

data class UserSettings(
    val interfaceLanguage: InterfaceLanguage = InterfaceLanguage.DEFAULT,
    val learningLanguage: LearningLanguage = LearningLanguage.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dailyGoal: Int = DEFAULT_DAILY_GOAL,
    val notificationsEnabled: Boolean = false,
    val onboardingCompleted: Boolean = false,
) {
    companion object {
        const val DEFAULT_DAILY_GOAL = 20
        val DAILY_GOAL_OPTIONS = listOf(5, 10, 20, 30, 50)
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
