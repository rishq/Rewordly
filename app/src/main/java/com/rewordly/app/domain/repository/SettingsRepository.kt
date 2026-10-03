package com.rewordly.app.domain.repository

import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.LearningGoal
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.ReminderFrequency
import com.rewordly.app.domain.model.ReminderKind
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.domain.model.TopicPreset
import com.rewordly.app.domain.model.UserSettings
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    val recentSearches: Flow<List<String>>

    suspend fun setInterfaceLanguage(language: InterfaceLanguage)

    suspend fun setLearningLanguage(language: LearningLanguage)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDailyGoal(goal: Int)

    suspend fun setNotificationsEnabled(enabled: Boolean)

    suspend fun setReminderTime(hour: Int, minute: Int)

    /** Every day, or only on the days passed to [setReminderDays]. */
    suspend fun setReminderFrequency(frequency: ReminderFrequency)

    /** Days the reminder may fire on. An empty set means the reminder never fires. */
    suspend fun setReminderDays(days: Set<DayOfWeek>)

    /** Enables or disables the quiet-hours window and sets its whole-hour bounds. */
    suspend fun setQuietHours(enabled: Boolean, startHour: Int, endHour: Int)

    /** Which notification kinds the user wants to receive. */
    suspend fun setNotificationKinds(kinds: Set<ReminderKind>)

    suspend fun setOnboardingCompleted()

    suspend fun addRecentSearch(query: String)

    suspend fun clearRecentSearches()

    // ---- personalized learning profile ----

    /** Stores the estimated level; null clears it so recommendations fall back to neutral rules. */
    suspend fun setLevel(level: Difficulty?)

    suspend fun setLearningGoal(goal: LearningGoal)

    suspend fun setInterests(interests: Set<TopicPreset>)

    suspend fun setSessionLength(length: Int)

    suspend fun setProfileConfigured()
}
