package helium314.keyboard.latin.vibevoice

import org.json.JSONObject

/**
 * The v2 streaming protocol on the wire, and nothing else: building what the keyboard sends,
 * parsing what the server sends. No socket, no Android, so the grammar is tested on its own.
 *
 * The contract is `codebase_docs/48-streaming-protocol-v2.md` in the server repository, and where
 * that document and `server_side/modules/stream_v2_wire.py` disagree, the code is right.
 * Unknown fields are ignored and an unknown `type` parses to [Unknown], as the contract asks.
 */
object StreamV2Wire {
    const val URL = "wss://vibevoice.net/api/v2/stream"

    data class Resume(val token: String, val lastIdx: Int, val ttlS: Int)

    fun start(apiKey: String, clientVersion: String, resume: Resume?): String {
        val o = JSONObject()
        o.put("type", "start") // first key, as the protocol requires
        o.put("api_key", apiKey)
        o.put("client", JSONObject().put("name", "vibevoice-board").put("version", clientVersion.take(64)))
        o.put("audio", JSONObject().put("encoding", "pcm_s16le").put("sample_rate", 16000).put("channels", 1))
        if (resume != null) {
            o.put("resume", JSONObject().put("token", resume.token).put("last_idx", resume.lastIdx).put("ttl_s", resume.ttlS))
        }
        return o.toString()
    }

    const val END = """{"type":"end"}"""
    const val FLUSH = """{"type":"flush"}"""

    sealed class Frame
    data class Ready(
        val session: String?,
        val token: String?,
        val resumed: Boolean,
        val receivedMs: Long,
        val language: String?,
        val minutesRemaining: Double?,
    ) : Frame()
    data class Segment(val idx: Int, val text: String, val ackMs: Long) : Frame()
    data class Flushed(val ackMs: Long) : Frame()
    data class Progress(val receivedMs: Long, val ackMs: Long) : Frame()
    data class Warning(val code: String, val message: String) : Frame()
    data class Error(val code: String, val message: String, val retryable: Boolean, val retryAfterMs: Long?) : Frame()
    data class Restarting(val reconnectAfterMs: Long) : Frame()
    data class End(val lastIdx: Int, val billedMs: Long) : Frame()
    object Pong : Frame()
    data class Unknown(val type: String) : Frame()

    /** Throws on text that is not a JSON object; the caller logs and ignores it. */
    fun parse(text: String): Frame {
        val o = JSONObject(text)
        return when (val type = o.optString("type")) {
            "ready" -> {
                val r = o.optJSONObject("resume")
                Ready(
                    session = o.optStringOrNull("session"),
                    token = r?.optStringOrNull("token"),
                    resumed = r?.optBoolean("resumed", false) ?: false,
                    receivedMs = r?.optLong("received_ms", 0L) ?: 0L,
                    language = o.optJSONObject("language")?.optStringOrNull("value"),
                    minutesRemaining = o.optJSONObject("quota")?.let { q ->
                        if (q.has("minutes_remaining")) q.optDouble("minutes_remaining") else null
                    },
                )
            }
            "segment" -> Segment(o.getInt("idx"), o.optString("text"), o.optLong("ack_ms", -1L))
            "flushed" -> Flushed(o.optLong("ack_ms", -1L))
            "progress" -> Progress(o.optLong("received_ms", -1L), o.optLong("ack_ms", -1L))
            "warning" -> Warning(o.optString("code"), o.optString("message"))
            "error" -> Error(
                o.optString("code"),
                o.optString("message"),
                o.optBoolean("retryable", false),
                if (o.has("retry_after_ms")) o.optLong("retry_after_ms") else null,
            )
            "restarting" -> Restarting(o.optLong("reconnect_after_ms", 4000L))
            "end" -> End(o.optInt("last_idx", 0), o.optLong("billed_ms", 0L))
            "pong" -> Pong
            else -> Unknown(type)
        }
    }

    /** Close codes 4000-4029, for an error frame that was lost before its close arrived. */
    fun retryableClose(code: Int): Boolean = code == 4010 || code == 4011 || code == 4029

    /** Codes that mean this server does not speak our v2 at all; the keyboard falls back to v1. */
    fun meansNoV2(code: String): Boolean = code == "bad_start" || code == "unknown_message"

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null
}
