package com.mikotrack.ui.quiz

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Validates the curated Catalan culture and personalities banks shipped as assets. */
class QuizBanksTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(name: String): List<CuratedQuestion> {
        val file = File("src/main/assets/mushrooms/$name")
        assertTrue("missing bank $name", file.exists())
        return json.decodeFromString(file.readText(Charsets.UTF_8))
    }

    @Test
    fun `cultura bank has 250 valid questions`() {
        assertBank(load("cultura_ca.json"), 250)
    }

    @Test
    fun `personalitats bank has 250 valid questions`() {
        assertBank(load("personalitats_ca.json"), 250)
    }

    @Test
    fun `banks do not repeat question texts`() {
        val all = load("cultura_ca.json") + load("personalitats_ca.json")
        assertEquals(all.size, all.map { it.q.trim() }.distinct().size)
    }

    private fun assertBank(bank: List<CuratedQuestion>, expected: Int) {
        assertEquals(expected, bank.size)
        for (item in bank) {
            assertEquals("bad options for: ${item.q}", 4, item.options.size)
            assertEquals("duplicate options for: ${item.q}", 4, item.options.distinct().size)
            assertTrue("bad answer idx for: ${item.q}", item.answer in 0..3)
            assertTrue("empty option for: ${item.q}", item.options.none { it.isBlank() })
        }
    }
}
