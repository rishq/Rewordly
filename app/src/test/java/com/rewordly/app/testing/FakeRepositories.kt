package com.rewordly.app.testing

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.domain.model.DailyProgress
import com.rewordly.app.domain.model.InterfaceLanguage
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.ProgressOverview
import com.rewordly.app.domain.model.ReminderFrequency
import com.rewordly.app.domain.model.ReminderKind
import com.rewordly.app.domain.model.ReviewHistory
import com.rewordly.app.domain.model.ReviewState
import com.rewordly.app.domain.model.ReviewStatistics
import com.rewordly.app.domain.model.ReviewSubmission
import com.rewordly.app.domain.model.ReviewSubmitResult
import com.rewordly.app.domain.model.Streak
import com.rewordly.app.domain.model.ThemeMode
import com.rewordly.app.domain.model.UserSettings
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordKeys
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import com.rewordly.app.domain.model.WordWithProgress
import com.rewordly.app.domain.repository.ProgressRepository
import com.rewordly.app.domain.repository.ReviewRepository
import com.rewordly.app.domain.repository.SettingsRepository
import com.rewordly.app.domain.repository.VocabularyRepository
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [VocabularyRepository] for ViewModel tests. */
class FakeVocabularyRepository(words: List<WordWithProgress>) : VocabularyRepository {
    val items = MutableStateFlow(words)
    val views = MutableStateFlow(0)
    val addedWords = mutableListOf<Word>()

    /** When set, lookups and inserts fail with a database error (reading words keeps working). */
    var dbFailure: Exception? = null

    override suspend fun ensureSeeded(): AppResult<Unit> = AppResult.Success(Unit)

    /** When set, reading the vocabulary fails, which simulates a broken database. */
    var failure: Exception? = null

    override fun observeWords(language: LearningLanguage): Flow<List<WordWithProgress>> =
        items.map { list -> failure?.let { throw it } ?: list }

    override fun observeRecentWords(language: LearningLanguage, limit: Int): Flow<List<WordWithProgress>> =
        items.map { list -> list.take(limit) }

    override fun observeSavedWords(language: LearningLanguage): Flow<List<WordWithProgress>> =
        items.map { list -> list.filter { it.progress.isSaved } }

    override fun observeWord(wordId: String): Flow<WordWithProgress?> =
        items.map { list -> list.firstOrNull { it.word.id == wordId } }

    override suspend fun setSaved(wordId: String, saved: Boolean): AppResult<Unit> = update(wordId) {
        it.copy(progress = it.progress.copy(isSaved = saved))
    }

    override suspend fun recordView(wordId: String): AppResult<Unit> {
        views.value++
        return AppResult.Success(Unit)
    }

    override suspend fun findExistingIds(
        language: LearningLanguage,
        keys: Collection<String>,
    ): AppResult<Map<String, String>> {
        dbFailure?.let { return AppResult.Failure(com.rewordly.app.core.common.AppError.Database(it)) }
        val wanted = keys.map(WordKeys::normalize).toSet()
        return AppResult.Success(
            items.value.filter { WordKeys.normalize(it.word.text) in wanted }
                .associate { WordKeys.normalize(it.word.text) to it.word.id },
        )
    }

    override suspend fun addWords(words: List<Word>): AppResult<List<String>> {
        dbFailure?.let { return AppResult.Failure(com.rewordly.app.core.common.AppError.Database(it)) }
        val known = items.value.map { WordKeys.normalize(it.word.text) }.toSet()
        val fresh = words.filter { WordKeys.normalize(it.text) !in known }
        addedWords += fresh
        items.value = items.value + fresh.map { WordWithProgress(it, WordProgress(it.id)) }
        return AppResult.Success(fresh.map { it.id })
    }

    fun progressOf(wordId: String) = items.value.first { it.word.id == wordId }.progress

    private fun update(wordId: String, transform: (WordWithProgress) -> WordWithProgress): AppResult<Unit> {
        items.value = items.value.map { if (it.word.id == wordId) transform(it) else it }
        return AppResult.Success(Unit)
    }
}

class FakeSettingsRepository(
    initial: UserSettings = UserSettings(),
) : SettingsRepository {
    val state = MutableStateFlow(initial)
    val searches = MutableStateFlow<List<String>>(emptyList())

    override val settings: Flow<UserSettings> = state

    override val recentSearches: Flow<List<String>> = searches

    override suspend fun setInterfaceLanguage(language: InterfaceLanguage) {
        state.value = state.value.copy(interfaceLanguage = language)
    }

    override suspend fun setLearningLanguage(language: LearningLanguage) {
        state.value = state.value.copy(learningLanguage = language)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setDailyGoal(goal: Int) {
        state.value = state.value.copy(dailyGoal = goal)
    }

    override suspend fun setReminderTime(hour: Int, minute: Int) {
        state.value = state.value.copy(reminderHour = hour, reminderMinute = minute)
    }

    override suspend fun setReminderFrequency(frequency: ReminderFrequency) {
        state.value = state.value.copy(reminderFrequency = frequency)
    }

    override suspend fun setReminderDays(days: Set<DayOfWeek>) {
        state.value = state.value.copy(reminderDays = days)
    }

    override suspend fun setQuietHours(enabled: Boolean, startHour: Int, endHour: Int) {
        state.value = state.value.copy(quietHoursEnabled = enabled, quietStartHour = startHour, quietEndHour = endHour)
    }

    override suspend fun setNotificationKinds(kinds: Set<ReminderKind>) {
        state.value = state.value.copy(notificationKinds = kinds)
    }

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        state.value = state.value.copy(notificationsEnabled = enabled)
    }

    override suspend fun setOnboardingCompleted() {
        state.value = state.value.copy(onboardingCompleted = true)
    }

    override suspend fun addRecentSearch(query: String) {
        searches.value = listOf(query) + searches.value.filterNot { it == query }
    }

    override suspend fun clearRecentSearches() {
        searches.value = emptyList()
    }

    override suspend fun setLevel(level: com.rewordly.app.domain.model.Difficulty?) {
        state.value = state.value.copy(level = level)
    }

    override suspend fun setLearningGoal(goal: com.rewordly.app.domain.model.LearningGoal) {
        state.value = state.value.copy(learningGoal = goal)
    }

    override suspend fun setInterests(interests: Set<com.rewordly.app.domain.model.TopicPreset>) {
        state.value = state.value.copy(interests = interests)
    }

    override suspend fun setSessionLength(length: Int) {
        state.value = state.value.copy(sessionLength = length)
    }

    override suspend fun setProfileConfigured() {
        state.value = state.value.copy(profileConfigured = true)
    }
}

class FakeProgressRepository(
    private val overview: ProgressOverview,
    private val statistics: ReviewStatistics = ReviewStatistics(),
    private val history: ReviewHistory = ReviewHistory(emptyList()),
) : ProgressRepository {
    override fun observeStatistics(): Flow<ReviewStatistics> = MutableStateFlow(statistics)

    override fun observeHistory(): Flow<ReviewHistory> = MutableStateFlow(history)

    override fun observeOverview(): Flow<ProgressOverview> = MutableStateFlow(overview)
}

fun overviewOf(
    today: DailyProgress = DailyProgress(java.time.LocalDate.of(2024, 5, 10), 0, 0, 10),
    streak: Streak = Streak(),
): ProgressOverview = ProgressOverview(
    today = today,
    wordsLearned = 0,
    wordsReviewed = 0,
    wordsDueToday = 0,
    newWordsAvailable = 0,
    reviewedToday = 0,
    savedWords = 0,
    totalWords = 0,
    streak = streak,
    activity = listOf(DailyProgress(today.date, 0, 0, today.goal)),
)

/**
 * In-memory [ReviewRepository] writing into a [FakeVocabularyRepository], so view models see their own writes.
 * Mirrors the real duplicate rule: one (session, word) answer per session.
 */
class FakeReviewRepository(private val vocabulary: FakeVocabularyRepository) : ReviewRepository {
    val submissions = mutableListOf<ReviewSubmission>()
    val learned = mutableListOf<Pair<String, ReviewState>>()
    var failNext = false
    var dueCount = 0

    /** When set, [submitReview] suspends until completed, which simulates a slow database write. */
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun getProgress(wordId: String): AppResult<WordProgress> = if (failNext) {
        failNext = false
        AppResult.Failure(com.rewordly.app.core.common.AppError.Database())
    } else {
        AppResult.Success(vocabulary.progressOf(wordId))
    }

    override suspend fun submitReview(submission: ReviewSubmission): AppResult<ReviewSubmitResult> {
        gate?.await()
        if (submissions.any { it.sessionId == submission.sessionId && it.wordId == submission.wordId }) {
            return AppResult.Success(ReviewSubmitResult.Duplicate)
        }
        submissions += submission
        vocabulary.items.value = vocabulary.items.value.map {
            if (it.word.id == submission.wordId) {
                val correct = submission.quality.isCorrect
                it.copy(
                    progress = it.progress.withReviewState(submission.newState).copy(
                        correctAnswers = it.progress.correctAnswers + if (correct) 1 else 0,
                        incorrectAnswers = it.progress.incorrectAnswers + if (correct) 0 else 1,
                    ),
                )
            } else {
                it
            }
        }
        return AppResult.Success(ReviewSubmitResult.Applied(submission.newState))
    }

    override suspend fun markLearned(wordId: String, sessionId: String, state: ReviewState, at: Long): AppResult<Unit> {
        learned += wordId to state
        vocabulary.items.value = vocabulary.items.value.map {
            if (it.word.id == wordId) it.copy(progress = it.progress.withReviewState(state)) else it
        }
        return AppResult.Success(Unit)
    }

    override suspend fun markNotLearned(wordId: String): AppResult<Unit> {
        vocabulary.items.value = vocabulary.items.value.map {
            if (it.word.id == wordId) {
                it.copy(progress = it.progress.withReviewState(ReviewState(wordId, learningStatus = WordStatus.NEW)))
            } else {
                it
            }
        }
        return AppResult.Success(Unit)
    }

    override suspend fun countDue(): AppResult<Int> = AppResult.Success(dueCount)
}
