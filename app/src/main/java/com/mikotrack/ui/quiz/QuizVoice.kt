package com.mikotrack.ui.quiz

/**
 * Pure helpers that turn a spoken transcript into quiz game actions. The
 * matching is deliberately language-agnostic: Catalan, Spanish and English
 * word tables are all consulted, because the speech recognizer answers in the
 * language configured on the device, which may differ from the quiz language.
 */
enum class VoiceCommand {
    /** Ask for the question again. */
    REPEAT,

    /** Leave the game. */
    STOP,

    /** Start (or restart) a game. */
    PLAY,
}

object QuizVoice {

    /** Spoken commands recognised at any point of the game. */
    private val COMMANDS = mapOf(
        VoiceCommand.REPEAT to listOf(
            "repeteix", "repeteix la pregunta", "repetir la pregunta", "repetir",
            "repite", "repite la pregunta", "repetir pregunta", "repeat",
            "repeat the question", "torno a repetir",
        ),
        VoiceCommand.STOP to listOf(
            "prou", "parar", "sortir", "surt", "atura t", "aturar se", "acabar",
            "para", "parar de jugar", "salir", "basta", "terminar",
            "stop", "quit", "exit", "leave", "stop playing",
        ),
        VoiceCommand.PLAY to listOf(
            "jugar", "torna a jugar", "tornar a jugar", "jugar de nou", "una altra vegada",
            "vamos a jugar", "jugar otra vez", "otra vez", "de nuevo",
            "play", "play again", "start", "new game",
        ),
    )

    /** Spoken numbers, used for the star rating riddles. */
    private val NUMBER_WORDS = mapOf(
        1 to listOf("u", "un", "una", "uno", "one", "one star", "un estel", "una estrella"),
        2 to listOf("dos", "dues", "two", "dous"),
        3 to listOf("tres", "three"),
        4 to listOf("quatre", "cuatro", "four"),
        5 to listOf("cinc", "cinco", "five"),
    )

    /** Words that place an option by order of appearance: first, second… */
    private val ORDER_WORDS = listOf(
        listOf(
            "primer", "primera", "la primera", "1r", "la 1",
            "primero", "el primero", "primera opcion", "primera opcio",
            "first", "the first",
        ),
        listOf(
            "segon", "segona", "la segona", "2n", "la 2",
            "segundo", "segunda", "el segundo", "segunda opcion",
            "second", "the second",
        ),
        listOf(
            "tercer", "tercera", "la tercera", "3r", "la 3",
            "tercero", "el tercero", "tercera opcion", "tercera opcio",
            "third", "the third",
        ),
        listOf(
            "quart", "quarta", "la quarta", "4t", "la 4",
            "cuarto", "el cuarto", "cuarta opcion", "cuarta opcio",
            "fourth", "the fourth",
        ),
    ).map { variants -> variants.map(::normalize) }

    /** Sentences that point at an option by number: "3", "opción 2"… */
    private val NUMBER_POSITION = Regex(
        """^(?:opci[o6]n?(?: es)? |number |numero |option |la |el )?([1-5])$"""
    )

    /** Letters A to D spoken as option labels. */
    private val OPTION_LETTERS = listOf("a", "b", "c", "d")

    /**
     * Small words carried by almost every sentence in the three supported
     * languages; they never identify an option on their own.
     */
    private val STOPWORDS = setOf(
        "a", "al", "el", "la", "els", "les", "lo", "los", "las", "y", "e", "o", "u",
        "es", "son", "esta", "estan", "esta", "di", "de", "del", "dels", "un", "una",
        "uno", "unos", "unas", "the", "is", "are", "an", "of", "and", "or", "that",
        "crec", "que", "quizas", "potser", "quiza", "igual", "sembla", "pareix",
        "opcio", "opcion", "resposta", "respuesta", "answer", "me", "mi", "my",
        "jo", "yo", "i", "en", "on", "hi", "hi ha", "fa", "faent",
    )

    /** Recognises a spoken [transcript] as one of the standing commands. */
    fun command(transcript: String): VoiceCommand? {
        val clean = normalize(transcript)
        if (clean.isEmpty()) return null
        for ((command, phrases) in COMMANDS) {
            if (phrases.any { it == clean }) return command
        }
        // Longer sentences like "ara vull repetir la pregunta" still count.
        if (clean.contains("repet") || clean.contains("repeat")) return VoiceCommand.REPEAT
        if (clean.contains("torna a jugar") || clean.contains("play again") ||
            clean.contains("otra vez") || clean.contains("de nuevo")
        ) {
            return VoiceCommand.PLAY
        }
        return null
    }

    /**
     * Finds which of the [question] options the [transcript] points at, or
     * null when nothing can be told apart. Strategy:
     *  1. spoken or digit numbers, for the star rating riddles;
     *  2. pure ordinal / letter answers ("la segona", "opció b");
     *  3. best word overlap between the sentence and each option, requiring a
     *     single winner so ambiguous answers are asked again.
     */
    fun matchAnswer(transcript: String, question: QuizQuestion): Int? {
        val clean = normalize(transcript)
        if (clean.isEmpty()) return null

        // Star rating: "quatre estrelles", "4", "dos ega stars".
        val stars = question.options.map { it.count { c -> c == '★' } }
        if (stars.all { it > 0 }) {
            val number = spokenNumber(clean)
            if (number != null) return stars.indexOf(number).takeIf { it >= 0 }
        }

        // A sentence that is nothing but an order, a number or a letter.
        ORDER_WORDS.forEachIndexed { index, variants ->
            if (variants.any { it == clean }) {
                return index
            }
        }
        NUMBER_POSITION.find(clean)?.groupValues?.get(1)?.toIntOrNull()?.let { number ->
            if (number in question.options.indices) return number - 1
        }
        OPTION_LETTERS.forEachIndexed { index, letter ->
            if (clean == letter || clean == "opcio $letter" || clean == "la $letter" ||
                clean == "lletra $letter"
            ) {
                return index
            }
        }

        // Word overlap against each option.
        val words = clean.split(' ').filter { it.length >= 3 && it !in STOPWORDS }
        if (words.isEmpty()) return null
        var bestIndex = -1
        var bestScore = 0
        var tie = false
        question.options.forEachIndexed { index, option ->
            val optionWords = normalize(option).split(' ').toSet()
            val optionRaw = normalize(option)
            var score = words.count { optionWords.contains(it) }
            // The player quoted the whole option (or the option inside the sentence).
            if (optionRaw.isNotEmpty() && (optionRaw.contains(clean) || clean.contains(optionRaw))) {
                score += 4
            }
            when {
                score > bestScore -> {
                    bestScore = score
                    bestIndex = index
                    tie = false
                }
                score == bestScore && score > 0 -> tie = true
            }
        }
        return if (bestScore > 0 && !tie) bestIndex else null
    }

    /** Reads a number 1..5 from digits or from the supported number words. */
    private fun spokenNumber(clean: String): Int? {
        Regex("""\d+""").find(clean)?.value?.toIntOrNull()?.let { if (it in 1..5) return it }
        for ((number, words) in NUMBER_WORDS) {
            if (words.any { word -> clean == word || clean.startsWith("$word ") || clean.endsWith(" $word") }) {
                return number
            }
        }
        return null
    }

    /** Lowercase, without accents nor punctuation, squeezed and trimmed. */
    fun normalize(text: String): String {
        val stripped = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .replace("'", "") // elisions: "l'orca" stays one word
            .replace(Regex("[^a-z0-9 ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
