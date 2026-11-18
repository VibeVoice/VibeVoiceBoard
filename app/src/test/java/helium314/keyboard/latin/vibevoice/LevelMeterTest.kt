package helium314.keyboard.latin.vibevoice

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LevelMeterTest {
    private val samplesPerRead = 1600 // 100 ms at 16 kHz
    private var seed = 12345L

    @Before
    fun resetFactor() {
        LevelMeter.noiseFloorFactor = LevelMeter.DEFAULT_NOISE_FLOOR_FACTOR.toDouble()
    }

    /** Uniform white noise with the given RMS (a uniform distribution has rms = peak / sqrt(3)). */
    private fun noise(rms: Double): DoubleArray {
        val peak = rms * Math.sqrt(3.0)
        return DoubleArray(samplesPerRead) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            ((seed ushr 11).toDouble() / (1L shl 53) * 2.0 - 1.0) * peak
        }
    }

    private var sinePos = 0
    private fun sine(rms: Double, hz: Double = 500.0): DoubleArray {
        val peak = rms * Math.sqrt(2.0)
        return DoubleArray(samplesPerRead) { peak * sin(2 * PI * hz * (sinePos++) / 16000.0) }
    }

    private fun pcm(samples: DoubleArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = (samples[i] * 32768.0).toInt().coerceIn(-32768, 32767)
            out[2 * i] = (s and 0xFF).toByte()
            out[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun LevelMeter.feed(samples: DoubleArray): Float {
        val bytes = pcm(samples)
        return update(bytes, bytes.size)
    }

    @Test
    fun steadyNoiseConvergesToNothing() {
        val meter = LevelMeter()
        var last = 1f
        repeat(100) { last = meter.feed(noise(0.01)) } // ten seconds of a fan
        assertTrue("display level for steady noise was $last", last < 0.05f)
    }

    @Test
    fun rawLevelOfTheSameNoiseIsClearlyVisible() {
        // The reason for the meter: today's curve shows this noise as a wave swell.
        val bytes = pcm(noise(0.01))
        assertTrue(AudioCapture.level(bytes, bytes.size) > 0.2f)
    }

    @Test
    fun speechOverNoiseLooksLikeSpeechAlone() {
        val meter = LevelMeter()
        repeat(60) { meter.feed(noise(0.01)) }
        var burst = 0f
        repeat(3) {
            val mixed = sine(0.10).zip(noise(0.01)) { a, b -> a + b }.toDoubleArray()
            burst = meter.feed(mixed)
        }
        val alone = pcm(sine(0.10)).let { AudioCapture.level(it, it.size) }
        assertTrue("burst $burst vs raw $alone", abs(burst - alone) < 0.15f)
        assertTrue(burst > 0.6f)
    }

    @Test
    fun aLowVoiceInAQuietRoomLooksExactlyAsBefore() {
        // A male voice's fundamental sits below the high-pass; the drawn level must not lose it.
        val meter = LevelMeter()
        repeat(30) { meter.feed(noise(0.0005)) }
        var level = 0f
        var raw = 0f
        repeat(5) {
            val voice = sine(0.03, hz = 120.0)
            level = meter.feed(voice)
            raw = pcm(voice).let { AudioCapture.level(it, it.size) }
        }
        assertEquals(raw, level, 0.01f)
    }

    @Test
    fun aSustainedSyllableIsNotLearnedAsNoise() {
        val meter = LevelMeter()
        repeat(60) { meter.feed(noise(0.01)) }
        var level = 0f
        repeat(10) { level = meter.feed(sine(0.08)) } // a whole second of vowel
        assertTrue("level after a second of speech was $level", level > 0.5f)
    }

    @Test
    fun floorFollowsAQuietingRoomDownQuickly() {
        val meter = LevelMeter()
        repeat(60) { meter.feed(noise(0.03)) }
        repeat(5) { meter.feed(noise(0.003)) } // half a second later the new floor is in place
        val level = meter.feed(noise(0.009))
        assertTrue("level was $level", level > 0.05f)
    }

    @Test
    fun zerosSetSilentNowAfter300msAndClearWithAudio() {
        val meter = LevelMeter()
        val zeros = ByteArray(samplesPerRead * 2)
        meter.update(zeros, zeros.size)
        meter.update(zeros, zeros.size)
        assertFalse(meter.silentNow) // 200 ms
        val level = meter.update(zeros, zeros.size)
        assertTrue(meter.silentNow) // 300 ms
        assertEquals(0f, level, 0f)

        val sound = pcm(noise(0.01))
        meter.update(sound, sound.size)
        assertFalse(meter.silentNow)
    }

    @Test
    fun theBufferIsLeftUntouched() {
        val meter = LevelMeter()
        val bytes = pcm(sine(0.1))
        val copy = bytes.copyOf()
        meter.update(bytes, bytes.size)
        assertTrue(bytes.contentEquals(copy))
    }

    @Test
    fun noiseThatStartsAfterZerosIsStillLearned() {
        val meter = LevelMeter()
        val zeros = ByteArray(samplesPerRead * 2)
        repeat(20) { meter.update(zeros, zeros.size) }
        var last = 1f
        repeat(100) { last = meter.feed(noise(0.01)) }
        assertTrue(last < 0.05f)
    }
}
