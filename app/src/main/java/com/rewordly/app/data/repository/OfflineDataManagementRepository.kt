package com.rewordly.app.data.repository

import com.rewordly.app.BuildConfig
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.common.IdProvider
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.core.database.dao.DataTransferDao
import com.rewordly.app.core.database.dao.RestoreWriteResult
import com.rewordly.app.core.database.dao.SQLITE_BIND_LIMIT
import com.rewordly.app.core.database.entity.DailyActivityEntity
import com.rewordly.app.core.database.entity.PopulatedWord
import com.rewordly.app.core.database.entity.ReviewLogEntity
import com.rewordly.app.core.database.entity.WordEntity
import com.rewordly.app.core.database.entity.WordExampleEntity
import com.rewordly.app.core.database.entity.WordProgressEntity
import com.rewordly.app.core.database.safeDbCall
import com.rewordly.app.data.local.toBackup
import com.rewordly.app.data.local.toDomain
import com.rewordly.app.data.local.toEntity
import com.rewordly.app.domain.model.BackupDecodeResult
import com.rewordly.app.domain.model.BackupPreview
import com.rewordly.app.domain.model.BackupWord
import com.rewordly.app.domain.model.DataStats
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.DuplicateKind
import com.rewordly.app.domain.model.DuplicatePolicy
import com.rewordly.app.domain.model.ImportPreview
import com.rewordly.app.domain.model.ImportSummary
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.RestoreStrategy
import com.rewordly.app.domain.model.RestoreSummary
import com.rewordly.app.domain.model.VocabularyBackup
import com.rewordly.app.domain.model.VocabularyEntry
import com.rewordly.app.domain.model.VocabularyFileFormat
import com.rewordly.app.domain.model.WordKeys
import com.rewordly.app.domain.model.WordSource
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.BackupInspection
import com.rewordly.app.domain.repository.DataManagementRepository
import com.rewordly.app.domain.repository.RestoreOutcome
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.service.BackupCodec
import com.rewordly.app.domain.service.DuplicateDetector
import com.rewordly.app.domain.service.VocabularyExporter
import com.rewordly.app.domain.service.VocabularyImportParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Everything the data-management screen needs, entirely on device.
 *
 * Reading a file, parsing it, writing an export and restoring a backup all happen on [Dispatchers.IO]:
 * a vocabulary file is untrusted input of unknown size, and nothing here may block the main thread.
 * Every write that touches more than one table goes through a `@Transaction` method of [DataTransferDao].
 *
 * Files themselves are handled by
 * [com.rewordly.app.core.storage.DocumentStore]: this class only ever sees their text.
 */
@Singleton
class OfflineDataManagementRepository @Inject constructor(
    private val dao: DataTransferDao,
    private val importParser: VocabularyImportParser,
    private val exporter: VocabularyExporter,
    private val backupCodec: BackupCodec,
    private val settingsRepository: SettingsRepository,
    private val timeProvider: TimeProvider,
    private val idProvider: IdProvider,
) : DataManagementRepository {

    override suspend fun stats(): AppResult<DataStats> = onIo {
        DataStats(
            words = dao.wordCount(),
            examples = dao.exampleCount(),
            trackedWords = dao.progressCount(),
            reviewLogEntries = dao.reviewLogCount(),
            activeDays = dao.activityCount(),
        )
    }

    override suspend fun previewImport(fileName: String, content: String): AppResult<ImportPreview> = onIo {
        val format = importParser.detectFormat(fileName, content)
        val parsed = importParser.parse(content, format)
        val existing = existingIdsByKey(parsed.entries.map { it.entry.text })
        ImportPreview(
            format = format,
            candidates = DuplicateDetector.mark(parsed.entries, existing),
            issues = parsed.issues,
            truncated = parsed.truncated,
        )
    }

    /**
     * Writes the entries of a previewed file.
     *
     * [DuplicatePolicy.SKIP] keeps what is already saved, [DuplicatePolicy.REPLACE] overwrites the
     * content of the matching word in place - the row keeps its id, so its progress and its review
     * history survive - and [DuplicatePolicy.KEEP_BOTH] adds the entry as a second word. Repeats
     * inside the file follow the same policy, so "add as new" really does keep both meanings.
     *
     * Nothing is merged implicitly, and the whole write is one transaction.
     */
    override suspend fun applyImport(preview: ImportPreview, policy: DuplicatePolicy): AppResult<ImportSummary> = onIo {
        val newEntries = mutableListOf<VocabularyEntry>()
        val replacements = mutableListOf<Pair<String, VocabularyEntry>>()
        preview.candidates.forEach { candidate ->
            val existingId = candidate.existingWordId
            when {
                !candidate.isDuplicate -> newEntries += candidate.entry
                policy == DuplicatePolicy.KEEP_BOTH -> newEntries += candidate.entry
                policy == DuplicatePolicy.REPLACE &&
                    candidate.duplicate == DuplicateKind.EXISTING &&
                    existingId != null -> replacements += existingId to candidate.entry
            }
        }

        // Only the words that are actually being replaced are read back, so their progress and their
        // creation date survive an update. Ids that vanished meanwhile simply drop out of the import.
        val existingById = replacements.map { it.first }
            .distinct()
            .chunked(SQLITE_BIND_LIMIT)
            .flatMap { dao.wordsByIds(it) }
            .associateBy { it.id }

        val base = timeProvider.nowMillis()
        val newWords = newEntries.mapIndexed { index, entry ->
            entry.toWordEntity(id = idProvider.newId(), createdAt = base + index)
        }
        val newExamples = newWords.zip(newEntries) { word, entry -> entry.exampleEntities(word.id) }.flatten()

        val replaced = replacements.mapNotNull { (id, entry) -> existingById[id]?.let { it to entry } }
        val replacedWords = replaced.map { (existing, entry) ->
            entry.toWordEntity(id = existing.id, createdAt = existing.createdAt, existing = existing)
        }
        val replacedExamples = replaced.flatMap { (existing, entry) -> entry.exampleEntities(existing.id) }

        val written = dao.writeImport(
            newWords = newWords,
            newExamples = newExamples,
            replacedWords = replacedWords,
            replacedExamples = replacedExamples,
        )
        ImportSummary(
            added = written.added,
            replaced = written.replaced,
            skipped = (preview.entryCount - written.added - written.replaced).coerceAtLeast(0),
        )
    }

    override suspend fun exportVocabulary(format: VocabularyFileFormat): AppResult<String> = onIo {
        val words = wordsWithProgress()
        when (format) {
            VocabularyFileFormat.CSV -> exporter.exportCsv(words)
            VocabularyFileFormat.JSON -> exporter.exportJson(words)
        }
    }

    override suspend fun exportBackup(): AppResult<String> = onIo {
        val examplesByWord = dao.allExamples().groupBy { it.wordId }
        val backup = VocabularyBackup(
            schemaVersion = VocabularyBackup.CURRENT_SCHEMA_VERSION,
            createdAt = timeProvider.nowMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            words = dao.allWords().map { word ->
                word.toBackup().copy(examples = examplesByWord[word.id].orEmpty().map { it.toBackup() })
            },
            progress = dao.allProgress().map { it.toBackup() },
            reviewLog = dao.allReviewLogs().map { it.toBackup() },
            activity = dao.allActivity().map { it.toBackup() },
            settings = settingsRepository.settings.first().toBackup(),
        )
        backupCodec.encode(backup)
    }

    override suspend fun restoreBackup(content: String, strategy: RestoreStrategy): AppResult<RestoreOutcome> = onIo {
        when (val decoded = backupCodec.decode(content)) {
            is BackupDecodeResult.Invalid -> RestoreOutcome.Rejected(decoded.issue)
            is BackupDecodeResult.Valid -> RestoreOutcome.Restored(applyBackup(decoded.backup, strategy))
        }
    }

    override suspend fun inspectBackup(content: String): AppResult<BackupInspection> = onIo {
        when (val decoded = backupCodec.decode(content)) {
            is BackupDecodeResult.Invalid -> BackupInspection.Rejected(decoded.issue)
            is BackupDecodeResult.Valid -> BackupInspection.Valid(decoded.backup.toPreview())
        }
    }

    // ------------------------------------------------------------- backup write

    private fun VocabularyBackup.toPreview(): BackupPreview = BackupPreview(
        createdAt = createdAt,
        appVersion = appVersion,
        words = words.size,
        examples = words.sumOf { it.examples.size },
        progress = progress.size,
        historyEntries = reviewLog.size,
    )

    private suspend fun applyBackup(backup: VocabularyBackup, strategy: RestoreStrategy): RestoreSummary {
        val words = backup.words.map { it.toEntity() }
        val wordIds = words.map { it.id }.toSet()
        val examples = backup.words.flatMap { it.exampleEntities() }
        // A hand-edited or truncated file can mention progress or history for a word it does not
        // contain. Such a row would violate the foreign key and roll the whole restore back, so it is
        // dropped instead - the user still gets everything the file actually describes.
        val progress = backup.progress.filter { it.wordId in wordIds }.map { it.toEntity() }
        val reviewLogs = backup.reviewLog.filter { it.wordId in wordIds }.map { it.toEntity() }
        val activity = backup.activity.map { it.toEntity() }
        val written = when (strategy) {
            RestoreStrategy.REPLACE -> dao.restoreReplace(words, examples, progress, reviewLogs, activity)
            RestoreStrategy.MERGE -> mergeBackup(words, examples, progress, reviewLogs, activity)
        }
        return RestoreSummary(
            wordsAdded = written.wordsAdded,
            wordsUpdated = written.wordsUpdated,
            progressRestored = written.progressRestored,
            historyRestored = written.historyRestored,
        )
    }

    /**
     * Adds what is missing and refreshes what matches by id. A backup word whose text is already
     * present under a different id is left out entirely: importing it would create the duplicate the
     * merge is supposed to avoid, and its progress would have nowhere to attach.
     */
    private suspend fun mergeBackup(
        words: List<WordEntity>,
        examples: List<WordExampleEntity>,
        progress: List<WordProgressEntity>,
        reviewLogs: List<ReviewLogEntity>,
        activity: List<DailyActivityEntity>,
    ): RestoreWriteResult {
        val existingById = words.map { it.id }
            .chunked(SQLITE_BIND_LIMIT)
            .flatMap { dao.wordsByIds(it) }
            .associateBy { it.id }
        val existingByText = existingIdsByKey(words.map { it.text })

        val newWords = mutableListOf<WordEntity>()
        val updatedWords = mutableListOf<WordEntity>()
        val insertedIds = mutableSetOf<String>()
        val updatedIds = mutableSetOf<String>()
        words.forEach { word ->
            val current = existingById[word.id]
            when {
                current != null -> {
                    // The backup carries no audio reference, so the local one is kept.
                    updatedWords += word.copy(audioUrl = current.audioUrl)
                    updatedIds += word.id
                }
                existingByText.containsKey(WordKeys.normalize(word.text)) -> Unit
                else -> {
                    newWords += word
                    insertedIds += word.id
                }
            }
        }

        val keptIds = insertedIds + updatedIds
        return dao.restoreMerge(
            newWords = newWords,
            newExamples = examples.filter { it.wordId in insertedIds },
            updatedWords = updatedWords,
            updatedExamples = examples.filter { it.wordId in updatedIds },
            progress = progress.filter { it.wordId in keptIds },
            reviewLogs = reviewLogs.filter { it.wordId in keptIds },
            activity = activity,
        )
    }

    // ------------------------------------------------------------------ helpers

    /** Runs on [Dispatchers.IO] and maps a failure to [com.rewordly.app.core.common.AppError.Database]. */
    private suspend fun <T> onIo(block: suspend () -> T): AppResult<T> =
        withContext(Dispatchers.IO) { safeDbCall(block) }

    private suspend fun wordsWithProgress(): List<WordWithProgress> {
        val examplesByWord = dao.allExamples().groupBy { it.wordId }
        return dao.allWords().map { word ->
            PopulatedWord(word = word, examples = examplesByWord[word.id].orEmpty(), progress = null).toDomain()
        }
    }

    /** Normalized word to local id, looked up in chunks to respect the SQLite bind limit. */
    private suspend fun existingIdsByKey(texts: List<String>): Map<String, String> {
        val keys = texts.map(WordKeys::normalize).distinct()
        if (keys.isEmpty()) return emptyMap()
        return keys.chunked(SQLITE_BIND_LIMIT)
            .flatMap { dao.findWordsByNormalizedText(LearningLanguage.ENGLISH.tag, it) }
            .associate { WordKeys.normalize(it.text) to it.id }
    }

    /**
     * Turns an imported entry into a row. When [existing] is given, only the content the file
     * describes is overwritten; everything the file does not mention - the id, the creation date, the
     * forms, the synonyms - is carried over, and the progress row is never touched.
     */
    private fun VocabularyEntry.toWordEntity(id: String, createdAt: Long, existing: WordEntity? = null): WordEntity {
        val partOfSpeech = enumName(PartOfSpeech.entries, partOfSpeech, PartOfSpeech.PHRASE)
        val level = enumName(Difficulty.entries, difficulty, Difficulty.A1)
        if (existing != null) {
            return existing.copy(
                text = text,
                translation = translation,
                partOfSpeech = partOfSpeech,
                difficulty = level,
                definition = definition,
                definitionTranslation = definitionTranslation,
                topic = topic,
            )
        }
        return WordEntity(
            id = id,
            language = LearningLanguage.ENGLISH.tag,
            text = text,
            translation = translation,
            translationLanguage = TRANSLATION_LANGUAGE,
            pronunciation = "",
            partOfSpeech = partOfSpeech,
            difficulty = level,
            definition = definition,
            definitionTranslation = definitionTranslation,
            forms = emptyList(),
            relatedWords = emptyList(),
            synonyms = emptyList(),
            audioUrl = null,
            createdAt = createdAt,
            source = WordSource.IMPORTED.name,
            topic = topic,
        )
    }

    private fun VocabularyEntry.exampleEntities(wordId: String): List<WordExampleEntity> =
        examples.mapIndexed { index, example ->
            WordExampleEntity(
                id = idProvider.newId(),
                wordId = wordId,
                text = example.english,
                translation = example.russian,
                position = index,
            )
        }

    private fun BackupWord.exampleEntities(): List<WordExampleEntity> =
        examples.sortedBy { it.position }.mapIndexed { index, example ->
            WordExampleEntity(
                id = example.id.ifBlank { idProvider.newId() },
                wordId = id,
                text = example.text,
                translation = example.translation,
                position = index,
            )
        }

    /** Unknown or blank values fall back to [fallback], matching how stored rows are read back. */
    private fun <T : Enum<T>> enumName(values: List<T>, raw: String, fallback: T): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return fallback.name
        return values.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.name ?: fallback.name
    }

    private companion object {
        /** Translations in imported files are Russian, like everywhere else in the app. */
        const val TRANSLATION_LANGUAGE = "ru"
    }
}
