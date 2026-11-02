package helium314.keyboard.latin.vibevoice

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString

/**
 * One dictation over streaming protocol v2 (`wss://vibevoice.net/api/v2/stream`).
 *
 * What v1 had to guess, v2 says in a frame, and this class reads it instead of guessing:
 * - how much audio the server holds: `progress.received_ms`, the point a resume sends from;
 * - up to where every word has been delivered: `ack_ms`, the point a new stream sends from;
 * - whether the link is alive: a `progress` every second, so three silent seconds mean dead
 *   (v1 learned it from TCP, which on a phone can take a minute -- bug report #10866);
 * - whether a reconnect kept the session: `ready.resume.resumed`, with missed segments replayed;
 * - whether the dictation is complete: the `end` frame.
 *
 * Segments are final text. Each is handed to the keyboard as a piece of its own, the way v1's
 * numbered pieces are, so text insertion in LatinIME is the same for both protocols.
 *
 * Kept from v1: the 30 s rolling buffer (audio is sent from it after any reconnect), keeping the
 * audio through a slow or failed first connection, and the microphone recovery ([AudioCapture]).
 */
class VibeVoiceStreamV2(
    private val apiKey: String,
    private val clientVersion: String,
    private val listener: VibeVoiceListener,
    private val url: String = StreamV2Wire.URL,
    private val newCapture: (CoroutineScope, (ByteArray, Int) -> Unit, (String) -> Unit) -> AudioSource =
        { scope, onAudio, onLost -> AudioCapture(scope, onAudio, onLost) },
) : VoiceSession {
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)
    private val lock = Any()

    @Volatile private var capture: AudioSource? = null
    override val currentLevel: Float get() = capture?.currentLevel ?: 0f
    @Volatile override var isLinkDegraded = false
        private set

    // Capture, in bytes since the session's first sample, and the last 30 s of it.
    private val ring = ByteArray(RING_BYTES)
    private var totalRead = 0L

    // The socket. Each connection gets a generation; callbacks of a replaced socket are ignored.
    private var ws: WebSocket? = null
    private var gen = 0
    /** The current socket has had `ready`: audio goes out live. */
    private var ready = false
    private var everReady = false

    // The server's stream.
    private var token: String? = null
    private val appliedIdx = HashSet<Int>()
    private var lastAppliedIdx = 0
    /** Capture position of the stream clock's 0, the first byte the current server stream received. */
    private var streamOriginPos = -1L
    /** `ack_ms` on the capture: everything spoken before it has arrived as text. */
    private var ackedPos = -1L
    /** `end` went out on the current server stream. */
    private var endSent = false

    // The session.
    @Volatile private var streaming = false
    @Volatile private var stopRequested = false
    @Volatile private var endReceived = false
    private val finished = AtomicBoolean(false)
    private var segmentsApplied = 0
    private var lastSegmentText = ""
    private var trimOverlapNext = false
    private var startedAt = 0L
    private var stoppedAt = 0L
    private var lastSegmentAt = 0L
    private var lastProgressAt = 0L
    private var connectStartedAt = 0L
    /** When the current outage began; 0 while the link is up. The first connection counts as one. */
    private var outageStartedAt = 0L
    private var retry = 0
    private var reconnectJob: Job? = null
    private var watchdogJob: Job? = null

    override fun startStreaming() {
        if (streaming || finished.get()) return
        streaming = true
        val now = SystemClock.elapsedRealtime()
        startedAt = now
        lastSegmentAt = now
        outageStartedAt = now
        val cap = newCapture(scope, ::onAudio, ::onMicLost)
        capture = cap
        if (!cap.start()) {
            streaming = false
            finished.set(true)
            listener.onError("Microphone unavailable")
            return
        }
        VibeVoiceDebugLogger.log("[V2] session start")
        connect()
        watchdogJob = scope.launch { watchdog() }
    }

    override fun stopStreaming() {
        if (!streaming) return
        streaming = false
        // The time first, then the flag, both under the lock the watchdog reads them under: a
        // watchdog tick that saw the flag with stoppedAt still 0 took the stop for ages old and
        // finished the session at once, dropping the text the server was about to send.
        synchronized(lock) {
            stoppedAt = SystemClock.elapsedRealtime()
            stopRequested = true
        }
        capture?.stop()
        synchronized(lock) {
            VibeVoiceDebugLogger.log("[V2] stop, captured ${totalRead / BYTES_PER_MS}ms")
            if (ready) sendEnd() else VibeVoiceDebugLogger.log("[V2] not ready, end deferred until ready")
        }
    }

    override fun cancel() {
        streaming = false
        finished.set(true)
        capture?.stop()
        synchronized(lock) {
            gen++
            val old = ws
            ws = null
            quietly { old?.cancel() }
        }
        scopeJob.cancel()
    }

    // ---------------------------------------------------------------- audio

    private fun onAudio(buf: ByteArray, n: Int) = synchronized(lock) {
        for (i in 0 until n) ring[((totalRead + i) % RING_BYTES).toInt()] = buf[i]
        totalRead += n
        if (ready) ws?.send(buf.toByteString(0, n))
    }

    private fun onMicLost(code: String) {
        listener.onWarning(code)
        stopStreaming()
    }

    /** Sends the captured audio from [from] up to now, as much of it as the ring still holds. */
    private fun sendBuffered(socket: WebSocket, from: Long) {
        val oldest = maxOf(0L, totalRead - RING_BYTES)
        var pos = from
        if (pos < oldest) {
            VibeVoiceDebugLogger.log("[V2] [RESEND_SHORT] ${(oldest - pos) / BYTES_PER_MS}ms no longer in the buffer")
            pos = oldest
        }
        val total = totalRead - pos
        while (pos < totalRead) {
            val len = minOf(CHUNK_BYTES.toLong(), totalRead - pos).toInt()
            val chunk = ByteArray(len)
            for (i in 0 until len) chunk[i] = ring[((pos + i) % RING_BYTES).toInt()]
            socket.send(chunk.toByteString())
            pos += len
        }
        if (total > 0) VibeVoiceDebugLogger.log("[V2] sent ${total / BYTES_PER_MS}ms from the buffer")
    }

    private fun sendEnd() {
        val socket = ws ?: return
        if (endSent) return
        socket.send(StreamV2Wire.END)
        endSent = true
        VibeVoiceDebugLogger.log("[V2] end sent")
    }

    // ---------------------------------------------------------------- connection

    private fun connect() = synchronized(lock) {
        if (finished.get()) return@synchronized
        val g = ++gen
        ready = false
        connectStartedAt = SystemClock.elapsedRealtime()
        VibeVoiceDebugLogger.log("[V2] connecting (attempt ${retry + 1}, resume=${token != null})")
        ws = VibeVoiceClient.sharedHttpClient.newWebSocket(Request.Builder().url(url).build(), Listener(g))
    }

    /** Drops the socket and tries again after a backoff, inside the outage window. */
    private fun linkLost(why: String) {
        synchronized(lock) {
            if (finished.get()) return
            VibeVoiceDebugLogger.log("[V2] link lost: $why")
            dropSocket()
            if (outageStartedAt == 0L) outageStartedAt = SystemClock.elapsedRealtime()
        }
        scheduleReconnect(BACKOFF_MS[minOf(retry, BACKOFF_MS.size - 1)])
    }

    private fun dropSocket() {
        gen++
        ready = false
        val old = ws
        ws = null
        quietly { old?.cancel() }
    }

    /**
     * Cancelling or closing a socket that has already failed can throw inside OkHttp (its call is
     * gone). Thrown from a socket callback, OkHttp swallows it, and whatever came after -- the
     * reconnect -- silently never happened.
     */
    private inline fun quietly(block: () -> Unit) {
        try { block() } catch (e: Exception) { VibeVoiceDebugLogger.log("[V2] ignored on a dead socket: ${e.javaClass.simpleName}") }
    }

    private fun scheduleReconnect(delayMs: Long) {
        reconnectJob?.cancel()
        VibeVoiceDebugLogger.log("[V2] reconnect in ${delayMs}ms")
        reconnectJob = scope.launch {
            delay(delayMs)
            if (finished.get()) return@launch
            retry++
            connect()
        }
    }

    private suspend fun watchdog() {
        while (scope.isActive && !finished.get()) {
            delay(WATCHDOG_MS)
            val now = SystemClock.elapsedRealtime()
            var lost: String? = null
            var giveUp = false
            var degraded: Boolean
            synchronized(lock) {
                if (ws != null && ready && now - lastProgressAt > PROGRESS_DEAD_MS) {
                    lost = "no progress for ${now - lastProgressAt}ms"
                } else if (ws != null && !ready && now - connectStartedAt > HANDSHAKE_RETRY_MS) {
                    lost = "no ready after ${now - connectStartedAt}ms"
                }
                val outage = if (outageStartedAt > 0) now - outageStartedAt else 0L
                degraded = outage > if (everReady) DEGRADED_AFTER_MS else HANDSHAKE_DEGRADED_MS
                if (outage > OUTAGE_WINDOW_MS) giveUp = true
            }
            if (degraded != isLinkDegraded) {
                isLinkDegraded = degraded
                listener.onLinkQualityChanged(degraded)
            }
            val stopTimedOut = synchronized(lock) {
                stopRequested && !endReceived && stoppedAt > 0 &&
                    (now - maxOf(stoppedAt, lastProgressAt) > STOP_WAIT_MS || now - stoppedAt > STOP_HARD_LIMIT_MS)
            }
            if (stopTimedOut) {
                VibeVoiceDebugLogger.log("[V2] no end ${now - stoppedAt}ms after the stop")
                finish()
                return
            }
            if (giveUp) {
                VibeVoiceDebugLogger.log("[V2] outage longer than ${OUTAGE_WINDOW_MS}ms, giving up")
                if (stopRequested) finish() else fail(VibeVoiceClient.ERR_LINK_LOST)
                return
            }
            lost?.let { linkLost(it) }
        }
    }

    // ---------------------------------------------------------------- frames

    private fun onReady(f: StreamV2Wire.Ready) = synchronized(lock) {
        val socket = ws ?: return@synchronized
        val now = SystemClock.elapsedRealtime()
        everReady = true
        outageStartedAt = 0L
        retry = 0
        lastProgressAt = now
        token = f.token
        val from: Long
        if (f.resumed && streamOriginPos >= 0) {
            from = streamOriginPos + f.receivedMs * BYTES_PER_MS
            VibeVoiceDebugLogger.log("[V2] ready, resumed, server holds ${f.receivedMs}ms, session ${f.session}")
        } else {
            // A new server stream, its clock at 0. The first one starts at the first sample; a
            // later one from the last ack, so nothing the old stream never delivered is lost and
            // the few words that may repeat are trimmed off its first segment.
            val oldest = maxOf(0L, totalRead - RING_BYTES)
            from = when {
                streamOriginPos < 0 -> oldest
                ackedPos >= 0 -> maxOf(ackedPos, oldest)
                else -> maxOf(streamOriginPos, oldest)
            }
            if (streamOriginPos >= 0) trimOverlapNext = segmentsApplied > 0
            streamOriginPos = from
            appliedIdx.clear()
            lastAppliedIdx = 0
            endSent = false
            VibeVoiceDebugLogger.log("[V2] ready, new stream from ${from / BYTES_PER_MS}ms, session ${f.session}, language ${f.language}")
        }
        // Resumed after the end went out: the server has it and replays what is missing, then ends.
        // Sending audio or another end would be a protocol error (doc 48, a drop right after end).
        if (!(stopRequested && endSent)) sendBuffered(socket, minOf(from, totalRead))
        ready = true
        if (stopRequested) sendEnd()
    }

    private fun onSegment(f: StreamV2Wire.Segment) {
        var shown: String
        val isNew: Boolean
        synchronized(lock) {
            if (!appliedIdx.add(f.idx)) {
                VibeVoiceDebugLogger.log("[V2] segment idx=${f.idx} already applied, ignored")
                return
            }
            if (f.idx > lastAppliedIdx + 1) VibeVoiceDebugLogger.log("[V2] [FRAME_GAP] idx ${lastAppliedIdx} -> ${f.idx}")
            lastAppliedIdx = maxOf(lastAppliedIdx, f.idx)
            if (f.ackMs >= 0 && streamOriginPos >= 0) ackedPos = streamOriginPos + f.ackMs * BYTES_PER_MS
            lastSegmentAt = SystemClock.elapsedRealtime()
            shown = f.text.trim()
            if (trimOverlapNext) {
                trimOverlapNext = false
                shown = VibeVoiceClient.trimLeadingOverlap(lastSegmentText, shown)
            }
            lastSegmentText = f.text
            VibeVoiceDebugLogger.log("[V2] segment idx=${f.idx} len=${f.text.length} ack=${f.ackMs}ms")
            if (shown.isBlank()) return
            isNew = segmentsApplied > 0
            segmentsApplied++
        }
        listener.onPartial(shown, isNew)
    }

    private fun onProgress(f: StreamV2Wire.Progress) = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        val gap = now - lastProgressAt
        if (gap > 1500) VibeVoiceDebugLogger.log("[V2] progress after ${gap}ms gap: received=${f.receivedMs} ack=${f.ackMs}")
        lastProgressAt = now
        if (f.ackMs >= 0 && streamOriginPos >= 0) ackedPos = streamOriginPos + f.ackMs * BYTES_PER_MS
    }

    private fun onServerError(f: StreamV2Wire.Error) {
        VibeVoiceDebugLogger.log("[V2] error ${f.code} retryable=${f.retryable}: ${f.message}")
        when {
            f.retryable -> {
                synchronized(lock) {
                    dropSocket()
                    if (outageStartedAt == 0L) outageStartedAt = SystemClock.elapsedRealtime()
                }
                scheduleReconnect(f.retryAfterMs ?: BACKOFF_MS[minOf(retry, BACKOFF_MS.size - 1)])
            }
            StreamV2Wire.meansNoV2(f.code) -> {
                v2Unavailable = true
                fail("v2 refused (${f.code}), next dictation uses v1")
            }
            f.code == "trial_exhausted" -> fail(VibeVoiceClient.ERR_TRIAL_EXHAUSTED)
            else -> fail(f.message.ifBlank { f.code })
        }
    }

    private fun onRestarting(f: StreamV2Wire.Restarting) {
        VibeVoiceDebugLogger.log("[V2] server restarting, new stream in ${f.reconnectAfterMs}ms")
        synchronized(lock) {
            token = null // a session does not survive a restart
            dropSocket()
            if (outageStartedAt == 0L) outageStartedAt = SystemClock.elapsedRealtime()
        }
        scheduleReconnect(f.reconnectAfterMs)
    }

    private fun onEnd(f: StreamV2Wire.End) {
        VibeVoiceDebugLogger.log("[V2] end, last_idx=${f.lastIdx} applied=$lastAppliedIdx billed=${f.billedMs}ms")
        endReceived = true
        finish()
    }

    // ---------------------------------------------------------------- ending

    /** The session is over: the `end` frame came, or waiting for it ran out after a stop. */
    private fun finish() {
        if (!finished.compareAndSet(false, true)) return
        teardown()
        if (stopRequested && !endReceived) {
            val unanswered = (stoppedAt - lastSegmentAt).coerceAtLeast(0L)
            VibeVoiceDebugLogger.log("[V2] [TAIL_MISSING] ended without end frame; ${unanswered}ms before the stop unanswered")
            listener.onTailMissing(unanswered)
        }
        if (segmentsApplied == 0) VibeVoiceDebugLogger.log("[V2] [EMPTY_RESULT] no segment in this session")
        listener.onClosed()
    }

    /** The session cannot go on. After a stop it is not an error: whatever arrived is kept. */
    private fun fail(error: String) {
        if (stopRequested) {
            finish()
            return
        }
        if (!finished.compareAndSet(false, true)) return
        teardown()
        listener.onError(error)
    }

    private fun teardown() {
        streaming = false
        capture?.stop()
        reconnectJob?.cancel()
        watchdogJob?.cancel()
        synchronized(lock) {
            gen++
            ready = false
            val old = ws
            ws = null
            quietly { old?.close(1000, "done") }
        }
        if (isLinkDegraded) {
            isLinkDegraded = false
            listener.onLinkQualityChanged(false)
        }
    }

    private inner class Listener(private val g: Int) : WebSocketListener() {
        private fun current() = synchronized(lock) { g == gen } && !finished.get()

        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                if (g != gen) return
                val resume = token?.let { StreamV2Wire.Resume(it, lastAppliedIdx, RESUME_TTL_S) }
                webSocket.send(StreamV2Wire.start(apiKey, clientVersion, resume))
            }
            VibeVoiceDebugLogger.log("WS Open [V2]")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!current()) return
            val f = try { StreamV2Wire.parse(text) } catch (e: Exception) {
                VibeVoiceDebugLogger.log("[V2] unparsable frame: ${e.message}")
                return
            }
            when (f) {
                is StreamV2Wire.Ready -> onReady(f)
                is StreamV2Wire.Segment -> onSegment(f)
                is StreamV2Wire.Progress -> onProgress(f)
                is StreamV2Wire.Flushed -> VibeVoiceDebugLogger.log("[V2] flushed ack=${f.ackMs}")
                is StreamV2Wire.Warning -> VibeVoiceDebugLogger.log("[V2] warning ${f.code}: ${f.message}")
                is StreamV2Wire.Error -> onServerError(f)
                is StreamV2Wire.Restarting -> onRestarting(f)
                is StreamV2Wire.End -> onEnd(f)
                StreamV2Wire.Pong -> Unit
                is StreamV2Wire.Unknown -> VibeVoiceDebugLogger.log("[V2] unknown frame type '${f.type}'")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!current()) return
            VibeVoiceDebugLogger.log("WS Failure [V2]: ${t.javaClass.simpleName} ${t.message}, http=${response?.code}")
            val code = response?.code
            // The upgrade was answered with an HTTP status: this server has no v2 here. A network
            // failure is the same on both protocols and is retried like any other.
            if (!everReady && code != null && code != 429 && code in 400..599 && code != 503) {
                v2Unavailable = true
                fail("v2 endpoint answered HTTP $code, next dictation uses v1")
                return
            }
            linkLost("failure: ${t.message}")
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!current()) return
            VibeVoiceDebugLogger.log("WS Closed [V2]: $code / $reason")
            when {
                code in 4000..4004 -> fail("closed by server ($code $reason)")
                else -> linkLost("closed $code")
            }
        }
    }

    companion object {
        /** Set when the v2 endpoint does not exist or refuses our start frame; v1 for the rest of the process. */
        @Volatile @JvmStatic var v2Unavailable = false

        private const val BYTES_PER_MS = 32L
        private const val RING_BYTES = 30 * 32000
        private const val CHUNK_BYTES = 3200
        private const val RESUME_TTL_S = 120
        private const val WATCHDOG_MS = 250L
        /** Doc 48: no progress for 3 s means the link is dead. */
        private const val PROGRESS_DEAD_MS = 3_000L
        private const val HANDSHAKE_RETRY_MS = 10_000L
        private const val DEGRADED_AFTER_MS = 2_000L
        private const val HANDSHAKE_DEGRADED_MS = 8_000L
        /** The rolling buffer's length: past this, unsent speech is being overwritten. */
        private const val OUTAGE_WINDOW_MS = 30_000L
        private const val STOP_WAIT_MS = 15_000L
        private const val STOP_HARD_LIMIT_MS = 60_000L
        private val BACKOFF_MS = longArrayOf(500, 1000, 2000, 3000)
    }
}
