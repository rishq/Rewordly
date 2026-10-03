package com.rewordly.app.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

interface TimeProvider {
    fun nowMillis(): Long

    fun zone(): ZoneId

    fun today(): LocalDate = Instant.ofEpochMilli(nowMillis()).atZone(zone()).toLocalDate()

    fun currentHour(): Int = Instant.ofEpochMilli(nowMillis()).atZone(zone()).hour

    fun startOfTodayMillis(): Long = today().atStartOfDay(zone()).toInstant().toEpochMilli()

    fun startOfTomorrowMillis(): Long = today().plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli()
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
