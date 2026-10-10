package com.rewordly.app.data.remote

import com.rewordly.app.domain.model.PartOfSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parser reads a template language, so each rule is pinned against a real page's shape. The
 * fragments below are trimmed copies of Wiktionary's own wikitext.
 */
class WiktionaryParserTest {
    private val deploy = """
        ==English==
        {{wikipedia}}
        ===Alternative forms===
        * {{alter|en|deploye}}

        ===Etymology===
        From {{der|en|fr|déployer}}.

        ===Pronunciation===
        * {{IPA|en|/dɪˈplɔɪ/}}
        * {{audio|en|en-us-deploy.ogg|Audio (US)}}

        ===Verb===
        {{en-verb}}
        # To prepare and arrange (originally military unit or units) for use.
        #: {{ux|en|“'''Deploy''' two units of infantry along the enemy's flank,” the general ordered.}}
        # To unfold, open, or otherwise become ready for use.
        #: {{ux|en|He waited tensely for his parachute to '''deploy'''.}}

        ===Noun===
        {{en-noun}}
        # {{lb|en|military}} The act of deploying.

        ===Translations===
        {{t|ru|развёртывать}}
        {{t+|ru|разверну́ть}}
    """.trimIndent()

    private fun parse() = WiktionaryParser.parse(deploy)

    @Test
    fun readsTheTranscriptionFromTheIpaTemplate() {
        assertEquals("/dɪˈplɔɪ/", parse().transcription)
    }

    @Test
    fun readsTheFirstPartOfSpeechHeadingAndSkipsTheOtherHeadings() {
        // Alternative forms, etymology and pronunciation come first but are not parts of speech.
        assertEquals(PartOfSpeech.VERB, parse().partOfSpeech)
    }

    @Test
    fun readsTheFirstSenseAsTheDefinitionAndDropsTheTemplatesAroundIt() {
        assertEquals("To prepare and arrange (originally military unit or units) for use.", parse().definition)
    }

    @Test
    fun readsUsageExamplesAndStripsTheBoldMarkup() {
        val examples = parse().examples
        assertEquals(2, examples.size)
        assertEquals("“Deploy two units of infantry along the enemy's flank,” the general ordered.", examples[0])
    }

    @Test
    fun readsRussianTranslationsAndDropsTheStressMarks() {
        assertEquals(listOf("развёртывать", "развернуть"), parse().translations)
    }

    @Test
    fun doesNotAskForASubpageWhenThePageHasItsOwnTranslations() {
        assertFalse(WiktionaryParser.needsTranslationSubpage(deploy))
    }

    @Test
    fun asksForTheSubpageWhenThePageDefersItsTranslations() {
        val deferred = """
            ===Verb===
            # To move swiftly.
            ===Translations===
            {{see translation subpage|Verb}}
        """.trimIndent()

        assertTrue(WiktionaryParser.needsTranslationSubpage(deferred))
        assertEquals(emptyList<String>(), WiktionaryParser.translations(deferred))
    }

    @Test
    fun skipsASenseThatIsNothingButATemplate() {
        // The real "run" page opens with an ISO language code whose English senses come later.
        val page = """
            ===Noun===
            # {{ISO 639|2&3|Kirundi}}
            # To move swiftly.
        """.trimIndent()

        assertEquals("To move swiftly.", WiktionaryParser.parse(page).definition)
    }

    @Test
    fun cutsALongDefinitionAtAWordBoundary() {
        val long = (1..60).joinToString(" ") { "word$it" }

        val definition = WiktionaryParser.parse("===Noun===\n# $long\n").definition

        assertTrue(definition.length <= 300)
        // The cut lands on a space, so the last token is a whole word rather than a fragment.
        assertTrue(definition.substringAfterLast(' ').matches(Regex("""word\d+""")))
    }

    @Test
    fun keepsEverythingEmptyForAPageWithoutTheExpectedSections() {
        val entry = WiktionaryParser.parse("==English==\n{{wikipedia}}\n")

        assertEquals("", entry.transcription)
        assertEquals(null, entry.partOfSpeech)
        assertEquals("", entry.definition)
        assertEquals(emptyList<String>(), entry.examples)
        assertEquals(emptyList<String>(), entry.translations)
    }

    @Test
    fun ignoresSubsectionHeadingsThatAreNotPartsOfSpeech() {
        val withSubsections = """
            ===Verb===
            # To move swiftly.
            ====Synonyms====
            * {{l|en|unfold}}
            ====Derived terms====
        """.trimIndent()

        assertEquals(PartOfSpeech.VERB, WiktionaryParser.parse(withSubsections).partOfSpeech)
    }

    @Test
    fun cleansLinksTemplatesAndHtmlOutOfText() {
        assertEquals(
            "A programmable device",
            WiktionaryParser.clean("A [[programmable]] {{lb|en|computing}} <b>device</b>"),
        )
    }

    @Test
    fun keepsTheLabelOfALabelledLink() {
        assertEquals("surrender", WiktionaryParser.clean("[[surrender|surrender]]"))
    }

    @Test
    fun closesTheGapATemplateLeavesInFrontOfPunctuation() {
        assertEquals(
            "To surrender; to inform on someone.",
            WiktionaryParser.clean("To [[surrender]] ; to inform on someone."),
        )
    }

    @Test
    fun keepsTheWordInsideAnInlineLinkTemplate() {
        assertEquals(
            "To surrender; to inform on someone.",
            WiktionaryParser.clean("To {{l|en|surrender}} ; to inform on someone."),
        )
        assertEquals("A swift movement", WiktionaryParser.clean("A {{m|en|swift}} movement"))
    }

    @Test
    fun dropsTheLabelTemplatesThatCarryNoMeaningOfTheirOwn() {
        assertEquals("military", WiktionaryParser.clean("{{lb|en|military}} military"))
    }
}
