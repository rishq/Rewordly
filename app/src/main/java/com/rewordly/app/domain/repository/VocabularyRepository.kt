package com.rewordly.app.domain.repository

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import kotlinx.coroutines.flow.Flow

interface VocabularyRepository {
    /** Inserts the bundled vocabulary if the database is empty. */
    suspend fun ensureSeeded(): AppResult<Unit>

    fun observeWords(language: LearningLanguage): Flow<List<WordWithProgress>>

    fun observeRecentWords(language: LearningLanguage, limit: Int): Flow<List<WordWithProgress>>

    fun observeWord(wordId: String): Flow<WordWithProgress?>

    fun searchWords(language: LearningLanguage, query: String): Flow<List<WordWithProgress>>

    suspend fun setSaved(wordId: String, saved: Boolean): AppResult<Unit>

    suspend fun setStatus(wordId: String, status: WordStatus): AppResult<Unit>
}
