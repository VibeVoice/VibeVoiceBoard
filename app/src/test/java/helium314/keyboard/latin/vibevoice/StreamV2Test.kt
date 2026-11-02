package helium314.keyboard.latin.vibevoice

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StreamV2Test {

    // ---------------------------------------------------------------- wire

    @Test fun startFrameCarriesTypeFirstAndTheResume() {
        val s = StreamV2Wire.start("k", "1.0.4-dev", StreamV2Wire.Resume("tok", 7, 120))
        assertTrue(s.startsWith("{\"type\":\"start\""))
        val o = JSONObject(s)
        assertEquals("k", o.getString("api_key"))
        assertEquals("pcm_s16le", o.getJSONObject("audio").getString("encoding"))
        assertEquals(16000, o.getJSONObject("audio").getInt("sample_rate"))
        assertEquals("tok", o.getJSONObject("resume").getString("token"))
        assertEquals(7, o.getJSONObject("resume").getInt("last_idx"))
        assertFalse(JSONObject(StreamV2Wire.start("k", "v", null)).has("resume"))
    }

    @Test fun parsesTheExampleFramesOfDoc48() {
        val ready = StreamV2Wire.parse("""{"type":"ready","protocol":2,"session":"20261029_142233",
            "resume":{"token":"abc","resumed":false,"ttl_s":60,"received_ms":0},
            "language":{"value":"de","source":"session"},"vocabulary":{"accepted":3,"truncated":false},
            "features":["flush","resume","progress","vocabulary"],
            "limits":{"max_vocabulary_chars":400,"max_resume_ttl_s":120},"quota":{"minutes_remaining":142.5}}""")
        assertEquals(StreamV2Wire.Ready("20261029_142233", "abc", false, 0, "de", 142.5), ready)
        assertEquals(StreamV2Wire.Segment(4, "Mach das Licht im Wohnzimmer an.", 53870),
            StreamV2Wire.parse("""{"type":"segment","idx":4,"text":"Mach das Licht im Wohnzimmer an.","start_ms":51230,"end_ms":53870,"ack_ms":53870,"language":"de"}"""))
        assertEquals(StreamV2Wire.Progress(61200, 53870), StreamV2Wire.parse("""{"type":"progress","received_ms":61200,"ack_ms":53870}"""))
        assertEquals(StreamV2Wire.End(9, 84210), StreamV2Wire.parse("""{"type":"end","last_idx":9,"billed_ms":84210}"""))
        assertEquals(StreamV2Wire.Error("rate_limited", "slow", true, 5000),
            StreamV2Wire.parse("""{"type":"error","code":"rate_limited","message":"slow","retryable":true,"retry_after_ms":5000}"""))
        assertEquals(StreamV2Wire.Restarting(4000), StreamV2Wire.parse("""{"type":"restarting","message":"x","reconnect_after_ms":4000}"""))
        assertEquals(StreamV2Wire.Unknown("future"), StreamV2Wire.parse("""{"type":"future","x":1}"""))
        // A trial: no quota, and a language not yet detected.
        val trial = StreamV2Wire.parse("""{"type":"ready","resume":{"token":"t","resumed":true,"received_ms":1200},"language":{"source":"default"}}""") as StreamV2Wire.Ready
        assertEquals(null, trial.minutesRemaining)
        assertEquals(null, trial.language)
        assertEquals(1200L, trial.receivedMs)
    }

    // ---------------------------------------------------------------- session against a fake server

    private lateinit var server: MockWebServer

    @Before fun setUp() { org.robolectric.shadows.ShadowLog.stream = System.out; server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    /** 100 ms of audio every 20 ms of wall time, a distinct byte per chunk. */
    private class FakeMic(private val scope: CoroutineScope, private val onAudio: (ByteArray, Int) -> Unit) : AudioSource {
        override val currentLevel = 0f
        val captured = AtomicLong()
        private var job: Job? = null
        override fun start(): Boolean {
            job = scope.launch {
                var n = 0
                while (isActive) {
                    val chunk = ByteArray(3200) { (n % 120).toByte() }
                    n++
                    onAudio(chunk, chunk.size)
                    captured.addAndGet(chunk.size.toLong())
                    delay(20)
                }
            }
            return true
        }
        override fun stop() { job?.cancel() }
    }

    private class Recorder : VibeVoiceListener {
        val pieces = Collections.synchronizedList(mutableListOf<Pair<String, Boolean>>())
        val errors = Collections.synchronizedList(mutableListOf<String>())
        @Volatile var tailMissing = false
        val closed = CountDownLatch(1)
        override fun onPartial(text: String, isNewSegment: Boolean) { pieces.add(text to isNewSegment) }
        override fun onFinal(text: String, isNewSegment: Boolean) { errors.add("unexpected onFinal") }
        override fun onError(error: String) { errors.add(error); closed.countDown() }
        override fun onWarning(code: String) {}
        override fun onLinkQualityChanged(degraded: Boolean) {}
        override fun onTailMissing(unansweredMs: Long) { tailMissing = true }
        override fun onClosed() { closed.countDown() }
        override fun onCommitComposing() {}
    }

    /** A scripted v2 server for one connection. */
    private open inner class FakeServer : WebSocketListener() {
        val bytes = AtomicLong()
        @Volatile var start: JSONObject? = null
        override fun onMessage(webSocket: WebSocket, bytes: ByteString) { this.bytes.addAndGet(bytes.size.toLong()) }
        override fun onMessage(webSocket: WebSocket, text: String) {
            val o = JSONObject(text)
            when (o.getString("type")) {
                "start" -> { start = o; onStart(webSocket, o) }
                "end" -> onEnd(webSocket)
            }
        }
        open fun onStart(ws: WebSocket, o: JSONObject) {}
        open fun onEnd(ws: WebSocket) {}
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {}
    }

    private fun session(rec: Recorder, mic: Array<FakeMic?>) = VibeVoiceStreamV2(
        "key", "test", rec, server.url("/api/v2/stream").toString(),
    ) { scope, onAudio, _ -> FakeMic(scope, onAudio).also { mic[0] = it } }

    private fun waitFor(what: String, timeoutMs: Long = 5000, cond: () -> Boolean) {
        val until = System.currentTimeMillis() + timeoutMs
        while (!cond()) {
            if (System.currentTimeMillis() > until) {
                for ((th, st) in Thread.getAllStackTraces()) {
                    if (st.any { it.className.contains("vibevoice") || it.className.contains("coroutines") })
                        println("THREAD ${th.name} ${th.state}\n  " + st.take(12).joinToString("\n  "))
                }
                throw AssertionError("timed out waiting for $what")
            }
            Thread.sleep(10)
        }
    }

    @Test fun normalDictationDeliversEverySegmentAndEnds() {
        val conn = object : FakeServer() {
            override fun onStart(ws: WebSocket, o: JSONObject) {
                ws.send("""{"type":"ready","protocol":2,"session":"s1","resume":{"token":"t1","resumed":false,"ttl_s":120,"received_ms":0}}""")
                ws.send("""{"type":"segment","idx":1,"text":"Hello world.","ack_ms":100}""")
            }
            override fun onEnd(ws: WebSocket) {
                ws.send("""{"type":"segment","idx":2,"text":"Second one.","ack_ms":400}""")
                ws.send("""{"type":"end","last_idx":2,"billed_ms":400}""")
                ws.close(1000, "done")
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(conn))
        val rec = Recorder()
        val mic = arrayOfNulls<FakeMic>(1)
        val s = session(rec, mic)
        s.startStreaming()
        waitFor("audio at the server") { conn.bytes.get() >= 3200 * 5 }
        s.stopStreaming()
        assertTrue("session never closed", rec.closed.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("Hello world." to false, "Second one." to true), rec.pieces.toList())
        assertTrue(rec.errors.toString(), rec.errors.isEmpty())
        assertFalse(rec.tailMissing)
        assertEquals("pcm_s16le", conn.start!!.getJSONObject("audio").getString("encoding"))
        assertFalse(conn.start!!.has("resume"))
        waitFor("all audio at the server") { conn.bytes.get() == mic[0]!!.captured.get() }
    }

    @Test fun droppedLinkResumesWithoutLosingOrRepeatingAudioOrText() {
        val heldAtDrop = AtomicLong(-1)
        val first = object : FakeServer() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { if (heldAtDrop.get() < 0) super.onMessage(webSocket, bytes) }
            override fun onStart(ws: WebSocket, o: JSONObject) {
                ws.send("""{"type":"ready","session":"s1","resume":{"token":"t1","resumed":false,"received_ms":0}}""")
                Thread {
                  try {
                    while (bytes.get() < 3200 * 6) Thread.sleep(5)
                    ws.send("""{"type":"segment","idx":1,"text":"One.","ack_ms":200}""")
                    Thread.sleep(50)
                    heldAtDrop.set(bytes.get())
                    ws.close(1001, "going away") // the link dies; the server keeps what it had
                  } catch (e: Throwable) { e.printStackTrace() }
                }.start()
            }
        }
        val second = object : FakeServer() {
            override fun onStart(ws: WebSocket, o: JSONObject) {
                val received = heldAtDrop.get() / 32
                ws.send("""{"type":"ready","session":"s1","resume":{"token":"t2","resumed":true,"received_ms":$received}}""")
                // Replay: idx 1 again (already applied), then the one the dead socket never carried.
                ws.send("""{"type":"segment","idx":1,"text":"One.","ack_ms":200}""")
                ws.send("""{"type":"segment","idx":2,"text":"Two.","ack_ms":300}""")
            }
            override fun onEnd(ws: WebSocket) {
                ws.send("""{"type":"end","last_idx":2,"billed_ms":900}""")
                ws.close(1000, "done")
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(first))
        server.enqueue(MockResponse().withWebSocketUpgrade(second))
        val rec = Recorder()
        val mic = arrayOfNulls<FakeMic>(1)
        val s = session(rec, mic)
        s.startStreaming()
        waitFor("the resumed connection") { second.start != null }
        waitFor("audio on the resumed connection") { second.bytes.get() >= 3200 * 3 }
        s.stopStreaming()
        assertTrue("session never closed", rec.closed.await(5, TimeUnit.SECONDS))

        val resume = second.start!!.getJSONObject("resume")
        assertEquals("t1", resume.getString("token"))
        assertEquals(1, resume.getInt("last_idx"))
        assertEquals(listOf("One." to false, "Two." to true), rec.pieces.toList())
        assertTrue(rec.errors.toString(), rec.errors.isEmpty())
        // What the first server held plus what the second received is exactly what was captured.
        waitFor("all audio accounted for") { heldAtDrop.get() + second.bytes.get() == mic[0]!!.captured.get() }
    }

    @Test fun trialExhaustedIsReportedAsSuchAndNotRetried() {
        val conn = object : FakeServer() {
            override fun onStart(ws: WebSocket, o: JSONObject) {
                ws.send("""{"type":"error","code":"trial_exhausted","message":"Trial used up.","retryable":false}""")
                ws.close(4002, "trial_exhausted")
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(conn))
        val rec = Recorder()
        val s = session(rec, arrayOfNulls(1))
        s.startStreaming()
        assertTrue(rec.closed.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(VibeVoiceClient.ERR_TRIAL_EXHAUSTED), rec.errors.toList())
        Thread.sleep(800)
        assertEquals(1, server.requestCount)
        s.cancel()
    }

    @Test fun anHttpRefusalOfTheUpgradeFallsBackToV1() {
        server.enqueue(MockResponse().setResponseCode(404))
        val rec = Recorder()
        val s = session(rec, arrayOfNulls(1))
        VibeVoiceStreamV2.v2Unavailable = false
        s.startStreaming()
        assertTrue(rec.closed.await(5, TimeUnit.SECONDS))
        assertEquals(1, rec.errors.size)
        assertTrue(VibeVoiceStreamV2.v2Unavailable)
        VibeVoiceStreamV2.v2Unavailable = false
    }

}
