package helium314.keyboard.latin.vibevoice

import android.content.Context
import android.os.Build
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

object VibeVoiceBugReporter {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private const val BUG_REPORT_URL = "https://vibevoice.net/api/v1/support/bug_report"
    private const val MAX_LOG_BYTES = 500 * 1024 // 500 KB cap

    fun gatherDiagnostics(context: Context): JSONObject {
        val runtime = Runtime.getRuntime()
        val freeMemMb = runtime.freeMemory() / (1024 * 1024)
        val totalMemMb = runtime.totalMemory() / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        val memoryInfo = "Heap Free: ${freeMemMb}MB / Total: ${totalMemMb}MB / Max: ${maxMemMb}MB"

        val logsBuilder = StringBuilder()
        
        // 1. Read persistent VibeVoice debug log if available
        try {
            val debugLogFile = File(context.filesDir, "vibevoice_debug.log")
            if (debugLogFile.exists()) {
                logsBuilder.append("=== VIBEVOICE DEBUG LOG ===\n")
                val text = debugLogFile.readText()
                if (text.length > MAX_LOG_BYTES) {
                    logsBuilder.append(text.takeLast(MAX_LOG_BYTES))
                } else {
                    logsBuilder.append(text)
                }
                logsBuilder.append("\n\n")
            }
        } catch (e: Exception) {
            logsBuilder.append("Failed to read vibevoice_debug.log: ${e.message}\n\n")
        }

        // 2. Read in-memory application log lines
        try {
            logsBuilder.append("=== IN-MEMORY APP LOGS ===\n")
            val internalLogs = Log.getLog(1000).joinToString("\n")
            logsBuilder.append(internalLogs)
        } catch (e: Exception) {
            logsBuilder.append("Failed to read in-memory logs: ${e.message}\n")
        }

        return JSONObject().apply {
            put("app_version", BuildConfig.VERSION_NAME)
            put("os_name", "Android")
            put("os_version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            put("architecture", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            put("desktop_env", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("session_type", "Input Method Service (IME)")
            put("memory_stats", memoryInfo)
            put("client_logs", logsBuilder.toString())
        }
    }

    suspend fun sendBugReport(context: Context, description: String): Result<Int> = withContext(Dispatchers.IO) {
        val apiKey = VibeVoiceClient.getApiKey(context)
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("No VibeVoice API key found. Please pair your account first."))
        }

        try {
            val payload = gatherDiagnostics(context).apply {
                put("description", description)
            }

            val requestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(BUG_REPORT_URL)
                .addHeader("X-API-Key", apiKey)
                .post(requestBody)
                .build()

            VibeVoiceClient.sharedHttpClient.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(responseStr)
                    val reportId = json.optInt("report_id", -1)
                    if (reportId != -1) {
                        Result.success(reportId)
                    } else {
                        Result.success(0)
                    }
                } else {
                    val errorMsg = try {
                        JSONObject(responseStr).optString("detail", response.message)
                    } catch (_: Exception) {
                        "Server error (${response.code})"
                    }
                    Result.failure(Exception(errorMsg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
