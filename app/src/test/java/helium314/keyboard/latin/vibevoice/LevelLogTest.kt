package helium314.keyboard.latin.vibevoice

import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelLogTest {
    private val lines = ArrayList<String>()
    private val log = LevelLog({ lines.add(it) }, intervalMs = 500L)

    @Test
    fun stats_minAvgMax() {
        val s = LevelLog.Stats()
        assertEquals("-", s.format())
        listOf(0.2, 0.4, 0.0, 0.6).forEach { s.add(it) }
        assertEquals(4L, s.count)
        assertEquals(0.0, s.min, 1e-12)
        assertEquals(0.6, s.max, 1e-12)
        assertEquals(0.3, s.avg, 1e-12)
        assertEquals("0.0000/0.3000/0.6000", s.format())
        s.reset()
        assertEquals(0L, s.count)
        assertEquals("-", s.format())
    }

    @Test
    fun emitsOneLineEveryHalfSecondNotPerRead() {
        log.begin(1_000L)
        assertEquals(1, lines.size) // the start line
        // 100 ms reads for 2 seconds: 20 reads, a line at 500, 1000, 1500 and 2000 ms
        for (i in 1..20) log.onRead(1_000L + i * 100L, 0.01, 0.012, 0.004, 0.3f, 0.003, 1.0)
        val levelLines = lines.drop(1)
        assertEquals(4, levelLines.size)
        assertTrue(levelLines.all { it.startsWith("[LEVEL] raw=") })
    }

    @Test
    fun lineCarriesEveryValueAndTheGateState() {
        log.begin(0L)
        LevelLog.drawnLevel = 0.25f
        log.onRead(600L, 0.0123, 0.0150, 0.0040, 0.314f, 0.0035, 0.92, 0.042)
        assertEquals("[LEVEL] raw=0.0123 tail=0.0150 hp=0.0040 fed=0.314 floor=0.0035 gate=open(0.92) fs=0.0420 drawn=0.250", lines.last())
        log.onRead(1_200L, 0.002, 0.002, 0.001, 0.0f, 0.002, 0.1)
        assertTrue(lines.last().contains("gate=closed(0.10)"))
        LevelLog.drawnLevel = 0f
    }

    @Test
    fun summaryAtTheEndHasMinAvgMaxAndGateShare() {
        log.begin(0L)
        LevelLog.drawnLevel = 0.5f
        log.onRead(100L, 0.01, 0.01, 0.01, 0.2f, 0.01, 1.0) // open
        log.onRead(200L, 0.03, 0.03, 0.03, 0.6f, 0.01, 0.0) // closed
        log.end()
        val end = lines.last()
        assertTrue(end, end.startsWith("[LEVEL] session end, reads=2"))
        assertTrue(end, end.contains("raw(min/avg/max)=0.0100/0.0200/0.0300"))
        assertTrue(end, end.contains("fed=0.200/0.400/0.600"))
        assertTrue(end, end.contains("drawn=0.500/0.500/0.500"))
        assertTrue(end, end.contains("gateOpen=50%"))
        LevelLog.drawnLevel = 0f
    }

    @Test
    fun nothingIsLoggedOutsideASession() {
        log.onRead(1_000L, 0.1, 0.1, 0.1, 0.5f, 0.1, 1.0)
        log.end()
        assertTrue(lines.isEmpty())
        log.begin(0L)
        log.end()
        val count = lines.size
        log.end() // a second end does not repeat the summary
        log.onRead(5_000L, 0.1, 0.1, 0.1, 0.5f, 0.1, 1.0)
        assertEquals(count, lines.size)
    }

    @Test
    fun beginStartsFreshStatistics() {
        log.begin(0L)
        log.onRead(100L, 0.5, 0.5, 0.5, 0.9f, 0.1, 1.0)
        log.end()
        lines.clear()
        log.begin(10_000L)
        log.onRead(10_100L, 0.01, 0.01, 0.01, 0.1f, 0.01, 0.0)
        log.end()
        assertTrue(lines.last(), lines.last().contains("raw(min/avg/max)=0.0100/0.0100/0.0100"))
    }

    @Test
    fun meterExposesWhatTheLogPrints() {
        val meter = LevelMeter()
        val n = 1600
        val bytes = ByteArray(n * 2)
        for (i in 0 until n) {
            val s = (0.05 * 32768.0 * sin(2 * Math.PI * 500.0 * i / 16000.0)).toInt()
            bytes[2 * i] = (s and 0xFF).toByte()
            bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        val level = meter.update(bytes, bytes.size)
        // a sine of peak 0.05 has an rms of 0.05 / sqrt(2)
        assertEquals(0.05 / Math.sqrt(2.0), meter.lastWholeRms, 0.002)
        assertEquals(0.05 / Math.sqrt(2.0), meter.lastTailRms, 0.004)
        assertTrue(meter.lastFilteredRms > 0.0 && meter.lastFilteredRms <= meter.lastTailRms * 1.01)
        assertTrue(meter.lastFloor > 0.0)
        assertTrue(meter.lastGate in 0.0..1.0)
        assertTrue(level in 0f..1f)
        meter.reset()
        assertEquals(0.0, meter.lastWholeRms, 0.0)
        assertFalse(abs(meter.lastGate) > 0.0)
    }
}
