package com.rewordly.app.testing

import com.rewordly.app.core.common.TimeProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Runs viewModelScope work on a test dispatcher so view models can be tested without Android. */
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

class FixedTimeProvider(
    private val now: Long = 1_700_000_000_000,
    private val zone: ZoneId = ZoneId.of("UTC"),
) : TimeProvider {
    override fun nowMillis(): Long = now

    override fun zone(): ZoneId = zone

    override fun today(): LocalDate = LocalDate.of(2023, 11, 15)
}

/** Controllable clock: tests move time explicitly and the derived local day always matches the instant. */
class TestTimeProvider(
    startMillis: Long = 1_700_000_000_000,
    private var zone: ZoneId = ZoneId.of("UTC"),
) : TimeProvider {
    private var now: Long = startMillis

    override fun nowMillis(): Long = now

    override fun zone(): ZoneId = zone

    fun setNow(millis: Long) {
        now = millis
    }

    fun advanceMillis(millis: Long) {
        now += millis
    }

    fun advanceDays(days: Long) = advanceMillis(days * 24 * 60 * 60 * 1000)

    fun setZone(newZone: ZoneId) {
        zone = newZone
    }
}
