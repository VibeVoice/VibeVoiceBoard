package helium314.keyboard.latin.vibevoice

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The automatic gain of the wave level: its full scale follows how loud the speaker gets. */
class LevelMeterGainTest {
    private val samplesPerRead = 1600 // 100 ms at 16 kHz
    private var seed = 987654321L
    private var sinePos = 0

    @Before
    fun setUp() {
        LevelMeter.noiseFloorFactor = LevelMeter.DEFAULT_NOISE_FLOOR_FACTOR.toDouble()
        LevelMeter.speechPeakSeed = LevelMeter.DEFAULT_SPEECH_PEAK.toDouble()
        LevelMeter.currentSpeechPeak = Double.NaN
    }

    @After
    fun tearDown() = setUp()

    private fun noise(rms: Double): DoubleArray {
        val peak = rms * sqrt(3.0)
        return DoubleArray(samplesPerRead) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            ((seed ushr 11).toDouble() / (1L shl 53) * 2.0 - 1.0) * peak
        }
    }

    private fun voice(rms: Double): DoubleArray {
        val peak = rms * sqrt(2.0)
        return DoubleArray(samplesPerRead) { peak * sin(2 * PI * 500.0 * (sinePos++) / 16000.0) }
    }

    private fun LevelMeter.feed(samples: DoubleArray): Float {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = (samples[i] * 32768.0).toInt().coerceIn(-32768, 32767)
            bytes[2 * i] = (s and 0xFF).toByte()
            bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return update(bytes, bytes.size)
    }

    /** Room noise, then [seconds] of a speaker whose syllables reach [rms]: 0.5 s of voice, 0.2 s of room, repeated. */
    private fun LevelMeter.speak(rms: Double, seconds: Double, room: Double = 0.0015): Float {
        var top = 0f
        var elapsed = 0.0
        while (elapsed < seconds) {
            repeat(5) { top = maxOf(top, feed(voice(rms))) }
            repeat(2) { feed(noise(room)) }
            elapsed += 0.7
        }
        return top
    }

    private fun LevelMeter.settleRoom(room: Double = 0.0015, seconds: Double = 6.0) = repeat((seconds * 10).toInt()) { feed(noise(room)) }

    @Test
    fun quietSpeakerStillMovesTheWaves() {
        val meter = LevelMeter()
        meter.settleRoom()
        meter.speak(0.01, 30.0)
        val top = meter.speak(0.01, 5.0)
        println("quiet speaker: top=$top fullScale=${meter.lastFullScale}")
        assertTrue("a quiet speaker reached only $top", top >= 0.6f)
        assertTrue(meter.lastFullScale <= 0.03)
    }

    @Test
    fun loudSpeakerDoesNotPinTheWavesAtTheTop() {
        val meter = LevelMeter()
        meter.settleRoom()
        meter.speak(0.1, 30.0)
        // ordinary speech (a third of the peak) is in the middle of the range, the peak is at the top
        val ordinary = meter.speak(0.03, 3.0)
        val peak = meter.speak(0.1, 3.0)
        println("loud speaker: ordinary=$ordinary peak=$peak fullScale=${meter.lastFullScale}")
        assertTrue("ordinary speech $ordinary", ordinary in 0.3f..0.8f)
        assertTrue("peak $peak", peak >= 0.9f)
        assertTrue(meter.lastFullScale in 0.06..LevelMeter.LEVEL_FULL_SCALE)
    }

    @Test
    fun roomNoiseAloneIsNeverAmplified() {
        val meter = LevelMeter()
        var last = 1f
        var worst = 0f
        repeat(300) { // thirty seconds of a fan, nothing else
            last = meter.feed(noise(0.01))
            if (it > 30) worst = maxOf(worst, last)
        }
        println("room noise: last=$last worst=$worst fullScale=${meter.lastFullScale}")
        assertTrue("noise level was $last", last < 0.05f)
        assertTrue("noise peaked at $worst", worst < 0.2f)
        assertTrue("nothing was learned from the room", LevelMeter.currentSpeechPeak.isNaN())
        // and the scale stays at its start, it did not shrink to the noise
        assertEquals(LevelMeter.fullScaleFor(LevelMeter.DEFAULT_SPEECH_PEAK.toDouble()), meter.lastFullScale, 0.02)
    }

    @Test
    fun theScaleNeverDropsBelowAMultipleOfTheRoomFloor() {
        val meter = LevelMeter()
        meter.settleRoom(room = 0.02) // a loud room: floor around 0.02
        meter.speak(0.005, 20.0, room = 0.02) // speech below the room never opens the gate
        assertTrue("scale ${meter.lastFullScale} vs floor ${meter.lastFloor}", meter.lastFullScale >= meter.lastFloor * 4.0 - 1e-9)
    }

    @Test
    fun switchingFromLoudToQuietAdaptsWithinHalfAMinute() {
        val meter = LevelMeter()
        meter.settleRoom()
        meter.speak(0.1, 30.0)
        val loudScale = meter.lastFullScale
        val first = meter.speak(0.01, 3.0)
        meter.speak(0.01, 40.0)
        val later = meter.speak(0.01, 3.0)
        println("loud->quiet: scale $loudScale -> ${meter.lastFullScale}, first=$first later=$later")
        assertTrue("scale only fell from $loudScale to ${meter.lastFullScale}", meter.lastFullScale < loudScale * 0.5)
        assertTrue("quiet speech after adapting reached only $later", later >= 0.6f)
        assertTrue("it should have been dim right after the switch: $first", first < later)
    }

    @Test
    fun theEstimateStartsFromTheStoredValueAndIsClamped() {
        LevelMeter.speechPeakSeed = 0.04
        val meter = LevelMeter()
        assertEquals(0.028, meter.lastFullScale, 1e-6)
        LevelMeter.speechPeakSeed = 5.0
        meter.reset()
        assertEquals(LevelMeter.LEVEL_FULL_SCALE, meter.lastFullScale, 1e-9)
        LevelMeter.speechPeakSeed = 0.0
        meter.reset()
        assertEquals(LevelMeter.MIN_FULL_SCALE, meter.lastFullScale, 1e-9)
    }

    @Test
    fun aSessionLearnsAndTheNextOneStartsThere() {
        val first = LevelMeter()
        first.settleRoom()
        first.speak(0.02, 25.0)
        val learned = LevelMeter.currentSpeechPeak
        assertFalse(learned.isNaN())
        // the wave view stores it and seeds the next session with it
        LevelMeter.speechPeakSeed = learned
        val second = LevelMeter()
        assertEquals(first.lastFullScale, second.lastFullScale, 0.01)
        assertTrue(learned in 0.01..0.04)
    }

    /**
     * The phone log of the user's own microphone: speech RMS 0.003..0.04, average about 0.008, peak about 0.06.
     * His ordinary loud speech (0.02..0.04) must reach at least 0.6, and the quiet 0.004 at least 0.3.
     */
    @Test
    fun replayOfTheRealPhoneLog() {
        val pattern = doubleArrayOf(0.004, 0.006, 0.008, 0.012, 0.02, 0.03, 0.04, 0.025, 0.01, 0.006, 0.004, 0.003, 0.008, 0.006, 0.06)
        val meter = LevelMeter()
        meter.settleRoom(room = 0.0015)
        var minLoud = 1f
        var maxLoud = 0f
        var sum = 0.0
        var count = 0
        for (round in 0 until 40) { // about a minute
            for (r in pattern) {
                val level = meter.feed(voice(r))
                sum += r
                count++
                if (round >= 20 && r in 0.02..0.04) {
                    minLoud = minOf(minLoud, level)
                    maxLoud = maxOf(maxLoud, level)
                }
            }
            repeat(3) { meter.feed(noise(0.0015)) }
        }
        val fs = meter.lastFullScale
        val quiet = sqrt(0.004 / fs)
        println("log replay: avg rms=${sum / count} fullScale=$fs level(0.02..0.04)=$minLoud..$maxLoud level(0.004)=$quiet level(0.008)=${sqrt(0.008 / fs)}")
        assertTrue("0.02..0.04 mapped to $minLoud", minLoud >= 0.6f)
        assertTrue("0.004 mapped to $quiet", quiet >= 0.3)
        // and it is not pinned: the busiest syllables are at most the top of the range
        assertTrue(maxLoud <= 1f)
    }
}
