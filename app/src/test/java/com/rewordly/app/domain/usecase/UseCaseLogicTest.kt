package com.rewordly.app.domain.usecase

import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UseCaseLogicTest {
    private fun item(index: Int, status: WordStatus = WordStatus.NEW, saved: Boolean = false): WordWithProgress {
        val word = MockVocabulary.words[index]
        return WordWithProgress(word, WordProgress(wordId = word.id, status = status, isSaved = saved))
    }

    @Test
    fun greetingForHour_coversWholeDay() {
        assertEquals(Greeting.NIGHT, GetGreetingUseCase.greetingForHour(3))
        assertEquals(Greeting.MORNING, GetGreetingUseCase.greetingForHour(8))
        assertEquals(Greeting.AFTERNOON, GetGreetingUseCase.greetingForHour(14))
        assertEquals(Greeting.EVENING, GetGreetingUseCase.greetingForHour(20))
        assertEquals(Greeting.NIGHT, GetGreetingUseCase.greetingForHour(23))
    }

    @Test
    fun normalize_trimsAndCollapsesWhitespace() {
        assertEquals("take care of", SearchWordsUseCase.normalize("  take   care\tof "))
        assertEquals("", SearchWordsUseCase.normalize("   "))
    }

    @Test
    fun learningSession_putsLearnedWordsLast() {
        val learned = item(0, WordStatus.LEARNED)
        val fresh = item(1)
        val learning = item(2, WordStatus.LEARNING)
        val ordered = StartLearningSessionUseCase.order(listOf(learned, fresh, learning))
        assertEquals(listOf(fresh, learning, learned), ordered)
    }

    @Test
    fun reviewQueue_prioritisesSavedAndLearningWords_andRespectsSize() {
        val plain = item(0)
        val saved = item(1, saved = true)
        val learning = item(2, WordStatus.LEARNING)
        val queue = BuildReviewQueueUseCase.select(listOf(plain, saved, learning), size = 2)
        assertEquals(listOf(saved, learning), queue)
        assertTrue(BuildReviewQueueUseCase.select(emptyList(), size = 5).isEmpty())
    }
}
