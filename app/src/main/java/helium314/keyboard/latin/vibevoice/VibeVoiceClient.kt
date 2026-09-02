package helium314.keyboard.latin.vibevoice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

interface VibeVoiceListener {
    fun onPartial(text: String, isNewSegment: Boolean)
    fun onFinal(text: String, isNewSegment: Boolean)
    fun onError(error: String)
    /**
     * The capture side failed but the session is being wound down gracefully, so the audio that
     * was already sent still gets transcribed. [code] is one of the `WARN_` constants.
     * Unlike [onError] this must not tear the session down — wait for the final result.
     */
    fun onWarning(code: String)
    fun onClosed()
    fun onCommitComposing()
}

class VibeVoiceClient(
    private val apiKey: String,
    private val listener: VibeVoiceListener
) {
    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var isStreaming = false
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var audioJob: Job? = null
    @Volatile private var totalRead = 0L
    @Volatile private var lastFullText = ""
    /**
     * RMS of the most recent audio buffer, 0..1. Written by the capture coroutine roughly every
     * 100 ms and read once per frame by [VoiceWaveView] — never delivered through a callback or a
     * Handler, because the only consumer is an animation that paints itself and would gain nothing
     * from being woken on the UI thread thirty times a second.
     */
    @Volatile var currentLevel = 0f
        private set
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)
    @Volatile private var closureJob: Job? = null

    private val rollingBuffer = ByteArray(30 * 32000) // 30 seconds of audio at 16kHz 16-bit mono
    // writeToRollingBuffer runs on the audio coroutine, readUnconfirmedAudio on the OkHttp callback
    // thread during a reconnect. Without this lock the writer wraps around and overwrites the oldest
    // bytes while the reader is still copying them, corrupting exactly the unconfirmed audio the
    // reconnect exists to preserve. @Volatile on totalRead does not protect the array itself.
    private val rollingBufferLock = Any()
    // How much audio had been handed to the socket when it dropped. Everything after this point
    // never reached the server and is what a reconnect has to resend.
    //
    // This used to resend everything since `audioConfirmedBytes`, which was derived from a `dur`
    // field in the result frames -- except the server has never sent that field, on any branch, so
    // the value stayed at 0 and every reconnect resent the whole 30-second buffer. The two flushes
    // in the bug report logs are 15.4 s and 26 s of already-transcribed audio, which the server
    // then transcribes a second time. If the server ever starts acknowledging processed seconds,
    // that is the better signal and belongs here.
    @Volatile private var disconnectedAtBytes = 0L
    // Index of the last content frame applied, and how many have been applied on this connection.
    // Both reset on reconnect: a reconnect is a new server-side session and its frame numbering
    // starts at 1 again, so carrying them over would make us discard every frame of the new one.
    @Volatile private var lastAppliedIdx = 0
    @Volatile private var framesAppliedThisConnection = 0
    @Volatile private var isReconnecting = false
    @Volatile private var retryCount = 0
    @Volatile private var isWsOpen = false
    @Volatile private var pendingEndStream = false
    private val preOpenBuffer = ArrayDeque<okio.ByteString>()
    private var preOpenBufferSizeBytes = 0
    private val maxPreOpenBufferBytes = MAX_PRE_OPEN_BUFFER_SECONDS * 16000 * 2

    /** Appends [length] bytes and advances [totalRead]; the two must happen atomically, because the
     *  reader derives its start offset from totalRead. */
    private fun writeToRollingBuffer(data: ByteArray, offset: Int, length: Int) = synchronized(rollingBufferLock) {
        val size = rollingBuffer.size
        for (i in 0 until length) {
            val idx = ((totalRead + i) % size).toInt()
            rollingBuffer[idx] = data[offset + i]
        }
        totalRead += length
    }

    /** Everything captured since [confirmedBytes], clamped to what the buffer still holds, or null if
     *  there is nothing to resend. Length and contents are read under one lock so they agree. */
    private fun readUnconfirmedAudio(confirmedBytes: Long): ByteArray? = synchronized(rollingBufferLock) {
        val size = rollingBuffer.size
        val length = minOf(totalRead - confirmedBytes, totalRead, size.toLong()).toInt()
        if (length <= 0) return@synchronized null
        val result = ByteArray(length)
        val startPos = totalRead - length
        for (i in 0 until length) {
            var index = ((startPos + i) % size).toInt()
            if (index < 0) index += size
            result[i] = rollingBuffer[index]
        }
        result
    }

    /**
     * True when the system is deliberately feeding us silence because another app won the
     * concurrent-capture arbitration. Re-initializing the recorder cannot beat that — the policy
     * is re-applied to the new client — so this distinguishes "give up cleanly" from "the
     * recorder itself wedged and a restart may help".
     */
    private fun isSilencedByPolicy(record: AudioRecord): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            record.activeRecordingConfiguration?.isClientSilenced == true
        } catch (e: Exception) {
            Log.e(TAG, "Could not read active recording configuration", e)
            false
        }
    }

    private fun connectWebSocket() {
        val request = Request.Builder()
            .url("wss://vibevoice.net/stream")
            .build()
        webSocket = sharedHttpClient.newWebSocket(request, createWebSocketListener())
    }

    private fun triggerReconnect() {
        if (!isStreaming) return
        isReconnecting = true
        synchronized(preOpenBuffer) {
            if (isWsOpen) disconnectedAtBytes = totalRead
            isWsOpen = false
        }
        
        listener.onCommitComposing()
        
        val delayMs = when (retryCount) {
            0 -> 500L
            1 -> 1000L
            else -> 2000L
        }
        retryCount++
        
        if (retryCount <= MAX_RETRIES) {
            VibeVoiceDebugLogger.log("Reconnecting in ${delayMs}ms (attempt $retryCount/$MAX_RETRIES)...")
            scope.launch {
                delay(delayMs)
                connectWebSocket()
            }
        } else {
            VibeVoiceDebugLogger.log("Max reconnect retries reached. Stopping stream.")
            isStreaming = false
            cleanupAudioCapture()
            listener.onError("Connection lost")
        }
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                VibeVoiceDebugLogger.log("WS Open (reconnect=$isReconnecting)")
                val authJson = JSONObject().put("api_key", apiKey).toString()
                webSocket.send(authJson)
                
                synchronized(preOpenBuffer) {
                    isWsOpen = true
                    
                    if (isReconnecting) {
                        // Audio recorded while the socket was down went into BOTH the rolling
                        // buffer and preOpenBuffer. The unconfirmed-bytes flush below already
                        // covers it, so dropping the queue here avoids sending it twice —
                        // duplicated audio makes the server repeat words in the transcript.
                        if (preOpenBuffer.isNotEmpty()) {
                            VibeVoiceDebugLogger.log("Reconnect: dropping ${preOpenBuffer.size} queued frames already covered by the rolling buffer")
                            preOpenBuffer.clear()
                            preOpenBufferSizeBytes = 0
                        }
                        lastAppliedIdx = 0
                        framesAppliedThisConnection = 0
                        val flushData = readUnconfirmedAudio(disconnectedAtBytes)
                        if (flushData != null) {
                            VibeVoiceDebugLogger.log("Reconnected: flushing ${flushData.size} bytes of unconfirmed audio")
                            webSocket.send(flushData.toByteString(0, flushData.size))
                        }
                        isReconnecting = false
                        retryCount = 0
                    }

                    for (bytes in preOpenBuffer) {
                        webSocket.send(bytes)
                    }
                    preOpenBuffer.clear()
                    preOpenBufferSizeBytes = 0

                    // stopStreaming() ran before the handshake completed, so it could not send
                    // END_STREAM without it overtaking the auth frame above.
                    if (pendingEndStream) {
                        pendingEndStream = false
                        VibeVoiceDebugLogger.log("Sending deferred END_STREAM after auth")
                        webSocket.send("END_STREAM")
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("text")) {
                        val resultText = json.getString("text")
                        val isFinal = json.optBoolean("is_final", false)
                        // Content frames count from 1, densely, and never repeat; the pieces do not
                        // overlap, so a client applies each index exactly once and concatenates.
                        // Servers before 2026-09-02 omit the field, hence the fallback further down.
                        val idx = json.optInt("idx", 0)

                        VibeVoiceDebugLogger.log("WS msg text len: ${resultText.length}, final: $isFinal, idx: $idx")
                        if (isFinal) {
                            // End-of-stream marker. Its text is empty by contract and its idx is how
                            // many content frames were sent, which is the only way to tell a short
                            // transcript apart from a lost frame.
                            if (idx > 0 && idx != framesAppliedThisConnection) {
                                VibeVoiceDebugLogger.log(
                                    "[FRAME_GAP] server reports $idx content frames, applied $framesAppliedThisConnection"
                                )
                            }
                            if (resultText.isBlank()) {
                                VibeVoiceDebugLogger.log("[EMPTY_RESULT] onFinal received empty text")
                            }

                            val isNewSegment = if (idx > 0) framesAppliedThisConnection > 0
                                else lastFullText.isNotEmpty() && !resultText.startsWith(lastFullText)
                            if (isNewSegment && resultText.isNotBlank()) {
                                VibeVoiceDebugLogger.log("New segment detected onFinal. Prev: '${lastFullText.take(20)}...', New: '${resultText.take(20)}...'")
                            }
                            lastFullText = resultText

                            listener.onFinal(resultText, isNewSegment)

                            if (!isStreaming) {
                                VibeVoiceDebugLogger.log("Closing WS immediately after final result marker")
                                closureJob?.cancel()
                                closureJob = null
                                webSocket.close(1000, "Done after Final")
                                if (this@VibeVoiceClient.webSocket == webSocket) {
                                    this@VibeVoiceClient.webSocket = null
                                }
                            }
                        } else {
                            val isNewSegment: Boolean
                            if (idx > 0) {
                                // A frame we have already applied. Cannot happen on a healthy
                                // connection, but applying one twice is what pastes the user's
                                // words in twice, so refuse it rather than trust the wire.
                                if (idx <= lastAppliedIdx) {
                                    VibeVoiceDebugLogger.log("Ignoring already applied frame idx=$idx (last=$lastAppliedIdx)")
                                    return
                                }
                                if (idx > lastAppliedIdx + 1) {
                                    VibeVoiceDebugLogger.log("[FRAME_GAP] jumped from idx=$lastAppliedIdx to idx=$idx")
                                }
                                // Every piece after the first opens a new segment: the pieces do not
                                // overlap, so the one being displayed has to be committed first.
                                isNewSegment = framesAppliedThisConnection > 0
                                lastAppliedIdx = idx
                                framesAppliedThisConnection++
                            } else {
                                // Server without idx: infer it from the text, as before. A piece that
                                // does not continue the previous one starts a new segment.
                                isNewSegment = lastFullText.isNotEmpty() && !resultText.startsWith(lastFullText)
                            }
                            if (isNewSegment) {
                                VibeVoiceDebugLogger.log("New segment detected onPartial. Prev: '${lastFullText.take(20)}...', New: '${resultText.take(20)}...'")
                            }
                            lastFullText = resultText

                            listener.onPartial(resultText, isNewSegment)
                        }
                    } else if (json.has("error")) {
                        val errorMsg = json.optString("error", "Unknown server error")
                        VibeVoiceDebugLogger.log("WS server error: $errorMsg")
                        listener.onError(errorMsg)
                    } else {
                        VibeVoiceDebugLogger.log("WS msg no text: $text")
                    }
                } catch (e: Exception) {
                    VibeVoiceDebugLogger.log("WS msg parse error: ${e.message}")
                    Log.e(TAG, "WS msg parse error", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                VibeVoiceDebugLogger.log("WS Failure: ${t.message}")
                if (isStreaming && retryCount < MAX_RETRIES) {
                    triggerReconnect()
                } else {
                    isStreaming = false
                    cleanupAudioCapture()
                    closureJob?.cancel()
                    closureJob = null
                    if (this@VibeVoiceClient.webSocket == webSocket) {
                        this@VibeVoiceClient.webSocket = null
                    }
                    listener.onError(t.message ?: "WebSocket Error")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                VibeVoiceDebugLogger.log("WS Closing: $code / $reason")
                webSocket.close(1000, "Acknowledge Close")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                VibeVoiceDebugLogger.log("WS Closed: $code / $reason")
                if (isStreaming && code != 1000) {
                    VibeVoiceDebugLogger.log("Unexpected WS close mid-session. Reconnecting...")
                    triggerReconnect()
                } else {
                    isStreaming = false
                    cleanupAudioCapture()
                    closureJob?.cancel()
                    closureJob = null
                    if (this@VibeVoiceClient.webSocket == webSocket) {
                        this@VibeVoiceClient.webSocket = null
                    }
                    listener.onClosed()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startStreaming() {
        if (isStreaming) return
        isStreaming = true
        closureJob?.cancel()
        closureJob = null
        isReconnecting = false
        retryCount = 0
        disconnectedAtBytes = 0L
        lastAppliedIdx = 0
        framesAppliedThisConnection = 0
        isWsOpen = false
        pendingEndStream = false

        synchronized(preOpenBuffer) {
            preOpenBuffer.clear()
            preOpenBufferSizeBytes = 0
        }

        connectWebSocket()

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            VibeVoiceDebugLogger.log("AudioRecord.getMinBufferSize failed: $minBuf")
            listener.onError("AudioRecord init failed")
            isStreaming = false
            return
        }
        val bufferSize = minBuf * 4

        fun initAudioRecord(): Boolean {
            try {
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing old AudioRecord", e)
            }
            try {
                val record = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // VOICE_RECOGNITION is not privacy-sensitive by default, and Android's
                    // concurrent-capture policy always hands the audio to the privacy-sensitive
                    // client — so any app recording from VOICE_COMMUNICATION (which is
                    // privacy-sensitive by default, e.g. a messenger's own voice input) wins and
                    // we are silently fed zeroed buffers. Marking our capture privacy-sensitive
                    // too moves the tie-break to "most recently started wins", which we win
                    // because the user just triggered us.
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
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                }
                audioRecord = record

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    VibeVoiceDebugLogger.log("AudioRecord init failed: state=${record.state}")
                    return false
                }
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    VibeVoiceDebugLogger.log("AudioRecord start failed: recordingState=${record.recordingState}")
                    return false
                }
                VibeVoiceDebugLogger.log("AudioRecord successfully initialized and started")
                return true
            } catch (e: Exception) {
                VibeVoiceDebugLogger.log("Exception initializing AudioRecord: ${e.message}")
                return false
            }
        }

        if (!initAudioRecord()) {
            cleanupAudioCapture()
            listener.onError("Microphone unavailable")
            isStreaming = false
            return
        }

        currentLevel = 0f
        totalRead = 0L // Reset for new session
        lastFullText = ""
        audioJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            var consecutiveZeroBytes = 0L
            val zeroLimitBytes = 16000 * 2 * 2 // 2 seconds of silence
            var recoveryAttempts = 0
            val maxRecoveryAttempts = 3
            var lastLogTime = 0L
            var totalReadsInSession = 0
            var sumOfSquares = 0L
            var totalSamples = 0L

            while (isActive && isStreaming) {
                val currentRecord = audioRecord
                if (currentRecord == null) {
                    delay(50)
                    continue
                }

                val startTime = System.nanoTime()
                val read = try {
                    currentRecord.read(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception reading from AudioRecord", e)
                    -1
                }
                val durationMs = (System.nanoTime() - startTime) / 1_000_000

                if (read > 0) {
                    totalReadsInSession++
                    
                    val numSamples = read / 2
                    var bufferSquares = 0L
                    for (i in 0 until numSamples) {
                        val b1 = buffer[2 * i].toInt() and 0xFF
                        val b2 = buffer[2 * i + 1].toInt() and 0xFF
                        val sample = ((b2 shl 8) or b1).toShort()
                        val sampleVal = sample.toLong()
                        bufferSquares += sampleVal * sampleVal
                    }
                    sumOfSquares += bufferSquares
                    totalSamples += numSamples
                    if (numSamples > 0) {
                        val rms = Math.sqrt(bufferSquares.toDouble() / numSamples) / 32768.0
                        // Raw RMS of speech sits around 0.02..0.06 and peaks near 0.17 -- the session
                        // totals in the bug report logs bear that out -- so feeding it straight to the
                        // animation moved the waves by a couple of percent and read as no reaction at
                        // all. The square root against a 0.15 full scale spreads that range over most
                        // of 0..1, which is where the web pipeline's FFT average already lands.
                        currentLevel = Math.sqrt(rms / LEVEL_FULL_SCALE)
                            .coerceIn(0.0, 1.0).toFloat()
                    }
                    
                    var isAllZeros = true
                    for (i in 0 until read) {
                        if (buffer[i] != 0.toByte()) {
                            isAllZeros = false
                            break
                        }
                    }

                    if (isAllZeros) {
                        consecutiveZeroBytes += read
                    } else {
                        consecutiveZeroBytes = 0L
                        recoveryAttempts = 0 // Reset attempts on successful read
                    }

                    val expectedMs = read / 32
                    val isRapidRead = isAllZeros && expectedMs > 20 && durationMs < expectedMs / 10
                    val isSilencedTooLong = consecutiveZeroBytes >= zeroLimitBytes

                    if (isRapidRead) {
                        delay((expectedMs - durationMs).coerceAtLeast(10L))
                    }

                    if (totalReadsInSession > 5 && isSilencedTooLong) {
                        if (isSilencedByPolicy(currentRecord)) {
                            // Another app holds the mic. Restarting is pointless, and killing the
                            // session outright throws away everything the server transcribed
                            // before the mic went quiet — so wind down through the normal stop
                            // path and let the final result be committed.
                            VibeVoiceDebugLogger.log(
                                "Microphone silenced by system capture policy (another app is recording). Ending session gracefully."
                            )
                            listener.onWarning(WARN_MIC_BUSY)
                            stopStreaming()
                            break
                        }

                        VibeVoiceDebugLogger.log("Dead microphone detected (2s of consecutive zeros). Attempting recovery...")

                        if (recoveryAttempts < maxRecoveryAttempts) {
                            recoveryAttempts++
                            VibeVoiceDebugLogger.log("Re-initializing AudioRecord (attempt $recoveryAttempts/$maxRecoveryAttempts)")

                            try {
                                currentRecord.stop()
                            } catch (_: Exception) {}

                            delay(300)

                            if (initAudioRecord()) {
                                consecutiveZeroBytes = 0L
                                totalReadsInSession = 0
                                continue
                            }
                        } else {
                            VibeVoiceDebugLogger.log("Max recovery attempts reached. Ending session gracefully.")
                            listener.onWarning(WARN_MIC_UNAVAILABLE)
                            stopStreaming()
                            break
                        }
                    }

                    writeToRollingBuffer(buffer, 0, read) // also advances totalRead
                    
                    val bytesToSend = buffer.toByteString(0, read)
                    synchronized(preOpenBuffer) {
                        if (isWsOpen) {
                            webSocket?.send(bytesToSend)
                        } else {
                            preOpenBuffer.addLast(bytesToSend)
                            preOpenBufferSizeBytes += bytesToSend.size
                            while (preOpenBufferSizeBytes > maxPreOpenBufferBytes && preOpenBuffer.isNotEmpty()) {
                                preOpenBufferSizeBytes -= preOpenBuffer.removeFirst().size
                            }
                        }
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastLogTime >= 5000) {
                         Log.d(TAG, "Total bytes read: $totalRead")
                         VibeVoiceDebugLogger.log("Audio KB read: ${totalRead / 1024}")
                         lastLogTime = now
                    }
                } else if (read == 0) {
                    delay(10)
                } else {
                    Log.e(TAG, "AudioRecord read error: $read")
                    
                    if (recoveryAttempts < maxRecoveryAttempts) {
                        recoveryAttempts++
                        VibeVoiceDebugLogger.log("Re-initializing AudioRecord on read error $read (attempt $recoveryAttempts/$maxRecoveryAttempts)")
                        try {
                            currentRecord.stop()
                        } catch (_: Exception) {}
                        delay(300)
                        if (initAudioRecord()) {
                            consecutiveZeroBytes = 0L
                            totalReadsInSession = 0
                            continue
                        }
                    } else {
                        // Same reasoning as above: the audio already streamed is still worth a
                        // transcript, so stop gracefully instead of discarding the session.
                        VibeVoiceDebugLogger.log("AudioRecord read error: $read — ending session gracefully")
                        listener.onWarning(WARN_MIC_UNAVAILABLE)
                        stopStreaming()
                        break
                    }
                }
            }
            val overallRms = if (totalSamples > 0) Math.sqrt(sumOfSquares.toDouble() / totalSamples) / 32768.0 else 0.0
            VibeVoiceDebugLogger.log("Session complete. Final total bytes: $totalRead, Overall RMS: ${String.format(java.util.Locale.US, "%.6f", overallRms)}")
            Log.d(TAG, "Exit recording loop. Final total bytes: $totalRead, Overall RMS: $overallRms")
        }
    }

    fun stopStreaming() {
        if (!isStreaming) return
        isStreaming = false
        cleanupAudioCapture()

        val ws = webSocket
        // Sending END_STREAM before the handshake finishes queues it ahead of the auth frame that
        // onOpen sends, and the server answers "invalid_auth_format" and drops the session. This
        // happens whenever a session is stopped within the first few hundred milliseconds.
        synchronized(preOpenBuffer) {
            if (isWsOpen) {
                ws?.send("END_STREAM")
            } else {
                VibeVoiceDebugLogger.log("Socket not open yet — deferring END_STREAM until after auth")
                pendingEndStream = true
            }
        }
        closureJob = scope.launch {
            VibeVoiceDebugLogger.log("Closing WS in 3.0s backstop timer started. Total bytes read: $totalRead")
            delay(3000)
            VibeVoiceDebugLogger.log("3.0s backstop timer expired. Closing WS.")
            ws?.close(1000, "Done (timeout)")
            if (this@VibeVoiceClient.webSocket == ws) {
                this@VibeVoiceClient.webSocket = null
            }
            closureJob = null
        }
    }

    fun cancel() {
        stopStreaming()
        scopeJob.cancel()
    }

    private fun cleanupAudioCapture() {
        audioJob?.cancel()
        audioJob = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val MAX_PRE_OPEN_BUFFER_SECONDS = 5
        private const val VIBEVOICE_API_KEY_PREF = "vibevoice_api_key"
        private const val TAG = "VibeVoiceClient"
        private const val MAX_RETRIES = 3
        /** RMS that counts as a full-scale level for the waves; see where currentLevel is written. */
        private const val LEVEL_FULL_SCALE = 0.15

        /** Another app won the concurrent-capture arbitration and the system is feeding us silence. */
        const val WARN_MIC_BUSY = "mic_busy"
        /** The recorder stopped delivering usable audio and restarting it did not help. */
        const val WARN_MIC_UNAVAILABLE = "mic_unavailable"

        @JvmField val sharedHttpClient = OkHttpClient.Builder()
            .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        @Volatile private var cachedPrefs: SharedPreferences? = null

        @JvmStatic
        fun vibeVoicePrefs(context: Context): SharedPreferences =
            cachedPrefs ?: synchronized(VibeVoiceClient::class.java) {
                cachedPrefs ?: createVibeVoicePrefs(context.applicationContext).also { cachedPrefs = it }
            }

        private fun createVibeVoicePrefs(context: Context): SharedPreferences = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "vibevoice_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences unavailable — API key will be stored in cleartext", e)
            context.getSharedPreferences("vibevoice_prefs", MODE_PRIVATE)
        }

        @JvmStatic
        fun getApiKey(context: Context): String? =
            vibeVoicePrefs(context).getString(VIBEVOICE_API_KEY_PREF, null)

        suspend fun requestDeviceCode(deviceName: String, clientVersion: String): JSONObject? = withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("device_name", deviceName)
                .put("client_version", clientVersion)
                .toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("https://vibevoice.net/api/oauth/device/code")
                .post(body)
                .build()
            try {
                sharedHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let { JSONObject(it) }
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        }

        suspend fun pollForToken(deviceCode: String): JSONObject? = withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("device_code", deviceCode)
                .toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("https://vibevoice.net/api/oauth/device/token")
                .post(body)
                .build()
            try {
                // RFC 8628: authorization_pending is signalled via HTTP 400 + JSON body, not a network error
                sharedHttpClient.newCall(request).execute().use { response ->
                    response.body?.string()?.let { JSONObject(it) }
                }
            } catch (e: Exception) {
                null
            }
        }

        suspend fun fetchQuota(apiKey: String): JSONObject? = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://vibevoice.net/api/me/usage")
                .header("X-API-Key", apiKey)
                .build()
            try {
                sharedHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let { JSONObject(it) }
                    } else {
                        Log.e(TAG, "Quota fetch failed: HTTP ${response.code}")
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Quota fetch failed with exception", e)
                null
            }
        }
    }
}
