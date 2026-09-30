package com.rewordly.app.domain.repository

import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    val recentSearches: Flow<List<String>>

    suspend fun setInterfaceLanguage(language: InterfaceLanguage)

    suspend fun setLearningLanguage(language: LearningLanguage)

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDailyGoal(goal: Int)

    suspend fun setNotificationsEnabled(enabled: Boolean)

    suspend fun setOnboardingCompleted()

    suspend fun addRecentSearch(query: String)

    suspend fun clearRecentSearches()
}
