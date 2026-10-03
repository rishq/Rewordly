package com.rewordly.app.domain.usecase

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.common.IdProvider
import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.domain.model.GeneratedWord
import com.rewordly.app.domain.model.GenerationHistoryEntry
import com.rewordly.app.domain.model.GenerationInput
import com.rewordly.app.domain.model.GenerationMode
import com.rewordly.app.domain.model.GenerationRequest
import com.rewordly.app.domain.model.GenerationSettings
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.PreviewItem
import com.rewordly.app.domain.model.SaveSummary
import com.rewordly.app.domain.model.UsageInfo
import com.rewordly.app.domain.model.toWord
import com.rewordly.app.domain.repository.GenerationHistoryRepository
import com.rewordly.app.domain.repository.GenerationSettingsRepository
import com.rewordly.app.domain.repository.VocabularyGenerationRepository
import com.rewordly.app.domain.repository.VocabularyRepository
import com.rewordly.app.domain.service.GenerationSessionStore
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** What the UI gets back from a generation: a stored, reviewable preview or a spelling suggestion. */
data class GenerationOutcome(
    /** Null when nothing was generated (for example only a spelling suggestion). */
    val historyId: String?,
    val items: List<PreviewItem>,
    val suggestedCorrection: String?,
    val usage: UsageInfo?,
    val discardedItems: Int,
)

/** Marks which generated words already exist locally. Existing words are never selectable for saving. */
class DetectDuplicatesUseCase @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
) {
    suspend operator fun invoke(
        words: List<GeneratedWord>,
        language: LearningLanguage = LearningLanguage.ENGLISH,
    ): AppResult<List<PreviewItem>> {
        val existing = when (val result = vocabularyRepository.findExistingIds(language, words.map { it.key })) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        return AppResult.Success(
            words.map { PreviewItem(word = it, existingWordId = existing[it.key], selected = it.key !in existing) },
        )
    }
}

/**
 * Runs a generation, stores it in the local history and returns a preview. Nothing is added to the vocabulary here:
 * that only happens when the user confirms in [SaveGeneratedWordsUseCase].
 */
class GenerateVocabularyUseCase @Inject constructor(
    private val generationRepository: VocabularyGenerationRepository,
    private val historyRepository: GenerationHistoryRepository,
    private val detectDuplicates: DetectDuplicatesUseCase,
    private val sessionStore: GenerationSessionStore,
    private val idProvider: IdProvider,
    private val timeProvider: TimeProvider,
) {
    /** Pass [replaceHistoryId] to refresh an existing history entry instead of creating a new one. */
    suspend operator fun invoke(
        request: GenerationRequest,
        replaceHistoryId: String? = null,
    ): AppResult<GenerationOutcome> {
        val result = when (val generated = generationRepository.generate(request)) {
            is AppResult.Success -> generated.data
            is AppResult.Failure -> return generated
        }
        val preview = when (val marked = detectDuplicates(result.items)) {
            is AppResult.Success -> marked.data
            is AppResult.Failure -> return marked
        }
        if (preview.isEmpty()) {
            return AppResult.Success(
                GenerationOutcome(null, emptyList(), result.suggestedCorrection, result.usage, result.discardedItems),
            )
        }

        val id = replaceHistoryId ?: idProvider.newId()
        val createdAt = replaceHistoryId
            ?.let { (historyRepository.getEntry(it) as? AppResult.Success)?.data?.createdAt }
            ?: timeProvider.nowMillis()
        val entry = GenerationHistoryEntry(
            id = id,
            mode = request.mode,
            description = describe(request.input),
            level = request.settings.level,
            requestedCount = request.settings.wordCount,
            resultCount = result.items.size,
            createdAt = createdAt,
            hasResult = true,
        )
        when (val saved = historyRepository.record(entry, result.items)) {
            is AppResult.Failure -> return saved
            is AppResult.Success -> Unit
        }
        sessionStore.put(id, request)
        return AppResult.Success(
            GenerationOutcome(id, preview, result.suggestedCorrection, result.usage, result.discardedItems),
        )
    }

    companion object {
        /** Topic or word as typed; for pasted text only its length, so the text itself is never persisted. */
        fun describe(input: GenerationInput): String = when (input) {
            is GenerationInput.Topic -> input.topic.trim()
            is GenerationInput.SingleWord -> input.word.trim()
            is GenerationInput.Text -> input.text.length.toString()
        }
    }
}

/** Rebuilds the request behind a stored generation so a word or the whole result can be regenerated. */
class RegenerationRequestProvider @Inject constructor(
    private val sessionStore: GenerationSessionStore,
    private val historyRepository: GenerationHistoryRepository,
    private val settingsRepository: GenerationSettingsRepository,
) {
    /**
     * Pasted text is only held in memory, so after a restart text generations cannot be repeated (null).
     * Topic and word generations can always be rebuilt from the history entry.
     */
    suspend fun requestFor(historyId: String): GenerationRequest? {
        sessionStore.get(historyId)?.let { return it }
        val entry = (historyRepository.getEntry(historyId) as? AppResult.Success)?.data ?: return null
        val flags = settingsRepository.settings.first()
        val settings = GenerationSettings(
            level = entry.level,
            wordCount = entry.requestedCount,
            includeExamples = flags.includeExamples,
            includeSynonyms = flags.includeSynonyms,
            includePronunciation = flags.includePronunciation,
        )
        val input = when (entry.mode) {
            GenerationMode.TOPIC -> GenerationInput.Topic(entry.description)
            GenerationMode.WORD -> GenerationInput.SingleWord(entry.description)
            GenerationMode.TEXT -> return null
        }
        return GenerationRequest(input, settings)
    }
}

/** Replaces one previewed word with a fresh one, or the whole preview with a fresh set. */
class RegenerateVocabularyUseCase @Inject constructor(
    private val requestProvider: RegenerationRequestProvider,
    private val generationRepository: VocabularyGenerationRepository,
    private val generate: GenerateVocabularyUseCase,
    private val historyRepository: GenerationHistoryRepository,
    private val detectDuplicates: DetectDuplicatesUseCase,
) {
    suspend fun canRegenerate(historyId: String): Boolean = requestProvider.requestFor(historyId) != null

    /** Returns the replacement for the word at [index] of [current]; the history entry is updated as well. */
    suspend fun word(historyId: String, current: List<GeneratedWord>, index: Int): AppResult<List<PreviewItem>> {
        val base = requestProvider.requestFor(historyId) ?: return AppResult.Failure(AppError.Unknown())
        val request = when (base.input) {
            // Exploring one word again keeps the word and asks for a fresh explanation.
            is GenerationInput.SingleWord -> base
            else -> base.copy(
                settings = base.settings.copy(wordCount = 1),
                exclude = current.map { it.word },
            )
        }
        val replacement = when (val result = generationRepository.generate(request)) {
            is AppResult.Success -> result.data.items.firstOrNull()
                ?: return AppResult.Failure(AppError.EmptyResponse)
            is AppResult.Failure -> return result
        }
        val updated = current.toMutableList().also { it[index] = replacement }
        historyRepository.updateItems(historyId, updated)
        return detectDuplicates(updated)
    }

    suspend fun all(historyId: String, current: List<GeneratedWord>): AppResult<GenerationOutcome> {
        val base = requestProvider.requestFor(historyId)
            ?: return AppResult.Failure(AppError.Unknown())
        val request = if (base.input is GenerationInput.SingleWord) {
            base
        } else {
            base.copy(exclude = current.map { it.word })
        }
        return generate(request, replaceHistoryId = historyId)
    }
}

/** Saves the words the user confirmed. Existing words and their progress are never overwritten. */
class SaveGeneratedWordsUseCase @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val detectDuplicates: DetectDuplicatesUseCase,
) {
    suspend operator fun invoke(selected: List<GeneratedWord>): AppResult<SaveSummary> {
        if (selected.isEmpty()) return AppResult.Success(SaveSummary(emptyList(), emptyList()))
        val checked = when (val result = detectDuplicates(selected)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        val fresh = checked.filter { !it.isDuplicate }.map { it.word.toWord() }
        val saved = when (val result = vocabularyRepository.addWords(fresh)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        return AppResult.Success(
            SaveSummary(savedIds = saved, skippedExistingIds = checked.mapNotNull { it.existingWordId }),
        )
    }
}
