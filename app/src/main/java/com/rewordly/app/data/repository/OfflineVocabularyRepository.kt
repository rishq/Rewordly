package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.common.IdProvider
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.core.database.dao.WordDao
import com.rewordly.app.core.database.dao.WordProgressDao
import com.rewordly.app.core.database.entity.WordEntity
import com.rewordly.app.core.database.entity.WordExampleEntity
import com.rewordly.app.core.database.entity.WordProgressEntity
import com.rewordly.app.core.database.safeDbCall
import com.rewordly.app.data.local.exampleEntities
import com.rewordly.app.data.local.toDomain
import com.rewordly.app.data.local.toEntity
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.VocabularyEntry
import com.rewordly.app.domain.model.VocabularyFileFormat
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordKeys
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordSource
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.VocabularyRepository
import com.rewordly.app.domain.service.ImportLimits
import com.rewordly.app.domain.service.VocabularyImportParser
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
    private val importParser: VocabularyImportParser,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider,
    private val seedSource: VocabularySeedSource,
) : VocabularyRepository {

    override suspend fun ensureSeeded(): AppResult<Unit> = safeDbCall {
        if (wordDao.count() != 0) return@safeDbCall
        val base = timeProvider.nowMillis()
        // The bundled B1 starter vocabulary: the learn loop needs cards from the very first run.
        val entries = importParser.parse(seedSource.read(), VocabularyFileFormat.CSV, ImportLimits()).entries
        var index = 0
        val seedWords = mutableListOf<WordEntity>()
        val seedExamples = mutableListOf<WordExampleEntity>()
        for (parsedEntry in entries) {
            val wordId = idProvider.newId()
            seedWords += parsedEntry.entry.toEntity(id = wordId, createdAt = base + index)
            parsedEntry.entry.examples.forEachIndexed { position, example ->
                seedExamples += WordExampleEntity(
                    id = idProvider.newId(),
                    wordId = wordId,
                    text = example.english,
                    translation = example.russian,
                    position = position,
                )
            }
            index++
        }
        // Offsets keep the bundled order stable when sorted by created_at.
        wordDao.insertVocabulary(words = seedWords, examples = seedExamples)
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

    /**
     * A bundled CSV row is written the same way an imported row is: unknown enum names fall back,
     * and the row carries no pronunciation or audio until the app fetches it.
     */
    private fun VocabularyEntry.toEntity(id: String, createdAt: Long): WordEntity {
        val partOfSpeech = partsOfSpeech().firstOrNull { it.name.equals(partOfSpeech.trim(), ignoreCase = true) }
        val level = difficulties().firstOrNull { it.name.equals(difficulty.trim(), ignoreCase = true) }
        return WordEntity(
            id = id,
            language = LearningLanguage.ENGLISH.tag,
            text = text,
            translation = translation,
            translationLanguage = SEED_TRANSLATION_LANGUAGE,
            pronunciation = "",
            partOfSpeech = (partOfSpeech ?: PartOfSpeech.PHRASE).name,
            difficulty = (level ?: Difficulty.A1).name,
            definition = definition,
            definitionTranslation = definitionTranslation,
            forms = emptyList(),
            relatedWords = emptyList(),
            synonyms = emptyList(),
            audioUrl = null,
            createdAt = createdAt,
            source = WordSource.BUNDLED.name,
            topic = topic,
        )
    }

    /** The row to start from when the word has no progress yet; the DAO fills it in inside its transaction. */
    private fun blankProgress(wordId: String, updatedAt: Long): WordProgressEntity =
        WordProgress(wordId = wordId).toEntity(updatedAt = updatedAt)

    private companion object {
        /** Translations in the bundled file are Russian, like everywhere else in the app. */
        const val SEED_TRANSLATION_LANGUAGE = "ru"

        fun partsOfSpeech(): List<PartOfSpeech> = PartOfSpeech.entries
        fun difficulties(): List<Difficulty> = Difficulty.entries
    }
}
