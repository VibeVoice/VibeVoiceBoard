package helium314.keyboard.latin.vibevoice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
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
                        if (isFinal) {
                            listener.onFinal(resultText)
                        } else {
                            listener.onPartial(resultText)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isStreaming = false
                listener.onError(t.message ?: "WebSocket Error")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isStreaming = false
                listener.onClosed()
            }
        })

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 4

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        audioJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            while (isActive && isStreaming) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    webSocket?.send(buffer.copyOfRange(0, read).toByteString())
                }
            }
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
