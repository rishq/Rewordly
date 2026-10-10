package com.rewordly.app.testing

import com.rewordly.app.core.database.dao.DailyActivityDao
import com.rewordly.app.core.database.dao.GenerationHistoryDao
import com.rewordly.app.core.database.dao.GenerationHistorySummary
import com.rewordly.app.core.database.dao.ProgressCounts
import com.rewordly.app.core.database.dao.ReviewLogDao
import com.rewordly.app.core.database.dao.ReviewLogTotals
import com.rewordly.app.core.database.dao.WordDao
import com.rewordly.app.core.database.dao.WordKeyRow
import com.rewordly.app.core.database.dao.WordProgressDao
import com.rewordly.app.core.database.entity.DailyActivityEntity
import com.rewordly.app.core.database.entity.GenerationHistoryEntity
import com.rewordly.app.core.database.entity.PopulatedWord
import com.rewordly.app.core.database.entity.ReviewLogEntity
import com.rewordly.app.core.database.entity.WordEntity
import com.rewordly.app.core.database.entity.WordExampleEntity
import com.rewordly.app.core.database.entity.WordProgressEntity
import com.rewordly.app.data.local.exampleEntities
import com.rewordly.app.data.local.toEntity
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordProgress
import com.rewordly.app.domain.model.WordStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory stand-ins for the Room DAOs, so repository logic can be tested on the JVM. */
class FakeWordDao(words: List<Word> = emptyList()) : WordDao {
    val entities = MutableStateFlow<List<WordEntity>>(emptyList())
    val examples = MutableStateFlow<List<WordExampleEntity>>(emptyList())
    val progress = MutableStateFlow<List<WordProgressEntity>>(emptyList())

    init {
        seed(words)
    }

    fun seed(words: List<Word>) {
        entities.value = words.mapIndexed { index, word -> word.toEntity(createdAt = index.toLong()) }
        examples.value = words.flatMap { it.exampleEntities() }
    }

    fun addProgress(entity: WordProgressEntity) {
        progress.value = progress.value.filterNot { it.wordId == entity.wordId } + entity
    }

    override suspend fun count(): Int = entities.value.size

    override fun observeCount(language: String): Flow<Int> =
        entities.map { list -> list.count { it.language == language } }

    override fun observeAll(language: String): Flow<List<PopulatedWord>> =
        entities.map { list -> list.filter { it.language == language }.sortedBy { it.createdAt }.map(::populate) }

    override fun observeRecent(language: String, limit: Int): Flow<List<PopulatedWord>> = entities.map { list ->
        list.filter { it.language == language }
            .sortedByDescending { entity -> lastTouched(entity) }
            .take(limit)
            .map(::populate)
    }

    private fun lastTouched(entity: WordEntity): Long =
        progress.value.firstOrNull { it.wordId == entity.id }?.updatedAt ?: entity.createdAt

    override fun observeSaved(language: String): Flow<List<PopulatedWord>> =
        observeAll(language).map { list -> list.filter { it.progress?.isSaved == true } }

    override fun observeById(id: String): Flow<PopulatedWord?> =
        observeAll(entities.value.firstOrNull { it.id == id }?.language.orEmpty())
            .map { list -> list.firstOrNull { it.word.id == id } }

    override fun observeByIds(ids: List<String>): Flow<List<PopulatedWord>> {
        val wanted = ids.toSet()
        return entities.map { list -> list.filter { it.id in wanted }.map(::populate) }
    }

    override suspend fun findByNormalizedText(language: String, keys: List<String>): List<WordKeyRow> = entities.value
        .filter { it.language == language && it.text.trim().lowercase() in keys }
        .map { WordKeyRow(it.id, it.text) }

    override suspend fun insertWords(words: List<WordEntity>) {
        entities.value = entities.value + words.filter { candidate -> entities.value.none { it.id == candidate.id } }
    }

    override suspend fun insertExamples(examples: List<WordExampleEntity>) {
        this.examples.value = this.examples.value + examples
    }

    private fun populate(entity: WordEntity) = PopulatedWord(
        word = entity,
        examples = examples.value.filter { it.wordId == entity.id }.sortedBy { it.position },
        progress = progress.value.firstOrNull { it.wordId == entity.id },
    )
}

class FakeWordProgressDao(
    /** Shared with [FakeWordDao.progress] so both fakes see the same rows. */
    private val rows: MutableStateFlow<List<WordProgressEntity>> = MutableStateFlow(emptyList()),
) : WordProgressDao {
    override suspend fun get(wordId: String): WordProgressEntity? = rows.value.firstOrNull { it.wordId == wordId }

    override suspend fun upsert(progress: WordProgressEntity) {
        rows.value = rows.value.filterNot { it.wordId == progress.wordId } + progress
    }

    override suspend fun countDue(endOfTodayMillis: Long, invalidAfterMillis: Long): Int =
        rows.value.count { isDue(it, endOfTodayMillis, invalidAfterMillis) }

    override fun observeCounts(endOfTodayMillis: Long, invalidAfterMillis: Long): Flow<ProgressCounts> =
        rows.map { list ->
            ProgressCounts(
                learned = list.count { it.status == WordStatus.LEARNED.name },
                started = list.count { it.status != WordStatus.NEW.name },
                due = list.count { isDue(it, endOfTodayMillis, invalidAfterMillis) },
                remembered = list.count { it.repetitionCount > 0 && it.consecutiveIncorrect == 0 },
                forgotten = list.count { it.consecutiveIncorrect > 0 },
                reviewed = list.sumOf { it.correctAnswers + it.incorrectAnswers },
                saved = list.count { it.isSaved },
            )
        }

    private fun isDue(row: WordProgressEntity, endOfToday: Long, invalidAfter: Long): Boolean {
        val next = row.nextReviewAt
        return row.status != WordStatus.NEW.name &&
            (next == null || next < 0 || next > invalidAfter || next < endOfToday)
    }
}

class FakeDailyActivityDao : DailyActivityDao {
    val rows = MutableStateFlow<List<DailyActivityEntity>>(emptyList())

    override fun observeRange(fromDay: String, toDay: String): Flow<List<DailyActivityEntity>> =
        rows.map { list -> list.filter { it.day >= fromDay && it.day <= toDay }.sortedBy { it.day } }

    override suspend fun addActivity(day: String, learned: Int, reviewed: Int) {
        val current = rows.value.firstOrNull { it.day == day }
        rows.value = rows.value.filterNot { it.day == day } + DailyActivityEntity(
            day = day,
            wordsLearned = (current?.wordsLearned ?: 0) + learned,
            wordsReviewed = (current?.wordsReviewed ?: 0) + reviewed,
        )
    }
}

/** Progress helper so tests can seed a repository. */
fun progressOf(wordId: String, updatedAt: Long, block: WordProgress.() -> WordProgress = { this }): WordProgressEntity =
    WordProgress(wordId = wordId).block().toEntity(updatedAt = updatedAt)

/** In-memory review log that enforces the same unique (session, word, kind) rule as the Room index. */
class FakeReviewLogDao(
    private val progress: MutableStateFlow<List<WordProgressEntity>> = MutableStateFlow(emptyList()),
) : ReviewLogDao {
    val logs = MutableStateFlow<List<ReviewLogEntity>>(emptyList())

    override suspend fun insertLog(log: ReviewLogEntity): Long {
        val duplicate = logs.value.any {
            it.sessionId == log.sessionId && it.wordId == log.wordId && it.kind == log.kind
        }
        if (duplicate) return -1L
        val id = logs.value.size + 1L
        logs.value = logs.value + log.copy(id = id)
        return id
    }

    override suspend fun upsertProgress(progress: WordProgressEntity) {
        this.progress.value = this.progress.value.filterNot { it.wordId == progress.wordId } + progress
    }

    override fun observeSince(sinceMillis: Long): Flow<List<ReviewLogEntity>> =
        logs.map { list -> list.filter { it.reviewedAt >= sinceMillis }.sortedBy { it.reviewedAt } }

    override fun observeTotals(): Flow<ReviewLogTotals> = logs.map { list ->
        val reviews = list.filter { it.kind == "REVIEW" }
        ReviewLogTotals(
            total = reviews.size,
            correct = reviews.count { it.quality >= 3 },
            qualitySum = reviews.sumOf { it.quality },
        )
    }
}

class FakeGenerationHistoryDao : GenerationHistoryDao {
    val rows = MutableStateFlow<List<GenerationHistoryEntity>>(emptyList())

    override fun observeSummaries(): Flow<List<GenerationHistorySummary>> = rows.map { list ->
        list.sortedByDescending { it.createdAt }.map {
            GenerationHistorySummary(
                id = it.id,
                mode = it.mode,
                description = it.description,
                level = it.level,
                requestedCount = it.requestedCount,
                resultCount = it.resultCount,
                createdAt = it.createdAt,
                hasResult = it.resultJson != null,
            )
        }
    }

    override suspend fun get(id: String): GenerationHistoryEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun insert(entry: GenerationHistoryEntity) {
        rows.value = rows.value.filterNot { it.id == entry.id } + entry
    }

    override suspend fun updateResult(id: String, json: String?, count: Int) {
        rows.value = rows.value.map { if (it.id == id) it.copy(resultJson = json, resultCount = count) else it }
    }

    override suspend fun delete(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}
