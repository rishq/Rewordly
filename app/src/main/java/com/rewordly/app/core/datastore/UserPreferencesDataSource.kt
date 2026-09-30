package com.rewordly.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.domain.model.UserSettings
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val settings: Flow<UserSettings> = preferences.map { prefs ->
        UserSettings(
            interfaceLanguage = InterfaceLanguage.fromTag(prefs[Keys.INTERFACE_LANGUAGE]) ?: InterfaceLanguage.DEFAULT,
            learningLanguage = LearningLanguage.fromTag(prefs[Keys.LEARNING_LANGUAGE]) ?: LearningLanguage.DEFAULT,
            themeMode = prefs[Keys.THEME_MODE]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            dailyGoal = prefs[Keys.DAILY_GOAL] ?: UserSettings.DEFAULT_DAILY_GOAL,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: false,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
        )
    }.distinctUntilChanged()

    val recentSearches: Flow<List<String>> = preferences.map { prefs ->
        prefs[Keys.RECENT_SEARCHES]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
    }.distinctUntilChanged()

    suspend fun setInterfaceLanguage(language: InterfaceLanguage) = edit { it[Keys.INTERFACE_LANGUAGE] = language.tag }

    suspend fun setLearningLanguage(language: LearningLanguage) = edit { it[Keys.LEARNING_LANGUAGE] = language.tag }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setDailyGoal(goal: Int) = edit { it[Keys.DAILY_GOAL] = goal }

    suspend fun setNotificationsEnabled(enabled: Boolean) = edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }

    suspend fun setOnboardingCompleted() = edit { it[Keys.ONBOARDING_COMPLETED] = true }

    suspend fun addRecentSearch(query: String) = edit { prefs ->
        val current = prefs[Keys.RECENT_SEARCHES]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
        val updated = (listOf(query) + current.filterNot { it.equals(query, ignoreCase = true) })
            .take(MAX_RECENT_SEARCHES)
        prefs[Keys.RECENT_SEARCHES] = updated.joinToString(SEPARATOR)
    }

    suspend fun clearRecentSearches() = edit { it.remove(Keys.RECENT_SEARCHES) }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private object Keys {
        val INTERFACE_LANGUAGE = stringPreferencesKey("interface_language")
        val LEARNING_LANGUAGE = stringPreferencesKey("learning_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    }

    private companion object {
        const val SEPARATOR = "\u001F"
        const val MAX_RECENT_SEARCHES = 8
    }
}
