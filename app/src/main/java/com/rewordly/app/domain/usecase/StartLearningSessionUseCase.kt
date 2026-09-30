package com.rewordly.app.domain.usecase

import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.LearningSession
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.VocabularyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Builds a learning session from the local vocabulary: words not yet learned come first.
 * A real scheduling algorithm will replace this ordering later.
 */
class StartLearningSessionUseCase @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(language: LearningLanguage): LearningSession {
        val words = vocabularyRepository.observeWords(language).first()
        return LearningSession(words = order(words), startedAt = timeProvider.nowMillis())
    }

    companion object {
        fun order(words: List<WordWithProgress>): List<WordWithProgress> =
            words.sortedBy { it.progress.status == WordStatus.LEARNED }
    }
}
