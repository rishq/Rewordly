package com.rewordly.app.domain.usecase

import com.rewordly.app.data.local.MockVocabulary
import com.rewordly.app.domain.model.LearningRules
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The study session has to mix words the user has never seen with words still in progress, and it must
 * never bring back a word that already graduated.
 */
class StudyQueueBuilderTest {
    private fun untouched(index: Int) = item(index, status = WordStatus.NEW, reviews = 0)

    private fun inProgress(index: Int) = item(index, status = WordStatus.LEARNING, reviews = 2)

    private fun graduated(index: Int) = item(
        index,
        status = WordStatus.LEARNED,
        reviews = LearningRules.GRADUATION_REPETITIONS,
    )

    private fun item(index: Int, status: WordStatus, reviews: Int): WordWithProgress {
        val word = MockVocabulary.words[index]
        return WordWithProgress(
            word,
            WordProgress(
                wordId = word.id,
                status = status,
                repetitionCount = reviews,
                correctAnswers = reviews,
            ),
        )
    }

    @Test
    fun build_leavesOutWordsThatAlreadyGraduated() {
        val pool = listOf(graduated(0), untouched(1), inProgress(2), graduated(3))
        val picked = StudyQueueBuilder.build(pool, size = 10, random = Random(1))
        assertEquals(setOf(pool[1].word.id, pool[2].word.id), picked.map { it.word.id }.toSet())
    }

    @Test
    fun build_alwaysMakesRoomForWordsInProgress() {
        val fresh = (0 until 20).map { untouched(it) }
        val practising = (20 until 23).map { inProgress(it) }
        val picked = StudyQueueBuilder.build(fresh + practising, size = 10, random = Random(5))
        assertEquals(10, picked.size)
        assertTrue(picked.containsAll(practising))
    }

    @Test
    fun build_fillsTheSessionFromTheOtherGroupWhenOneRunsOut() {
        val picked = StudyQueueBuilder.build(
            listOf(untouched(0), inProgress(1), inProgress(2), inProgress(3)),
            size = 4,
            random = Random(3),
        )
        assertEquals(4, picked.size)
        assertEquals(4, picked.map { it.word.id }.distinct().size)
    }

    @Test
    fun build_returnsTheWholePoolWhenItIsSmallerThanTheSession() {
        val pool = (0 until 3).map { untouched(it) }
        val picked = StudyQueueBuilder.build(pool, size = 10, random = Random(1))
        assertEquals(pool.map { it.word.id }.toSet(), picked.map { it.word.id }.toSet())
    }

    @Test
    fun build_isEmpty_forAnEmptyPool_orANonPositiveSize() {
        assertTrue(StudyQueueBuilder.build(emptyList(), size = 10, random = Random(1)).isEmpty())
        assertTrue(StudyQueueBuilder.build(listOf(untouched(0)), size = 0, random = Random(1)).isEmpty())
    }

    @Test
    fun build_shufflesInsideEachGroup() {
        val pool = (0 until 8).map { untouched(it) }
        val first = StudyQueueBuilder.build(pool, size = 8, random = Random(11)).map { it.word.id }
        val again = StudyQueueBuilder.build(pool, size = 8, random = Random(11)).map { it.word.id }
        val other = StudyQueueBuilder.build(pool, size = 8, random = Random(12)).map { it.word.id }
        assertEquals("the same seed must be reproducible", first, again)
        assertEquals(pool.map { it.word.id }.toSet(), first.toSet())
        assertTrue("a different seed must reorder the session", first != other)
    }
}
