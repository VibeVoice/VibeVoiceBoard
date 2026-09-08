package helium314.keyboard.latin.vibevoice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
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

/**
 * What `POST /api/trial/key` can answer. The three failures are not interchangeable: 409 means show
 * the account step, 429 means try again later, and anything else is a fault. Collapsing any two
 * either invites somebody to register when it should have retried, or shows an error when it should
 * have invited. See P-058 R4 in the VibeVoice repository.
 *
 * Top level rather than inside the companion: `VibeVoiceClient.TrialResult` is how every caller
 * wants to write it, and a class nested in a companion is `VibeVoiceClient.Companion.TrialResult`.
 */
sealed class TrialResult {
    data class Granted(val key: String, val minutesGranted: Double) : TrialResult()
    object AlreadyUsed : TrialResult()
    object RateLimited : TrialResult()
    object Failed : TrialResult()
}

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
    /**
     * The link became too poor to stream into, or recovered.
     *
     * Not an error and not a warning: the session is still running and still recording into the
     * rolling buffer. The keyboard uses it to say so on the space bar, which is where the user is
     * already looking, and to stop saying so when it clears.
     */
    fun onLinkQualityChanged(degraded: Boolean)
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

    /**
     * When the server was last heard from, and when the current outage started.
     *
     * A dead radio does not usually kill the socket. TCP retransmits, OkHttp's ping was thirty
     * seconds apart, and in between the client cheerfully wrote audio into a send buffer that never
     * drained while the user went on talking into nothing. The socket-is-dead signal we had is the
     * *last* symptom of a bad link, not the first.
     */
    @Volatile private var lastServerMessageAt = 0L
    @Volatile private var degradedSinceMs = 0L
    private var watchdogJob: Job? = null

    /**
     * Whether the link is currently too poor to be streaming into.
     *
     * Read by the keyboard to change what the space bar says. Deliberately not an error: a tunnel
     * is a normal thing to drive through, and the session survives it as long as the unsent audio
     * still fits in the rolling buffer.
     */
    @Volatile var isLinkDegraded = false
        private set
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
            2 -> 2000L
            else -> 3000L
        }
        retryCount++
        if (retryCount == 1) degradedSinceMs = SystemClock.elapsedRealtime()

        // Keep trying while the audio nobody has received still fits in the buffer.
        //
        // Three attempts over three and a half seconds was the old rule, and it threw away tolerance
        // we had already paid for: the rolling buffer holds thirty seconds, so anything shorter than
        // that is recoverable without losing a word. Three and a half seconds is not a tunnel, a
        // lift, or a train between stations, which are exactly the situations this is for.
        //
        // Two bounds, because either alone can run away. The buffer bound stops mattering once
        // capture has been torn down and totalRead stops growing; the clock bound stops a permanent
        // outage from retrying for ever.
        val unsent = totalRead - disconnectedAtBytes
        val outageMs = SystemClock.elapsedRealtime() - degradedSinceMs
        if (unsent < rollingBuffer.size && outageMs < RECONNECT_WINDOW_MS) {
            VibeVoiceDebugLogger.log("Reconnecting in ${delayMs}ms (attempt $retryCount/$MAX_RETRIES)...")
            scope.launch {
                delay(delayMs)
                // The session can end inside that delay -- the user stops it, or the microphone is
                // taken away. Reconnecting then opens a socket nobody asked for, and the backstop
                // timer that would have closed it was armed on the previous one.
                if (!isStreaming) {
                    VibeVoiceDebugLogger.log("Reconnect cancelled: session no longer streaming")
                    return@launch
                }
                connectWebSocket()
            }
        } else {
            VibeVoiceDebugLogger.log("Giving up: unsent=$unsent outageMs=$outageMs")
            isStreaming = false
            setLinkDegraded(false)
            cleanupAudioCapture()
            // A code, not a sentence. What the user reads is the keyboard's business, and "Dictation
            // error: Connection lost" framed a tunnel as a fault in the product.
            listener.onError(ERR_LINK_LOST)
        }
    }

    /**
     * Watches the link while a session runs, once a second.
     *
     * Two independent symptoms, because they fail in different directions:
     *
     * `queueSize()` is what OkHttp has accepted from us and not yet put on the wire. It grows when
     * the radio cannot keep up with 32 kB/s, which is the honest definition of "too poor to stream
     * into" and the earliest thing we can see. Six seconds' worth is the threshold: below that it
     * is ordinary jitter, above it the backlog is not coming back on its own.
     *
     * Server silence catches the other case, where the socket is fine in our direction and dead in
     * theirs. The server acknowledges continuously, so eight seconds without a word means the round
     * trip is broken even though nothing has thrown.
     *
     * Neither ends the session. They raise a flag the keyboard reads, and a reconnect is only
     * triggered once the queue has grown past what the buffer could resend anyway.
     */
    private fun startLinkWatchdog() {
        watchdogJob?.cancel()
        lastServerMessageAt = SystemClock.elapsedRealtime()
        watchdogJob = scope.launch {
            while (isActive && isStreaming) {
                delay(LINK_CHECK_INTERVAL_MS)
                if (!isStreaming) break
                val ws = webSocket
                val queued = try { ws?.queueSize() ?: 0L } catch (_: Exception) { 0L }
                val silentFor = SystemClock.elapsedRealtime() - lastServerMessageAt
                val bad = isWsOpen && (queued > LINK_QUEUE_STALL_BYTES || silentFor > LINK_SILENCE_STALL_MS)
                if (bad != isLinkDegraded) {
                    VibeVoiceDebugLogger.log("Link ${if (bad) "degraded" else "recovered"}: queued=$queued silentMs=$silentFor")
                    setLinkDegraded(bad)
                }
                // A backlog bigger than the rolling buffer can never be made good by waiting: the
                // audio behind it has already been overwritten. Cut the socket and let the normal
                // reconnect path resend what is still held.
                if (isLinkDegraded && queued > rollingBuffer.size) {
                    VibeVoiceDebugLogger.log("Send queue past the buffer; forcing a reconnect")
                    try { ws?.cancel() } catch (_: Exception) { }
                }
            }
        }
    }

    private fun setLinkDegraded(degraded: Boolean) {
        isLinkDegraded = degraded
        degradedSinceMs = if (degraded) SystemClock.elapsedRealtime() else 0L
        listener.onLinkQualityChanged(degraded)
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
                // Any frame at all, not just a transcript: a link that carries anything back is a
                // link that is working, and the server sends acknowledgements even while nobody is
                // speaking.
                lastServerMessageAt = SystemClock.elapsedRealtime()
                if (isLinkDegraded) setLinkDegraded(false)
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
                        // An error frame is the server refusing the session, not a hiccup: a bad key
                        // or an exhausted quota will refuse the next three attempts too. Tearing the
                        // session down here matters because the close that follows would otherwise
                        // find isStreaming still true and start reconnecting into the same refusal,
                        // with the microphone running throughout.
                        isStreaming = false
                        cleanupAudioCapture()
                        abandonSocket("server error")
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
                if (isStreaming) {
                    // The decision whether another attempt is worth making lives in one place now,
                    // and it is about how much audio is still recoverable rather than about a count.
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
        isLinkDegraded = false
        startLinkWatchdog()
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
            abandonSocket("mic unavailable")
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
            // The socket was opened before the microphone was, so it is still there, still
            // completing its handshake and still about to authenticate for a session that no longer
            // exists. Without this it stays open until the server tires of it, and its late
            // callbacks land on an aborted session.
            abandonSocket("mic unavailable")
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
                        // Measured over the tail of the buffer, not all of it. A read carries
                        // 160-250 ms of audio, so averaging the whole thing centres the estimate
                        // more than a tenth of a second in the past and the waves visibly trail the
                        // voice. The last quarter is the freshest part we have; the animation reads
                        // this thirty times a second and would happily take more.
                        val tailStart = (numSamples * 3) / 4
                        var tailSquares = 0L
                        for (i in tailStart until numSamples) {
                            val b1 = buffer[2 * i].toInt() and 0xFF
                            val b2 = buffer[2 * i + 1].toInt() and 0xFF
                            val sample = ((b2 shl 8) or b1).toShort().toLong()
                            tailSquares += sample * sample
                        }
                        val tailCount = numSamples - tailStart
                        val rms = if (tailCount > 0)
                            Math.sqrt(tailSquares.toDouble() / tailCount) / 32768.0
                        else Math.sqrt(bufferSquares.toDouble() / numSamples) / 32768.0
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

    /**
     * Drops the socket without the graceful END_STREAM handshake, for the paths where there is no
     * session left to be graceful about. Safe to call when there is no socket.
     */
    private fun abandonSocket(reason: String) {
        val ws = webSocket ?: return
        webSocket = null
        isWsOpen = false
        pendingEndStream = false
        VibeVoiceDebugLogger.log("Abandoning socket: $reason")
        ws.cancel()
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
        // Reached by every path that ends a session, abnormal ones included, which is why the
        // watchdog is torn down here rather than in stopStreaming alone.
        watchdogJob?.cancel()
        watchdogJob = null
        if (isLinkDegraded) setLinkDegraded(false)
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

        /**
         * The trial key, deliberately NOT in [VIBEVOICE_API_KEY_PREF].
         *
         * `getApiKey() != null` is what four call sites read as "this user has an account": the
         * link panel hides itself, the wizard skips its account step, the settings screen shows a
         * quota. A trial key stored there would silently answer yes to all of them, and the one
         * screen whose entire job is to turn a trial into an account would never appear.
         *
         * So the two live apart, and [getApiKey] falls back from one to the other. Streaming, bug
         * reports and every other consumer of a key keep working on a trial without knowing it is
         * one; only [isLinked] can tell the difference, and only the places that must.
         */
        private const val VIBEVOICE_TRIAL_KEY_PREF = "vibevoice_trial_key"

        /**
         * The install this device presents to `POST /api/trial/key`, generated once.
         *
         * A random UUID and nothing derived from the device: P-058 requires an id that does not
         * identify the phone across apps, and the server cannot check that, so the client is the
         * whole of the contract. 36 characters, inside the server's 32..128 window.
         */
        private const val VIBEVOICE_INSTALL_ID_PREF = "vibevoice_install_id"

        /**
         * Set once the server has refused a session for a spent trial.
         *
         * Without it every tap on the microphone opens a socket, sends the auth frame and is
         * refused -- a round trip and a second of dead air to learn something already known.
         */
        private const val VIBEVOICE_TRIAL_SPENT_PREF = "vibevoice_trial_spent"

        /**
         * The stream's refusal when the free minutes are gone.
         *
         * Distinct from an invalid key on purpose, and the distinction is the whole point: one is
         * an invitation to link an account, the other is a fault. See P-058 R4.
         */
        const val ERR_TRIAL_EXHAUSTED = "trial_exhausted"
        private const val TAG = "VibeVoiceClient"
        private const val MAX_RETRIES = 3

        private const val LINK_CHECK_INTERVAL_MS = 1000L
        /** Six seconds of audio at 16 kHz, 16-bit mono. Below this it is jitter; above it, a backlog. */
        private const val LINK_QUEUE_STALL_BYTES = 6 * 32000L
        /** The server acknowledges continuously, so this much silence means the round trip is broken. */
        private const val LINK_SILENCE_STALL_MS = 8000L
        /**
         * How long an outage may last before the session ends.
         *
         * Matched to the rolling buffer: thirty seconds of audio is what a reconnect can resend, so
         * waiting longer would mean coming back with a gap in the middle of a sentence.
         */
        private const val RECONNECT_WINDOW_MS = 30_000L
        /** RMS that counts as a full-scale level for the waves; see where currentLevel is written. */
        private const val LEVEL_FULL_SCALE = 0.15

        /**
         * The link stayed down long enough that the audio behind it could not be recovered.
         *
         * A code rather than a message: what the user reads belongs to the keyboard, and it should
         * not read like a fault in the product. Driving into a tunnel is not a bug.
         */
        const val ERR_LINK_LOST = "link_lost"

        /** Another app won the concurrent-capture arbitration and the system is feeding us silence. */
        const val WARN_MIC_BUSY = "mic_busy"
        /** The recorder stopped delivering usable audio and restarting it did not help. */
        const val WARN_MIC_UNAVAILABLE = "mic_unavailable"

        // Ten seconds, not thirty. The ping is the backstop that notices a socket which is dead but
        // has not been told so, and at thirty it took up to a minute -- long enough to lose a whole
        // dictation into a link that had already gone. The watchdog usually gets there first now,
        // but the two answer different questions and both are cheap.
        @JvmField val sharedHttpClient = OkHttpClient.Builder()
            .pingInterval(10, java.util.concurrent.TimeUnit.SECONDS)
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

        /**
         * A key to transcribe with: the account's if there is one, otherwise the trial's.
         *
         * Callers that need to know which they got should ask [isLinked]. Callers that only need
         * to send audio -- which is nearly all of them -- must not, and do not.
         */
        @JvmStatic
        fun getApiKey(context: Context): String? =
            vibeVoicePrefs(context).getString(VIBEVOICE_API_KEY_PREF, null)
                ?: vibeVoicePrefs(context).getString(VIBEVOICE_TRIAL_KEY_PREF, null)

        /** Whether an account has been linked. A trial key is not an account. */
        @JvmStatic
        fun isLinked(context: Context): Boolean =
            vibeVoicePrefs(context).getString(VIBEVOICE_API_KEY_PREF, null) != null

        @JvmStatic
        fun hasTrialKey(context: Context): Boolean =
            vibeVoicePrefs(context).getString(VIBEVOICE_TRIAL_KEY_PREF, null) != null

        /** Whether the server has already refused a session because the trial is spent. */
        @JvmStatic
        fun isTrialSpent(context: Context): Boolean =
            vibeVoicePrefs(context).getBoolean(VIBEVOICE_TRIAL_SPENT_PREF, false)

        @JvmStatic
        fun markTrialSpent(context: Context) {
            vibeVoicePrefs(context).edit().putBoolean(VIBEVOICE_TRIAL_SPENT_PREF, true).apply()
        }

        /**
         * This install's id, generated on first use and never regenerated.
         *
         * Reinstalling produces a new one and therefore a new trial. That is the accepted abuse
         * ceiling -- ten minutes per reinstall -- and P-058 says so out loud rather than reaching
         * for attestation to close it.
         */
        @JvmStatic
        fun installId(context: Context): String {
            val prefs = vibeVoicePrefs(context)
            prefs.getString(VIBEVOICE_INSTALL_ID_PREF, null)?.let { return it }
            return synchronized(VibeVoiceClient::class.java) {
                prefs.getString(VIBEVOICE_INSTALL_ID_PREF, null) ?: java.util.UUID.randomUUID().toString()
                    .also { prefs.edit().putString(VIBEVOICE_INSTALL_ID_PREF, it).apply() }
            }
        }

        /**
         * Asks for this install's free minutes and stores the key it gets.
         *
         * Returns without asking if an account is already linked -- a linked device spending a
         * trial would burn it for nothing -- or if one has already been stored.
         */
        suspend fun requestTrialKey(context: Context): TrialResult = withContext(Dispatchers.IO) {
            val prefs = vibeVoicePrefs(context)
            if (isLinked(context)) return@withContext TrialResult.AlreadyUsed
            prefs.getString(VIBEVOICE_TRIAL_KEY_PREF, null)?.let {
                return@withContext TrialResult.Granted(it, 0.0)
            }
            val body = JSONObject().put("install_id", installId(context)).toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("https://vibevoice.net/api/trial/key")
                .post(body)
                .build()
            try {
                sharedHttpClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        409 -> return@use TrialResult.AlreadyUsed
                        429 -> return@use TrialResult.RateLimited
                    }
                    if (!response.isSuccessful) return@use TrialResult.Failed
                    val json = response.body?.string()?.let { JSONObject(it) } ?: return@use TrialResult.Failed
                    val key = json.optString("api_key").takeIf { it.isNotBlank() }
                        ?: return@use TrialResult.Failed
                    prefs.edit().putString(VIBEVOICE_TRIAL_KEY_PREF, key).apply()
                    VibeVoiceDebugLogger.log("Trial key granted")
                    TrialResult.Granted(key, json.optDouble("minutes_granted", 0.0))
                }
            } catch (e: Exception) {
                VibeVoiceDebugLogger.log("Trial key request failed: ${e.message}")
                TrialResult.Failed
            }
        }

        /**
         * How much of the trial is left. Null when there is no trial key, when the key is not a
         * trial (the server answers 404 for that), or when the request failed.
         */
        suspend fun trialStatus(context: Context): JSONObject? = withContext(Dispatchers.IO) {
            val key = vibeVoicePrefs(context).getString(VIBEVOICE_TRIAL_KEY_PREF, null) ?: return@withContext null
            val request = Request.Builder()
                .url("https://vibevoice.net/api/trial/status")
                .header("X-API-Key", key)
                .build()
            try {
                sharedHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string()?.let { JSONObject(it) } else null
                }
            } catch (e: Exception) {
                null
            }
        }

        /**
         * [installId] is optional on the wire and carried here always: the server stores it on the
         * device-code row and burns this install's trial when the token is collected, so unlinking
         * and relinking cannot hand the free minutes back (P-058 R7). An older server ignores it.
         */
        suspend fun requestDeviceCode(
            deviceName: String,
            clientVersion: String,
            installId: String? = null
        ): JSONObject? = withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("device_name", deviceName)
                .put("client_version", clientVersion)
                .apply { if (installId != null) put("install_id", installId) }
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
