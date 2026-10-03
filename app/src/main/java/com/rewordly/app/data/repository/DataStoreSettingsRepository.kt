package com.rewordly.app.data.repository

import com.rewordly.app.core.datastore.UserPreferencesDataSource
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.LearningGoal
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.ReminderFrequency
import com.rewordly.app.domain.model.ReminderKind
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.domain.model.TopicPreset
import com.rewordly.app.domain.model.UserSettings
import com.rewordly.app.domain.repository.SettingsRepository
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataSource: UserPreferencesDataSource,
) : SettingsRepository {
    override val settings: Flow<UserSettings> = dataSource.settings

    override val recentSearches: Flow<List<String>> = dataSource.recentSearches

    override suspend fun setInterfaceLanguage(language: InterfaceLanguage) = dataSource.setInterfaceLanguage(language)

    override suspend fun setLearningLanguage(language: LearningLanguage) = dataSource.setLearningLanguage(language)

    override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)

    override suspend fun setDailyGoal(goal: Int) = dataSource.setDailyGoal(goal)

    override suspend fun setNotificationsEnabled(enabled: Boolean) = dataSource.setNotificationsEnabled(enabled)

    override suspend fun setReminderTime(hour: Int, minute: Int) = dataSource.setReminderTime(hour, minute)

    override suspend fun setReminderFrequency(frequency: ReminderFrequency) = dataSource.setReminderFrequency(frequency)

    override suspend fun setReminderDays(days: Set<DayOfWeek>) = dataSource.setReminderDays(days)

    override suspend fun setQuietHours(enabled: Boolean, startHour: Int, endHour: Int) =
        dataSource.setQuietHours(enabled, startHour, endHour)

    override suspend fun setNotificationKinds(kinds: Set<ReminderKind>) = dataSource.setNotificationKinds(kinds)

    override suspend fun setOnboardingCompleted() = dataSource.setOnboardingCompleted()

    override suspend fun addRecentSearch(query: String) = dataSource.addRecentSearch(query)

    override suspend fun clearRecentSearches() = dataSource.clearRecentSearches()

    override suspend fun setLevel(level: Difficulty?) = dataSource.setLevel(level)

    override suspend fun setLearningGoal(goal: LearningGoal) = dataSource.setLearningGoal(goal)

    override suspend fun setInterests(interests: Set<TopicPreset>) = dataSource.setInterests(interests)

    override suspend fun setSessionLength(length: Int) = dataSource.setSessionLength(length)

    override suspend fun setProfileConfigured() = dataSource.setProfileConfigured()
}
