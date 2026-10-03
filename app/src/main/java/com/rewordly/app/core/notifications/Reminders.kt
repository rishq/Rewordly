package com.rewordly.app.core.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rewordly.app.MainActivity
import com.rewordly.app.R
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.core.common.withLocale
import com.rewordly.app.core.navigation.NotificationDestination
import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.ReminderKind
import com.rewordly.app.domain.repository.NotificationLedger
import com.rewordly.app.domain.repository.ProgressRepository
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.service.ReminderContext
import com.rewordly.app.domain.service.ReminderDecider
import com.rewordly.app.domain.service.ReminderDecision
import com.rewordly.app.domain.service.ReminderScheduler
import com.rewordly.app.domain.service.ReminderSyncer
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val WORK_NAME = "review_reminder"
private const val CHANNEL_REMINDERS = "review_reminders"
private const val CHANNEL_ENCOURAGEMENT = "learning_encouragement"
private const val MAX_RETRIES = 3

/**
 * One-time work chained day after day instead of a periodic request: periodic work cannot hit a
 * wall-clock time, and re-computing the delay each day keeps DST and time zone changes correct.
 * WorkManager respects Doze and background limits, so delivery can be a little late but never needs
 * exact-alarm permissions.
 */
class WorkManagerReminderScheduler @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val timeProvider: TimeProvider,
) : ReminderScheduler {
    override fun scheduleAt(triggerAtMillis: Long) {
        val delay = (triggerAtMillis - timeProvider.nowMillis()).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReviewReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds
    abstract fun bindReminderScheduler(impl: WorkManagerReminderScheduler): ReminderScheduler
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun settingsRepository(): SettingsRepository

    fun progressRepository(): ProgressRepository

    fun notificationLedger(): NotificationLedger

    fun reminderSyncer(): ReminderSyncer

    fun timeProvider(): TimeProvider
}

/**
 * Delivers the scheduled reminder.
 *
 * The worker only decides *whether* something useful can be said; all the rules live in
 * [ReminderDecider] so they are covered by plain unit tests. Whatever happens, the next day is
 * scheduled again, otherwise the chain would stop after a single skipped reminder.
 */
class ReviewReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = EntryPoints.get(applicationContext, ReminderEntryPoint::class.java)
        val settings = entry.settingsRepository().settings.first()
        // Disabled reminders send nothing and do not chain another run.
        if (!settings.notificationsEnabled) return Result.success()

        val time = entry.timeProvider()
        val overview = try {
            entry.progressRepository().observeOverview().first()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (overview == null) {
            if (runAttemptCount < MAX_RETRIES) return Result.retry()
            entry.reminderSyncer().resync()
            return Result.success()
        }

        val today = time.today()
        val ledger = entry.notificationLedger()
        val context = ReminderContext.of(
            settings = settings,
            overview = overview,
            nowMillis = time.nowMillis(),
            zone = time.zone(),
            lastNotifiedAt = ledger.lastNotifiedAt(),
            kindsNotifiedToday = ledger.kindsNotifiedOn(today),
        )
        when (val decision = ReminderDecider.decide(context)) {
            is ReminderDecision.Notify -> {
                if (ReminderNotifier.show(applicationContext, decision.kind, context, settings.interfaceLanguage)) {
                    ledger.record(decision.kind, today, context.nowMillis)
                }
            }
            ReminderDecision.Skip -> Unit
        }
        entry.reminderSyncer().resync()
        return Result.success()
    }
}

/** Re-schedules the reminder when the clock or the time zone changes. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val syncer = EntryPoints.get(context.applicationContext, ReminderEntryPoint::class.java).reminderSyncer()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                syncer.resync()
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Renders one notification per kind.
 *
 * Everything the user sees is phrased around what they can do next, and nothing about their
 * vocabulary is included: a notification may appear on a lock screen.
 */
object ReminderNotifier {
    fun canNotify(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Returns whether the notification was actually posted, so the ledger only records real sends. */
    @SuppressLint("MissingPermission")
    fun show(context: Context, kind: ReminderKind, reminder: ReminderContext, language: InterfaceLanguage): Boolean {
        if (!canNotify(context)) return false
        val localized = localizedContext(context, language)
        val channel = channelOf(kind)
        ensureChannel(context, localized, channel)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(localized.getString(titleOf(kind)))
            .setContentText(textOf(kind, localized, reminder))
            .setStyle(NotificationCompat.BigTextStyle().bigText(textOf(kind, localized, reminder)))
            .setContentIntent(openIntent(context, contentDestination(kind)))
            .addAction(0, localized.getString(R.string.notification_action_start), openIntent(context, LEARN))
            .apply {
                if (kind == ReminderKind.DUE_WORDS) {
                    addAction(0, localized.getString(R.string.notification_action_review), openIntent(context, REVIEW))
                }
            }
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(kind), notification)
        return true
    }

    /** Tap opens the screen the notification is actually about. */
    private fun contentDestination(kind: ReminderKind): NotificationDestination = when (kind) {
        ReminderKind.DUE_WORDS -> REVIEW
        ReminderKind.GOAL_INCOMPLETE, ReminderKind.STREAK_AT_RISK, ReminderKind.DAILY -> LEARN
        ReminderKind.SESSION_DONE -> HOME
    }

    private fun titleOf(kind: ReminderKind): Int = when (kind) {
        ReminderKind.DUE_WORDS -> R.string.notification_due_title
        ReminderKind.GOAL_INCOMPLETE -> R.string.notification_goal_title
        ReminderKind.STREAK_AT_RISK -> R.string.notification_streak_title
        ReminderKind.DAILY -> R.string.notification_daily_title
        ReminderKind.SESSION_DONE -> R.string.notification_done_title
    }

    private fun textOf(kind: ReminderKind, localized: Context, reminder: ReminderContext): String {
        val resources = localized.resources
        return when (kind) {
            ReminderKind.DUE_WORDS ->
                resources.getQuantityString(R.plurals.notification_due_text, reminder.dueWords, reminder.dueWords)
            ReminderKind.GOAL_INCOMPLETE -> resources.getQuantityString(
                R.plurals.notification_goal_text,
                reminder.goalRemaining,
                reminder.goalRemaining,
                reminder.dailyGoal,
            )
            ReminderKind.STREAK_AT_RISK -> resources.getQuantityString(
                R.plurals.notification_streak_text,
                reminder.streakDays,
                reminder.streakDays,
            )
            ReminderKind.DAILY -> localized.getString(R.string.notification_daily_text)
            ReminderKind.SESSION_DONE -> resources.getQuantityString(
                R.plurals.notification_done_text,
                reminder.wordsLearnedToday,
                reminder.wordsLearnedToday,
            )
        }
    }

    private fun channelOf(kind: ReminderKind): String =
        if (kind == ReminderKind.SESSION_DONE) CHANNEL_ENCOURAGEMENT else CHANNEL_REMINDERS

    /** A distinct request code per destination keeps the extras of each action intact. */
    private fun openIntent(context: Context, destination: NotificationDestination): PendingIntent =
        PendingIntent.getActivity(
            context,
            destination.ordinal,
            Intent(context, MainActivity::class.java)
                .putExtra(NotificationDestination.EXTRA, destination.extraValue)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** A separate id per kind so two different kinds on one day do not replace each other. */
    private fun notificationId(kind: ReminderKind): Int = NOTIFICATION_ID_BASE + kind.ordinal

    private fun ensureChannel(context: Context, localized: Context, channelId: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nameRes = if (channelId == CHANNEL_ENCOURAGEMENT) {
            R.string.notification_channel_encouragement
        } else {
            R.string.notification_channel_name
        }
        val channel =
            NotificationChannel(channelId, localized.getString(nameRes), NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun localizedContext(context: Context, language: InterfaceLanguage): Context = context.withLocale(language)

    private const val NOTIFICATION_ID_BASE = 1001
    private val LEARN = NotificationDestination.LEARN
    private val REVIEW = NotificationDestination.REVIEW
    private val HOME = NotificationDestination.HOME
}
