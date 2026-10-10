package com.rewordly.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import com.rewordly.app.domain.repository.ProgressRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Pushes the current progress to the home screen widget.
 *
 * The widget is never polled: it is redrawn only when the reduced state actually changes, which is
 * driven by the same local Room flow the rest of the app uses. While the app is closed nothing can
 * change, so the widget simply keeps showing the last known state.
 */
@Singleton
class ProgressWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val progressRepository: ProgressRepository,
) {
    /** Never returns; run it in an app scope. */
    suspend fun observe() {
        // With no widget placed there is nothing to push to, and Glance's updateAll is not cheap. A widget
        // added later still renders correctly, because the launcher rebuilds it from the current data.
        if (!hasPlacedWidgets()) return
        progressRepository.observeOverview()
            .map(WidgetStateMapper::from)
            .distinctUntilChanged()
            .collect { refresh() }
    }

    private fun hasPlacedWidgets(): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ProgressWidgetReceiver::class.java))
        return ids.isNotEmpty()
    }

    /** Redraws every placed widget from the current local data. */
    suspend fun refresh() {
        ProgressWidget().updateAll(context)
    }
}
