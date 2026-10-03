package com.rewordly.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.rewordly.app.MainActivity
import com.rewordly.app.R
import com.rewordly.app.core.common.withLocale
import com.rewordly.app.core.navigation.NotificationDestination
import com.rewordly.app.domain.repository.ProgressRepository
import com.rewordly.app.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun progressRepository(): ProgressRepository

    fun settingsRepository(): SettingsRepository
}

/**
 * Compact overview of today's learning: goal progress, the streak, what is waiting for review and a
 * button that opens the learning screen.
 *
 * Data access goes through the same [ProgressRepository] the rest of the app uses, so the widget
 * cannot disagree with the Progress screen. Nothing is read while rendering: the state is fetched
 * once when the widget is (re)built, and the app pushes a refresh when progress changes.
 */
class ProgressWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPoints.get(context.applicationContext, WidgetEntryPoint::class.java)
        val language = entry.settingsRepository().settings.first().interfaceLanguage
        val state = loadState(entry.progressRepository())
        val localized = context.withLocale(language)
        provideContent { Content(state, localized) }
    }

    /** A failed read shows the empty state instead of an error: a widget must never be blank. */
    private suspend fun loadState(repository: ProgressRepository): WidgetState = try {
        WidgetStateMapper.from(repository.observeOverview().first())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        WidgetState.EMPTY
    }

    @Composable
    private fun Content(state: WidgetState, localized: Context) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .appWidgetBackground()
                .background(WidgetColors.background)
                .cornerRadius(CORNER_RADIUS)
                .padding(12.dp)
                .clickable(openApp(localized, NotificationDestination.HOME)),
        ) {
            Header(state, localized)
            Spacer(GlanceModifier.height(8.dp))
            if (state.hasHistory) {
                Goal(state, localized)
            } else {
                Text(
                    text = localized.getString(R.string.widget_empty),
                    style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp),
                )
            }
            Spacer(GlanceModifier.height(12.dp))
            Actions(state, localized)
        }
    }

    @Composable
    private fun Header(state: WidgetState, localized: Context) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = localized.getString(R.string.app_name),
                style = TextStyle(color = WidgetColors.muted, fontSize = 13.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (state.streakDays > 0) {
                Text(
                    text = localized.resources.getQuantityString(
                        R.plurals.widget_streak,
                        state.streakDays,
                        state.streakDays,
                    ),
                    style = TextStyle(color = WidgetColors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }

    @Composable
    private fun Goal(state: WidgetState, localized: Context) {
        Text(
            text = if (state.isGoalReached) {
                localized.getString(R.string.widget_goal_reached, state.wordsLearnedToday)
            } else {
                localized.getString(R.string.widget_goal_progress, state.wordsLearnedToday, state.dailyGoal)
            },
            style = TextStyle(color = WidgetColors.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(6.dp))
        GoalBar(state.goalFraction)
        if (state.dueWords > 0) {
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = localized.resources.getQuantityString(
                    R.plurals.widget_due,
                    state.dueWords,
                    state.dueWords,
                ),
                style = TextStyle(color = WidgetColors.primary, fontSize = 13.sp),
            )
        }
    }

    /** The built-in bar renders identically on every launcher, unlike a hand-rolled pair of boxes. */
    @Composable
    private fun GoalBar(fraction: Float) {
        LinearProgressIndicator(
            progress = fraction,
            color = WidgetColors.primary,
            backgroundColor = WidgetColors.track,
            modifier = GlanceModifier.fillMaxWidth(),
        )
    }

    @Composable
    private fun Actions(state: WidgetState, localized: Context) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(
                text = localized.getString(R.string.widget_action_start),
                onClick = openApp(localized, NotificationDestination.LEARN),
            )
            if (state.dueWords > 0) {
                Spacer(GlanceModifier.defaultWeight())
                Button(
                    text = localized.getString(R.string.widget_action_review),
                    onClick = openApp(localized, NotificationDestination.REVIEW),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Image(
                provider = ImageProvider(R.drawable.ic_widget_refresh),
                contentDescription = localized.getString(R.string.widget_action_refresh),
                modifier = GlanceModifier.padding(4.dp).clickable(actionRunCallback<RefreshWidgetAction>()),
                colorFilter = ColorFilter.tint(WidgetColors.muted),
            )
        }
    }

    /**
     * Opens the app on a specific screen. The widget never starts a learning session on its own: the
     * app decides what to show, which keeps the navigation state intact.
     */
    private fun openApp(localized: Context, destination: NotificationDestination): Action {
        val intent = Intent(localized, MainActivity::class.java)
            .putExtra(NotificationDestination.EXTRA, destination.extraValue)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return actionStartActivity(intent)
    }

    private companion object {
        val CORNER_RADIUS = 16.dp
        val BAR_HEIGHT = 6.dp
        val BAR_RADIUS = 3.dp
    }
}

/** Re-reads the local progress and redraws the widget. Triggered by the refresh control. */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        ProgressWidget().update(context, glanceId)
    }
}

class ProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProgressWidget()
}
