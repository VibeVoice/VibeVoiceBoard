package helium314.keyboard.latin.vibevoice

import android.content.Context
import helium314.keyboard.latin.BuildConfig

/**
 * One dictation, whichever wire protocol carries it.
 *
 * The keyboard, the waves, the floating mark and the foreground service only ever start, stop and
 * cancel a session and read its level and link state. They hold this type, so the protocol is
 * chosen in one place ([create]) and nothing else changes with it.
 */
interface VoiceSession {
    /** RMS of the most recent audio, 0..1, polled by the animations once per frame. */
    val currentLevel: Float
    /** Whether the link is currently too poor to stream into. */
    val isLinkDegraded: Boolean
    fun startStreaming()
    fun stopStreaming()
    fun cancel()

    companion object {
        /** Preference holding `v1` or `v2`; absent means the build's default. */
        const val PREF_STREAM_PROTOCOL = "vibevoice_stream_protocol"

        /**
         * v2 is the default in every build since 1.0.4; the preference can still force either.
         * A v2 endpoint that does not exist (HTTP 400/404/405/426) or refuses our start frame
         * turns v2 off for the rest of the process (see [VibeVoiceStreamV2.v2Unavailable]), so
         * the next dictation works on v1.
         */
        @JvmStatic
        fun useV2(context: Context): Boolean {
            if (VibeVoiceStreamV2.v2Unavailable) return false
            val pref = VibeVoiceClient.vibeVoicePrefs(context).getString(PREF_STREAM_PROTOCOL, null)
            return when (pref) {
                "v1" -> false
                "v2" -> true
                else -> true
            }
        }

        @JvmStatic
        fun create(context: Context, apiKey: String, listener: VibeVoiceListener): VoiceSession {
            val v2 = useV2(context)
            VibeVoiceDebugLogger.log("Session protocol: ${if (v2) "v2" else "v1"}")
            return if (v2) VibeVoiceStreamV2(apiKey, BuildConfig.VERSION_NAME, listener)
            else VibeVoiceClient(apiKey, listener)
        }
    }
}
