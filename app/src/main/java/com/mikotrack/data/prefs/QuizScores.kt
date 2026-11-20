package com.mikotrack.data.prefs

/** One finished quiz run: points, player name and moment of the record. */
data class QuizScore(val name: String, val score: Int, val atMillis: Long)

/**
 * Encoding and ranking of the quiz leaderboard persisted in the DataStore,
 * following the same simple scheme used by [GameScores].
 */
object QuizScores {

    /** Only the best scores are kept in the leaderboard. */
    const val MAX_RANK = 10

    /** The scores as a single string: `score:name:millis;score:name:millis;…` */
    fun encode(scores: List<QuizScore>): String =
        scores.joinToString(";") { "${it.score}:${sanitize(it.name)}:${it.atMillis}" }

    fun decode(raw: String?): List<QuizScore> =
        raw?.split(';')?.mapNotNull { entry ->
            val parts = entry.split(':')
            // The name sits between the score and the timestamp; it may itself
            // contain no delimiters because [sanitize] strips them.
            if (parts.size == 3) {
                runCatching {
                    QuizScore(parts[1], parts[0].toInt(), parts[2].toLong())
                }.getOrNull()?.takeIf { it.name.isNotBlank() }
            } else {
                null
            }
        }?.sortedByDescending { it.score } ?: emptyList()

    /** Adds [score] and returns the best [MAX_RANK] entries, most points first. */
    fun insert(raw: String?, score: QuizScore): String =
        encode((decode(raw) + score).sortedByDescending { it.score }.take(MAX_RANK))

    /** Removes the field delimiters and caps the length of a player name. */
    fun sanitize(name: String): String =
        name.replace(':', ' ').replace(';', ' ').trim().take(24)
}
