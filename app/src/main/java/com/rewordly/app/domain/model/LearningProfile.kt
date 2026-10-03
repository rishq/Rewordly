package com.rewordly.app.domain.model

/** What the learner wants to get out of English. Used to pick fitting vocabulary. */
enum class LearningGoal { CASUAL, TRAVEL, WORK, ACADEMIC }

/**
 * Everything the app knows about the learner. Every field is optional and can be changed at any time;
 * [configured] only records whether the user already went through the optional profile setup.
 *
 * The profile is a view over [UserSettings] (see [toLearningProfile]) so there is a single source of
 * truth in DataStore and no duplicated state.
 */
data class LearningProfile(
    /** Estimated CEFR level, or null while it has never been estimated. */
    val level: Difficulty? = null,
    val goal: LearningGoal = LearningGoal.CASUAL,
    val interests: Set<TopicPreset> = emptySet(),
    val dailyNewWordTarget: Int = DEFAULT_DAILY_NEW_WORDS,
    val sessionLength: Int = DEFAULT_SESSION_LENGTH,
    val configured: Boolean = false,
) {
    companion object {
        const val DEFAULT_DAILY_NEW_WORDS = 10
        const val DEFAULT_SESSION_LENGTH = 12
        val DAILY_NEW_WORD_OPTIONS = listOf(5, 10, 15, 20)
        val SESSION_LENGTH_OPTIONS = listOf(5, 10, 15, 20, 30)
    }
}

fun UserSettings.toLearningProfile(): LearningProfile = LearningProfile(
    level = level,
    goal = learningGoal,
    interests = interests,
    dailyNewWordTarget = dailyGoal,
    sessionLength = sessionLength,
    configured = profileConfigured,
)
