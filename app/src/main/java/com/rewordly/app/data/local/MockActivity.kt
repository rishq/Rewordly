package com.rewordly.app.data.local

/**
 * Placeholder streak/activity figures until daily activity is tracked in the database.
 * Index 0 is six days ago; the last entry is replaced by today's real count.
 */
object MockActivity {
    val wordsLearnedLastWeek: List<Int> = listOf(8, 14, 20, 5, 22, 17, 0)
    const val REVIEWED_BASELINE = 64
    const val CURRENT_STREAK = 6
    const val LONGEST_STREAK = 14
}
