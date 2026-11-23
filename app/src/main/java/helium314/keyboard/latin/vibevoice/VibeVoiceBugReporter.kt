package helium314.keyboard.latin.vibevoice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.FoldableUtils
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

    /**
     * The state a report needs to explain a fault that is not in the dictation log: what the
     * screen is, how the keyboard is set up, what the account can do, where the app came from and
     * what it may use. Technical values only, no text the user typed.
     *
     * Written at the top of the client log rather than as fields of its own, because the server
     * stores the log whole and would drop fields it does not know. Before this, a layout fault on
     * a phone other than ours arrived as a model name and a guess.
     */
    private fun deviceSnapshot(context: Context, usage: JSONObject?): String {
        val sb = StringBuilder("=== DEVICE & SETTINGS ===\n")
        fun line(key: String, value: () -> Any?) {
            // Throwable, not Exception: a method missing on an old Android is a NoSuchMethodError,
            // and one field of a diagnostic must not take the settings screen down with it.
            val v = try { value() } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                "unavailable (${e.javaClass.simpleName})"
            }
            sb.append(key).append(": ").append(v).append('\n')
        }
        val res = context.resources
        val dm = res.displayMetrics
        val conf = res.configuration
        line("display") { "${dm.widthPixels}x${dm.heightPixels} px, ${dm.densityDpi} dpi (density ${dm.density})" }
        line("screen_dp") { "${conf.screenWidthDp}x${conf.screenHeightDp} dp, smallest ${conf.smallestScreenWidthDp} dp" }
        line("orientation") { if (conf.orientation == Configuration.ORIENTATION_LANDSCAPE) "landscape" else "portrait" }
        line("font_scale") { conf.fontScale }
        line("locale") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) conf.locales[0]?.toLanguageTag()
            else @Suppress("DEPRECATION") conf.locale?.toLanguageTag()
        }
        line("dark_mode") { (conf.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES }
        line("folded") { FoldableUtils.isFolded }

        val prefs = context.prefs()
        line("split_keyboard") {
            "portrait=${prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, Defaults.PREF_ENABLE_SPLIT_KEYBOARD)} " +
                "landscape=${prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE, Defaults.PREF_ENABLE_SPLIT_KEYBOARD)} " +
                "folded=${prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_FOLDED, Defaults.PREF_ENABLE_SPLIT_KEYBOARD)}"
        }
        line("theme") { "${prefs.getString(Settings.PREF_THEME_STYLE, null) ?: "default"} / ${prefs.getString(Settings.PREF_THEME_COLORS, null) ?: "default"}" }
        // Only once the keyboard has loaded its settings in this process; a report sent from the
        // settings screen before the keyboard ever opened has none, and says so.
        line("keyboard") {
            val v = Settings.getValues()
            "height=${v.mKeyboardHeightScale} split_active=${v.mIsSplitKeyboardEnabled} one_handed=${v.mOneHandedModeEnabled} " +
                "number_row=${v.mShowsNumberRow} font=${v.mFontSizeMultiplier} side_padding=${v.mSidePaddingScale} " +
                "bottom_padding=${v.mBottomPaddingScale}"
        }

        line("account") {
            when {
                VibeVoiceClient.isLinked(context) -> "linked"
                VibeVoiceClient.getApiKey(context) != null -> if (VibeVoiceClient.isTrialSpent(context)) "trial, spent" else "trial"
                else -> "no key"
            }
        }
        // The server's own answer, so the numbers are the ones it bills by. Anything that could
        // name the person is left out; the report carries the account already.
        line("usage") {
            usage?.let { u ->
                val safe = JSONObject()
                for (k in u.keys()) if (!Regex("mail|name|phone", RegexOption.IGNORE_CASE).containsMatchIn(k)) safe.put(k, u.get(k))
                safe.toString()
            } ?: "not available"
        }

        line("installer") {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION") pm.getInstallerPackageName(context.packageName)
            } ?: "unknown (sideloaded?)"
        }
        line("app_build") { "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})" }
        // Runtime permissions and the overlay check exist from Android 6; before that, install-time
        // grants mean both are simply held.
        val m = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        line("perm_microphone") {
            if (m) context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            else "granted at install"
        }
        line("perm_overlay") { if (m) android.provider.Settings.canDrawOverlays(context) else "granted at install" }
        line("perm_notifications") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            else "not required"
        }
        line("default_ime") {
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.startsWith(context.packageName + "/") == true
        }
        return sb.append('\n').toString()
    }

    fun gatherDiagnostics(context: Context, usage: JSONObject? = null): JSONObject {
        val runtime = Runtime.getRuntime()
        val freeMemMb = runtime.freeMemory() / (1024 * 1024)
        val totalMemMb = runtime.totalMemory() / (1024 * 1024)
        val maxMemMb = runtime.maxMemory() / (1024 * 1024)

        val memoryInfo = "Heap Free: ${freeMemMb}MB / Total: ${totalMemMb}MB / Max: ${maxMemMb}MB"

        val logsBuilder = StringBuilder(deviceSnapshot(context, usage))

        // 1. Read persistent VibeVoice debug log if available
        try {
            // Asked of the logger rather than assumed: a debug build keeps the log on the shared
            // volume so a file manager can reach it, and a report that read the old path would have
            // come in with no log at all.
            val debugLogFile = VibeVoiceDebugLogger.currentFile() ?: File(context.filesDir, "vibevoice_debug.log")
            if (debugLogFile.exists()) {
                logsBuilder.append("=== VIBEVOICE DEBUG LOG ===\n")
                val fileLength = debugLogFile.length()
                if (fileLength > MAX_LOG_BYTES) {
                    debugLogFile.inputStream().use { stream ->
                        // Seek, not skip. skip() is allowed to cover less than asked and to return
                        // zero at a buffer boundary on a perfectly healthy file, and the loop that
                        // drove it gave up on that zero -- sending the whole megabyte to a server
                        // that answers a body that size with 413, so the report vanished quietly.
                        stream.channel.position(fileLength - MAX_LOG_BYTES)
                        logsBuilder.append(stream.readBytes().decodeToString())
                    }
                } else {
                    logsBuilder.append(debugLogFile.readText())
                }
                logsBuilder.append("\n\n")
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
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
            // Best effort: a report must still go out when the usage call fails.
            val usage = try { VibeVoiceClient.fetchQuota(apiKey) } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
            val payload = gatherDiagnostics(context, usage).apply {
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
