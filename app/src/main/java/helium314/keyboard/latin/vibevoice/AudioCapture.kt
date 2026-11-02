package helium314.keyboard.latin.vibevoice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The microphone: 16 kHz, 16-bit, mono PCM, with the recovery the v1 client grew over time --
 * privacy-sensitive capture so a messenger's own voice input does not silence us, re-initialising
 * a recorder that delivers two seconds of zeros, and ending gracefully when the system policy hands
 * the microphone to another app.
 *
 * Taken from [VibeVoiceClient] for the v2 session; v1 still carries its own copy so this change
 * cannot alter it. Once v2 has replaced v1, that copy goes.
 *
 * [onAudio] runs on the capture coroutine for every read; [onMicLost] once, when the microphone
 * cannot be recovered, with a `WARN_` code. Neither is called after [stop].
 */
interface AudioSource {
    val currentLevel: Float
    fun start(): Boolean
    fun stop()
}

class AudioCapture(
    private val scope: CoroutineScope,
    private val onAudio: (ByteArray, Int) -> Unit,
    private val onMicLost: (String) -> Unit,
) : AudioSource {
    private val lock = Any()
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var job: Job? = null
    @Volatile private var running = false
    @Volatile override var currentLevel = 0f
        private set

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var bufferSize = 0

    /** Opens the microphone and starts reading. False when it cannot be opened at all. */
    override fun start(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            VibeVoiceDebugLogger.log("AudioRecord.getMinBufferSize failed: $minBuf")
            return false
        }
        bufferSize = minBuf * 4
        synchronized(lock) {
            running = true
        }
        if (!initAudioRecord()) {
            synchronized(lock) {
                running = false
                releaseLocked()
            }
            return false
        }
        currentLevel = 0f
        synchronized(lock) {
            if (!running) {
                releaseLocked()
                return false
            }
            job = scope.launch { loop() }
        }
        return true
    }

    override fun stop() {
        val oldJob: Job?
        synchronized(lock) {
            running = false
            oldJob = job
            job = null
            releaseLocked()
            currentLevel = 0f
        }
        oldJob?.cancel()
    }

    private fun releaseLocked() {
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    @SuppressLint("MissingPermission")
    private fun initAudioRecord(): Boolean {
        synchronized(lock) {
            if (!running) return false
            try {
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing old AudioRecord", e)
            }
            audioRecord = null
        }
        try {
            val record = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Privacy-sensitive, so the concurrent-capture tie-break is "most recently started
                // wins" rather than "the privacy-sensitive client wins" -- see VibeVoiceClient.
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfig)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setPrivacySensitive(true)
                    .build()
            } else {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, channelConfig, audioFormat, bufferSize)
            }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                VibeVoiceDebugLogger.log("AudioRecord init failed: state=${record.state}")
                try { record.release() } catch (_: Exception) {}
                return false
            }
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                VibeVoiceDebugLogger.log("AudioRecord start failed: recordingState=${record.recordingState}")
                try { record.stop() } catch (_: Exception) {}
                try { record.release() } catch (_: Exception) {}
                return false
            }
            synchronized(lock) {
                if (!running) {
                    try { record.stop() } catch (_: Exception) {}
                    try { record.release() } catch (_: Exception) {}
                    return false
                }
                audioRecord = record
            }
            VibeVoiceDebugLogger.log("AudioRecord successfully initialized and started")
            return true
        } catch (e: Exception) {
            VibeVoiceDebugLogger.log("Exception initializing AudioRecord: ${e.message}")
            return false
        }
    }

    private fun isSilencedByPolicy(record: AudioRecord): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            record.activeRecordingConfiguration?.isClientSilenced == true
        } catch (e: Exception) {
            Log.e(TAG, "Could not read active recording configuration", e)
            false
        }
    }

    private fun lose(code: String) {
        if (!running) return
        running = false
        onMicLost(code)
    }

    private suspend fun CoroutineScope.loop() {
        val buffer = ByteArray(bufferSize)
        var consecutiveZeroBytes = 0L
        val zeroLimitBytes = 16000 * 2 * 2 // 2 seconds of silence
        var recoveryAttempts = 0
        val maxRecoveryAttempts = 3
        var readsSinceInit = 0

        while (isActive && running) {
            val record = audioRecord
            if (record == null) {
                // Only a failed re-open leaves no recorder while running. Waiting here would keep
                // a session "recording" that sends nothing until the user notices; end it instead.
                VibeVoiceDebugLogger.log("Microphone could not be re-opened. Ending session gracefully.")
                lose(VibeVoiceClient.WARN_MIC_UNAVAILABLE)
                break
            }
            val startTime = System.nanoTime()
            val read = try {
                record.read(buffer, 0, buffer.size)
            } catch (e: Exception) {
                Log.e(TAG, "Exception reading from AudioRecord", e)
                -1
            }
            val durationMs = (System.nanoTime() - startTime) / 1_000_000
            if (!running) break

            if (read > 0) {
                readsSinceInit++
                currentLevel = level(buffer, read)

                var isAllZeros = true
                for (i in 0 until read) {
                    if (buffer[i] != 0.toByte()) {
                        isAllZeros = false
                        break
                    }
                }
                if (isAllZeros) consecutiveZeroBytes += read
                else {
                    consecutiveZeroBytes = 0L
                    recoveryAttempts = 0
                }

                val expectedMs = read / 32
                if (isAllZeros && expectedMs > 20 && durationMs < expectedMs / 10) {
                    delay((expectedMs - durationMs).coerceAtLeast(10L))
                }

                if (readsSinceInit > 5 && consecutiveZeroBytes >= zeroLimitBytes) {
                    if (isSilencedByPolicy(record)) {
                        VibeVoiceDebugLogger.log("Microphone silenced by system capture policy (another app is recording). Ending session gracefully.")
                        lose(VibeVoiceClient.WARN_MIC_BUSY)
                        break
                    }
                    VibeVoiceDebugLogger.log("Dead microphone detected (2s of consecutive zeros). Attempting recovery...")
                    if (recoveryAttempts < maxRecoveryAttempts) {
                        recoveryAttempts++
                        VibeVoiceDebugLogger.log("Re-initializing AudioRecord (attempt $recoveryAttempts/$maxRecoveryAttempts)")
                        try { record.stop() } catch (_: Exception) {}
                        delay(300)
                        if (initAudioRecord()) {
                            consecutiveZeroBytes = 0L
                            readsSinceInit = 0
                            continue
                        }
                    } else {
                        VibeVoiceDebugLogger.log("Max recovery attempts reached. Ending session gracefully.")
                        lose(VibeVoiceClient.WARN_MIC_UNAVAILABLE)
                        break
                    }
                }
                onAudio(buffer, read)
            } else if (read == 0) {
                delay(10)
            } else {
                Log.e(TAG, "AudioRecord read error: $read")
                if (recoveryAttempts < maxRecoveryAttempts) {
                    recoveryAttempts++
                    VibeVoiceDebugLogger.log("Re-initializing AudioRecord on read error $read (attempt $recoveryAttempts/$maxRecoveryAttempts)")
                    try { record.stop() } catch (_: Exception) {}
                    delay(300)
                    if (initAudioRecord()) {
                        consecutiveZeroBytes = 0L
                        readsSinceInit = 0
                        continue
                    }
                } else {
                    VibeVoiceDebugLogger.log("AudioRecord read error: $read — ending session gracefully")
                    lose(VibeVoiceClient.WARN_MIC_UNAVAILABLE)
                    break
                }
            }
        }
    }

    companion object {
        private const val TAG = "AudioCapture"
        private const val LEVEL_FULL_SCALE = 0.15

        /** The level for the waves from the freshest quarter of a read, as in [VibeVoiceClient]. */
        internal fun level(buffer: ByteArray, read: Int): Float {
            val numSamples = read / 2
            if (numSamples <= 0) return 0f
            val tailStart = (numSamples * 3) / 4
            var squares = 0L
            for (i in tailStart until numSamples) {
                val b1 = buffer[2 * i].toInt() and 0xFF
                val b2 = buffer[2 * i + 1].toInt() and 0xFF
                val s = ((b2 shl 8) or b1).toShort().toLong()
                squares += s * s
            }
            val rms = Math.sqrt(squares.toDouble() / (numSamples - tailStart)) / 32768.0
            return Math.sqrt(rms / LEVEL_FULL_SCALE).coerceIn(0.0, 1.0).toFloat()
        }
    }
}
