package com.rewordly.app.domain.repository

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordWithProgress
import kotlinx.coroutines.flow.Flow

interface VocabularyRepository {
    /** Inserts the bundled vocabulary if the database is empty. */
    suspend fun ensureSeeded(): AppResult<Unit>

    fun observeWords(language: LearningLanguage): Flow<List<WordWithProgress>>

    /**
     * Only the words in [ids]. A learning session knows its own word ids, so it reads those rows instead of
     * re-hydrating the whole vocabulary every time a card is answered.
     */
    fun observeWordsByIds(ids: List<String>): Flow<List<WordWithProgress>>

    fun observeRecentWords(language: LearningLanguage, limit: Int): Flow<List<WordWithProgress>>

    fun observeSavedWords(language: LearningLanguage): Flow<List<WordWithProgress>>

    fun observeWord(wordId: String): Flow<WordWithProgress?>

    suspend fun setSaved(wordId: String, saved: Boolean): AppResult<Unit>

    /** Registers that the user opened the card, so progress shows how often a word was seen. */
    suspend fun recordView(wordId: String): AppResult<Unit>

    /** Maps each normalized text in [keys] that already exists locally to the id of that word. */
    suspend fun findExistingIds(language: LearningLanguage, keys: Collection<String>): AppResult<Map<String, String>>

    /**
     * Inserts words that are not in the vocabulary yet and returns the ids that were added. Existing words, their
     * progress and review history are never touched.
     */
    suspend fun addWords(words: List<Word>): AppResult<List<String>>
}
