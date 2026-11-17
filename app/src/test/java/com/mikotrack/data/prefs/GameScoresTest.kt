package com.mikotrack.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests of the ranking encoding used by the easter egg games. */
class GameScoresTest {

    @Test
    fun `round trips through the encoding`() {
        val scores = listOf(GameScore(300, 2, 1_000L), GameScore(50, 1, 2_000L))
        assertEquals(scores, GameScores.decode(GameScores.encode(scores)))
    }

    @Test
    fun `insert keeps the best scores sorted`() {
        var raw: String? = null
        for (score in listOf(10, 500, 30, 900, 20, 700, 100, 400)) {
            raw = GameScores.insert(raw, GameScore(score, 1, score.toLong()))
        }
        val rank = GameScores.decode(raw)
        assertEquals(5, rank.size)
        assertEquals(listOf(900, 700, 500, 400, 100), rank.map { it.score })
    }

    @Test
    fun `decode tolerates corrupt data`() {
        assertTrue(GameScores.decode("nonsense;7:1").isEmpty())
        assertEquals(listOf(GameScore(7, 1, 5L)), GameScores.decode("7:1:5;nonsense"))
    }
}
