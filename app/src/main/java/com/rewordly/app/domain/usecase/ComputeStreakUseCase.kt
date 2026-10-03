package com.rewordly.app.domain.usecase

import com.rewordly.app.domain.model.DailyActivity
import com.rewordly.app.domain.model.Streak
import java.time.LocalDate
import javax.inject.Inject

/**
 * Local learning streak from the calendar days the user was active.
 * A day counts as active when at least one word was learned or reviewed. No scheduling involved.
 */
class ComputeStreakUseCase @Inject constructor() {
    operator fun invoke(activity: List<DailyActivity>, today: LocalDate): Streak = compute(activity, today)

    companion object {
        fun compute(activity: List<DailyActivity>, today: LocalDate): Streak {
            val activeDays = activity.filter { it.actions > 0 }.map { it.date }.toSortedSet()
            if (activeDays.isEmpty()) return Streak()
            val lastActive = activeDays.last()
            val current = when {
                lastActive == today || lastActive == today.minusDays(1) -> countBack(activeDays, lastActive)
                else -> 0
            }
            return Streak(current = current, longest = longestRun(activeDays), lastActiveDate = lastActive)
        }

        private fun countBack(days: Set<LocalDate>, from: LocalDate): Int {
            var streak = 0
            var day = from
            while (days.contains(day)) {
                streak++
                day = day.minusDays(1)
            }
            return streak
        }

        private fun longestRun(days: Set<LocalDate>): Int {
            var longest = 0
            var run = 0
            var previous: LocalDate? = null
            for (day in days) {
                run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
                previous = day
                if (run > longest) longest = run
            }
            return longest
        }
    }
}
