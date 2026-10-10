package com.rewordly.app.data.repository

import com.rewordly.app.core.common.AppError
import com.rewordly.app.core.common.AppResult
import com.rewordly.app.core.network.free.MyMemoryApi
import com.rewordly.app.core.network.free.MyMemoryData
import com.rewordly.app.core.network.free.MyMemoryResponse
import com.rewordly.app.core.network.free.WiktionaryApi
import com.rewordly.app.core.network.free.WiktionaryError
import com.rewordly.app.core.network.free.WiktionaryParse
import com.rewordly.app.core.network.free.WiktionaryParseResponse
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.WordLookup
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeWordLookupRepositoryTest {
    private val wiktionary = FakeWiktionaryApi()
    private val myMemory = FakeMyMemoryApi()
    private val repository = FreeWordLookupRepository(wiktionary, myMemory)

    private fun found(result: AppResult<WordLookup>) = (result as AppResult.Success).data as WordLookup.Found

    @Test
    fun readsTheWholeEntryFromWiktionaryWithoutAskingTheTranslator() = runTest {
        wiktionary.pages["deploy"] = """
            ===Pronunciation===
            * {{IPA|en|/dɪˈplɔɪ/}}
            ===Verb===
            # To prepare and arrange for use.
            ===Translations===
            {{t|ru|развёртывать}}
        """.trimIndent()

        val entry = found(repository.lookUp("deploy"))

        assertEquals("развёртывать", entry.translation)
        assertEquals("/dɪˈplɔɪ/", entry.transcription)
        assertEquals(PartOfSpeech.VERB, entry.partOfSpeech)
        assertEquals("To prepare and arrange for use.", entry.definition)
        assertEquals(emptyList<String>(), myMemory.requested)
    }

    @Test
    fun fallsBackToTheTranslatorWhenWiktionaryHasNoTranslation() = runTest {
        wiktionary.pages["computer"] = "===Pronunciation===\n* {{IPA|en|/kəmˈpjuː.tə/}}\n"
        myMemory.translated = "компьютер"

        val entry = found(repository.lookUp("computer"))

        assertEquals("компьютер", entry.translation)
        // The transcription still comes from Wiktionary: the two sources fill different gaps.
        assertEquals("/kəmˈpjuː.tə/", entry.transcription)
        assertEquals(listOf("computer"), myMemory.requested)
    }

    @Test
    fun readsTheTranslationSubpageWhenThePageDefersToIt() = runTest {
        wiktionary.pages["run"] = "===Verb===\n# To move swiftly.\n===Translations===\n{{see translation subpage|Verb}}"
        wiktionary.pages["run/translations"] = "{{t|ru|бе́гать}}"

        val entry = found(repository.lookUp("run"))

        assertEquals("бегать", entry.translation)
        assertEquals(listOf("run", "run/translations"), wiktionary.requested)
        assertEquals(emptyList<String>(), myMemory.requested)
    }

    @Test
    fun ignoresATranslationThatOnlyEchoesTheWordBack() = runTest {
        wiktionary.pages["zzzqqq"] = null
        myMemory.translated = "zzzqqq"

        assertEquals(AppResult.Success(WordLookup.NotFound), repository.lookUp("zzzqqq"))
    }

    @Test
    fun neverStoresTheQuotaWarningAsATranslation() = runTest {
        wiktionary.pages["deploy"] = null
        myMemory.translated =
            "MYMEMORY WARNING: YOU USED ALL AVAILABLE FREE TRANSLATIONS FOR TODAY. NEXT AVAILABLE IN 5 HOURS"

        assertEquals(AppResult.Success(WordLookup.NotFound), repository.lookUp("deploy"))
    }

    @Test
    fun neverStoresASentenceAsTheTranslationOfOneWord() = runTest {
        wiktionary.pages["deploy"] = null
        myMemory.translated = "To prepare and arrange for use. ".repeat(5).trim()

        assertEquals(AppResult.Success(WordLookup.NotFound), repository.lookUp("deploy"))
    }

    @Test
    fun reportsAMissWhenNothingKnowsTheWord() = runTest {
        wiktionary.pages["zzzqqq"] = null

        assertEquals(AppResult.Success(WordLookup.NotFound), repository.lookUp("zzzqqq"))
    }

    @Test
    fun reportsTheFailureWhenNoSourceCouldBeReached() = runTest {
        wiktionary.failure = IOException("no route to host")

        val result = repository.lookUp("deploy")

        // The original exception is carried along, so the type is what identifies the error here.
        assertTrue((result as AppResult.Failure).error is AppError.Network)
    }

    @Test
    fun keepsTheWordWhenOnlyTheTranslationCameBack() = runTest {
        wiktionary.pages["deploy"] = null
        myMemory.translated = "развёртывать"

        val entry = found(repository.lookUp("deploy"))

        assertEquals("развёртывать", entry.translation)
        assertEquals("", entry.transcription)
        assertEquals("", entry.definition)
        assertEquals(null, entry.partOfSpeech)
    }

    @Test
    fun trimsTheWordBeforeAskingAnySource() = runTest {
        wiktionary.pages["deploy"] = "===Translations===\n{{t|ru|развёртывать}}"

        assertTrue(repository.lookUp("  deploy  ") is AppResult.Success)

        assertEquals(listOf("deploy"), wiktionary.requested)
    }
}

private class FakeWiktionaryApi : WiktionaryApi {
    /** A null value means the page does not exist. */
    val pages = mutableMapOf<String, String?>()
    val requested = mutableListOf<String>()
    var failure: Throwable? = null

    override suspend fun wikitext(page: String): WiktionaryParseResponse {
        requested += page
        failure?.let { throw it }
        val wikitext = pages[page]
            ?: return WiktionaryParseResponse(error = WiktionaryError("missingtitle", "no such page"))
        return WiktionaryParseResponse(parse = WiktionaryParse(title = page, wikitext = wikitext))
    }
}

private class FakeMyMemoryApi : MyMemoryApi {
    var translated = ""
    val requested = mutableListOf<String>()

    override suspend fun translate(query: String, languagePair: String): MyMemoryResponse {
        requested += query
        return MyMemoryResponse(responseData = MyMemoryData(translatedText = translated))
    }
}
