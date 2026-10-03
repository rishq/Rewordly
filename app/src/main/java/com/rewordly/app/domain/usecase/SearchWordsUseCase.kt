package com.rewordly.app.domain.usecase

import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.VocabularyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Local search over the bundled vocabulary: matches the English word and the Russian
 * translation, case-insensitively. Room delivers the list off the main thread.
 */
class SearchWordsUseCase @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
) {
    operator fun invoke(language: LearningLanguage, rawQuery: String): Flow<List<WordWithProgress>> {
        val query = normalize(rawQuery)
        return if (query.isEmpty()) {
            flowOf(emptyList())
        } else {
            vocabularyRepository.observeWords(language).map {
                match(it, query)
            }
        }
    }

    companion object {
        fun normalize(query: String): String = query.trim().replace(Regex("\\s+"), " ")

        fun match(words: List<WordWithProgress>, rawQuery: String): List<WordWithProgress> {
            val query = normalize(rawQuery).lowercase()
            if (query.isEmpty()) return emptyList()
            return words
                .mapNotNull { item -> matchRank(item, query)?.let { rank -> item to rank } }
                .sortedWith(compareBy({ it.second }, { it.first.word.text }))
                .map { it.first }
        }

        /** Lower rank sorts first: exact English, then prefix, then substring, then translation. */
        private fun matchRank(item: WordWithProgress, query: String): Int? {
            val text = item.word.text.lowercase()
            return when {
                text == query -> 0
                text.startsWith(query) -> 1
                text.contains(query) -> 2
                item.word.translation.text.lowercase().contains(query) -> 3
                else -> null
            }
        }
    }
}
