package com.rewordly.app.domain.usecase

import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.VocabularyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Placeholder review queue: saved or in-progress words first, then the rest. No spaced repetition yet. */
class BuildReviewQueueUseCase @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
) {
    suspend operator fun invoke(language: LearningLanguage, size: Int = DEFAULT_SIZE): List<WordWithProgress> =
        select(vocabularyRepository.observeWords(language).first(), size)

    companion object {
        const val DEFAULT_SIZE = 10

        fun select(words: List<WordWithProgress>, size: Int): List<WordWithProgress> {
            val (priority, rest) = words.partition {
                it.progress.isSaved || it.progress.status == WordStatus.LEARNING
            }
            return (priority + rest).take(size)
        }
    }
}
