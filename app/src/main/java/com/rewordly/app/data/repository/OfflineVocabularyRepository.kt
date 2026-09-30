package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.core.database.dao.WordDao
import com.rewordly.app.core.database.dao.WordProgressDao
import com.rewordly.app.core.database.safeDbCall
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.data.local.exampleEntities
import com.rewordly.app.data.local.toDomain
import com.rewordly.app.data.local.toEntity
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.VocabularyRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class OfflineVocabularyRepository @Inject constructor(
    private val wordDao: WordDao,
    private val progressDao: WordProgressDao,
    private val timeProvider: TimeProvider,
) : VocabularyRepository {

    override suspend fun ensureSeeded(): AppResult<Unit> = safeDbCall {
        if (wordDao.count() == 0) {
            val base = timeProvider.nowMillis()
            val words = MockVocabulary.words
            // Offsets keep the bundled order stable when sorted by created_at.
            wordDao.insertVocabulary(
                words = words.mapIndexed { index, word -> word.toEntity(createdAt = base + index) },
                examples = words.flatMap { it.exampleEntities() },
            )
        }
    }

    override fun observeWords(language: LearningLanguage): Flow<List<WordWithProgress>> =
        wordDao.observeAll(language.tag).map { list -> list.map { it.toDomain() } }

    override fun observeRecentWords(language: LearningLanguage, limit: Int): Flow<List<WordWithProgress>> =
        wordDao.observeRecent(language.tag, limit).map { list -> list.map { it.toDomain() } }

    override fun observeWord(wordId: String): Flow<WordWithProgress?> =
        wordDao.observeById(wordId).map { it?.toDomain() }.distinctUntilChanged()

    override fun searchWords(language: LearningLanguage, query: String): Flow<List<WordWithProgress>> {
        val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return wordDao.search(language.tag, pattern = "%$escaped%", prefix = "$escaped%")
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun setSaved(wordId: String, saved: Boolean): AppResult<Unit> =
        updateProgress(wordId) { it.copy(isSaved = saved) }

    override suspend fun setStatus(wordId: String, status: WordStatus): AppResult<Unit> =
        updateProgress(wordId) { it.copy(status = status, lastReviewedAt = timeProvider.nowMillis()) }

    private suspend fun updateProgress(wordId: String, transform: (WordProgress) -> WordProgress): AppResult<Unit> =
        safeDbCall {
            val current = progressDao.get(wordId)?.toDomain() ?: WordProgress(wordId = wordId)
            progressDao.upsert(transform(current).toEntity(updatedAt = timeProvider.nowMillis()))
        }
}
