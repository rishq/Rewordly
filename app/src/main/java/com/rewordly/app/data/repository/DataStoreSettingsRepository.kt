package com.rewordly.app.data.repository

import com.rewordly.app.core.datastore.UserPreferencesDataSource
import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.domain.model.UserSettings
import com.rewordly.app.domain.repository.SettingsRepository
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

    override suspend fun setOnboardingCompleted() = dataSource.setOnboardingCompleted()

    override suspend fun addRecentSearch(query: String) = dataSource.addRecentSearch(query)

    override suspend fun clearRecentSearches() = dataSource.clearRecentSearches()
}
