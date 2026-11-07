package com.spkup.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Low-Latency, Zero-Beep Hardware DSP Audio Engine for 16 kHz 16-bit Mono PCM.
 *
 * Core Features:
 * 1. Zero OS Beep/Ding: Uses raw AudioRecord with VOICE_RECOGNITION, bypassing SpeechRecognizer Intents.
 * 2. Hardware DSP: Auto-attaches NoiseSuppressor and AcousticEchoCanceler if available on device chips.
 * 3. Lossless Dual-Buffer: Continuously runs 300ms Pre-Roll ring buffer and preserves 450ms Trailing Tail.
 * 4. 60 FPS Real-Time RMS Flow: Broadcasts linear amplitude [0.0f..1.0f] for waveform scaling.
 */
class ZeroBeepAudioRecorder(private val context: Context) {

    companion object {
        const val TAG = "SpkUpAudio"
        const val SAMPLE_RATE_HZ = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val FRAME_SIZE_SAMPLES = 320 // 20 ms @ 16 kHz
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordingJob: Job? = null

    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null

    val dualRingBuffer = DualRingAudioBuffer()

    private val isRecordingRunning = AtomicBoolean(false)
    private val isCapturingActiveSpeech = AtomicBoolean(false)

    private val _amplitudeFlow = MutableStateFlow(0f)
    val amplitudeFlow: StateFlow<Float> = _amplitudeFlow.asStateFlow()

    private val _isRecordingActive = MutableStateFlow(false)
    val isRecordingActive: StateFlow<Boolean> = _isRecordingActive.asStateFlow()

    private val lock = Any()

    /**
     * Initializes AudioRecord and attaches hardware DSP noise suppressor & echo canceler.
     */
    private fun initAudioRecord(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted!")
            return false
        }

        try {
            val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufSize, FRAME_SIZE_SAMPLES * 8)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                record.release()
                return false
            }

            // Hardware DSP Attachment
            val sessionId = record.audioSessionId
            if (sessionId != 0) {
                if (NoiseSuppressor.isAvailable()) {
                    try {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                            enabled = true
                        }
                        Log.d(TAG, "Hardware NoiseSuppressor enabled on session $sessionId")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to attach NoiseSuppressor: ${e.message}")
                    }
                }

                if (AcousticEchoCanceler.isAvailable()) {
                    try {
                        acousticEchoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                            enabled = true
                        }
                        Log.d(TAG, "Hardware AcousticEchoCanceler enabled on session $sessionId")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to attach AcousticEchoCanceler: ${e.message}")
                    }
                }
            }

            audioRecord = record
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating AudioRecord: ${e.message}")
            return false
        }
    }

    /**
     * Starts continuous low-latency background capture (rolls the 300ms pre-roll FIFO ring buffer while idle).
     */
    fun startPreRollListening() {
        synchronized(lock) {
            if (isRecordingRunning.get()) return

            if (audioRecord == null) {
                if (!initAudioRecord()) return
            }

            try {
                audioRecord?.startRecording()
                isRecordingRunning.set(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start AudioRecord: ${e.message}")
                return
            }

            recordingJob = scope.launch {
                val readBuffer = ShortArray(FRAME_SIZE_SAMPLES)

                while (isActive && isRecordingRunning.get()) {
                    val record = audioRecord ?: break
                    val readCount = record.read(readBuffer, 0, FRAME_SIZE_SAMPLES)

                    if (readCount > 0) {
                        // Append to Dual-Ring buffer
                        dualRingBuffer.append(readBuffer, readCount)

                        // Calculate RMS amplitude for UI Waveform
                        val rms = VadProcessor.calculateRmsLinear(readBuffer, readCount)
                        // Scale and low-pass smooth
                        _amplitudeFlow.value = (rms * 2.5f).coerceIn(0f, 1f)
                    } else if (readCount < 0) {
                        Log.e(TAG, "AudioRecord read error: $readCount")
                        break
                    }
                }
            }
        }
    }

    /**
     * Called when the user initiates speech (Hold mode ACTION_DOWN or Tap mode pill opened).
     * Locks the 300 ms pre-roll and transitions to ACTIVE recording.
     */
    fun startActiveRecording() {
        synchronized(lock) {
            if (!isRecordingRunning.get()) {
                startPreRollListening()
            }
            dualRingBuffer.startActiveSpeech()
            isCapturingActiveSpeech.set(true)
            _isRecordingActive.value = true
        }
    }

    /**
     * CRITICAL Lossless Tail Buffer on Release:
     * When Hold release or Confirm ✓ occurs, continues capturing audio for an extra 450 ms
     * before closing the stream, guaranteeing zero trailing word cutoff.
     */
    suspend fun stopAndFinalize(): ShortArray = withContext(Dispatchers.IO) {
        synchronized(lock) {
            isCapturingActiveSpeech.set(false)
            _isRecordingActive.value = false
            dualRingBuffer.startTailCapture()
        }

        // Wait 450 ms on background IO dispatcher so the microphone captures the final spoken word
        delay(DualRingAudioBuffer.TAIL_MS.toLong())

        val consolidated = synchronized(lock) {
            val audio = dualRingBuffer.finalizeAndConsolidate()
            dualRingBuffer.clear()
            _amplitudeFlow.value = 0f
            audio
        }

        // Apply clean dead-air trimming
        VadProcessor.trimDeadAir(consolidated)
    }

    /**
     * Immediately stops recording and discards all buffers (used on Cancel ✕).
     */
    fun cancelImmediately() {
        synchronized(lock) {
            isCapturingActiveSpeech.set(false)
            _isRecordingActive.value = false
            dualRingBuffer.clear()
            _amplitudeFlow.value = 0f
        }
    }

    /**
     * Completely shuts down the audio stream and releases hardware DSP resources.
     */
    fun release() {
        synchronized(lock) {
            isRecordingRunning.set(false)
            isCapturingActiveSpeech.set(false)
            _isRecordingActive.value = false
            _amplitudeFlow.value = 0f

            recordingJob?.cancel()
            recordingJob = null

            try {
                noiseSuppressor?.release()
            } catch (_: Exception) {}
            noiseSuppressor = null

            try {
                acousticEchoCanceler?.release()
            } catch (_: Exception) {}
            acousticEchoCanceler = null

            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (_: Exception) {}
            audioRecord = null

            dualRingBuffer.clear()
        }
    }
}
