package com.rewordly.app.data.local

import com.rewordly.app.domain.model.Difficulty
import com.rewordly.app.domain.model.LearningLanguage
import com.rewordly.app.domain.model.PartOfSpeech
import com.rewordly.app.domain.model.Word
import com.rewordly.app.domain.model.WordExample
import com.rewordly.app.domain.model.WordTranslation

/**
 * Bundled starter vocabulary (English -> Russian) used until content is delivered by a backend.
 * This is learning content, not UI text, so it intentionally lives outside string resources.
 */
object MockVocabulary {
    private const val TRANSLATION_LANGUAGE = "ru"

    val words: List<Word> = listOf(
        word(
            "beautiful", "красивый", "/ˈbjuːtɪfəl/", PartOfSpeech.ADJECTIVE, Difficulty.A1,
            examples = listOf(
                "She has a beautiful voice." to "У неё красивый голос.",
                "What a beautiful morning!" to "Какое прекрасное утро!",
            ),
            forms = listOf("more beautiful", "most beautiful"),
            related = listOf("beauty", "beautifully"),
            synonyms = listOf("lovely", "pretty", "gorgeous"),
        ),
        word(
            "improve", "улучшать", "/ɪmˈpruːv/", PartOfSpeech.VERB, Difficulty.A2,
            examples = listOf(
                "I want to improve my English." to "Я хочу улучшить свой английский.",
                "His health improved quickly." to "Его здоровье быстро улучшилось.",
            ),
            forms = listOf("improves", "improved", "improving"),
            related = listOf("improvement"),
            synonyms = listOf("enhance", "upgrade", "better"),
        ),
        word(
            "reliable", "надёжный", "/rɪˈlaɪəbəl/", PartOfSpeech.ADJECTIVE, Difficulty.B1,
            examples = listOf(
                "He is a reliable friend." to "Он надёжный друг.",
                "We need a reliable internet connection." to "Нам нужно надёжное подключение к интернету.",
            ),
            forms = listOf("more reliable", "most reliable"),
            related = listOf("rely", "reliability", "reliably"),
            synonyms = listOf("dependable", "trustworthy"),
        ),
        word(
            "achieve", "достигать", "/əˈtʃiːv/", PartOfSpeech.VERB, Difficulty.B1,
            examples = listOf(
                "She achieved all her goals this year." to "В этом году она достигла всех своих целей.",
                "You can achieve anything with practice." to "С практикой можно добиться чего угодно.",
            ),
            forms = listOf("achieves", "achieved", "achieving"),
            related = listOf("achievement", "achievable"),
            synonyms = listOf("accomplish", "reach", "attain"),
        ),
        word(
            "journey", "путешествие", "/ˈdʒɜːrni/", PartOfSpeech.NOUN, Difficulty.A2,
            examples = listOf(
                "The journey took three hours." to "Поездка заняла три часа.",
                "Learning a language is a long journey." to "Изучение языка — это долгий путь.",
            ),
            forms = listOf("journeys"),
            related = listOf("travel", "trip"),
            synonyms = listOf("trip", "voyage"),
        ),
        word(
            "curious", "любопытный", "/ˈkjʊəriəs/", PartOfSpeech.ADJECTIVE, Difficulty.B1,
            examples = listOf(
                "Children are naturally curious." to "Дети от природы любопытны.",
                "I'm curious about your plans." to "Мне интересно узнать о твоих планах.",
            ),
            forms = listOf("more curious", "most curious"),
            related = listOf("curiosity", "curiously"),
            synonyms = listOf("inquisitive", "interested"),
        ),
        word(
            "opportunity", "возможность", "/ˌɒpəˈtjuːnəti/", PartOfSpeech.NOUN, Difficulty.B1,
            examples = listOf(
                "This job is a great opportunity." to "Эта работа — отличная возможность.",
                "Don't miss the opportunity to travel." to "Не упускай возможность путешествовать.",
            ),
            forms = listOf("opportunities"),
            related = listOf("opportune"),
            synonyms = listOf("chance", "possibility"),
        ),
        word(
            "carefully",
            "внимательно",
            "/ˈkeəfəli/",
            PartOfSpeech.ADVERB,
            Difficulty.A2,
            examples = listOf(
                "Please read the instructions carefully." to "Пожалуйста, внимательно прочитайте инструкцию.",
                "He drives very carefully." to "Он водит очень аккуратно.",
            ),
            related = listOf("careful", "care", "careless"),
            synonyms = listOf("cautiously", "attentively"),
        ),
        word(
            "decision", "решение", "/dɪˈsɪʒən/", PartOfSpeech.NOUN, Difficulty.A2,
            examples = listOf(
                "It was a difficult decision." to "Это было трудное решение.",
                "We made the decision together." to "Мы приняли решение вместе.",
            ),
            forms = listOf("decisions"),
            related = listOf("decide", "decisive"),
            synonyms = listOf("choice", "resolution"),
        ),
        word(
            "overcome", "преодолевать", "/ˌəʊvəˈkʌm/", PartOfSpeech.VERB, Difficulty.B2,
            examples = listOf(
                "She overcame her fear of flying." to "Она преодолела свой страх полётов.",
                "Together we can overcome any problem." to "Вместе мы справимся с любой проблемой.",
            ),
            forms = listOf("overcomes", "overcame", "overcome", "overcoming"),
            related = listOf("overcoming"),
            synonyms = listOf("conquer", "defeat", "get over"),
        ),
        word(
            "enough",
            "достаточно",
            "/ɪˈnʌf/",
            PartOfSpeech.ADVERB,
            Difficulty.A1,
            examples = listOf(
                "Is the room warm enough?" to "В комнате достаточно тепло?",
                "We have enough time." to "У нас достаточно времени.",
            ),
            synonyms = listOf("sufficiently", "adequately"),
        ),
        word(
            "surprise", "сюрприз, удивление", "/səˈpraɪz/", PartOfSpeech.NOUN, Difficulty.A2,
            examples = listOf(
                "We have a surprise for you." to "У нас для тебя сюрприз.",
                "To my surprise, he agreed." to "К моему удивлению, он согласился.",
            ),
            forms = listOf("surprises"),
            related = listOf("surprised", "surprising"),
            synonyms = listOf("astonishment", "shock"),
        ),
        word(
            "although",
            "хотя",
            "/ɔːlˈðəʊ/",
            PartOfSpeech.CONJUNCTION,
            Difficulty.B1,
            examples = listOf(
                "Although it was raining, we went out." to "Хотя шёл дождь, мы вышли на улицу.",
                "He's friendly, although a bit shy." to "Он дружелюбный, хотя немного застенчивый.",
            ),
            related = listOf("though", "even though"),
            synonyms = listOf("though", "even though"),
        ),
        word(
            "thorough", "тщательный", "/ˈθʌrə/", PartOfSpeech.ADJECTIVE, Difficulty.B2,
            examples = listOf(
                "The doctor did a thorough examination." to "Врач провёл тщательный осмотр.",
                "We need a thorough plan." to "Нам нужен продуманный план.",
            ),
            forms = listOf("more thorough", "most thorough"),
            related = listOf("thoroughly", "thoroughness"),
            synonyms = listOf("careful", "detailed", "meticulous"),
        ),
        word(
            "take care of", "заботиться о", "/teɪk keər ɒv/", PartOfSpeech.PHRASE, Difficulty.A2,
            examples = listOf(
                "She takes care of her little brother." to "Она заботится о своём младшем брате.",
                "Don't worry, I'll take care of it." to "Не волнуйся, я об этом позабочусь.",
            ),
            forms = listOf("takes care of", "took care of", "taking care of"),
            related = listOf("care", "caring"),
            synonyms = listOf("look after", "look out for"),
        ),
        word(
            "resilient", "стойкий, жизнестойкий", "/rɪˈzɪliənt/", PartOfSpeech.ADJECTIVE, Difficulty.C1,
            examples = listOf(
                "Kids are often more resilient than adults." to "Дети часто бывают стойче взрослых.",
                "The city's economy proved resilient." to "Экономика города оказалась устойчивой.",
            ),
            forms = listOf("more resilient", "most resilient"),
            related = listOf("resilience"),
            synonyms = listOf("tough", "strong", "adaptable"),
        ),
        word(
            "meanwhile",
            "тем временем",
            "/ˈmiːnwaɪl/",
            PartOfSpeech.ADVERB,
            Difficulty.B2,
            examples = listOf(
                "Meanwhile, dinner was getting cold." to "Тем временем ужин остывал.",
                "I'll cook. Meanwhile, you can set the table." to "Я приготовлю. А ты пока накрой на стол.",
            ),
            synonyms = listOf("meantime", "at the same time"),
        ),
        word(
            "ubiquitous",
            "вездесущий, повсеместный",
            "/juːˈbɪkwɪtəs/",
            PartOfSpeech.ADJECTIVE,
            Difficulty.C2,
            examples = listOf(
                "Smartphones have become ubiquitous." to "Смартфоны стали повсеместными.",
                "Coffee shops are ubiquitous in this city." to "Кофейни в этом городе на каждом шагу.",
            ),
            related = listOf("ubiquity"),
            synonyms = listOf("omnipresent", "everywhere", "widespread"),
        ),
    )

    private fun word(
        text: String,
        translation: String,
        pronunciation: String,
        partOfSpeech: PartOfSpeech,
        difficulty: Difficulty,
        examples: List<Pair<String, String>>,
        forms: List<String> = emptyList(),
        related: List<String> = emptyList(),
        synonyms: List<String> = emptyList(),
    ): Word {
        val id = "en-" + text.lowercase().replace(' ', '-')
        return Word(
            id = id,
            language = LearningLanguage.ENGLISH,
            text = text,
            translation = WordTranslation(TRANSLATION_LANGUAGE, translation),
            pronunciation = pronunciation,
            partOfSpeech = partOfSpeech,
            difficulty = difficulty,
            examples = examples.mapIndexed { index, (sentence, sentenceTranslation) ->
                WordExample(id = "$id-ex$index", wordId = id, text = sentence, translation = sentenceTranslation)
            },
            forms = forms,
            relatedWords = related,
            synonyms = synonyms,
        )
    }
}
