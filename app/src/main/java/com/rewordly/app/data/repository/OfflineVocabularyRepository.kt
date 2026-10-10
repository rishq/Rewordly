package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.core.database.dao.WordDao
import com.rewordly.app.core.database.dao.WordProgressDao
import com.rewordly.app.core.database.entity.WordProgressEntity
import com.rewordly.app.core.database.safeDbCall
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.data.local.exampleEntities
import com.rewordly.app.data.local.toDomain
import com.rewordly.app.data.local.toEntity
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordKeys
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.VocabularyRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
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

    override fun observeWordsByIds(ids: List<String>): Flow<List<WordWithProgress>> = if (ids.isEmpty()) {
        flowOf(emptyList())
    } else {
        wordDao.observeByIds(ids).map { list -> list.map { it.toDomain() } }
    }

    override fun observeRecentWords(language: LearningLanguage, limit: Int): Flow<List<WordWithProgress>> =
        wordDao.observeRecent(language.tag, limit).map { list -> list.map { it.toDomain() } }

    override fun observeSavedWords(language: LearningLanguage): Flow<List<WordWithProgress>> =
        wordDao.observeSaved(language.tag).map { list -> list.map { it.toDomain() } }

    override fun observeWord(wordId: String): Flow<WordWithProgress?> =
        wordDao.observeById(wordId).map { it?.toDomain() }.distinctUntilChanged()

    override suspend fun setSaved(wordId: String, saved: Boolean): AppResult<Unit> = safeDbCall {
        val now = timeProvider.nowMillis()
        progressDao.update(wordId, blankProgress(wordId, now)) { it.copy(isSaved = saved, updatedAt = now) }
    }

    override suspend fun recordView(wordId: String): AppResult<Unit> = safeDbCall {
        val now = timeProvider.nowMillis()
        progressDao.update(wordId, blankProgress(wordId, now)) {
            it.copy(views = it.views + 1, lastViewedAt = now, updatedAt = now)
        }
    }

    override suspend fun findExistingIds(
        language: LearningLanguage,
        keys: Collection<String>,
    ): AppResult<Map<String, String>> = safeDbCall {
        if (keys.isEmpty()) {
            emptyMap()
        } else {
            wordDao.findByNormalizedText(language.tag, keys.map(WordKeys::normalize).distinct())
                .associate { WordKeys.normalize(it.text) to it.id }
        }
    }

    override suspend fun addWords(words: List<Word>): AppResult<List<String>> = safeDbCall {
        val existing = wordDao.findByNormalizedText(
            LearningLanguage.ENGLISH.tag,
            words.map { WordKeys.normalize(it.text) }.distinct(),
        ).map { WordKeys.normalize(it.text) }.toSet()
        val fresh = words.distinctBy { WordKeys.normalize(it.text) }
            .filter { WordKeys.normalize(it.text) !in existing }
        val base = timeProvider.nowMillis()
        wordDao.insertVocabulary(
            words = fresh.mapIndexed { index, word -> word.toEntity(createdAt = base + index) },
            examples = fresh.flatMap { it.exampleEntities() },
        )
        fresh.map { it.id }
    }

    /** The row to start from when the word has no progress yet; the DAO fills it in inside its transaction. */
    private fun blankProgress(wordId: String, updatedAt: Long): WordProgressEntity =
        WordProgress(wordId = wordId).toEntity(updatedAt = updatedAt)
}
