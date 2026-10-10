package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.network.free.MyMemoryApi
import com.rewordly.app.core.network.free.WiktionaryApi
import com.rewordly.app.core.network.toNetworkAppError
import com.rewordly.app.data.remote.WiktionaryEntry
import com.rewordly.app.data.remote.WiktionaryParser
import com.rewordly.app.domain.model.WordLookup
import com.rewordly.app.domain.repository.WordLookupRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * Looks a word up in free public sources, without a key, an account or the project's backend.
 *
 * Two sources are combined because neither is enough on its own:
 *  - Wiktionary has the transcription, the part of speech, the definition and, for most words, a Russian
 *    translation. It is community maintained, so any single field can be missing.
 *  - MyMemory fills the translation when Wiktionary has none, which covers the words whose translation
 *    lives on a subpage.
 *
 * A word counts as found only when a translation came back from somewhere: a card with a transcription
 * but no meaning would be worse than an honest "nothing found".
 *
 * An error is reported only when a source that could have answered was actually unreachable. A source
 * that answers with nothing is a miss, not a failure, so a blank page plus a blank translation is
 * [WordLookup.NotFound] and the user is told to check the spelling rather than to try again later.
 */
@Singleton
class FreeWordLookupRepository @Inject constructor(
    private val wiktionary: WiktionaryApi,
    private val myMemory: MyMemoryApi,
) : WordLookupRepository {

    override suspend fun lookUp(word: String): AppResult<WordLookup> {
        val text = word.trim()
        val page = fetchWiktionary(text)
        val translation = page.entry?.translations?.firstOrNull()
            ?: machineTranslation(text).orEmpty()

        if (translation.isBlank()) {
            val unreachable = page.error
            return if (unreachable == null) {
                AppResult.Success(WordLookup.NotFound)
            } else {
                AppResult.Failure(unreachable)
            }
        }

        val entry = page.entry
        return AppResult.Success(
            WordLookup.Found(
                word = text,
                translation = translation,
                transcription = entry?.transcription.orEmpty(),
                partOfSpeech = entry?.partOfSpeech,
                definition = entry?.definition.orEmpty(),
                examples = entry?.examples.orEmpty(),
            ),
        )
    }

    /**
     * The page for [word], plus the translation subpage when the main page defers to one.
     *
     * A missing page is not an error — Wiktionary simply has no entry for that word — so the reason is
     * carried out separately and only used when the translation comes back empty as well.
     */
    private suspend fun fetchWiktionary(word: String): PageResult {
        val response = try {
            wiktionary.wikitext(word)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return PageResult(entry = null, error = e.toNetworkAppError())
        }
        val wikitext = response.parse?.wikitext ?: return PageResult(entry = null, error = null)

        val parsed = WiktionaryParser.parse(wikitext)
        val entry = if (WiktionaryParser.needsTranslationSubpage(wikitext)) {
            parsed.copy(translations = subpageTranslations(word))
        } else {
            parsed
        }
        return PageResult(entry = entry, error = null)
    }

    private suspend fun subpageTranslations(word: String): List<String> = try {
        wiktionary.wikitext("$word/translations").parse?.wikitext
            ?.let(WiktionaryParser::translations)
            .orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // The subpage is an optional extra: without it the machine translation still gets its chance.
        emptyList()
    }

    /**
     * A machine translation, or null when the service answered with nothing usable.
     *
     * Three answers are rejected rather than stored: the service echoes the word back when it has no
     * translation for it, it reports an exhausted daily quota *as* the translated text (a whole sentence
     * that would otherwise land in the translation field), and a translation of a single word is short by
     * nature, so anything long is not a translation of that word.
     */
    private suspend fun machineTranslation(word: String): String? = try {
        myMemory.translate(word, LANGUAGE_PAIR).responseData?.translatedText
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.takeIf { !it.equals(word, ignoreCase = true) }
            ?.takeIf { !it.contains(QUOTA_WARNING, ignoreCase = true) }
            ?.takeIf { it.length <= MAX_TRANSLATION_LENGTH }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    private data class PageResult(val entry: WiktionaryEntry?, val error: AppError?)

    private companion object {
        /** The app teaches English into Russian, matching the AI prompt and the bundled vocabulary. */
        const val LANGUAGE_PAIR = "en|ru"

        /** MyMemory puts this sentence in `translatedText` once the anonymous daily quota is spent. */
        const val QUOTA_WARNING = "MYMEMORY WARNING"

        /** Matches the ceiling the AI prompt puts on a translation. */
        const val MAX_TRANSLATION_LENGTH = 100
    }
}
