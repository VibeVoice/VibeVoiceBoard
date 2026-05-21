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

    @SuppressLint("MissingPermission")
    fun startStreaming() {
        if (isStreaming) return
        isStreaming = true
        closureJob?.cancel()
        closureJob = null

        val preOpenBuffer = ArrayDeque<okio.ByteString>()
        var preOpenBufferSizeBytes = 0
        val maxPreOpenBufferBytes = MAX_PRE_OPEN_BUFFER_SECONDS * 16000 * 2
        var isWsOpen = false

        val request = Request.Builder()
            .url("wss://vibevoice.net/stream")
            .build()
        webSocket = sharedHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                VibeVoiceDebugLogger.log("WS Open")
                // Send API key as first message
                val authJson = JSONObject().put("api_key", apiKey).toString()
                webSocket.send(authJson)
                
                synchronized(preOpenBuffer) {
                    isWsOpen = true
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
                                VibeVoiceDebugLogger.log("Closing WS immediately after final result")
                                closureJob?.cancel()
                                webSocket.close(1000, "Done after Final")
                            }
                        } else {
                            val isNewSegment = lastFullText.isNotEmpty() && !resultText.startsWith(lastFullText)
                            if (isNewSegment) {
                                VibeVoiceDebugLogger.log("New segment detected onPartial. Prev: '${lastFullText.take(20)}...', New: '${resultText.take(20)}...'")
                            }
                            lastFullText = resultText
                            
                            listener.onPartial(resultText, isNewSegment)
                            if (!isStreaming) {
                                VibeVoiceDebugLogger.log("Shortening timeout after partial result")
                                closureJob?.cancel()
                                closureJob = scope.launch {
                                    delay(500)
                                    webSocket.close(1000, "Done after Flush")
                                }
                            }
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
                isStreaming = false
                cleanupAudioCapture()
                closureJob?.cancel()
                closureJob = null
                this@VibeVoiceClient.webSocket = null
                VibeVoiceDebugLogger.log("WS Failure: ${t.message}")
                listener.onError(t.message ?: "WebSocket Error")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isStreaming = false
                cleanupAudioCapture()
                closureJob?.cancel()
                closureJob = null
                this@VibeVoiceClient.webSocket = null
                VibeVoiceDebugLogger.log("WS Closed: $code / $reason")
                listener.onClosed()
            }
        })

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

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        Log.d("VibeVoiceClient", "AudioRecord state: ${audioRecord?.state}, bufferSize: $bufferSize")
        try {
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("not initialized, state=${audioRecord?.state}")
            }
            audioRecord?.startRecording()
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("recordingState=${audioRecord?.recordingState}")
            }
        } catch (e: Exception) {
            VibeVoiceDebugLogger.log("AudioRecord.startRecording failed: ${e.message}")
            cleanupAudioCapture()
            listener.onError("Microphone unavailable: ${e.message}")
            isStreaming = false
            return
        }
        Log.d("VibeVoiceClient", "AudioRecord recordingState: ${audioRecord?.recordingState}")
        totalRead = 0L // Reset for new session
        lastFullText = ""
        audioJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            while (isActive && isStreaming) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
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
                    if (totalRead % (bufferSize * 10) == 0L) { // Periodic log
                         Log.d("VibeVoiceClient", "Total bytes read: $totalRead")
                         VibeVoiceDebugLogger.log("Audio KB read: ${totalRead / 1024}")
                    }
                } else if (read == 0) {
                    delay(10)
                } else {
                    Log.e("VibeVoiceClient", "AudioRecord read error: $read")
                    VibeVoiceDebugLogger.log("AudioRecord read error: $read — stopping session")
                    listener.onError("Microphone read error: $read")
                    stopStreaming()
                    break
                }
            }
            Log.d("VibeVoiceClient", "Exit recording loop. Final total bytes: $totalRead")
        }
    }

    fun stopStreaming() {
        if (!isStreaming) return
        isStreaming = false
        cleanupAudioCapture()

        webSocket?.send("END_STREAM")
        // Close later after receiving finals or just close now
        closureJob = scope.launch {
            VibeVoiceDebugLogger.log("Closing WS in 1.5s timer started. Total bytes read: $totalRead")
            delay(1500)
            webSocket?.close(1000, "Done (timeout)")
            webSocket = null
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
        } catch (_: IllegalStateException) {
        }
        audioRecord?.release()
        audioRecord = null
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private const val MAX_PRE_OPEN_BUFFER_SECONDS = 5
        private const val VIBEVOICE_API_KEY_PREF = "vibevoice_api_key"
        private const val TAG = "VibeVoiceClient"

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
        } catch (_: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences unavailable — API key will be stored in cleartext")
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
