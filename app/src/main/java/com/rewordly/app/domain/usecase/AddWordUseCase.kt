package com.rewordly.app.domain.usecase

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordKeys
import com.rewordly.app.domain.model.WordLookup
import com.rewordly.app.domain.model.toWord
import com.rewordly.app.domain.repository.VocabularyRepository
import com.rewordly.app.domain.repository.WordLookupRepository
import javax.inject.Inject

/** What happened when the user asked to add a word. */
sealed interface AddWordOutcome {
    /**
     * The word is in the vocabulary now. [hasTranscription] is false when no source knew how to
     * pronounce it, so the UI can say so instead of leaving the user to wonder.
     */
    data class Added(val word: Word, val hasTranscription: Boolean) : AddWordOutcome

    /** The word was already there; nothing was written and nothing was looked up. */
    data class AlreadyExists(val wordId: String, val text: String) : AddWordOutcome
}

/**
 * Adds a word the user typed, filling in the translation and the transcription from free dictionaries.
 *
 * The duplicate check runs first and short-circuits: an existing word is reported as [AddWordOutcome.
 * AlreadyExists] without a single network call, because the user already has everything this could fetch.
 *
 * A word nobody could translate comes back as [AppError.EmptyResponse] rather than being saved half-empty.
 */
class AddWordUseCase @Inject constructor(
    private val lookupRepository: WordLookupRepository,
    private val vocabularyRepository: VocabularyRepository,
) {
    suspend operator fun invoke(rawWord: String): AppResult<AddWordOutcome> {
        val text = rawWord.trim()
        val key = WordKeys.normalize(text)

        val existing = when (val result = vocabularyRepository.findExistingIds(LearningLanguage.ENGLISH, listOf(key))) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        existing[key]?.let { return AppResult.Success(AddWordOutcome.AlreadyExists(it, text)) }

        val lookup = when (val result = lookupRepository.lookUp(text)) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        val found = lookup as? WordLookup.Found
            ?: return AppResult.Failure(AppError.EmptyResponse)

        val word = found.toWord()
        val saved = when (val result = vocabularyRepository.addWords(listOf(word))) {
            is AppResult.Success -> result.data
            is AppResult.Failure -> return result
        }
        // The insert is ignored when the row appeared between the check and the write, so a missing id
        // means the word is there after all and was simply not added by this call.
        if (saved.isEmpty()) {
            return AppResult.Success(AddWordOutcome.AlreadyExists(word.id, word.text))
        }
        return AppResult.Success(AddWordOutcome.Added(word, hasTranscription = found.transcription.isNotBlank()))
    }
}
