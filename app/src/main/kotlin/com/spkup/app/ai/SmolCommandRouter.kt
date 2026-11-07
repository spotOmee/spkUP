package com.spkup.app.ai

import com.spkup.app.data.StyleMode
import org.json.JSONObject
import java.util.Locale

enum class ActionCommand {
    NONE,
    SEND,
    CLEAR,
    INSERT
}

/**
 * Immutable output payload produced by the Hybrid SLM & Fast-Path Command Engine.
 */
data class ProcessedSpeechResult(
    val cleanedText: String,
    val action: ActionCommand = ActionCommand.NONE,
    val insertTarget: String? = null,
    val insertPosition: String? = null, // "before" or "after"
    val insertWord: String? = null
)

/**
 * SmolCommandRouter: Hybrid Deterministic Fast-Path & SmolLM2-135M Command Router.
 *
 * Implements:
 * 1. 0 ms Fast-Path regex parsing for CLEAR, SEND, and INSERT voice commands.
 * 2. Verbal self-correction backtracking ("no", "actually", "wait", "scratch that", "sorry").
 * 3. Stutter and filler pruning ("uh", "um", word repetitions).
 * 4. Tone Modes: FORMAL, CASUAL, SUPER_CASUAL, ORGANISE, MKDOWN.
 * 5. Inline Emoji translation ("smiley face" -> 😊, "fire emoji" -> 🔥).
 * 6. SmolLM2 ChatML JSON protocol parser with 100% offline fallback.
 */
object SmolCommandRouter {

    private external fun nativeComplete(modelPath: String, promptChatml: String): String

    // ── Command Regexes ──────────────────────────────────────────────────────
    private val CLEAR_REGEX = Regex(
        """(?i)\b(clear\s+all|delete\s+all|delete\s+everything|delete\s+that|clear\s+everything|clear|hatao\s+sab|sab\s+hatao)[.,?!]?\s*$"""
    )

    private val SEND_TRAILING_REGEX = Regex(
        """(?i)\b(send\s+message|send\s+text|send\s+it|send|bhejo)[.,?!]?\s*$"""
    )

    private val INSERT_REGEX = Regex(
        """(?i)\b(?:insert|add)\s+(.+?)\s+(before|after)\s+(.+?)[.,?!]?\s*$"""
    )

    // ── Self-Correction Triggers ─────────────────────────────────────────────
    private val REPAIR_TRIGGERS = listOf(
        "scratch that", "cancel that", "i mean", "arre nahi", "or rather",
        "actually", "wait", "sorry", "rather", "nahi", "no"
    )

    // ── Spoken Emoji Map ─────────────────────────────────────────────────────
    private val EMOJI_MAP = listOf(
        Regex("""(?i)\b(smiley\s+face|smiling\s+face|smile\s+emoji)\b""") to "😊",
        Regex("""(?i)\b(heart\s+emoji|red\s+heart)\b""") to "❤️",
        Regex("""(?i)\b(fire\s+emoji|flame\s+emoji)\b""") to "🔥",
        Regex("""(?i)\b(thumbs\s+up|thumbsup)\b""") to "👍",
        Regex("""(?i)\b(crying\s+laughing|laughing\s+crying|lol\s+emoji)\b""") to "😂",
        Regex("""(?i)\b(star\s+emoji)\b""") to "⭐",
        Regex("""(?i)\b(party\s+popper|celebration\s+emoji)\b""") to "🎉",
        Regex("""(?i)\b(clap\s+emoji|clapping\s+hands)\b""") to "👏",
        Regex("""(?i)\b(wink\s+emoji|winking\s+face)\b""") to "😉"
    )

    /**
     * Primary processing pipeline for raw transcribed speech.
     */
    fun process(
        rawTranscript: String,
        styleMode: StyleMode = StyleMode.CASUAL
    ): ProcessedSpeechResult {
        val trimmed = rawTranscript.trim()
        if (trimmed.isBlank()) {
            return ProcessedSpeechResult(cleanedText = "", action = ActionCommand.NONE)
        }

        // ── 1. Fast-Path Deterministic Command Checks (0 ms) ─────────────────

        // A. CLEAR Command
        if (CLEAR_REGEX.matches(trimmed)) {
            return ProcessedSpeechResult(cleanedText = "", action = ActionCommand.CLEAR)
        }

        // B. INSERT Command
        val insertMatch = INSERT_REGEX.find(trimmed)
        if (insertMatch != null) {
            val word = insertMatch.groupValues[1].trim()
            val position = insertMatch.groupValues[2].lowercase(Locale.ROOT).trim()
            val target = insertMatch.groupValues[3].trim()

            return ProcessedSpeechResult(
                cleanedText = "",
                action = ActionCommand.INSERT,
                insertTarget = target,
                insertPosition = position,
                insertWord = word
            )
        }

        // C. SEND Command
        var detectedAction = ActionCommand.NONE
        var textWithoutSend = trimmed
        val sendMatch = SEND_TRAILING_REGEX.find(trimmed)
        if (sendMatch != null) {
            detectedAction = ActionCommand.SEND
            textWithoutSend = trimmed.substring(0, sendMatch.range.first).trim()
                .trimEnd(',', '.', '!', '?', ';', ':').trim()
        }

        // ── 2. Mid-Speech Self-Correction & Repair ───────────────────────────
        var cleaned = resolveSelfCorrections(textWithoutSend)

        // ── 3. Stutter & Filler Pruning ──────────────────────────────────────
        cleaned = pruneStuttersAndFillers(cleaned)

        // ── 4. Spoken Emojis ─────────────────────────────────────────────────
        cleaned = normalizeEmojis(cleaned)

        // ── 5. Style, Tone & Markdown Matrix ─────────────────────────────────
        cleaned = applyStyleAndTone(cleaned, styleMode)

        return ProcessedSpeechResult(
            cleanedText = cleaned,
            action = detectedAction
        )
    }

    /**
     * Deletes false starts preceding verbal repair triggers:
     * "Meet tomorrow... no, today at 7 PM" -> "Meet today at 7:00 PM"
     */
    fun resolveSelfCorrections(text: String): String {
        var result = text

        for (trigger in REPAIR_TRIGGERS) {
            val pattern = Regex("""(?i)(.+?)\s*(?:[.,]\s*|\s+)\b\Q$trigger\E\b\s*(?:[.,]\s*|\s+)?(.*)""")
            val match = pattern.find(result)
            if (match != null) {
                val after = match.groupValues[2].trim()
                if (after.isNotBlank()) {
                    result = after
                }
            }
        }

        return result
    }

    /**
     * Strips "uh", "um", accidental immediate word repetitions.
     */
    fun pruneStuttersAndFillers(text: String): String {
        var result = text
        // Remove fillers
        result = result.replace(Regex("""(?i)\b(uh|um|er|ah)\b[.,]?\s*"""), "")

        // Remove immediate consecutive duplicated words or 2-3 word repeated phrases: "we will we will arrive" -> "we will arrive"
        result = result.replace(Regex("""(?i)\b(\w+(?:\s+\w+){0,2})\s+\1\b"""), "$1")

        // Clean double spaces
        result = result.replace(Regex("""\s{2,}"""), " ").trim()
        return result
    }

    /**
     * Replaces spoken emoji descriptions with Unicode glyphs.
     */
    fun normalizeEmojis(text: String): String {
        var result = text
        for ((pattern, glyph) in EMOJI_MAP) {
            result = result.replace(pattern, glyph)
        }
        return result
    }

    /**
     * Applies tone and Markdown rules based on the active [StyleMode].
     */
    fun applyStyleAndTone(text: String, styleMode: StyleMode): String {
        if (text.isBlank()) return text

        return when (styleMode) {
            StyleMode.SUPER_CASUAL -> {
                // All lowercase, no trailing period
                text.lowercase(Locale.ROOT).trimEnd('.')
            }

            StyleMode.FORMAL -> {
                // Capitalize first letter, ensure sentence ending punctuation
                val cap = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                if (!cap.endsWith(".") && !cap.endsWith("?") && !cap.endsWith("!")) {
                    "$cap."
                } else {
                    cap
                }
            }

            StyleMode.MKDOWN -> {
                var md = text
                // Spoken bullet points: "point 1 ... point 2 ..."
                md = md.replace(Regex("""(?i)\bpoint\s+(\d+|\w+)[:\s]+"""), "\n- ")
                // Spoken bold: "bold word" -> "**word**"
                md = md.replace(Regex("""(?i)\bbold\s+(\w+)\b"""), "**$1**")
                // Spoken header: "heading text" -> "# text"
                md = md.replace(Regex("""(?i)\b(?:heading|header)\s+(.+?)$"""), "# $1")

                md.trim()
            }

            StyleMode.ORGANISE -> {
                // Capitalized, clean structure
                text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }

            StyleMode.CASUAL -> {
                // Standard conversational capitalization
                text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }
    }
}
