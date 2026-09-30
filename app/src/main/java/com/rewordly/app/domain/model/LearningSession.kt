package com.rewordly.app.domain.model

data class LearningSession(
    val words: List<WordWithProgress>,
    val startedAt: Long,
)
