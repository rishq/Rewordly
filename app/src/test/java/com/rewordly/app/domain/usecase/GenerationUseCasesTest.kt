package com.rewordly.app.domain.usecase

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.GenerationInput
import com.rewordly.app.domain.model.GenerationMode
import com.rewordly.app.domain.model.GenerationRequest
import com.rewordly.app.domain.model.GenerationResult
import com.rewordly.app.domain.model.GenerationSettings
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordSource
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.service.GenerationSessionStore
import com.rewordly.app.testing.FakeGenerationHistoryRepository
import com.rewordly.app.testing.FakeGenerationSettingsRepository
import com.rewordly.app.testing.FakeVocabularyGenerationRepository
import com.rewordly.app.testing.FakeVocabularyRepository
import com.rewordly.app.testing.SequentialIdProvider
import com.rewordly.app.testing.TestTimeProvider
import com.rewordly.app.testing.generatedWord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationUseCasesTest {
    private val existing = MockVocabulary.words.take(3).map { WordWithProgress(it, WordProgress(it.id)) }
    private val vocabulary = FakeVocabularyRepository(existing)
    private val generation = FakeVocabularyGenerationRepository()
    private val history = FakeGenerationHistoryRepository()
    private val sessions = GenerationSessionStore()
    private val settings = FakeGenerationSettingsRepository()
    private val time = TestTimeProvider()
    private val detect = DetectDuplicatesUseCase(vocabulary)
    private val generate =
        GenerateVocabularyUseCase(generation, history, detect, sessions, SequentialIdProvider(), time)
    private val save = SaveGeneratedWordsUseCase(vocabulary, detect)
    private val regenerate = RegenerateVocabularyUseCase(
        RegenerationRequestProvider(sessions, history, settings),
        generation,
        generate,
        history,
        detect,
    )
    private val topic = GenerationRequest(GenerationInput.Topic("Programming"), GenerationSettings(Difficulty.B2, 5))
    private val existingText = existing[0].word.text

    private fun outcome(result: AppResult<GenerationOutcome>) = (result as AppResult.Success).data

    // ---- generation and history ----

    @Test
    fun generation_isStoredInTheHistory_andReturnedAsAPreview() = runTest {
        generation.enqueue(generatedWord("deploy"), generatedWord("compile"))
        val result = outcome(generate(topic))

        assertEquals("gen-1", result.historyId)
        assertEquals(listOf("deploy", "compile"), result.items.map { it.word.word })
        val entry = history.entries.value.single()
        assertEquals(GenerationMode.TOPIC, entry.mode)
        assertEquals("Programming", entry.description)
        assertEquals(Difficulty.B2, entry.level)
        assertEquals(5, entry.requestedCount)
        assertEquals(2, entry.resultCount)
        assertEquals(time.nowMillis(), entry.createdAt)
        assertEquals(2, history.stored.getValue("gen-1").size)
    }

    @Test
    fun nothingIsAddedToTheVocabulary_untilTheUserSaves() = runTest {
        generation.enqueue(generatedWord("deploy"))
        generate(topic)
        assertTrue(vocabulary.addedWords.isEmpty())
        assertEquals(existing.size, vocabulary.items.value.size)
    }

    @Test
    fun pastedText_isNeverPersisted_onlyItsLengthIs() = runTest {
        val secret = "My private diary entry about confidential things that nobody else should read."
        generation.enqueue(generatedWord("confidential"))
        generate(GenerationRequest(GenerationInput.Text(secret), GenerationSettings()))

        val entry = history.entries.value.single()
        assertEquals(GenerationMode.TEXT, entry.mode)
        assertEquals(secret.length.toString(), entry.description)
        assertFalse(history.entries.value.toString().contains("diary"))
        assertFalse(history.stored.values.flatten().toString().contains("diary"))
    }

    @Test
    fun failures_leaveNoHistoryBehind() = runTest {
        generation.results += AppResult.Failure(AppError.RateLimited(30))
        val result = generate(topic)
        assertEquals(AppError.RateLimited(30), (result as AppResult.Failure).error)
        assertTrue(history.entries.value.isEmpty())
    }

    @Test
    fun suggestionOnly_isReturnedWithoutAHistoryEntry() = runTest {
        generation.results += AppResult.Success(GenerationResult(emptyList(), suggestedCorrection = "necessary"))
        val result = outcome(generate(GenerationRequest(GenerationInput.SingleWord("neccesary"), GenerationSettings())))
        assertNull(result.historyId)
        assertEquals("necessary", result.suggestedCorrection)
        assertTrue(history.entries.value.isEmpty())
    }

    @Test
    fun replacingAnEntry_keepsItsIdAndOriginalDate() = runTest {
        generation.enqueue(generatedWord("deploy"))
        val first = outcome(generate(topic))
        time.advanceDays(1)
        generation.enqueue(generatedWord("compile"), generatedWord("build"))
        generate(topic, replaceHistoryId = first.historyId)

        val entry = history.entries.value.single()
        assertEquals(first.historyId, entry.id)
        assertEquals(1_700_000_000_000, entry.createdAt)
        assertEquals(listOf("compile", "build"), history.stored.getValue(entry.id).map { it.word })
    }

    // ---- duplicates ----

    @Test
    fun existingWords_areDetected_ignoringCaseAndWhitespace() = runTest {
        generation.enqueue(generatedWord("  ${existingText.uppercase()}  "), generatedWord("deploy"))
        val items = outcome(generate(topic)).items

        assertEquals(existing[0].word.id, items[0].existingWordId)
        assertTrue(items[0].isDuplicate)
        assertFalse("a duplicate must not be preselected", items[0].selected)
        assertNull(items[1].existingWordId)
        assertTrue(items[1].selected)
    }

    @Test
    fun savingWords_addsOnlyNewOnes_andLeavesExistingProgressUntouched() = runTest {
        val learned =
            WordProgress(existing[0].word.id, status = WordStatus.LEARNED, correctAnswers = 7, intervalDays = 12)
        vocabulary.items.value = vocabulary.items.value.map {
            if (it.word.id == learned.wordId) it.copy(progress = learned) else it
        }

        val summary = (save(listOf(generatedWord(existingText), generatedWord("deploy"))) as AppResult.Success).data

        assertEquals(listOf("ai-deploy"), summary.savedIds)
        assertEquals(listOf(existing[0].word.id), summary.skippedExistingIds)
        assertEquals(learned, vocabulary.progressOf(learned.wordId))
        assertEquals(1, vocabulary.addedWords.size)
    }

    @Test
    fun savedWords_becomeGeneratedVocabularyEntries() = runTest {
        save(listOf(generatedWord("Deploy Pipeline", translation = "конвейер")))
        val word = vocabulary.addedWords.single()
        assertEquals("ai-deploy-pipeline", word.id)
        assertEquals("Deploy Pipeline", word.text)
        assertEquals("конвейер", word.translation.text)
        assertEquals(WordSource.GENERATED, word.source)
        assertEquals("ai-deploy-pipeline-ex-0", word.examples.single().id)
        assertEquals(WordStatus.NEW, vocabulary.progressOf(word.id).status)
    }

    @Test
    fun savingTheSameSelectionTwice_doesNotDuplicate() = runTest {
        val words = listOf(generatedWord("deploy"))
        save(words)
        val second = (save(words) as AppResult.Success).data
        assertTrue(second.savedIds.isEmpty())
        assertEquals(1, vocabulary.addedWords.size)
    }

    @Test
    fun savingNothing_isANoOp_andDatabaseErrorsAreReported() = runTest {
        assertTrue((save(emptyList()) as AppResult.Success).data.savedIds.isEmpty())
        vocabulary.dbFailure = IllegalStateException("disk full")
        assertTrue(((save(listOf(generatedWord("deploy"))) as AppResult.Failure).error) is AppError.Database)
    }

    // ---- regeneration ----

    @Test
    fun regeneratingOneWord_excludesWhatIsAlreadyShown_andReplacesOnlyThatWord() = runTest {
        generation.enqueue(generatedWord("deploy"), generatedWord("compile"))
        val id = outcome(generate(topic)).historyId!!
        generation.enqueue(generatedWord("build"))

        val items = (
            regenerate.word(
                id,
                listOf(generatedWord("deploy"), generatedWord("compile")),
                1,
            ) as AppResult.Success
            ).data

        assertEquals(listOf("deploy", "build"), items.map { it.word.word })
        val request = generation.requests.last()
        assertEquals(1, request.settings.wordCount)
        assertEquals(listOf("deploy", "compile"), request.exclude)
        assertEquals(listOf("deploy", "build"), history.stored.getValue(id).map { it.word })
    }

    @Test
    fun exploringAWordAgain_keepsTheWord_withoutExclusions() = runTest {
        generation.enqueue(generatedWord("serendipity"))
        val request = GenerationRequest(GenerationInput.SingleWord("serendipity"), GenerationSettings())
        val id = outcome(generate(request)).historyId!!
        generation.enqueue(generatedWord("serendipity", translation = "счастливая случайность"))

        regenerate.word(id, listOf(generatedWord("serendipity")), 0)

        assertTrue(generation.requests.last().exclude.isEmpty())
        assertEquals(GenerationInput.SingleWord("serendipity"), generation.requests.last().input)
    }

    @Test
    fun regeneratingEverything_asksForDifferentWords_underTheSameHistoryEntry() = runTest {
        generation.enqueue(generatedWord("deploy"))
        val id = outcome(generate(topic)).historyId!!
        generation.enqueue(generatedWord("compile"))

        val result = outcome(regenerate.all(id, listOf(generatedWord("deploy"))))

        assertEquals(id, result.historyId)
        assertEquals(listOf("deploy"), generation.requests.last().exclude)
        assertEquals(1, history.entries.value.size)
    }

    @Test
    fun textGenerations_cannotBeRepeatedOnceTheInMemoryRequestIsGone() = runTest {
        generation.enqueue(generatedWord("deploy"))
        val id = outcome(
            generate(GenerationRequest(GenerationInput.Text("x".repeat(60)), GenerationSettings())),
        ).historyId!!
        assertTrue(regenerate.canRegenerate(id))

        sessions.clear()
        assertFalse(regenerate.canRegenerate(id))
        assertTrue(regenerate.word(id, listOf(generatedWord("deploy")), 0) is AppResult.Failure)
    }

    @Test
    fun topicGenerations_canBeRebuiltFromTheHistoryAfterARestart() = runTest {
        generation.enqueue(generatedWord("deploy"))
        val id = outcome(generate(topic)).historyId!!
        sessions.clear()

        assertTrue(regenerate.canRegenerate(id))
        val rebuilt = RegenerationRequestProvider(sessions, history, settings).requestFor(id)
        assertNotNull(rebuilt)
        assertEquals(GenerationInput.Topic("Programming"), rebuilt!!.input)
        assertEquals(Difficulty.B2, rebuilt.settings.level)
    }

    // ---- input rules ----

    @Test
    fun inputRules_rejectEmptyTooShortTooLongAndInvalidInput() {
        assertEquals(InputError.EMPTY, GenerationInputRules.topicError("  "))
        assertEquals(InputError.TOO_LONG, GenerationInputRules.topicError("a".repeat(61)))
        assertNull(GenerationInputRules.topicError("Software Development"))

        assertEquals(InputError.TOO_SHORT, GenerationInputRules.textError("short text"))
        assertEquals(InputError.TOO_LONG, GenerationInputRules.textError("a".repeat(GenerationInputRules.MAX_TEXT + 1)))
        assertNull(GenerationInputRules.textError("a".repeat(GenerationInputRules.MIN_TEXT)))

        assertEquals(InputError.EMPTY, GenerationInputRules.wordError(""))
        assertEquals(InputError.INVALID, GenerationInputRules.wordError("слово"))
        assertEquals(InputError.INVALID, GenerationInputRules.wordError("12345"))
        assertNull(GenerationInputRules.wordError("give up"))
        assertNull(GenerationInputRules.wordError("well-known"))
    }
}
