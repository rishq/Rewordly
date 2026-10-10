package com.rewordly.app.domain.usecase

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordLookup
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordSource
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.VocabularyRepository
import com.rewordly.app.testing.FakeVocabularyRepository
import com.rewordly.app.testing.FakeWordLookupRepository
import com.rewordly.app.testing.foundWord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddWordUseCaseTest {
    private val existing = MockVocabulary.words.take(2).map { WordWithProgress(it, WordProgress(it.id)) }
    private val vocabulary = FakeVocabularyRepository(existing)
    private val lookup = FakeWordLookupRepository()
    private val addWord = AddWordUseCase(lookup, vocabulary)

    private fun outcome(result: AppResult<AddWordOutcome>) = (result as AppResult.Success).data

    @Test
    fun addsTheWordWithItsTranslationAndTranscription() = runTest {
        lookup.returns(foundWord("deploy", translation = "развёртывать", transcription = "/dɪˈplɔɪ/"))

        val added = outcome(addWord("  deploy  ")) as AddWordOutcome.Added

        assertEquals("deploy", added.word.text)
        assertEquals("развёртывать", added.word.translation.text)
        assertEquals("/dɪˈplɔɪ/", added.word.pronunciation)
        assertEquals(WordSource.LOOKUP, added.word.source)
        assertTrue(added.hasTranscription)
        assertEquals(listOf("deploy"), vocabulary.addedWords.map { it.text })
        // The word is looked up trimmed, so trailing spaces do not become part of the query.
        assertEquals(listOf("deploy"), lookup.lookedUp)
    }

    @Test
    fun saysWhenTheWordWasSavedWithoutATranscription() = runTest {
        lookup.returns(foundWord("deploy", transcription = ""))

        val added = outcome(addWord("deploy")) as AddWordOutcome.Added

        assertFalse(added.hasTranscription)
        assertEquals("", added.word.pronunciation)
    }

    @Test
    fun keepsTheEnglishExamplesTheSourcesReturnAndLeavesTheirTranslationEmpty() = runTest {
        lookup.returns(foundWord("deploy", examples = listOf("Deploy two units of infantry.")))

        val added = outcome(addWord("deploy")) as AddWordOutcome.Added

        assertEquals(1, added.word.examples.size)
        assertEquals("Deploy two units of infantry.", added.word.examples[0].text)
        assertEquals("", added.word.examples[0].translation)
    }

    @Test
    fun doesNotLookUpAWordThatIsAlreadyThere() = runTest {
        val known = existing[0].word.text

        val result = outcome(addWord(known)) as AddWordOutcome.AlreadyExists

        assertEquals(existing[0].word.id, result.wordId)
        assertEquals(known, result.text)
        // Nothing was fetched and nothing was written: the user already has this word.
        assertEquals(emptyList<String>(), lookup.lookedUp)
        assertEquals(emptyList<Word>(), vocabulary.addedWords)
    }

    @Test
    fun treatsAWordNoSourceCouldTranslateAsAnEmptyResponse() = runTest {
        lookup.returns(WordLookup.NotFound)

        val result = addWord("zzzqqq")

        assertEquals(AppResult.Failure(AppError.EmptyResponse), result)
        assertEquals(emptyList<Word>(), vocabulary.addedWords)
    }

    @Test
    fun propagatesAFailedLookupWithoutSavingAnything() = runTest {
        lookup.failsWith(AppError.Network())

        val result = addWord("deploy")

        assertEquals(AppResult.Failure(AppError.Network()), result)
        assertEquals(emptyList<Word>(), vocabulary.addedWords)
    }

    @Test
    fun reportsTheWordAsExistingWhenTheInsertWasIgnored() = runTest {
        // A concurrent write can land between the duplicate check and the insert; the insert is then
        // ignored and the word is in the vocabulary after all.
        val racing = AddWordUseCase(lookup, RacingVocabulary())
        lookup.returns(foundWord("deploy"))

        val result = outcome(racing("deploy")) as AddWordOutcome.AlreadyExists

        assertEquals("deploy", result.text)
    }
}

/** Reports every word as new and then refuses the insert, which is what a lost race looks like. */
private class RacingVocabulary : VocabularyRepository {
    override suspend fun ensureSeeded(): AppResult<Unit> = AppResult.Success(Unit)

    override fun observeWords(language: LearningLanguage): Flow<List<WordWithProgress>> = flowOf(emptyList())

    override fun observeWordsByIds(ids: List<String>): Flow<List<WordWithProgress>> = flowOf(emptyList())

    override fun observeRecentWords(language: LearningLanguage, limit: Int): Flow<List<WordWithProgress>> =
        flowOf(emptyList())

    override fun observeSavedWords(language: LearningLanguage): Flow<List<WordWithProgress>> = flowOf(emptyList())

    override fun observeWord(wordId: String): Flow<WordWithProgress?> = flowOf(null)

    override suspend fun setSaved(wordId: String, saved: Boolean): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun recordView(wordId: String): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun findExistingIds(
        language: LearningLanguage,
        keys: Collection<String>,
    ): AppResult<Map<String, String>> = AppResult.Success(emptyMap())

    override suspend fun addWords(words: List<Word>): AppResult<List<String>> = AppResult.Success(emptyList())
}
