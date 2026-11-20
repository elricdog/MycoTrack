package com.mikotrack.ui.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Checks that spoken transcripts are understood as answers and commands in
 * the three supported languages, accents included.
 */
class QuizVoiceTest {

    private val speciesQuestion = QuizQuestion(
        key = "t1",
        category = QuizCategory.COMMON_NAME,
        prompt = "Quin bolet amaga el nom \"Bolet de pa\"",
        options = listOf("Campratons", "Blanques", "Rossinyol", "Cama seces"),
        answerIndex = 0,
    )

    private val starsQuestion = QuizQuestion(
        key = "t2",
        category = QuizCategory.EDIBILITY,
        prompt = "Quantes estrelles mereix l'oufal bolet?",
        options = listOf("★", "★★", "★★★", "★★★★"),
        answerIndex = 3,
    )

    @Test
    fun `exact option text matches`() {
        assertEquals(1, QuizVoice.matchAnswer("Blanques", speciesQuestion))
        assertEquals(2, QuizVoice.matchAnswer("jo crec que el rossinyol", speciesQuestion))
    }

    @Test
    fun `accents and case are ignored`() {
        // "Camproncigs" spoken transcription close to option 0.
        assertEquals(0, QuizVoice.matchAnswer("CAMPRATONS", speciesQuestion))
    }

    @Test
    fun `option number matches`() {
        assertEquals(2, QuizVoice.matchAnswer("opció 3", speciesQuestion))
        assertEquals(1, QuizVoice.matchAnswer("2", speciesQuestion))
    }

    @Test
    fun `option letter matches`() {
        assertEquals(0, QuizVoice.matchAnswer("a", speciesQuestion))
        assertEquals(3, QuizVoice.matchAnswer("opció D", speciesQuestion))
    }

    @Test
    fun `ordinal words match in the three languages`() {
        assertEquals(1, QuizVoice.matchAnswer("la segona", speciesQuestion))
        assertEquals(3, QuizVoice.matchAnswer("el cuarto", speciesQuestion))
        assertEquals(0, QuizVoice.matchAnswer("first", speciesQuestion))
    }

    @Test
    fun `star questions accept numbers and number words`() {
        assertEquals(3, QuizVoice.matchAnswer("quatre estrelles", starsQuestion))
        assertEquals(2, QuizVoice.matchAnswer("3", starsQuestion))
        assertEquals(1, QuizVoice.matchAnswer("dos", starsQuestion))
        assertEquals(0, QuizVoice.matchAnswer("one", starsQuestion))
    }

    @Test
    fun `ambiguous transcripts match nothing`() {
        assertNull(QuizVoice.matchAnswer("no ho sé", speciesQuestion))
        assertNull(QuizVoice.matchAnswer("", speciesQuestion))
        // Two options hit the same way: nobody wins, so the question is asked again.
        assertNull(QuizVoice.matchAnswer("rossinyol blanques", speciesQuestion))
    }

    @Test
    fun `spoken commands are recognised`() {
        assertEquals(VoiceCommand.REPEAT, QuizVoice.command("Repeteix"))
        assertEquals(VoiceCommand.REPEAT, QuizVoice.command("repite la pregunta"))
        assertEquals(VoiceCommand.REPEAT, QuizVoice.command("ara vull repetir"))
        assertEquals(VoiceCommand.STOP, QuizVoice.command("Prou"))
        assertEquals(VoiceCommand.STOP, QuizVoice.command("basta"))
        assertEquals(VoiceCommand.STOP, QuizVoice.command("stop"))
        assertEquals(VoiceCommand.PLAY, QuizVoice.command("jugar"))
        assertEquals(VoiceCommand.PLAY, QuizVoice.command("play again"))
    }

    @Test
    fun `answers are not commands`() {
        assertNull(QuizVoice.command("rossinyol"))
        assertNull(QuizVoice.command("tres estrelles"))
    }

    @Test
    fun `normalization strips accents and punctuation`() {
        assertEquals("canal de la lorda", QuizVoice.normalize("Canal de la L'Orda!"))
        assertEquals("huit", QuizVoice.normalize("huit..."))
    }
}
