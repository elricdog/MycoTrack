package com.mikotrack.data.prefs

/** The games of the easter egg. */
enum class GameId { ARKANOID, TETRIS, PACMAN, RHYTHM, MEMORY, TOPO, SOLITAIRE }

/** One finished game: points, level reached and moment of the record. */
data class GameScore(val score: Int, val level: Int, val atMillis: Long)

/** Encoding and ranking of the score lists persisted in the DataStore. */
object GameScores {

    /** Only the best scores of each game are kept. */
    const val MAX_RANK = 5

    /** The scores as a single string: `score:level:millis;score:level:millis;…` */
    fun encode(scores: List<GameScore>): String =
        scores.joinToString(";") { "${it.score}:${it.level}:${it.atMillis}" }

    fun decode(raw: String?): List<GameScore> =
        raw?.split(';')?.mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size == 3) {
                runCatching { GameScore(parts[0].toInt(), parts[1].toInt(), parts[2].toLong()) }.getOrNull()
            } else {
                null
            }
        } ?: emptyList()

    /** Adds [score] and returns the best [MAX_RANK] entries, most points first. */
    fun insert(raw: String?, score: GameScore): String =
        encode((decode(raw) + score).sortedByDescending { it.score }.take(MAX_RANK))
}
