package com.rewordly.app.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.LearningGoal
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.LearningProfile
import com.rewordly.app.domain.model.ReminderFrequency
import com.rewordly.app.domain.model.ReminderKind
import com.rewordly.app.domain.model.ReminderPreferences
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.domain.model.TopicPreset
import com.rewordly.app.domain.model.UserSettings
import java.io.IOException
import java.time.DayOfWeek
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
            reminderHour = (prefs[Keys.REMINDER_HOUR] ?: UserSettings.DEFAULT_REMINDER_HOUR).coerceIn(0, 23),
            reminderMinute = (prefs[Keys.REMINDER_MINUTE] ?: UserSettings.DEFAULT_REMINDER_MINUTE).coerceIn(0, 59),
            reminderFrequency = prefs[Keys.REMINDER_FREQUENCY]?.let { name ->
                ReminderFrequency.entries.firstOrNull { it.name == name }
            } ?: ReminderFrequency.EVERY_DAY,
            reminderDays = prefs[Keys.REMINDER_DAYS]?.let(::parseDays) ?: ReminderPreferences.ALL_DAYS,
            quietHoursEnabled = prefs[Keys.QUIET_HOURS_ENABLED] ?: false,
            quietStartHour = (prefs[Keys.QUIET_START_HOUR] ?: UserSettings.DEFAULT_QUIET_START_HOUR)
                .coerceIn(0, 23),
            quietEndHour = (prefs[Keys.QUIET_END_HOUR] ?: UserSettings.DEFAULT_QUIET_END_HOUR).coerceIn(0, 23),
            notificationKinds = prefs[Keys.NOTIFICATION_KINDS]?.let(::parseKinds) ?: ReminderKind.DEFAULTS,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            level = prefs[Keys.LEVEL]?.let { name -> Difficulty.entries.firstOrNull { it.name == name } },
            learningGoal = prefs[Keys.LEARNING_GOAL]?.let { name ->
                LearningGoal.entries.firstOrNull { it.name == name }
            } ?: LearningGoal.CASUAL,
            interests = prefs[Keys.INTERESTS].orEmpty()
                .mapNotNull { name -> TopicPreset.entries.firstOrNull { it.name == name } }
                .toSet(),
            sessionLength = (prefs[Keys.SESSION_LENGTH] ?: LearningProfile.DEFAULT_SESSION_LENGTH)
                .coerceIn(MIN_SESSION_LENGTH, MAX_SESSION_LENGTH),
            profileConfigured = prefs[Keys.PROFILE_CONFIGURED] ?: false,
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

    suspend fun setReminderTime(hour: Int, minute: Int) = edit {
        it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
        it[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
    }

    suspend fun setReminderFrequency(frequency: ReminderFrequency) = edit {
        it[Keys.REMINDER_FREQUENCY] = frequency.name
    }

    suspend fun setReminderDays(days: Set<DayOfWeek>) = edit {
        it[Keys.REMINDER_DAYS] = days.map(DayOfWeek::name).toSet()
    }

    suspend fun setQuietHours(enabled: Boolean, startHour: Int, endHour: Int) = edit {
        it[Keys.QUIET_HOURS_ENABLED] = enabled
        it[Keys.QUIET_START_HOUR] = startHour.coerceIn(0, 23)
        it[Keys.QUIET_END_HOUR] = endHour.coerceIn(0, 23)
    }

    suspend fun setNotificationKinds(kinds: Set<ReminderKind>) = edit {
        it[Keys.NOTIFICATION_KINDS] = kinds.map(ReminderKind::name).toSet()
    }

    suspend fun setOnboardingCompleted() = edit { it[Keys.ONBOARDING_COMPLETED] = true }

    suspend fun addRecentSearch(query: String) = edit { prefs ->
        val current = prefs[Keys.RECENT_SEARCHES]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
        val updated = (listOf(query) + current.filterNot { it.equals(query, ignoreCase = true) })
            .take(MAX_RECENT_SEARCHES)
        prefs[Keys.RECENT_SEARCHES] = updated.joinToString(SEPARATOR)
    }

    suspend fun clearRecentSearches() = edit { it.remove(Keys.RECENT_SEARCHES) }

    suspend fun setLevel(level: Difficulty?) = edit { prefs ->
        if (level == null) prefs.remove(Keys.LEVEL) else prefs[Keys.LEVEL] = level.name
    }

    suspend fun setLearningGoal(goal: LearningGoal) = edit { it[Keys.LEARNING_GOAL] = goal.name }

    suspend fun setInterests(interests: Set<TopicPreset>) = edit {
        it[Keys.INTERESTS] = interests.map(TopicPreset::name).toSet()
    }

    suspend fun setSessionLength(length: Int) = edit {
        it[Keys.SESSION_LENGTH] = length.coerceIn(MIN_SESSION_LENGTH, MAX_SESSION_LENGTH)
    }

    suspend fun setProfileConfigured() = edit { it[Keys.PROFILE_CONFIGURED] = true }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private object Keys {
        val INTERFACE_LANGUAGE = stringPreferencesKey("interface_language")
        val LEARNING_LANGUAGE = stringPreferencesKey("learning_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val REMINDER_FREQUENCY = stringPreferencesKey("reminder_frequency")
        val REMINDER_DAYS = stringSetPreferencesKey("reminder_days")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_START_HOUR = intPreferencesKey("quiet_start_hour")
        val QUIET_END_HOUR = intPreferencesKey("quiet_end_hour")
        val NOTIFICATION_KINDS = stringSetPreferencesKey("notification_kinds")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val LEVEL = stringPreferencesKey("learning_level")
        val LEARNING_GOAL = stringPreferencesKey("learning_goal")
        val INTERESTS = stringSetPreferencesKey("learning_interests")
        val SESSION_LENGTH = intPreferencesKey("session_length")
        val PROFILE_CONFIGURED = booleanPreferencesKey("profile_configured")
    }

    private companion object {
        const val SEPARATOR = "\u001F"
        const val MAX_RECENT_SEARCHES = 8
        const val MIN_SESSION_LENGTH = 1
        const val MAX_SESSION_LENGTH = 50

        /**
         * Unknown stored names are dropped rather than failing the whole read, so removing a value
         * from the enum in a future version degrades gracefully instead of resetting every setting.
         */
        fun parseDays(names: Set<String>): Set<DayOfWeek> =
            names.mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }.toSet()

        fun parseKinds(names: Set<String>): Set<ReminderKind> =
            names.mapNotNull { name -> ReminderKind.entries.firstOrNull { it.name == name } }.toSet()
    }
}
