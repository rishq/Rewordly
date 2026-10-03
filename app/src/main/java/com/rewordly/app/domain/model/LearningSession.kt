package com.rewordly.app.domain.model

/**
 * A single learning run through a fixed set of words. Kept as ids only so it can be
 * serialized into a [androidx.lifecycle.SavedStateHandle] and survive process death.
 */
data class LearningSession(
    val sessionId: String,
    val wordIds: List<String>,
    val currentPosition: Int = 0,
    val startedAt: Long,
    val completedAt: Long? = null,
    val completedWordIds: Set<String> = emptySet(),
) {
    val total: Int get() = wordIds.size
    val completedCount: Int get() = completedWordIds.size
    val isFinished: Boolean get() = wordIds.isNotEmpty() && completedWordIds.size >= wordIds.size
    val fraction: Float
        get() = if (total == 0) 0f else (completedCount.toFloat() / total).coerceIn(0f, 1f)
}
