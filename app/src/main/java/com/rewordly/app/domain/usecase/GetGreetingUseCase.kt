package com.rewordly.app.domain.usecase

import com.rewordly.app.core.common.TimeProvider
import javax.inject.Inject

enum class Greeting { MORNING, AFTERNOON, EVENING, NIGHT }

class GetGreetingUseCase @Inject constructor(
    private val timeProvider: TimeProvider,
) {
    operator fun invoke(): Greeting = greetingForHour(timeProvider.currentHour())

    companion object {
        fun greetingForHour(hour: Int): Greeting = when (hour) {
            in 5..11 -> Greeting.MORNING
            in 12..17 -> Greeting.AFTERNOON
            in 18..22 -> Greeting.EVENING
            else -> Greeting.NIGHT
        }
    }
}
