package com.rewordly.app.domain.usecase

import com.rewordly.app.core.common.TimeProvider
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.LearningSession
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.model.isGraduated
import com.rewordly.app.domain.model.isUntouched
import com.rewordly.app.domain.repository.VocabularyRepository
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.first

/**
 * Builds the study session shown by the "Cards" tab: a random mix of words the user has never seen and
 * words still being practised. Words that graduated ([isGraduated]) are left out for good - after that
 * they only come back through the spaced-repetition queue.
 */
class StartLearningSessionUseCase @Inject constructor(
    private val vocabularyRepository: VocabularyRepository,
    private val timeProvider: TimeProvider,
) {
    suspend operator fun invoke(language: LearningLanguage, size: Int = DEFAULT_SIZE): LearningSession {
        val words = vocabularyRepository.observeWords(language).first()
        val startedAt = timeProvider.nowMillis()
        return LearningSession(
            sessionId = sessionId(startedAt),
            wordIds = StudyQueueBuilder.build(words, size).map { it.word.id },
            startedAt = startedAt,
        )
    }

    companion object {
        const val DEFAULT_SIZE = 10

        fun sessionId(startedAt: Long): String = "session-$startedAt"
    }
}

/**
 * Pure session content, deterministic for a given [random]. New words and words in progress are shuffled
 * inside their own group and then drawn alternately, so a large backlog of brand new words can never push
 * the words the user is actually practising out of the session - and a session that has both kinds of word
 * always contains both.
 */
object StudyQueueBuilder {
    fun build(words: List<WordWithProgress>, size: Int, random: Random = Random.Default): List<WordWithProgress> {
        if (size <= 0) return emptyList()
        val pool = words.filterNot { it.progress.isGraduated }
        val fresh = ArrayDeque(pool.filter { it.progress.isUntouched }.shuffled(random))
        val inProgress = ArrayDeque(pool.filterNot { it.progress.isUntouched }.shuffled(random))
        val picked = ArrayList<WordWithProgress>(minOf(size, pool.size))
        var preferFresh = random.nextBoolean()
        while (picked.size < size && (fresh.isNotEmpty() || inProgress.isNotEmpty())) {
            val takeFresh = when {
                inProgress.isEmpty() -> true
                fresh.isEmpty() -> false
                else -> preferFresh
            }
            picked += if (takeFresh) fresh.removeFirst() else inProgress.removeFirst()
            preferFresh = !preferFresh
        }
        return picked
    }
}
