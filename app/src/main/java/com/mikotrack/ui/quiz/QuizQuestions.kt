package com.mikotrack.ui.quiz

import com.mikotrack.ui.mushrooms.Mushroom
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Themed area a quiz question belongs to. The UI uses it to badge the card
 * (emoji + label) so the game feels varied.
 */
enum class QuizCategory {
    /** Guess the scientific name of a species. */
    SCIENTIFIC_NAME,

    /** Guess the vernacular name behind a scientific name. */
    COMMON_NAME,

    /** When does the species fruit. */
    SEASON,

    /** Culinary rating (1..5 stars) of the species. */
    EDIBILITY,

    /** Preferred tree/forest habitat of the species. */
    HABITAT,

    /** Genus of a species, from its scientific name. */
    GENUS,

    /** Month of the year a species fruits (or does not). */
    MONTH,

    /** Curated "mushroom world" trivia from the bundled question banks. */
    CULTURE,

    /** Curated general-culture trivia about Catalonia (festivals, land, lore). */
    CATALONIA,

    /** Curated trivia about famous people from the Catalan cultural domain. */
    FIGURES,
}

/**
 * A four-option multiple-choice question. Options are already shuffled and
 * [answerIndex] points at the correct one. [key] identifies the underlying
 * question so the game can avoid repeating recent questions.
 */
data class QuizQuestion(
    val key: String,
    val category: QuizCategory,
    val prompt: String,
    val options: List<String>,
    val answerIndex: Int,
    /** Optional didactic note shown after answering. */
    val fact: String? = null,
) {
    val correctAnswer: String get() = options[answerIndex]
}

/**
 * Localised question templates. The generated questions embed the species
 * name into one of these; each template contains a single %1$s placeholder.
 */
data class QuizPrompts(
    val scientificName: String,
    val commonName: String,
    val season: String,
    val edibility: String,
    val habitat: String,
    /** Templates left blank disable the generators that would use them. */
    val genus: String = "",
    val sameGenus: String = "",
    val genusOutsider: String = "",
    val monthFind: String = "",
    val monthCannot: String = "",
    val kitchenBest: String = "",
    val kitchenWorst: String = "",
    /** Localised names of the twelve months, starting with January. */
    val monthNames: List<String> = emptyList(),
)

/** One curated question as stored in the per-language JSON quiz banks. */
@Serializable
data class CuratedQuestion(
    val q: String,
    val options: List<String>,
    val answer: Int,
    val fact: String? = null,
)

/**
 * Builds the full deck for one run from the mushroom catalogue and the curated
 * culture bank. The order is deterministic for a given [random] seed, which
 * the unit tests rely on.
 *
 * @param prompts localised question templates.
 * @param treeLabels map of habitat tree keys (as used in the catalogue JSON)
 *   to their translated labels; used by the habitat questions.
 */
fun buildQuizDeck(
    mushrooms: List<Mushroom>,
    curated: List<CuratedQuestion>,
    prompts: QuizPrompts,
    treeLabels: Map<String, String>,
    random: Random = Random.Default,
    catalonia: List<CuratedQuestion> = emptyList(),
    figures: List<CuratedQuestion> = emptyList(),
): List<QuizQuestion> {
    val questions = curated.toQuizQuestions("culture", QuizCategory.CULTURE).toMutableList()
    questions += catalonia.toQuizQuestions("ctat", QuizCategory.CATALONIA)
    questions += figures.toQuizQuestions("pers", QuizCategory.FIGURES)
    questions += scientificNameQuestions(mushrooms, prompts, random)
    questions += commonNameQuestions(mushrooms, prompts, random)
    questions += seasonQuestions(mushrooms, prompts, random)
    questions += edibilityQuestions(mushrooms, prompts, random)
    questions += habitatQuestions(mushrooms, prompts, treeLabels, random)
    questions += genusQuestions(mushrooms, prompts, random)
    questions += sameGenusQuestions(mushrooms, prompts, random)
    questions += genusOutsiderQuestions(mushrooms, prompts, random)
    questions += monthQuestions(mushrooms, prompts, random)
    questions += kitchenChampionQuestions(mushrooms, prompts, random)
    return questions
}

private fun List<CuratedQuestion>.toQuizQuestions(
    keyPrefix: String,
    category: QuizCategory,
): List<QuizQuestion> =
    mapIndexedNotNull { index, question ->
        val options = question.options
        if (options.size != 4 || question.answer !in options.indices ||
            options.distinct().size != 4 || options[question.answer].isBlank()
        ) {
            null
        } else {
            QuizQuestion(
                key = "$keyPrefix:$index",
                category = category,
                prompt = question.q,
                options = options,
                answerIndex = question.answer,
                fact = question.fact?.takeIf { it.isNotBlank() },
            )
        }
    }

/** "What is the scientific name of <common name>?" */
private fun scientificNameQuestions(
    mushrooms: List<Mushroom>,
    prompts: QuizPrompts,
    random: Random,
): List<QuizQuestion> {
    if (mushrooms.size < 4) return emptyList()
    return mushrooms.mapNotNull { species ->
        val genus = species.data.scientificName.substringBefore(' ')
        val distractors = mushrooms
            .filter { it.data.id != species.data.id && it.data.scientificName.substringBefore(' ') != genus }
            .map { it.data.scientificName }
            .distinct()
            .shuffled(random)
            .take(3)
        if (distractors.size < 3) return@mapNotNull null
        quizFrom(
            key = "sci:${species.data.id}",
            category = QuizCategory.SCIENTIFIC_NAME,
            prompt = prompts.scientificName.format(species.texts.commonName),
            correct = species.data.scientificName,
            distractors = distractors,
            random = random,
        )
    }
}

/** "Which mushroom hides behind this scientific name?" */
private fun commonNameQuestions(
    mushrooms: List<Mushroom>,
    prompts: QuizPrompts,
    random: Random,
): List<QuizQuestion> {
    if (mushrooms.size < 4) return emptyList()
    return mushrooms.mapNotNull { species ->
        val distractors = mushrooms
            .filter { it.data.id != species.data.id }
            .map { it.texts.commonName }
            .distinct()
            .shuffled(random)
            .take(3)
        if (distractors.size < 3) return@mapNotNull null
        quizFrom(
            key = "com:${species.data.id}",
            category = QuizCategory.COMMON_NAME,
            prompt = prompts.commonName.format(species.data.scientificName),
            correct = species.texts.commonName,
            distractors = distractors,
            random = random,
        )
    }
}

/** "In which season does it fruit?" using the descriptive season texts. */
private fun seasonQuestions(
    mushrooms: List<Mushroom>,
    prompts: QuizPrompts,
    random: Random,
): List<QuizQuestion> {
    val seasons = mushrooms.map { it.texts.season.trim() }.distinct()
    if (seasons.size < 4) return emptyList()
    return mushrooms.mapNotNull { species ->
        val correct = species.texts.season.trim()
        val distractors = seasons.filter { !it.equals(correct, ignoreCase = true) }
            .shuffled(random)
            .take(3)
        if (distractors.size < 3) return@mapNotNull null
        quizFrom(
            key = "season:${species.data.id}",
            category = QuizCategory.SEASON,
            prompt = prompts.season.format(species.texts.commonName),
            correct = correct,
            distractors = distractors,
            random = random,
        )
    }
}

private const val STAR_LABELS_MAX = 5

/** "How many stars does it deserve in the kitchen?" (catalogue edibility). */
private fun edibilityQuestions(
    mushrooms: List<Mushroom>,
    prompts: QuizPrompts,
    random: Random,
): List<QuizQuestion> {
    val starLabels = (1..STAR_LABELS_MAX).associateWith { stars ->
        "★".repeat(stars) + "☆".repeat(STAR_LABELS_MAX - stars)
    }
    return mushrooms.mapNotNull { species ->
        val rating = species.data.edibility.coerceIn(1, STAR_LABELS_MAX)
        val distractors = (1..STAR_LABELS_MAX).filter { it != rating }.shuffled(random).take(3)
        quizFrom(
            key = "edi:${species.data.id}",
            category = QuizCategory.EDIBILITY,
            prompt = prompts.edibility.format(species.texts.commonName),
            correct = starLabels.getValue(rating),
            distractors = distractors.map { starLabels.getValue(it) },
            random = random,
        )
    }
}

/** "Where is it easiest to find it?" (highest-weight tree of its habitat). */
private fun habitatQuestions(
    mushrooms: List<Mushroom>,
    prompts: QuizPrompts,
    treeLabels: Map<String, String>,
    random: Random,
): List<QuizQuestion> {
    if (treeLabels.size < 4) return emptyList()
    return mushrooms.mapNotNull { species ->
        val bestTreeKey = species.data.habitat.trees
            .filter { (key, weight) -> weight > 0f && treeLabels.containsKey(key) }
            .maxByOrNull { (_, weight) -> weight }
            ?.key ?: return@mapNotNull null
        val correct = treeLabels.getValue(bestTreeKey)
        val distractors = treeLabels.entries
            .filter { it.key != bestTreeKey && it.value != correct }
            .map { it.value }
            .distinct()
            .shuffled(random)
            .take(3)
        if (distractors.size < 3) return@mapNotNull null
        quizFrom(
            key = "hab:${species.data.id}",
            category = QuizCategory.HABITAT,
            prompt = prompts.habitat.format(species.texts.commonName),
            correct = correct,
            distractors = distractors,
            random = random,
        )
    }
}

/** "What genus does <common name> belong to?" (first word of the scientific name). */
private fun genusQuestions(
    mushrooms: List<Mushroom>,
    prompts: QuizPrompts,
    random: Random,
): List<QuizQuestion> {
    if (prompts.genus.isBlank()) return emptyList()
    val genera = mushrooms.map { it.data.scientificName.substringBefore(' ') }.distinct()
    if (genera.size < 4) return emptyList()
    return mushrooms.mapNotNull { species ->
        val genus = species.data.scientificName.substringBefore(' ')
        val distractors = genera.filter { it != genus }.shuffled(random).take(3)
        if (distractors.size < 3) return@mapNotNull null
        quizFrom(
            key = "gen:${species.data.id}",
            category = QuizCategory.GENUS,
            prompt = prompts.genus.format(species.texts.commonName),
            correct = genus,
            distractors = distractors,
            random = random,
        )
    }
}

/** "Which of these species shares the genus of <scientific name>?" */
private fun sameGenusQuestions(
    mushrooms: List<Mushroom>,
    prompts: QuizPrompts,
    random: Random,
): List<QuizQuestion> {
    if (prompts.sameGenus.isBlank()) return emptyList()
    val byGenus = mushrooms.groupBy { it.data.scientificName.substringBefore(' ') }
    if (byGenus.size < 4) return emptyList()
    return mushrooms.mapNotNull { species ->
        val genus = species.data.scientificName.substringBefore(' ')
        val partner = (byGenus[genus] ?: emptyList())
            .filter { it.data.id != species.data.id }
            .shuffled(random)
            .firstOrNull() ?: return@mapNotNull null
        val distractors = mushrooms
            .filter { it.data.scientificName.substringBefore(' ') != genus }
            .map { it.data.scientificName }
            .distinct()
            .shuffled(random)
            .take(3)
        if (distractors.size < 3 || distractors.contains(partner.data.scientificName)) return@mapNotNull null
        quizFrom(
            key = "sam:${species.data.id}",
            category = QuizCategory.GENUS,
            prompt = prompts.sameGenus.format(species.data.scientificName),
            correct = partner.data.scientificName,
            distractors = distractors,
            random = random,
        )
    }
}

/** "Which of these species is NOT in genus <genus>?" (three congeners + one outsider). */
private fun genusOutsiderQuestions(
    mushrooms: List<Mushroom>,
    prompts: QuizPrompts,
    random: Random,
): List<QuizQuestion> {
    if (prompts.genusOutsider.isBlank()) return emptyList()
    return mushrooms
        .groupBy { it.data.scientificName.substringBefore(' ') }
        .filter { (_, members) -> members.size >= 3 }
        .toSortedMap()
        .flatMap { (genus, members) ->
            val picks = members.shuffled(random)
            val rest = mushrooms
                .filter { it.data.scientificName.substringBefore(' ') != genus }
                .map { it.data.scientificName }
                .distinct()
                .shuffled(random)
            if (rest.isEmpty()) return@flatMap emptyList()
            (0..picks.size - 3).mapNotNull { i ->
                val conSpecifics = picks.subList(i, i + 3).map { it.data.scientificName }
                val outsider = rest[i % rest.size]
                if (outsider in conSpecifics) return@mapNotNull null
                quizFrom(
                    key = "gout:$genus:$i",
                    category = QuizCategory.GENUS,
                    prompt = prompts.genusOutsider.format(genus),
                    correct = outsider,
                    distractors = conSpecifics,
                    random = random,
                )
            }
        }
}

/** Month questions: one month in which the species does (or does not) fruit. */
private fun monthQuestions(
    mushrooms: List<Mushroom>,
    prompts: QuizPrompts,
    random: Random,
): List<QuizQuestion> {
    if (prompts.monthFind.isBlank() && prompts.monthCannot.isBlank()) return emptyList()
    if (prompts.monthNames.size != 12) return emptyList()
    val monthLabels = prompts.monthNames.map { it.trim() }
    if (monthLabels.distinct().size != 12) return emptyList()

    val questions = mutableListOf<QuizQuestion>()
    for (species in mushrooms) {
        val inMonths = (1..12).filter { species.isInSeason(it) }
        val outMonths = (1..12) - inMonths.toSet()
        if (inMonths.isEmpty() || outMonths.isEmpty()) continue

        // "In which of these months can it be found?": answer in season, three out of season.
        if (prompts.monthFind.isNotBlank() && outMonths.size >= 3) {
            for (month in inMonths.shuffled(random).take(MONTH_QUESTIONS_PER_SPECIES)) {
                val distractors = outMonths.filter { it != month }.shuffled(random).take(3)
                if (distractors.size < 3) continue
                questions += quizFrom(
                    key = "mnf:${species.data.id}:$month",
                    category = QuizCategory.MONTH,
                    prompt = prompts.monthFind.format(species.texts.commonName),
                    correct = monthLabels[month - 1],
                    distractors = distractors.map { monthLabels[it - 1] },
                    random = random,
                )
            }
        }
        // "In which of these months is it NOT season?": answer out of season, three in season.
        if (prompts.monthCannot.isNotBlank() && inMonths.size >= 3) {
            for (month in outMonths.shuffled(random).take(MONTH_QUESTIONS_PER_SPECIES)) {
                val distractors = inMonths.filter { it != month }.shuffled(random).take(3)
                if (distractors.size < 3) continue
                questions += quizFrom(
                    key = "mno:${species.data.id}:$month",
                    category = QuizCategory.MONTH,
                    prompt = prompts.monthCannot.format(species.texts.commonName),
                    correct = monthLabels[month - 1],
                    distractors = distractors.map { monthLabels[it - 1] },
                    random = random,
                )
            }
        }
    }
    return questions
}

private const val MONTH_QUESTIONS_PER_SPECIES = 2

/**
 * "Which of these is the most (least) prized in the kitchen?": the answer has a
 * strictly higher (lower) catalogue rating than the three distractors, so the
 * question is never ambiguous.
 */
private fun kitchenChampionQuestions(
    mushrooms: List<Mushroom>,
    prompts: QuizPrompts,
    random: Random,
): List<QuizQuestion> {
    if (mushrooms.size < 4) return emptyList()
    if (prompts.kitchenBest.isBlank() && prompts.kitchenWorst.isBlank()) return emptyList()
    val questions = mutableListOf<QuizQuestion>()
    for (species in mushrooms) {
        val rating = species.data.edibility.coerceIn(1, 5)
        if (prompts.kitchenBest.isNotBlank()) {
            val distractors = mushrooms
                .filter {
                    it.data.id != species.data.id &&
                        it.texts.commonName != species.texts.commonName &&
                        it.data.edibility.coerceIn(1, 5) < rating
                }
                .map { it.texts.commonName }
                .distinct()
                .shuffled(random)
                .take(3)
            if (distractors.size == 3) {
                questions += quizFrom(
                    key = "kbest:${species.data.id}",
                    category = QuizCategory.EDIBILITY,
                    prompt = prompts.kitchenBest,
                    correct = species.texts.commonName,
                    distractors = distractors,
                    random = random,
                )
            }
        }
        if (prompts.kitchenWorst.isNotBlank()) {
            val distractors = mushrooms
                .filter {
                    it.data.id != species.data.id &&
                        it.texts.commonName != species.texts.commonName &&
                        it.data.edibility.coerceIn(1, 5) > rating
                }
                .map { it.texts.commonName }
                .distinct()
                .shuffled(random)
                .take(3)
            if (distractors.size == 3) {
                questions += quizFrom(
                    key = "kworst:${species.data.id}",
                    category = QuizCategory.EDIBILITY,
                    prompt = prompts.kitchenWorst,
                    correct = species.texts.commonName,
                    distractors = distractors,
                    random = random,
                )
            }
        }
    }
    return questions
}

private fun quizFrom(
    key: String,
    category: QuizCategory,
    prompt: String,
    correct: String,
    distractors: List<String>,
    random: Random,
): QuizQuestion {
    val options = (listOf(correct) + distractors).shuffled(random)
    return QuizQuestion(
        key = key,
        category = category,
        prompt = prompt,
        options = options,
        answerIndex = options.indexOf(correct),
    )
}
