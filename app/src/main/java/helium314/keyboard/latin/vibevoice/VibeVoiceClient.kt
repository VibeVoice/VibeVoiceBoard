package helium314.keyboard.latin.vibevoice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)
    @Volatile private var closureJob: Job? = null

    private val rollingBuffer = ByteArray(30 * 32000) // 30 seconds of audio at 16kHz 16-bit mono
    @Volatile private var audioConfirmedBytes = 0L
    @Volatile private var isReconnecting = false
    @Volatile private var retryCount = 0
    @Volatile private var isWsOpen = false
    private val preOpenBuffer = ArrayDeque<okio.ByteString>()
    private var preOpenBufferSizeBytes = 0
    private val maxPreOpenBufferBytes = MAX_PRE_OPEN_BUFFER_SECONDS * 16000 * 2

    private fun writeToRollingBuffer(data: ByteArray, offset: Int, length: Int) {
        val size = rollingBuffer.size
        for (i in 0 until length) {
            val idx = ((totalRead + i) % size).toInt()
            rollingBuffer[idx] = data[offset + i]
        }
    }

    private fun readFromRollingBuffer(length: Int): ByteArray {
        val size = rollingBuffer.size
        val result = ByteArray(length)
        val startPos = totalRead - length
        for (i in 0 until length) {
            var index = ((startPos + i) % size).toInt()
            if (index < 0) index += size
            result[i] = rollingBuffer[index]
        }
        return result
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
                        val unconfirmed = (totalRead - audioConfirmedBytes).toInt()
                        val clampLength = minOf(unconfirmed, totalRead.toInt(), rollingBuffer.size)
                        if (clampLength > 0) {
                            VibeVoiceDebugLogger.log("Reconnected: flushing $clampLength bytes of unconfirmed audio")
                            val flushData = readFromRollingBuffer(clampLength)
                            webSocket.send(flushData.toByteString(0, clampLength))
                        }
                        isReconnecting = false
                        retryCount = 0
                    }

                    for (bytes in preOpenBuffer) {
                        webSocket.send(bytes)
                    }
                    preOpenBuffer.clear()
                    preOpenBufferSizeBytes = 0
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("text")) {
                        val resultText = json.getString("text")
                        val isFinal = json.optBoolean("is_final", false)
                        
                        if (json.has("dur")) {
                            val dur = json.optDouble("dur", 0.0)
                            audioConfirmedBytes = (dur * 32000).toLong()
                        }

                        VibeVoiceDebugLogger.log("WS msg text len: ${resultText.length}, final: $isFinal")
                        if (isFinal) {
                            if (resultText.isBlank()) {
                                VibeVoiceDebugLogger.log("[EMPTY_RESULT] onFinal received empty text")
                            }
                            
                            val isNewSegment = lastFullText.isNotEmpty() && !resultText.startsWith(lastFullText)
                            if (isNewSegment) {
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
                            val isNewSegment = lastFullText.isNotEmpty() && !resultText.startsWith(lastFullText)
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
        audioConfirmedBytes = 0L
        isWsOpen = false

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
                val record = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
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
                    for (i in 0 until numSamples) {
                        val b1 = buffer[2 * i].toInt() and 0xFF
                        val b2 = buffer[2 * i + 1].toInt() and 0xFF
                        val sample = ((b2 shl 8) or b1).toShort()
                        val sampleVal = sample.toLong()
                        sumOfSquares += sampleVal * sampleVal
                    }
                    totalSamples += numSamples
                    
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
                        val reason = "2s of consecutive zeros"
                        VibeVoiceDebugLogger.log("Dead microphone detected ($reason). Attempting recovery...")

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
                            VibeVoiceDebugLogger.log("Max recovery attempts reached. Stopping session.")
                            listener.onError("Microphone unavailable")
                            stopStreaming()
                            break
                        }
                    }

                    writeToRollingBuffer(buffer, 0, read)
                    totalRead += read
                    
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
                        VibeVoiceDebugLogger.log("AudioRecord read error: $read — stopping session")
                        listener.onError("Microphone read error: $read")
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
        ws?.send("END_STREAM")
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
    }
}
