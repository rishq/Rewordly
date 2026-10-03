package com.rewordly.app.domain.model

/** Difficulty filter shown in the UI. [ALL] keeps every CEFR level. */
enum class DifficultyFilter { ALL, A1, A2, B1, B2 }

enum class StatusFilter { ALL, NEW, LEARNING, LEARNED, SAVED }

data class VocabularyFilters(
    val difficulty: DifficultyFilter = DifficultyFilter.ALL,
    val status: StatusFilter = StatusFilter.ALL,
) {
    val isActive: Boolean get() = this != VocabularyFilters()
}

fun DifficultyFilter.difficultyOrNull(): Difficulty? = when (this) {
    DifficultyFilter.ALL -> null
    DifficultyFilter.A1 -> Difficulty.A1
    DifficultyFilter.A2 -> Difficulty.A2
    DifficultyFilter.B1 -> Difficulty.B1
    DifficultyFilter.B2 -> Difficulty.B2
}
