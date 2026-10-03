package com.rewordly.app.domain.service

import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.domain.model.ReminderPreferences
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.usecase.ReminderTimeCalculator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Platform hook that fires the reminder once at an absolute time. Implemented with WorkManager. */
interface ReminderScheduler {
    fun scheduleAt(triggerAtMillis: Long)

    fun cancel()
}

/**
 * Keeps the scheduled reminder in line with the settings: enabled/disabled, chosen time, active
 * weekdays, quiet hours and the current time zone.
 *
 * Every call recomputes the next trigger from the current wall clock, so a time-zone or DST change is
 * corrected on the next sync rather than accumulating drift.
 */
@Singleton
class ReminderSyncer @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val timeProvider: TimeProvider,
) {
    /** Re-applies the current settings once. */
    suspend fun resync() = apply(settingsRepository.settings.first().reminderPreferences)

    /** Applies the settings now and on every relevant change. Never returns; run it in an app scope. */
    suspend fun keepInSync() {
        settingsRepository.settings
            .map { it.reminderPreferences }
            .distinctUntilChanged()
            .collect { apply(it) }
    }

    private fun apply(preferences: ReminderPreferences) {
        // With no active day nothing could ever fire, so the work is removed instead of left queued.
        if (!preferences.enabled || preferences.hasNoActiveDays) {
            scheduler.cancel()
            return
        }
        scheduler.scheduleAt(
            ReminderTimeCalculator.nextTriggerMillis(
                now = timeProvider.nowMillis(),
                zone = timeProvider.zone(),
                preferences = preferences,
            ),
        )
    }
}
