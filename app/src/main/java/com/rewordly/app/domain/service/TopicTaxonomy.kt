package com.rewordly.app.domain.service

import com.rewordly.app.domain.model.TopicPreset
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordKeys

/**
 * Maps vocabulary onto the topic interests a user picks in their learning profile.
 *
 * The bundled starter vocabulary is curated by hand (it is a small, fixed set); anything else -
 * words saved from AI generation, or words added later - falls back to a keyword table applied to
 * the word, its definition and its related words. Everything here is pure and deterministic, so
 * the same word always belongs to the same topics.
 *
 * A word with no topic information simply returns an empty set: the recommender then treats it as
 * level-based instead of inventing an interest it cannot justify.
 */
object TopicTaxonomy {
    /** Curated topics for the bundled starter vocabulary, keyed by normalized word text. */
    private val curated: Map<String, Set<TopicPreset>> = mapOf(
        // A1
        "beautiful" to setOf(TopicPreset.EVERYDAY),
        "enough" to setOf(TopicPreset.EVERYDAY),
        "surprise" to setOf(TopicPreset.EVERYDAY, TopicPreset.COMMUNICATION),
        "journey" to setOf(TopicPreset.TRAVEL),
        "careful" to setOf(TopicPreset.EVERYDAY),
        "begin" to setOf(TopicPreset.EVERYDAY, TopicPreset.EDUCATION),
        "borrow" to setOf(TopicPreset.EVERYDAY, TopicPreset.BUSINESS),
        "answer" to setOf(TopicPreset.COMMUNICATION, TopicPreset.EDUCATION),
        // A2
        "improve" to setOf(TopicPreset.EDUCATION, TopicPreset.BUSINESS),
        "reliable" to setOf(TopicPreset.BUSINESS, TopicPreset.TECHNOLOGY),
        "opportunity" to setOf(TopicPreset.BUSINESS, TopicPreset.EDUCATION),
        "decision" to setOf(TopicPreset.BUSINESS, TopicPreset.EVERYDAY),
        "require" to setOf(TopicPreset.BUSINESS),
        "although" to setOf(TopicPreset.EDUCATION, TopicPreset.EVERYDAY),
        "take care of" to setOf(TopicPreset.EVERYDAY),
        "weather" to setOf(TopicPreset.EVERYDAY, TopicPreset.SCIENCE),
        // B1
        "achieve" to setOf(TopicPreset.BUSINESS, TopicPreset.EDUCATION),
        "environment" to setOf(TopicPreset.SCIENCE),
        "aware" to setOf(TopicPreset.EVERYDAY, TopicPreset.SCIENCE),
        "challenge" to setOf(TopicPreset.BUSINESS, TopicPreset.EDUCATION),
        "support" to setOf(TopicPreset.BUSINESS, TopicPreset.EVERYDAY),
        "suggest" to setOf(TopicPreset.BUSINESS, TopicPreset.COMMUNICATION),
        "describe" to setOf(TopicPreset.COMMUNICATION, TopicPreset.EDUCATION),
        "develop" to setOf(TopicPreset.TECHNOLOGY, TopicPreset.PROGRAMMING, TopicPreset.BUSINESS),
        // B2
        "overcome" to setOf(TopicPreset.EVERYDAY, TopicPreset.EDUCATION),
        "thorough" to setOf(TopicPreset.BUSINESS, TopicPreset.EDUCATION),
        "prioritise" to setOf(TopicPreset.BUSINESS),
        "confident" to setOf(TopicPreset.EVERYDAY, TopicPreset.BUSINESS),
        "generous" to setOf(TopicPreset.EVERYDAY),
        "obvious" to setOf(TopicPreset.EDUCATION, TopicPreset.SCIENCE),
        "reduce" to setOf(TopicPreset.SCIENCE, TopicPreset.BUSINESS),
        "apologise" to setOf(TopicPreset.COMMUNICATION, TopicPreset.EVERYDAY),
    )

    /** Keyword stems used for words that are not curated. Matched against whole tokens. */
    private val keywords: Map<TopicPreset, List<String>> = mapOf(
        TopicPreset.PROGRAMMING to listOf(
            "code", "program", "develop", "deploy", "compile", "bug", "software", "api",
            "database", "server", "function", "variable", "debug", "algorithm", "framework", "repository",
        ),
        TopicPreset.TECHNOLOGY to listOf(
            "computer", "internet", "device", "digital", "data", "network", "machine", "app",
            "technology", "online", "robot", "screen", "cloud", "browser", "hardware", "automate",
        ),
        TopicPreset.BUSINESS to listOf(
            "business", "market", "company", "money", "price", "customer", "manage", "finance",
            "office", "meeting", "project", "budget", "contract", "profit", "invest", "hire",
            "team", "product", "sell", "buy", "salary", "invoice", "client", "negotiate",
        ),
        TopicPreset.TRAVEL to listOf(
            "travel", "journey", "trip", "airport", "hotel", "ticket", "flight", "luggage",
            "tourist", "abroad", "passport", "train", "country", "map", "holiday", "vacation",
            "suitcase", "destination", "border", "visa",
        ),
        TopicPreset.SCIENCE to listOf(
            "science", "physics", "chemistry", "biology", "energy", "climate", "experiment",
            "research", "molecule", "planet", "environment", "cell", "theory", "gravity",
            "species", "genetic", "laboratory", "atmosphere",
        ),
        TopicPreset.EDUCATION to listOf(
            "study", "learn", "teach", "school", "university", "exam", "lesson", "student",
            "teacher", "course", "homework", "degree", "knowledge", "education", "grade",
            "lecture", "semester", "curriculum",
        ),
        TopicPreset.MOVIES to listOf(
            "film", "movie", "actor", "cinema", "scene", "director", "series", "episode",
            "plot", "character", "documentary", "screenplay", "audience", "sequel",
        ),
        TopicPreset.COMMUNICATION to listOf(
            "talk", "speak", "say", "tell", "ask", "answer", "conversation", "message",
            "email", "call", "discuss", "explain", "listen", "respond", "opinion", "argue",
            "persuade", "mention",
        ),
        TopicPreset.EVERYDAY to listOf(
            "home", "house", "food", "family", "friend", "time", "day", "water", "eat",
            "sleep", "morning", "evening", "clothes", "weather", "help", "feel", "happy",
            "tired", "walk", "cook",
        ),
    )

    /**
     * Topics of [word]. A topic stored on the word itself (from an imported file) wins, because the
     * user stated it explicitly; otherwise the curated table and then the keyword table are used.
     */
    fun topicsOf(word: Word): Set<TopicPreset> {
        val declared = parseTopicNames(word.topic)
        if (declared.isNotEmpty()) return declared
        return curated[WordKeys.normalize(word.text)] ?: deriveFromText(word)
    }

    /** Splits a comma, semicolon or pipe separated list of topic names, keeping only known presets. */
    fun parseTopicNames(raw: String): Set<TopicPreset> = raw.split(TOPIC_SEPARATOR)
        .mapNotNull { token ->
            val name = token.trim().uppercase().replace(' ', '_').replace('-', '_')
            if (name.isEmpty()) null else TopicPreset.entries.firstOrNull { it.name == name }
        }
        .toSet()

    /** Inverse of [parseTopicNames], used when a word is written to a file. */
    fun encodeTopicNames(topics: Set<TopicPreset>): String =
        TopicPreset.entries.filter { it in topics }.joinToString(",") { it.name }

    /** Topics of [word] that the user actually listed as an interest. */
    fun matchedInterests(word: Word, interests: Set<TopicPreset>): Set<TopicPreset> =
        if (interests.isEmpty()) emptySet() else topicsOf(word).intersect(interests)

    fun matches(word: Word, interests: Set<TopicPreset>): Boolean = matchedInterests(word, interests).isNotEmpty()

    private fun deriveFromText(word: Word): Set<TopicPreset> {
        val tokens = tokenize(
            buildString {
                append(word.text)
                append(' ')
                append(word.definition)
                append(' ')
                append(word.relatedWords.joinToString(" "))
                append(' ')
                append(word.synonyms.joinToString(" "))
            },
        )
        if (tokens.isEmpty()) return emptySet()
        return keywords.entries
            .filter { (_, stems) -> stems.any { stem -> tokens.any { it.matchesStem(stem) } } }
            .map { it.key }
            .toSet()
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase().split(NON_LETTER).filter { it.length >= MIN_TOKEN_LENGTH }

    /** A token matches a stem when it is the stem itself or an inflected form of it. */
    private fun String.matchesStem(stem: String): Boolean =
        this == stem || (length > stem.length && startsWith(stem) && stem.length >= MIN_STEM_LENGTH)

    private val NON_LETTER = Regex("[^a-z]+")
    private val TOPIC_SEPARATOR = Regex("[,;|]")
    private const val MIN_TOKEN_LENGTH = 3
    private const val MIN_STEM_LENGTH = 4
}
