package com.rewordly.app.testing

import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.common.IdProvider
import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.GeneratedExample
import com.rewordly.app.domain.model.GeneratedWord
import com.rewordly.app.domain.model.GenerationHistoryEntry
import com.rewordly.app.domain.model.GenerationRequest
import com.rewordly.app.domain.model.GenerationResult
import com.rewordly.app.domain.model.GenerationSettings
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.UsageInfo
import com.rewordly.app.domain.repository.GenerationHistoryRepository
import com.rewordly.app.domain.repository.GenerationSettingsRepository
import com.rewordly.app.domain.repository.VocabularyGenerationRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

fun generatedWord(
    word: String,
    translation: String = "перевод",
    difficulty: Difficulty = Difficulty.B1,
    examples: List<GeneratedExample> =
        listOf(GeneratedExample("I use $word every day.", "Я использую это каждый день.")),
) = GeneratedWord(
    word = word,
    translation = translation,
    pronunciation = "/$word/",
    partOfSpeech = PartOfSpeech.NOUN,
    difficulty = difficulty,
    definition = "Definition of $word.",
    definitionTranslation = "Определение слова.",
    examples = examples,
    synonyms = listOf("alpha"),
    relatedWords = listOf("beta"),
)

/** Scripted [VocabularyGenerationRepository]: queue results, optionally hold a call open to test cancellation. */
class FakeVocabularyGenerationRepository : VocabularyGenerationRepository {
    val requests = mutableListOf<GenerationRequest>()
    val results = ArrayDeque<AppResult<GenerationResult>>()
    var gate: CompletableDeferred<Unit>? = null
    private val _usage = MutableStateFlow<UsageInfo?>(null)
    override val usage: StateFlow<UsageInfo?> = _usage

    fun setUsage(info: UsageInfo?) {
        _usage.value = info
    }

    fun enqueue(vararg words: GeneratedWord) {
        results += AppResult.Success(GenerationResult(words.toList()))
    }

    override suspend fun generate(request: GenerationRequest): AppResult<GenerationResult> {
        requests += request
        gate?.await()
        return results.removeFirstOrNull() ?: AppResult.Failure(com.rewordly.app.core.common.AppError.EmptyResponse)
    }
}

class FakeGenerationHistoryRepository : GenerationHistoryRepository {
    val entries = MutableStateFlow<List<GenerationHistoryEntry>>(emptyList())
    val stored = mutableMapOf<String, List<GeneratedWord>>()

    override fun observeHistory(): Flow<List<GenerationHistoryEntry>> = entries.map {
        it.sortedByDescending { e -> e.createdAt }
    }

    override suspend fun record(entry: GenerationHistoryEntry, items: List<GeneratedWord>): AppResult<Unit> {
        entries.value = entries.value.filterNot { it.id == entry.id } + entry.copy(resultCount = items.size)
        stored[entry.id] = items
        return AppResult.Success(Unit)
    }

    override suspend fun getEntry(id: String): AppResult<GenerationHistoryEntry?> =
        AppResult.Success(entries.value.firstOrNull { it.id == id })

    override suspend fun getItems(id: String): AppResult<List<GeneratedWord>> = AppResult.Success(stored[id].orEmpty())

    override suspend fun updateItems(id: String, items: List<GeneratedWord>): AppResult<Unit> {
        stored[id] = items
        return AppResult.Success(Unit)
    }

    override suspend fun delete(id: String): AppResult<Unit> {
        entries.value = entries.value.filterNot { it.id == id }
        stored.remove(id)
        return AppResult.Success(Unit)
    }

    override suspend fun clear(): AppResult<Unit> {
        entries.value = emptyList()
        stored.clear()
        return AppResult.Success(Unit)
    }
}

class FakeGenerationSettingsRepository(initial: GenerationSettings = GenerationSettings()) :
    GenerationSettingsRepository {
    val state = MutableStateFlow(initial)
    val topic = MutableStateFlow("")
    val notice = MutableStateFlow(false)

    override val settings: Flow<GenerationSettings> = state
    override val lastTopic: Flow<String> = topic
    override val textNoticeAccepted: Flow<Boolean> = notice

    override suspend fun save(settings: GenerationSettings) {
        state.value = settings
    }

    override suspend fun saveLastTopic(topic: String) {
        this.topic.value = topic
    }

    override suspend fun acceptTextNotice() {
        notice.value = true
    }
}

class SequentialIdProvider(private val prefix: String = "gen") : IdProvider {
    private var counter = 0

    override fun newId(): String = "$prefix-${++counter}"
}
