package com.rewordly.app.data.remote

import com.rewordly.app.domain.model.PartOfSpeech

/** The fields this app reads out of a Wiktionary page. Everything is best effort and may be empty. */
data class WiktionaryEntry(
    val transcription: String = "",
    val partOfSpeech: PartOfSpeech? = null,
    val definition: String = "",
    val examples: List<String> = emptyList(),
    val translations: List<String> = emptyList(),
)

/**
 * Reads the handful of fields this app needs out of Wiktionary's wikitext.
 *
 * Wikitext is a template language, so this is a targeted scan and not a parser: it looks for the exact
 * constructs Wiktionary uses for pronunciation, part of speech, senses and Russian translations, and
 * ignores everything else. That keeps it small and fast, at the cost of needing a look when Wiktionary
 * changes a template — which is why every rule here is covered by [WiktionaryParserTest].
 *
 * Kept free of Android and network types so the rules can be tested against captured wikitext.
 */
object WiktionaryParser {
    /** `{{IPA|en|/dɪˈplɔɪ/}}`; also matches the multi-pronunciation form and takes the first one. */
    private val IPA = Regex("""\{\{IPA\|en\|([^}|]+)""")

    /** A part-of-speech heading such as `===Verb===`; other heading levels are not senses. */
    private val POS_HEADING = Regex("""^===\s*([A-Za-z ]+?)\s*===$""", RegexOption.MULTILINE)

    /** A numbered sense: `# To move swiftly.` — but not `#:` examples or `#*` quotations. */
    private val SENSE_LINE = Regex("""^#\s+[^#:]""")

    /** A usage example: `#: {{ux|en|Deploy two units...}}`. */
    private val USAGE_EXAMPLE = Regex("""^#[:*]\s*\{\{ux\|en\|(.+?)\}\}""", RegexOption.MULTILINE)

    /** A Russian translation: `{{t|ru|бежать}}`, `{{t+|ru|бежать}}` or the `tt` variants. */
    private val RU_TRANSLATION = Regex("""\{\{t\+?t?\|ru\|([^}|]+)""")

    /** Large pages move their translations to a `word/translations` subpage and say so with this template. */
    private const val TRANSLATION_SUBPAGE_MARKER = "translation subpage"

    /** A combining acute marks stress. It is useful in Russian text, but noisy inside a translation field. */
    private const val STRESS_MARK = '\u0301'

    fun parse(wikitext: String): WiktionaryEntry = WiktionaryEntry(
        transcription = firstGroup(IPA, wikitext),
        partOfSpeech = partOfSpeech(wikitext),
        definition = definition(wikitext),
        examples = examples(wikitext),
        translations = translations(wikitext),
    )

    /** Russian translations, which live on the main page or on its translation subpage. */
    fun translations(wikitext: String): List<String> = RU_TRANSLATION.findAll(wikitext)
        .map { clean(it.groupValues[1]).replace(STRESS_MARK.toString(), "") }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

    /** True when the page defers its translations to a subpage, so the caller has to fetch one more page. */
    fun needsTranslationSubpage(wikitext: String): Boolean =
        wikitext.contains(TRANSLATION_SUBPAGE_MARKER) && translations(wikitext).isEmpty()

    private fun partOfSpeech(wikitext: String): PartOfSpeech? = POS_HEADING.findAll(wikitext)
        .mapNotNull { HEADINGS[it.groupValues[1].trim().lowercase()] }
        .firstOrNull()

    /**
     * The first sense that actually reads as a definition.
     *
     * Some pages open with a sense that is nothing but a template: `# {{ISO 639|2&3|Kirundi}}` on a page
     * whose English senses come later, or a place-name sense on a page that shares its spelling with a
     * proper noun. Those clean down to nothing or to a stray dot, so they are skipped rather than shown.
     */
    private fun definition(wikitext: String): String {
        val sense = wikitext.lineSequence()
            .filter { SENSE_LINE.containsMatchIn(it) }
            .map { clean(it.removePrefix("#").trim()) }
            .firstOrNull { it.length >= MIN_DEFINITION }
            ?: return ""
        // A long definition is cut at a word boundary, so a card never ends in half a word.
        return if (sense.length <= MAX_DEFINITION) sense else sense.take(MAX_DEFINITION).substringBeforeLast(' ')
    }

    private fun examples(wikitext: String): List<String> = USAGE_EXAMPLE.findAll(wikitext)
        .map { clean(it.groupValues[1]) }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_EXAMPLES)
        .toList()

    private fun firstGroup(regex: Regex, text: String): String = regex.find(text)?.groupValues?.get(1)?.trim().orEmpty()

    /**
     * Strips the inline markup a definition or an example can carry: links keep their label, templates
     * such as `{{lb|en|military}}` disappear, and emphasis and HTML tags are dropped.
     */
    internal fun clean(text: String): String = text
        .replace(LINK_WITH_LABEL, "$1")
        .replace(LINK, "$1")
        .replace(LINK_TEMPLATE, "$1")
        .replace(TEMPLATE, "")
        .replace(BOLD, "")
        .replace(ITALIC, "")
        .replace(HTML_TAG, "")
        .replace(WHITESPACE, " ")
        .replace(SPACE_BEFORE_PUNCTUATION, "$1")
        .trim()

    private val LINK_WITH_LABEL = Regex("""\[\[[^\]|]*\|([^\]]*)]]""")
    private val LINK = Regex("""\[\[([^\]]*)]]""")

    /**
     * `{{l|en|word}}` and `{{m|en|word}}` are inline links that carry the word itself, so unlike the
     * labels around them (`{{lb|en|military}}`) they have to survive as text rather than disappear.
     */
    private val LINK_TEMPLATE = Regex("""\{\{(?:l|m)\|[a-z-]+\|([^}|]+)[^{}]*}}""")
    private val TEMPLATE = Regex("""\{\{[^{}]*}}""")
    private val BOLD = Regex("""'''""")
    private val ITALIC = Regex("""''""")
    private val HTML_TAG = Regex("""<[^>]+>""")
    private val WHITESPACE = Regex("""\s+""")

    /** Dropping a template leaves the space that preceded the punctuation after it: "surrender ;". */
    private val SPACE_BEFORE_PUNCTUATION = Regex("""\s+([,.;:!?])""")

    private const val MAX_DEFINITION = 300

    /** Shorter than this and a "definition" is punctuation left over from a stripped template. */
    private const val MIN_DEFINITION = 3

    private const val MAX_EXAMPLES = 3

    /** The headings Wiktionary uses for parts of speech; anything else is not a sense this app models. */
    private val HEADINGS: Map<String, PartOfSpeech> = mapOf(
        "noun" to PartOfSpeech.NOUN,
        "proper noun" to PartOfSpeech.NOUN,
        "verb" to PartOfSpeech.VERB,
        "adjective" to PartOfSpeech.ADJECTIVE,
        "adverb" to PartOfSpeech.ADVERB,
        "pronoun" to PartOfSpeech.PRONOUN,
        "preposition" to PartOfSpeech.PREPOSITION,
        "conjunction" to PartOfSpeech.CONJUNCTION,
        "interjection" to PartOfSpeech.INTERJECTION,
        "phrase" to PartOfSpeech.PHRASE,
        "proverb" to PartOfSpeech.PHRASE,
        "idiom" to PartOfSpeech.PHRASE,
    )
}
