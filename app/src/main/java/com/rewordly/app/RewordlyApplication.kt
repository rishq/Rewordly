package com.rewordly.app

import android.app.Application
import androidx.work.Configuration
import com.rewordly.app.core.notifications.SessionEncouragementNotifier
import com.rewordly.app.domain.service.ReminderSyncer
import com.rewordly.app.widget.ProgressWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class RewordlyApplication : Application(), Configuration.Provider {
    @Inject lateinit var reminderSyncer: ReminderSyncer

    @Inject lateinit var sessionEncouragement: SessionEncouragementNotifier

    @Inject lateinit var widgetUpdater: ProgressWidgetUpdater

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * WorkManager is initialised on demand instead of by `androidx.startup` on every cold start: it is only
     * needed once a reminder is actually scheduled, and the default configuration is all this app uses.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // Keeps the WorkManager reminder aligned with settings, time zone and clock changes.
        appScope.launch { reminderSyncer.keepInSync() }
        // Two collectors over the same local progress flow: the encouragement after a finished session, and
        // pushing the new state to the home screen widget. The overview they both read is shared now, so
        // neither adds a database subscription of its own. No polling and no network.
        appScope.launch { sessionEncouragement.observe() }
        appScope.launch { widgetUpdater.observe() }
    }
}
