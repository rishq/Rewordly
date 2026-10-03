package com.rewordly.app.core.notifications

import android.content.Context
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.domain.repository.NotificationLedger
import com.rewordly.app.domain.repository.ProgressRepository
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.service.ReminderContext
import com.rewordly.app.domain.service.ReminderDecider
import com.rewordly.app.domain.service.ReminderDecision
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive

/**
 * Sends a single encouragement the moment the daily goal is completed.
 *
 * This is an event, not a schedule, so it is driven by the local progress flow rather than by
 * WorkManager. It reuses [ReminderDecider] for every "do not be intrusive" rule, which means it is
 * never delivered during quiet hours, right after another reminder, or twice in one day.
 *
 * The observation is restarted when the local day rolls over: `observeOverview()` captures the
 * current day when it is created, so a single long-lived collection would keep reporting yesterday's
 * numbers. Nothing is lost in between, because a new subscription emits the current state at once.
 */
@Singleton
class SessionEncouragementNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val progressRepository: ProgressRepository,
    private val ledger: NotificationLedger,
    private val timeProvider: TimeProvider,
) {
    /** Never returns; run it in an app scope. */
    suspend fun observe() {
        while (currentCoroutineContext().isActive) {
            val day = timeProvider.today()
            progressRepository.observeOverview()
                .takeWhile { timeProvider.today() == day }
                .map { it.today.isGoalReached }
                .distinctUntilChanged()
                .filter { it }
                .collect { notifyIfAllowed() }
        }
    }

    private suspend fun notifyIfAllowed() {
        val settings = settingsRepository.settings.first()
        if (!settings.notificationsEnabled) return
        val today = timeProvider.today()
        val now = timeProvider.nowMillis()
        val reminder = ReminderContext.of(
            settings = settings,
            overview = progressRepository.observeOverview().first(),
            nowMillis = now,
            zone = timeProvider.zone(),
            lastNotifiedAt = ledger.lastNotifiedAt(),
            kindsNotifiedToday = ledger.kindsNotifiedOn(today),
        )
        val decision = ReminderDecider.decideEncouragement(reminder)
        if (decision is ReminderDecision.Notify &&
            ReminderNotifier.show(context, decision.kind, reminder, settings.interfaceLanguage)
        ) {
            ledger.record(decision.kind, today, now)
        }
    }
}
