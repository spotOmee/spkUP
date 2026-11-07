package com.spkup.app

import com.spkup.app.audio.DualRingAudioBuffer
import com.spkup.app.audio.VadProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBufferAndVadTest {

    @Test
    fun testBufferCapacityConstants() {
        assertEquals(4800, DualRingAudioBuffer.PRE_ROLL_SAMPLES)
        assertEquals(7200, DualRingAudioBuffer.TAIL_SAMPLES)
        assertEquals(16000, DualRingAudioBuffer.SAMPLE_RATE_HZ)
        assertEquals(300, DualRingAudioBuffer.PRE_ROLL_MS)
        assertEquals(450, DualRingAudioBuffer.TAIL_MS)
    }

    @Test
    fun testPreRollFifoOverwritingAndSnapshot() {
        val buffer = DualRingAudioBuffer()

        // Write 6000 samples (exceeding 4800 capacity) into pre-roll
        val chunk1 = ShortArray(4800) { it.toShort() }
        buffer.append(chunk1, chunk1.size)

        val chunk2 = ShortArray(1200) { (it + 4800).toShort() }
        buffer.append(chunk2, chunk2.size)

        // Start active speech takes snapshot of the most recent 4800 samples
        buffer.startActiveSpeech()

        val consolidated = buffer.finalizeAndConsolidate()
        assertEquals(4800, consolidated.size)

        // The first sample in snapshot should be sample 1200
        assertEquals(1200.toShort(), consolidated[0])
        // The last sample should be sample 5999
        assertEquals(5999.toShort(), consolidated[4799])
    }

    @Test
    fun testFullConsolidationSequence() {
        val buffer = DualRingAudioBuffer()

        // 1. Pre-roll: write 500 samples of value 1
        val preRoll = ShortArray(500) { 1 }
        buffer.append(preRoll, preRoll.size)

        // 2. Start active speech: write 1000 samples of value 2
        buffer.startActiveSpeech()
        val speech = ShortArray(1000) { 2 }
        buffer.append(speech, speech.size)

        // 3. Start tail capture: write 700 samples of value 3
        buffer.startTailCapture()
        val tail = ShortArray(700) { 3 }
        buffer.append(tail, tail.size)

        // 4. Finalize
        val consolidated = buffer.finalizeAndConsolidate()
        assertEquals(500 + 1000 + 700, consolidated.size)

        // Verify pre-roll partition
        for (i in 0 until 500) {
            assertEquals(1.toShort(), consolidated[i])
        }
        // Verify active speech partition
        for (i in 500 until 1500) {
            assertEquals(2.toShort(), consolidated[i])
        }
        // Verify tail partition
        for (i in 1500 until 2200) {
            assertEquals(3.toShort(), consolidated[i])
        }
    }

    @Test
    fun testVadConversionRoundtrip() {
        val original = shortArrayOf(-32768, -16384, 0, 16384, 32767)
        val floats = VadProcessor.shortToFloat(original)
        val recovered = VadProcessor.floatToShort(floats)

        for (i in original.indices) {
            // Allow small quantization diff (within 1 sample)
            assertEquals(original[i].toFloat(), recovered[i].toFloat(), 2.0f)
        }
    }

    @Test
    fun testVadSilenceDetectionAndTrimming() {
        // Create 20 frames (6400 samples): 5 frames silence + 10 frames speech + 5 frames silence
        val frameSize = VadProcessor.FRAME_SIZE_SAMPLES // 320
        val totalSamples = frameSize * 20
        val audio = ShortArray(totalSamples)

        // Silence = 0
        // Speech frames: frame 5 to frame 14 with amplitude 8000
        for (f in 5..14) {
            val start = f * frameSize
            for (i in 0 until frameSize) {
                audio[start + i] = if (i % 2 == 0) 8000.toShort() else (-8000).toShort()
            }
        }

        assertTrue(VadProcessor.hasSpeechActivity(audio, threshold = 0.05f))

        val trimmed = VadProcessor.trimDeadAir(audio, threshold = 0.05f, headMarginFrames = 1, tailMarginFrames = 1)
        assertTrue(trimmed.size < audio.size)
        assertTrue(trimmed.size >= 10 * frameSize)
    }

    @Test
    fun testPureSilenceRejection() {
        val silentAudio = ShortArray(3200) { 0 }
        assertFalse(VadProcessor.hasSpeechActivity(silentAudio, threshold = 0.015f))
        assertEquals(0f, VadProcessor.calculateRmsLinear(silentAudio), 0.0001f)
    }
}
