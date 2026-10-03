package com.rewordly.app.domain.usecase

import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import kotlin.random.Random
import org.junit.Assert.assertEquals
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
    fun studyQueue_leavesOutWordsThatGraduated() {
        val graduated = item(0, WordStatus.LEARNED)
        val fresh = item(1)
        val inProgress = item(2, WordStatus.LEARNING)
        val picked = StudyQueueBuilder.build(listOf(graduated, fresh, inProgress), size = 10, random = Random(1))
        assertEquals(setOf(fresh.word.id, inProgress.word.id), picked.map { it.word.id }.toSet())
    }
}
