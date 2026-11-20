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

    private val fullPrompts = prompts.copy(
        genus = "genus of %1\$s?",
        sameGenus = "same genus as %1\$s?",
        genusOutsider = "not in genus %1\$s?",
        monthFind = "month you can find %1\$s?",
        monthCannot = "month you cannot find %1\$s?",
        kitchenBest = "which is the most prized?",
        kitchenWorst = "which is the least prized?",
        monthNames = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        ),
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
    fun `catalonia and figures banks get own keys and categories`() {
        val entry = CuratedQuestion("q?", listOf("a", "b", "c", "d"), 2, "fet")
        val deck = buildQuizDeck(
            mushrooms = emptyList(),
            curated = listOf(entry),
            prompts = prompts,
            treeLabels = treeLabels,
            random = Random(1),
            catalonia = listOf(entry),
            figures = listOf(entry, entry),
        )
        assertEquals(listOf("culture:0", "ctat:0", "pers:0", "pers:1"), deck.map { it.key })
        assertEquals(
            listOf(QuizCategory.CULTURE, QuizCategory.CATALONIA, QuizCategory.FIGURES, QuizCategory.FIGURES),
            deck.map { it.category },
        )
        deck.forEach { assertEquals("c", it.options[it.answerIndex]) }
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
    fun `new generators are disabled when their prompts are blank`() {
        val deck = buildQuizDeck(mushrooms, curated, prompts, treeLabels, Random(9))
        assertTrue(deck.none { it.category == QuizCategory.GENUS })
        assertTrue(deck.none { it.category == QuizCategory.MONTH })
        assertTrue(deck.none { it.key.startsWith("kbest:") || it.key.startsWith("kworst:") })
    }

    @Test
    fun `genus question uses the first word of the scientific name`() {
        val deck = buildQuizDeck(mushrooms, curated, fullPrompts, treeLabels, Random(11))
        val rovello = deck.first { it.key == "gen:rovello" }
        assertEquals("Lactarius", rovello.correctAnswer)
        assertTrue(rovello.options.none { it == "Lactarius" && it != rovello.correctAnswer })
        assertEquals(QuizCategory.GENUS, rovello.category)
    }

    @Test
    fun `same genus question picks a congener and rejects other genera`() {
        val deck = buildQuizDeck(mushrooms, curated, fullPrompts, treeLabels, Random(4))
        val same = deck.filter { it.key.startsWith("sam:") }
        assertTrue("no genus has two species in this catalogue", same.isEmpty())

        val withCongeners = mushrooms + species(
            "rovellet", "Lactarius quieticolor", "Rovellet", "Tardor", 4, mapOf("scots_pine" to 1f),
        ) + species(
            "camagrocs_group", "Craterellus lutescens", "Camagrocs", "Tardor", 4, mapOf("beech" to 1f),
        )
        val deck2 = buildQuizDeck(withCongeners, curated, fullPrompts, treeLabels, Random(4))
        val both = deck2.filter { it.key in setOf("sam:rovello", "sam:rovellet") }
        assertEquals(2, both.size)
        for (question in both) {
            assertEquals("Lactarius", question.correctAnswer.substringBefore(' '))
            assertEquals(1, question.options.count { it.startsWith("Lactarius") })
        }
    }

    @Test
    fun `genus outsider question has exactly one species outside the genus`() {
        val catalogue = listOf(
            species("l1", "Lactarius deliciosus", "Rovelló", "Tardor", 4, mapOf("scots_pine" to 1f)),
            species("l2", "Lactarius semisanguifluus", "Pinetell", "Tardor", 5, mapOf("scots_pine" to 1f)),
            species("l3", "Lactarius quieticolor", "Rovellet", "Tardor", 3, mapOf("holm_oak" to 1f)),
            species("b1", "Boletus edulis", "Cep", "Tardor", 5, mapOf("beech" to 1f)),
            species("a1", "Amanita caesarea", "Ou de Reig", "Estiu", 5, mapOf("holm_oak" to 1f)),
            species("m1", "Morchella esculenta", "Murgola", "Primavera", 4, mapOf("birch" to 1f)),
        )
        val deck = buildQuizDeck(catalogue, curated, fullPrompts, treeLabels, Random(8))
        val outsides = deck.filter { it.key.startsWith("gout:") }
        outsides.forEach { question ->
            val genus = question.key.substringAfter("gout:").substringBefore(':')
            assertEquals(genus + ": three congeners", 3, question.options.count { it.startsWith("$genus ") })
            assertEquals(genus + ": one outsider", 1, question.options.count { !it.startsWith("$genus ") })
            assertTrue(
                "answer is the outsider",
                !question.correctAnswer.startsWith("$genus "),
            )
        }
    }

    @Test
    fun `month questions respect the season ranges`() {
        val deck = buildQuizDeck(mushrooms, curated, fullPrompts, treeLabels, Random(2))
        val months = deck.filter { it.category == QuizCategory.MONTH }
        assertTrue("month questions are generated", months.isNotEmpty())
        // The synthetic species fruit September to November only.
        val inSeason = setOf("September", "October", "November")
        for (month in months) {
            assertEquals("month labels only", 4, month.options.distinct().size)
            if (month.key.startsWith("mnf:")) {
                assertTrue("answer in season: ${month.correctAnswer}", month.correctAnswer in inSeason)
                assertTrue(month.options.filterIndexed { i, _ -> i != month.answerIndex }.none { it in inSeason })
            } else {
                assertTrue("answer out of season", month.correctAnswer !in inSeason)
                assertEquals(3, month.options.count { it in inSeason })
            }
        }
    }

    @Test
    fun `kitchen champion questions have a strict winner`() {
        val deck = buildQuizDeck(mushrooms, curated, fullPrompts, treeLabels, Random(6))
        val bests = deck.filter { it.key.startsWith("kbest:") }
        val worsts = deck.filter { it.key.startsWith("kworst:") }
        assertTrue(bests.isNotEmpty() && worsts.isNotEmpty())
        val commonRating = mushrooms.associate { it.commonName to it.edibility }
        for (question in bests) {
            val answer = commonRating.getValue(question.correctAnswer)
            question.options.filterIndexed { i, _ -> i != question.answerIndex }.forEach {
                assertTrue("rating strictly higher than $it", answer > commonRating.getValue(it))
            }
        }
        for (question in worsts) {
            val answer = commonRating.getValue(question.correctAnswer)
            question.options.filterIndexed { i, _ -> i != question.answerIndex }.forEach {
                assertTrue("rating strictly lower than $it", answer < commonRating.getValue(it))
            }
        }
    }

    @Test
    fun `deck keys are unique with all generators on`() {
        val deck = buildQuizDeck(mushrooms, curated, fullPrompts, treeLabels, Random(1))
        assertEquals(deck.size, deck.distinctBy { it.key }.size)
    }

    @Test
    fun `real catalogues offer at least 500 questions per language`() {
        val legendKeys = listOf(
            "holm_oak", "cork_oak", "downy_oak", "portuguese_oak", "sessile_oak", "pyrenean_oak",
            "beech", "chestnut", "birch", "strawberry_tree", "wild_olive", "mixed_broadleaf",
            "scots_pine", "mountain_pine", "black_pine", "aleppo_pine", "stone_pine", "maritime_pine",
            "fir_other_conifers", "mixed_conifers", "mixed_conifer_broadleaf", "riparian",
            "other_broadleaf", "junipers", "plantations", "native_non_native", "non_native",
        )
        val treeLabels = legendKeys.associateWith { it }
        val assets = java.io.File("src/main/assets/mushrooms")
        val speciesJson = java.io.File(assets, "species.json").readText(Charsets.UTF_8)
        for (language in listOf("ca", "es", "en")) {
            val species = com.mikotrack.ui.mushrooms.MushroomCatalog.parseSpecies(speciesJson)
            val texts = com.mikotrack.ui.mushrooms.MushroomCatalog.parseTexts(
                java.io.File(assets, "texts_$language.json").readText(Charsets.UTF_8),
            )
            val catalogue = com.mikotrack.ui.mushrooms.MushroomCatalog.combine(species, texts)
            assertEquals(44, catalogue.size)
            val curatedBank = kotlinx.serialization.json.Json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(CuratedQuestion.serializer()),
                java.io.File(assets, "quiz_$language.json").readText(Charsets.UTF_8),
            )
            val deck = buildQuizDeck(catalogue, curatedBank, fullPrompts, treeLabels, Random(1234))
            assertTrue("$language pool is ${deck.size}", deck.size >= 500)
            assertEquals("$language unique keys", deck.size, deck.distinctBy { it.key }.size)
            for (question in deck) {
                assertEquals("$language ${question.key} options", 4, question.options.size)
                assertEquals("$language ${question.key} unique", 4, question.options.distinct().size)
                assertTrue("$language ${question.key} answer", question.answerIndex in 0..3)
                assertTrue(
                    "$language ${question.key} non-blank",
                    question.options.none { it.isBlank() },
                )
            }
        }
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
