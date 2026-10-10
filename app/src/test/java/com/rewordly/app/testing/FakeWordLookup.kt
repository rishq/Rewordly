package com.rewordly.app.testing

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.WordLookup
import com.rewordly.app.domain.repository.WordLookupRepository

/** A looked-up word with everything the free sources could supply filled in. */
fun foundWord(
    word: String,
    translation: String = "перевод",
    transcription: String = "/$word/",
    partOfSpeech: PartOfSpeech? = PartOfSpeech.NOUN,
    definition: String = "Definition of $word.",
    examples: List<String> = emptyList(),
) = WordLookup.Found(
    word = word,
    translation = translation,
    transcription = transcription,
    partOfSpeech = partOfSpeech,
    definition = definition,
    examples = examples,
)

/** Scripted [WordLookupRepository] that records every word it was asked about. */
class FakeWordLookupRepository(
    private var result: AppResult<WordLookup> = AppResult.Success(WordLookup.NotFound),
) : WordLookupRepository {
    val lookedUp = mutableListOf<String>()

    fun returns(lookup: WordLookup) {
        result = AppResult.Success(lookup)
    }

    fun failsWith(error: AppError) {
        result = AppResult.Failure(error)
    }

    override suspend fun lookUp(word: String): AppResult<WordLookup> {
        lookedUp += word
        return result
    }
}
