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
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordKeys
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordSource
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.SeedPackTracker
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
    private val packSource: WordPackSource,
    private val packTracker: SeedPackTracker,
) : VocabularyRepository {

    /**
     * Syncs every bundled word pack.
     *
     * A pack is applied once per version: a fresh install inserts all of its words; on a later
     * version bump missing words are inserted and stale bundled content is updated in place, so
     * progress (bound to the word id) and review history survive untouched. Rows already owned by
     * another source (AI generations, imports, lookups) are never touched, and words the user added
     * by hand are left alone even when a pack later ships the same text. Applied versions live in
     * DataStore; [OfflineVocabularyRepositoryTest] pins the behaviour.
     */
    override suspend fun ensureSeeded(): AppResult<Unit> = safeDbCall {
        val appliedVersions = packTracker.appliedVersions()
        WordPackCatalog.packs.forEachIndexed { packIndex, pack ->
            if (((appliedVersions[pack.id] ?: 0) >= pack.version)) return@forEachIndexed

            val content = packSource.read(pack.assetName)
            val parsed = importParser.parse(content, importParser.detectFormat(pack.assetName, content), ImportLimits())
            val entries = parsed.entries.map { it.entry }
            if (entries.isNotEmpty()) {
                applyPack(packIndex, entries)
            }
            packTracker.markApplied(pack.id, pack.version)
        }
        Unit
    }

    /**
     * Inserts pack words that do not exist yet and refreshes bundled rows whose content changed.
     * Rows owned by another source (AI, files, lookups) are skipped entirely.
     */
    private suspend fun applyPack(packIndex: Int, entries: List<VocabularyEntry>) {
        val now = timeProvider.nowMillis()
        val byNormalized = entries.associateBy { WordKeys.normalize(it.text) }
        val existing = wordDao.findRows(
            language = LearningLanguage.ENGLISH.tag,
            sources = WordSource.entries.map(WordSource::name),
            keys = byNormalized.keys.toList(),
        ).associateBy { WordKeys.normalize(it.text) }

        var index = 0
        val freshWords = mutableListOf<WordEntity>()
        val freshExamples = mutableListOf<WordExampleEntity>()
        val changedRows = mutableListOf<WordEntity>()
        val changedIds = mutableListOf<String>()
        val rebuildExamples = mutableListOf<WordExampleEntity>()
        for ((key, entry) in byNormalized) {
            val row = existing[key]
            if (row == null) {
                val wordId = idProvider.newId()
                // Offsets keep the bundled order stable when sorted by created_at.
                freshWords += entry.toEntity(id = wordId, createdAt = now + packIndex * 1_000_000L + index)
                entry.examples.forEachIndexed { position, example ->
                    freshExamples += WordExampleEntity(
                        id = idProvider.newId(),
                        wordId = wordId,
                        text = example.english,
                        translation = example.russian,
                        position = position,
                    )
                }
            } else if (row.source == WordSource.BUNDLED.name && row.contentDiffersFrom(entry)) {
                val refreshed = row.refreshedWith(entry)
                changedRows += refreshed
                changedIds += row.id
                entry.examples.forEachIndexed { position, example ->
                    rebuildExamples += WordExampleEntity(
                        id = idProvider.newId(),
                        wordId = row.id,
                        text = example.english,
                        translation = example.russian,
                        position = position,
                    )
                }
            }
            index++
        }
        wordDao.insertVocabulary(words = freshWords, examples = freshExamples)
        wordDao.updateWords(changedRows)
        if (changedIds.isNotEmpty()) wordDao.replaceExamples(changedIds, rebuildExamples)
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
     * A bundled pack row is written the same way an imported row is: unknown enum names fall back,
     * and the row carries no audio until the app fetches it.
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
            pronunciation = pronunciation,
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

    /** True when any deck content a pack owns has changed and the row must be refreshed. */
    private fun VocabularyEntry.resolvedPartOfSpeech(): String {
        val match = partsOfSpeech().firstOrNull { it.name.equals(partOfSpeech.trim(), ignoreCase = true) }
        return (match ?: PartOfSpeech.PHRASE).name
    }

    private fun VocabularyEntry.resolvedDifficulty(): String {
        val level = difficulties().firstOrNull { it.name.equals(difficulty.trim(), ignoreCase = true) }
        return (level ?: Difficulty.A1).name
    }

    private fun WordEntity.refreshedWith(pack: VocabularyEntry): WordEntity = copy(
        translation = pack.translation,
        pronunciation = pack.pronunciation,
        partOfSpeech = pack.resolvedPartOfSpeech(),
        difficulty = pack.resolvedDifficulty(),
        definition = pack.definition,
        definitionTranslation = pack.definitionTranslation,
        topic = pack.topic,
    )

    private fun WordEntity.contentDiffersFrom(pack: VocabularyEntry): Boolean =
        translation != pack.translation || pronunciation != pack.pronunciation ||
            partOfSpeech != pack.resolvedPartOfSpeech() ||
            difficulty != pack.resolvedDifficulty() ||
            definition != pack.definition ||
            definitionTranslation != pack.definitionTranslation ||
            topic != pack.topic

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
