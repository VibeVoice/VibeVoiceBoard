package helium314.keyboard.latin.vibevoice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(error: String)
    fun onClosed()
}

class VibeVoiceClient(
    private val apiKey: String,
    private val listener: VibeVoiceListener
) {
    private val okHttpClient = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var isStreaming = false
    private var audioRecord: AudioRecord? = null
    private var audioJob: Job? = null
    private var totalRead = 0L
    private val scope = CoroutineScope(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    fun startStreaming() {
        if (isStreaming) return
        isStreaming = true

        val request = Request.Builder()
            .url("wss://vibevoice.net/stream")
            .build()
        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                VibeVoiceDebugLogger.log("WS Open")
                // Send API key as first message
                val authJson = JSONObject().put("api_key", apiKey).toString()
                webSocket.send(authJson)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("text")) {
                        val resultText = json.getString("text")
                        val isFinal = json.optBoolean("is_final", false)
                        VibeVoiceDebugLogger.log("WS msg text len: ${resultText.length}, final: $isFinal")
                        if (isFinal) {
                            listener.onFinal(resultText)
                        } else {
                            listener.onPartial(resultText)
                        }
                    } else {
                         VibeVoiceDebugLogger.log("WS msg no text: $text")
                    }
                } catch (e: Exception) {
                    VibeVoiceDebugLogger.log("WS msg parse error: ${e.message}")
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isStreaming = false
                VibeVoiceDebugLogger.log("WS Failure: ${t.message}")
                listener.onError(t.message ?: "WebSocket Error")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isStreaming = false
                VibeVoiceDebugLogger.log("WS Closed: $code / $reason")
                listener.onClosed()
            }
        })

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 4

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        Log.d("VibeVoiceClient", "AudioRecord state: ${audioRecord?.state}, bufferSize: $bufferSize")
        audioRecord?.startRecording()
        Log.d("VibeVoiceClient", "AudioRecord recordingState: ${audioRecord?.recordingState}")
        totalRead = 0L // Reset for new session
        audioJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            while (isActive && isStreaming) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    totalRead += read
                    webSocket?.send(buffer.copyOfRange(0, read).toByteString())
                    if (totalRead % (bufferSize * 10) == 0L) { // Periodic log
                         Log.d("VibeVoiceClient", "Total bytes read: $totalRead")
                         VibeVoiceDebugLogger.log("Audio KB read: ${totalRead / 1024}")
                    }
                } else if (read < 0) {
                     Log.e("VibeVoiceClient", "AudioRecord read error: $read")
                }
            }
            Log.d("VibeVoiceClient", "Exit recording loop. Final total bytes: $totalRead")
        }
    }

    fun stopStreaming() {
        if (!isStreaming) return
        isStreaming = false
        audioJob?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        webSocket?.send("END_STREAM")
        // Close later after receiving finals or just close now
        scope.launch {
            VibeVoiceDebugLogger.log("Closing WS in 1s. Total bytes read: $totalRead")
            delay(1000)
            webSocket?.close(1000, "Done")
            webSocket = null
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        suspend fun requestDeviceCode(deviceName: String, clientVersion: String): JSONObject? = withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val body = JSONObject()
                .put("device_name", deviceName)
                .put("client_version", clientVersion)
                .toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("https://vibevoice.net/api/oauth/device/code")
                .post(body)
                .build()
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.string()?.let { JSONObject(it) }
                } else null
            } catch (e: Exception) {
                null
            }
        }

        suspend fun pollForToken(deviceCode: String): JSONObject? = withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val body = JSONObject()
                .put("device_code", deviceCode)
                .toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("https://vibevoice.net/api/oauth/device/token")
                .post(body)
                .build()
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.string()?.let { JSONObject(it) }
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
