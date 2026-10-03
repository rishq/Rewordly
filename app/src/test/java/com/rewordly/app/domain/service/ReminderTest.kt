package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.UserSettings
import com.rewordly.app.domain.usecase.ReminderTimeCalculator
import com.rewordly.app.testing.FakeSettingsRepository
import com.rewordly.app.testing.MainDispatcherRule
import com.rewordly.app.testing.TestTimeProvider
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReminderTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val utc = ZoneId.of("UTC")

    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    // ---- next trigger time ----

    @Test
    fun nextTrigger_isTodayWhenTheTimeIsStillAhead() {
        val now = at(utc, 2024, 5, 10, 8)
        assertEquals(at(utc, 2024, 5, 10, 19), ReminderTimeCalculator.nextTriggerMillis(now, utc, 19, 0))
    }

    @Test
    fun nextTrigger_isTomorrowWhenTheTimeHasPassed_orIsExactlyNow() {
        val now = at(utc, 2024, 5, 10, 19)
        assertEquals(at(utc, 2024, 5, 11, 19), ReminderTimeCalculator.nextTriggerMillis(now, utc, 19, 0))
        assertEquals(at(utc, 2024, 5, 11, 7, 30), ReminderTimeCalculator.nextTriggerMillis(now, utc, 7, 30))
    }

    @Test
    fun nextTrigger_usesTheCurrentZone() {
        val almaty = ZoneId.of("Asia/Almaty")
        val now = at(utc, 2024, 5, 10, 12) // 17:00 in Almaty
        assertEquals(at(almaty, 2024, 5, 10, 19), ReminderTimeCalculator.nextTriggerMillis(now, almaty, 19, 0))
        assertEquals(at(utc, 2024, 5, 10, 19), ReminderTimeCalculator.nextTriggerMillis(now, utc, 19, 0))
    }

    @Test
    fun nextTrigger_surviveDstGaps() {
        val ny = ZoneId.of("America/New_York")
        // 02:30 does not exist on 2024-03-10; the clock jumps from 02:00 to 03:00.
        val now = at(ny, 2024, 3, 10, 0, 30)
        val trigger = ReminderTimeCalculator.nextTriggerMillis(now, ny, 2, 30)
        assertTrue(trigger > now)
        assertTrue(trigger - now < 3 * 60 * 60 * 1000)
    }

    @Test
    fun nextTrigger_clampsInvalidTimes() {
        val now = at(utc, 2024, 5, 10, 8)
        assertEquals(at(utc, 2024, 5, 10, 23, 59), ReminderTimeCalculator.nextTriggerMillis(now, utc, 99, 99))
    }

    // ---- syncing with settings ----

    private class FakeScheduler : ReminderScheduler {
        val scheduled = mutableListOf<Long>()
        var cancelled = 0

        override fun scheduleAt(triggerAtMillis: Long) {
            scheduled += triggerAtMillis
        }

        override fun cancel() {
            cancelled++
        }
    }

    @Test
    fun disabledReminders_cancelAndNeverSchedule() = runTest {
        val scheduler = FakeScheduler()
        val syncer =
            ReminderSyncer(
                FakeSettingsRepository(UserSettings(notificationsEnabled = false)),
                scheduler,
                TestTimeProvider(),
            )
        syncer.resync()
        assertTrue(scheduler.scheduled.isEmpty())
        assertEquals(1, scheduler.cancelled)
    }

    @Test
    fun enabledReminders_scheduleAtTheChosenTime() = runTest {
        val time = TestTimeProvider(startMillis = at(utc, 2024, 5, 10, 8))
        val settings =
            FakeSettingsRepository(UserSettings(notificationsEnabled = true, reminderHour = 20, reminderMinute = 15))
        val scheduler = FakeScheduler()
        ReminderSyncer(settings, scheduler, time).resync()
        assertEquals(listOf(at(utc, 2024, 5, 10, 20, 15)), scheduler.scheduled)
    }

    @Test
    fun resync_afterATimeZoneChange_movesTheTrigger() = runTest {
        val time = TestTimeProvider(startMillis = at(utc, 2024, 5, 10, 8))
        val settings = FakeSettingsRepository(UserSettings(notificationsEnabled = true, reminderHour = 19))
        val scheduler = FakeScheduler()
        val syncer = ReminderSyncer(settings, scheduler, time)
        syncer.resync()
        time.setZone(ZoneId.of("Asia/Almaty"))
        syncer.resync()
        assertEquals(at(utc, 2024, 5, 10, 19), scheduler.scheduled[0])
        assertEquals(at(ZoneId.of("Asia/Almaty"), 2024, 5, 10, 19), scheduler.scheduled[1])
    }

    @Test
    fun keepInSync_reactsToToggleAndTimeChanges_butNotToUnrelatedSettings() = runTest {
        val settings = FakeSettingsRepository(UserSettings(notificationsEnabled = false))
        val scheduler = FakeScheduler()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            ReminderSyncer(settings, scheduler, TestTimeProvider()).keepInSync()
        }

        assertEquals(1, scheduler.cancelled)
        settings.setNotificationsEnabled(true)
        assertEquals(1, scheduler.scheduled.size)
        settings.setReminderTime(6, 45)
        assertEquals(2, scheduler.scheduled.size)
        settings.setDailyGoal(20)
        assertEquals(2, scheduler.scheduled.size)
        settings.setNotificationsEnabled(false)
        assertEquals(2, scheduler.cancelled)
        job.cancel()
    }
}
