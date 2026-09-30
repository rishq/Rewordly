package com.rewordly.app.domain.model

enum class WordStatus { NEW, LEARNING, LEARNED }

/** Per-word learning state. Review fields are prepared for a future spaced-repetition system. */
data class WordProgress(
    val wordId: String,
    val status: WordStatus = WordStatus.NEW,
    val isSaved: Boolean = false,
    val correctAnswers: Int = 0,
    val incorrectAnswers: Int = 0,
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long? = null,
)

data class WordWithProgress(
    val word: Word,
    val progress: WordProgress,
)
