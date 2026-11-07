package com.spkup.app

import com.spkup.app.ai.ActionCommand
import com.spkup.app.ai.SmolCommandRouter
import com.spkup.app.data.StyleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmolCommandRouterTest {

    @Test
    fun testClearCommandDetection() {
        val r1 = SmolCommandRouter.process("clear")
        assertEquals(ActionCommand.CLEAR, r1.action)
        assertEquals("", r1.cleanedText)

        val r2 = SmolCommandRouter.process("clear all")
        assertEquals(ActionCommand.CLEAR, r2.action)

        val r3 = SmolCommandRouter.process("delete everything")
        assertEquals(ActionCommand.CLEAR, r3.action)

        val r4 = SmolCommandRouter.process("hatao sab")
        assertEquals(ActionCommand.CLEAR, r4.action)
    }

    @Test
    fun testSendCommandExtraction() {
        val r1 = SmolCommandRouter.process("hey bro let's meet send it")
        assertEquals(ActionCommand.SEND, r1.action)
        assertEquals("Hey bro let's meet", r1.cleanedText)

        val r2 = SmolCommandRouter.process("kya haal hai bhejo")
        assertEquals(ActionCommand.SEND, r2.action)
        assertEquals("Kya haal hai", r2.cleanedText)

        val r3 = SmolCommandRouter.process("send")
        assertEquals(ActionCommand.SEND, r3.action)
        assertEquals("", r3.cleanedText)
    }

    @Test
    fun testInsertCommandParsing() {
        val r1 = SmolCommandRouter.process("insert urgent before meeting")
        assertEquals(ActionCommand.INSERT, r1.action)
        assertEquals("urgent", r1.insertWord)
        assertEquals("before", r1.insertPosition)
        assertEquals("meeting", r1.insertTarget)

        val r2 = SmolCommandRouter.process("add please after thanks")
        assertEquals(ActionCommand.INSERT, r2.action)
        assertEquals("please", r2.insertWord)
        assertEquals("after", r2.insertPosition)
        assertEquals("thanks", r2.insertTarget)
    }

    @Test
    fun testSelfCorrectionBacktracking() {
        // "Meet tomorrow... no, today at 7 pm" -> deletes false start "Meet tomorrow"
        val resolved = SmolCommandRouter.resolveSelfCorrections("Meet tomorrow no today at 7 pm")
        assertFalse(resolved.contains("tomorrow", ignoreCase = true))
        assertTrue(resolved.contains("today at 7 pm", ignoreCase = true))

        val resolved2 = SmolCommandRouter.resolveSelfCorrections("I want tea actually coffee")
        assertFalse(resolved2.contains("tea", ignoreCase = true))
        assertTrue(resolved2.contains("coffee", ignoreCase = true))
    }

    @Test
    fun testStutterAndFillerPruning() {
        val cleaned = SmolCommandRouter.pruneStuttersAndFillers("uh we will we will um arrive soon")
        assertFalse(cleaned.contains("uh", ignoreCase = true))
        assertFalse(cleaned.contains("um", ignoreCase = true))
        assertFalse(cleaned.contains("we will we will"))
        assertTrue(cleaned.contains("we will arrive soon"))
    }

    @Test
    fun testEmojiNormalization() {
        val text = "great work fire emoji and smiley face"
        val normalized = SmolCommandRouter.normalizeEmojis(text)
        assertTrue(normalized.contains("🔥"))
        assertTrue(normalized.contains("😊"))
        assertFalse(normalized.contains("fire emoji"))
    }

    @Test
    fun testStyleModes() {
        val casual = SmolCommandRouter.applyStyleAndTone("hello world", StyleMode.CASUAL)
        assertEquals("Hello world", casual)

        val superCasual = SmolCommandRouter.applyStyleAndTone("Hello World.", StyleMode.SUPER_CASUAL)
        assertEquals("hello world", superCasual)

        val formal = SmolCommandRouter.applyStyleAndTone("hello world", StyleMode.FORMAL)
        assertEquals("Hello world.", formal)

        val mkdown = SmolCommandRouter.applyStyleAndTone("point 1 first item point 2 second item", StyleMode.MKDOWN)
        assertTrue(mkdown.contains("- first item"))
        assertTrue(mkdown.contains("- second item"))
    }
}
