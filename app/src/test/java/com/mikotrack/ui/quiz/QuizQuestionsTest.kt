package com.mikotrack.ui.quiz

import com.mikotrack.ui.mushrooms.HabitatPreference
import com.mikotrack.ui.mushrooms.Mushroom
import com.mikotrack.ui.mushrooms.SpeciesData
import com.mikotrack.ui.mushrooms.SpeciesTexts
import com.mikotrack.data.prefs.QuizScore
import com.mikotrack.data.prefs.QuizScores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Generation rules of the quiz deck: every question must show exactly four
 * unique options with the right answer inside, and the curated banks must be
 * validated against the same contract.
 */
class QuizQuestionsTest {

    private val prompts = QuizPrompts(
        scientificName = "sci %1\$s",
        commonName = "com %1\$s",
        season = "season %1\$s",
        edibility = "edi %1\$s",
        habitat = "hab %1\$s",
    )

    private val treeLabels = mapOf(
        "scots_pine" to "Pini roig",
        "holm_oak" to "Roure de fulla petita",
        "beech" to "Faig",
        "oak" to "Roure",
        "birch" to "Bedoll",
    )

    private fun species(
        id: String,
        scientific: String,
        common: String,
        season: String,
        edibility: Int,
        trees: Map<String, Float>,
    ) = Mushroom(
        SpeciesData(
            id = id,
            scientificName = scientific,
            image = "$id.jpg",
            imageCredit = com.mikotrack.ui.mushrooms.ImageCredit("a", "b", "c", "d"),
            season = listOf(com.mikotrack.ui.mushrooms.SeasonRange(9, 11)),
            edibility = edibility,
            habitat = HabitatPreference(trees = trees),
        ),
        SpeciesTexts(
            commonName = common,
            culinary = "",
            habitat = "",
            season = season,
            lookAlikes = "",
        ),
    )

    private val mushrooms = listOf(
        species("rovello", "Lactarius deliciosus", "Rovelló", "Tardor", 4, mapOf("scots_pine" to 1f, "holm_oak" to 0.2f)),
        species("cep", "Boletus edulis", "Cep", "Tardor", 5, mapOf("beech" to 1f, "holm_oak" to 0.5f)),
        species("ou_reig", "Amanita caesarea", "Ou de Reig", "Estiu i tardor", 5, mapOf("holm_oak" to 1f)),
        species("murgola", "Morchella esculenta", "Murgola", "Primavera", 4, mapOf("birch" to 0.6f, "beech" to 0.8f)),
        species("fredolic", "Tricholoma equestre", "Fredolic", "Tardor", 3, mapOf("scots_pine" to 0.9f)),
    )

    private val curated = listOf(
        CuratedQuestion("q1", listOf("a", "b", "c", "d"), 2, "fact"),
        CuratedQuestion("bad-options", listOf("a", "b", "c"), 0, null),
        CuratedQuestion("bad-answer", listOf("a", "b", "c", "d"), 9, null),
        CuratedQuestion("repeated", listOf("a", "a", "b", "c"), 0, null),
    )

    @Test
    fun `every question has four unique options containing the answer`() {
        val deck = buildQuizDeck(mushrooms, curated, prompts, treeLabels, Random(42))
        assertTrue("deck is not empty", deck.isNotEmpty())
        for (question in deck) {
            assertEquals("four options in ${question.key}", 4, question.options.size)
            assertEquals("unique options in ${question.key}", 4, question.options.distinct().size)
            assertTrue(
                "answer inside options of ${question.key}",
                question.answerIndex in 0..3,
            )
            assertEquals(question.options[question.answerIndex], question.correctAnswer)
        }
    }

    @Test
    fun `invalid curated questions are dropped and valid ones kept`() {
        val deck = buildQuizDeck(mushrooms, curated, prompts, treeLabels, Random(1))
        val culture = deck.filter { it.category == QuizCategory.CULTURE }
        assertEquals(1, culture.size)
        assertEquals("q1", culture.first().prompt)
        assertEquals("culture:0", culture.first().key)
    }

    @Test
    fun `wrong options never equal the right answer`() {
        val deck = buildQuizDeck(mushrooms, curated, prompts, treeLabels, Random(7))
        for (question in deck) {
            val wrong = question.options.filterIndexed { i, _ -> i != question.answerIndex }
            assertTrue(
                "no distractor repeats the answer in ${question.key}",
                wrong.none { it == question.correctAnswer },
            )
        }
    }

    @Test
    fun `generation is deterministic for the same seed`() {
        val first = buildQuizDeck(mushrooms, curated, prompts, treeLabels, Random(3))
        val second = buildQuizDeck(mushrooms, curated, prompts, treeLabels, Random(3))
        assertEquals(first, second)
    }

    @Test
    fun `habitat question uses the best weighted tree`() {
        val deck = buildQuizDeck(mushrooms, curated, prompts, treeLabels, Random(5))
        val rovello = deck.first { it.key == "hab:rovello" }
        assertEquals("Pini roig", rovello.correctAnswer)
    }

    @Test
    fun `edibility question renders the star rating`() {
        val deck = buildQuizDeck(mushrooms, curated, prompts, treeLabels, Random(5))
        val cep = deck.first { it.key == "edi:cep" }
        assertEquals("★★★★★", cep.correctAnswer)
    }

    @Test
    fun `small catalogues skip the questions that need many species`() {
        val deck = buildQuizDeck(mushrooms.take(2), curated, prompts, treeLabels, Random(5))
        assertTrue(
            "no name or season riddles with only two species",
            deck.none {
                it.category in setOf(
                    QuizCategory.SCIENTIFIC_NAME,
                    QuizCategory.COMMON_NAME,
                    QuizCategory.SEASON,
                )
            },
        )
        assertTrue(deck.any { it.category == QuizCategory.EDIBILITY })
        assertTrue(deck.any { it.category == QuizCategory.CULTURE })
    }

    @Test
    fun `quiz scores round trip through the encoding`() {
        val scores = listOf(QuizScore("Montse", 320, 1_000L), QuizScore("Jordi", 80, 2_000L))
        assertEquals(scores, QuizScores.decode(QuizScores.encode(scores)))
    }

    @Test
    fun `quiz score insert keeps the best ranked first`() {
        var raw: String? = null
        for ((index, score) in listOf(10, 500, 30, 900, 20, 700, 100, 400).withIndex()) {
            raw = QuizScores.insert(raw, QuizScore("P$index", score, score.toLong()))
        }
        val rank = QuizScores.decode(raw)
        assertEquals(10, QuizScores.MAX_RANK)
        assertEquals(8, rank.size)
        assertEquals(listOf(900, 700, 500, 400, 100, 30, 20, 10), rank.map { it.score })
    }

    @Test
    fun `quiz score names cannot break the encoding`() {
        val raw = QuizScores.encode(listOf(QuizScore("a:b;c", 5, 1L)))
        val decoded = QuizScores.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals("a b c", decoded.first().name)
    }

    @Test
    fun `corrupt quiz score data is tolerated`() {
        assertTrue(QuizScores.decode("nonsense;7:1").isEmpty())
        assertTrue(QuizScores.decode(null).isEmpty())
    }
}
