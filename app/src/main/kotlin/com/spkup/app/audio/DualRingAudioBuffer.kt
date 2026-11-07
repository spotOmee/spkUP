package com.spkup.app.audio

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe Dual-Ring Lossless Audio Buffer for 16 kHz 16-bit Mono PCM.
 *
 * Prevents initial syllable cutoff and trailing word clipping ("Aakhiri Word Na Kate"):
 * 1. 300 ms Circular Pre-Roll Buffer (4,800 samples):
 *    Continuously updated in FIFO ring buffer while idle so initial phonemes ("1, 2, 3") are preserved.
 * 2. Active Speech Buffer:
 *    Stores all samples during user speech.
 * 3. 450 ms Trailing Tail Buffer (7,200 samples):
 *    Captures an extra 450 ms after touch release or confirm tap so the trailing word is never truncated.
 */
class DualRingAudioBuffer(
    val sampleRateHz: Int = SAMPLE_RATE_HZ,
    val preRollMs: Int = PRE_ROLL_MS,
    val tailMs: Int = TAIL_MS
) {

    companion object {
        const val SAMPLE_RATE_HZ = 16000
        const val PRE_ROLL_MS = 300
        const val TAIL_MS = 450

        // 16000 * 0.300 = 4800 samples
        const val PRE_ROLL_SAMPLES = (SAMPLE_RATE_HZ * PRE_ROLL_MS) / 1000

        // 16000 * 0.450 = 7200 samples
        const val TAIL_SAMPLES = (SAMPLE_RATE_HZ * TAIL_MS) / 1000
    }

    enum class Stage {
        IDLE_PRE_ROLL,
        ACTIVE_RECORDING,
        TAIL_CAPTURE,
        COMPLETED
    }

    private val lock = Any()

    // ── 1. Circular Pre-Roll Ring Buffer (4,800 samples) ─────────────────────
    private val preRollBuffer = ShortArray(PRE_ROLL_SAMPLES)
    private var preRollWriteIndex = 0
    private var preRollTotalWritten = 0L

    // Snapshot of pre-roll taken at the exact moment recording transitions to ACTIVE
    private var preRollSnapshot: ShortArray = ShortArray(0)

    // ── 2. Active Speech Dynamic Storage ─────────────────────────────────────
    private val activeSpeechChunks = ArrayList<ShortArray>()
    private var activeSpeechTotalSamples = 0

    // ── 3. Trailing Tail Buffer (7,200 samples) ──────────────────────────────
    private val tailBuffer = ShortArray(TAIL_SAMPLES)
    private var tailWriteIndex = 0

    var currentStage: Stage = Stage.IDLE_PRE_ROLL
        private set

    /**
     * Appends PCM samples into the appropriate buffer stage.
     */
    fun append(data: ShortArray, length: Int) {
        if (length <= 0) return
        val count = minOf(length, data.size)

        synchronized(lock) {
            when (currentStage) {
                Stage.IDLE_PRE_ROLL -> {
                    // Write into circular pre-roll FIFO ring buffer
                    for (i in 0 until count) {
                        preRollBuffer[preRollWriteIndex] = data[i]
                        preRollWriteIndex = (preRollWriteIndex + 1) % PRE_ROLL_SAMPLES
                        preRollTotalWritten++
                    }
                }

                Stage.ACTIVE_RECORDING -> {
                    // Append into active speech buffer
                    val chunk = ShortArray(count)
                    System.arraycopy(data, 0, chunk, 0, count)
                    activeSpeechChunks.add(chunk)
                    activeSpeechTotalSamples += count
                }

                Stage.TAIL_CAPTURE -> {
                    // Append into 450ms tail buffer up to capacity
                    val remaining = TAIL_SAMPLES - tailWriteIndex
                    val toCopy = minOf(count, remaining)
                    if (toCopy > 0) {
                        System.arraycopy(data, 0, tailBuffer, tailWriteIndex, toCopy)
                        tailWriteIndex += toCopy
                    }
                }

                Stage.COMPLETED -> {
                    // Discard further incoming frames
                }
            }
        }
    }

    /**
     * Transitions from IDLE pre-roll to ACTIVE recording.
     * Takes an immutable snapshot of the latest 300 ms circular buffer.
     */
    fun startActiveSpeech() {
        synchronized(lock) {
            if (currentStage == Stage.IDLE_PRE_ROLL) {
                // Extract linear chronological pre-roll snapshot
                val available = minOf(preRollTotalWritten, PRE_ROLL_SAMPLES.toLong()).toInt()
                val snapshot = ShortArray(available)
                var startPos = (preRollWriteIndex - available)
                if (startPos < 0) {
                    startPos += PRE_ROLL_SAMPLES
                }

                for (i in 0 until available) {
                    snapshot[i] = preRollBuffer[(startPos + i) % PRE_ROLL_SAMPLES]
                }
                preRollSnapshot = snapshot
                activeSpeechChunks.clear()
                activeSpeechTotalSamples = 0
                tailWriteIndex = 0
                currentStage = Stage.ACTIVE_RECORDING
            }
        }
    }

    /**
     * Transitions from ACTIVE speech to 450 ms TAIL capture.
     */
    fun startTailCapture() {
        synchronized(lock) {
            if (currentStage == Stage.ACTIVE_RECORDING) {
                currentStage = Stage.TAIL_CAPTURE
                tailWriteIndex = 0
            }
        }
    }

    /**
     * Consolidates [300 ms Pre-Roll] + [Active Speech] + [450 ms Tail]
     * into a single contiguous PCM array.
     */
    fun finalizeAndConsolidate(): ShortArray {
        synchronized(lock) {
            currentStage = Stage.COMPLETED

            val preRollLen = preRollSnapshot.size
            val activeLen = activeSpeechTotalSamples
            val tailLen = tailWriteIndex

            val totalLen = preRollLen + activeLen + tailLen
            val consolidated = ShortArray(totalLen)

            var offset = 0

            // 1. Copy 300ms Pre-Roll
            if (preRollLen > 0) {
                System.arraycopy(preRollSnapshot, 0, consolidated, offset, preRollLen)
                offset += preRollLen
            }

            // 2. Copy Active Speech
            for (chunk in activeSpeechChunks) {
                System.arraycopy(chunk, 0, consolidated, offset, chunk.size)
                offset += chunk.size
            }

            // 3. Copy 450ms Trailing Tail
            if (tailLen > 0) {
                System.arraycopy(tailBuffer, 0, consolidated, offset, tailLen)
                offset += tailLen
            }

            return consolidated
        }
    }

    /**
     * Discards all stored samples and resets state to IDLE pre-roll.
     */
    fun clear() {
        synchronized(lock) {
            preRollWriteIndex = 0
            preRollTotalWritten = 0L
            preRollSnapshot = ShortArray(0)
            activeSpeechChunks.clear()
            activeSpeechTotalSamples = 0
            tailWriteIndex = 0
            currentStage = Stage.IDLE_PRE_ROLL
        }
    }
}
