package com.rewordly.app.domain.usecase

import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.VocabularyRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SearchWordsUseCase @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
) {
    operator fun invoke(language: LearningLanguage, rawQuery: String): Flow<List<WordWithProgress>> {
        val query = normalize(rawQuery)
        return if (query.isEmpty()) flowOf(emptyList()) else vocabularyRepository.searchWords(language, query)
    }

    companion object {
        fun normalize(query: String): String = query.trim().replace(Regex("\\s+"), " ")
    }
}
