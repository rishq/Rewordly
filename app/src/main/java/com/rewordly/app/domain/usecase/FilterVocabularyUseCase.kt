package com.rewordly.app.domain.usecase

import com.rewordly.app.domain.model.StatusFilter
import com.rewordly.app.domain.model.VocabularyFilters
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.model.difficultyOrNull
import javax.inject.Inject

/** Pure list filtering so the UI never inspects progress itself. Difficulty and status combine. */
class FilterVocabularyUseCase @Inject constructor() {
    operator fun invoke(words: List<WordWithProgress>, filters: VocabularyFilters): List<WordWithProgress> =
        apply(words, filters)

    companion object {
        fun apply(words: List<WordWithProgress>, filters: VocabularyFilters): List<WordWithProgress> {
            val difficulty = filters.difficulty.difficultyOrNull()
            return words.filter { item ->
                (difficulty == null || item.word.difficulty == difficulty) && matchesStatus(item, filters.status)
            }
        }

        private fun matchesStatus(item: WordWithProgress, status: StatusFilter): Boolean = when (status) {
            StatusFilter.ALL -> true
            StatusFilter.NEW -> item.progress.status == WordStatus.NEW
            StatusFilter.LEARNING -> item.progress.status == WordStatus.LEARNING
            StatusFilter.LEARNED -> item.progress.status == WordStatus.LEARNED
            StatusFilter.SAVED -> item.progress.isSaved
        }
    }
}
