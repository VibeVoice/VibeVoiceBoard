// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.vibevoice

import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The level the waves are drawn from, kept apart from the audio that is sent to the server.
 *
 * The raw level ([AudioCapture.level]) answers "how loud is it", which is the wrong question for a
 * display: a fan or a car engine is loud, steady and uninteresting, and it kept the waves swelling
 * the whole time so that speech no longer stood out. This meter asks "is this louder than the
 * room", and only uses the answer to open or close a gate over the raw level:
 *
 *  - a one-pole high-pass takes the rumble out of the measurement the gate decides on (engines,
 *    wind, handling noise), which carries most of the energy of steady noise;
 *  - an adaptive floor follows the quietest recent filtered level -- quickly down, slowly up -- so a
 *    syllable, which is over in well under a second, does not become the floor but a minute of
 *    traffic does;
 *  - the gate is shut up to [noiseFloorFactor] times the floor and fully open from twice that.
 *
 * What is drawn is the raw level times the gate. An earlier version drew the filtered level minus
 * the floor instead: the filter cut into the fundamental of a low voice (100-130 Hz) and the
 * subtraction shrank quiet syllables most, so speech came out flatter and followed the voice less.
 * With the gate open, speech looks exactly as it did before the meter existed.
 *
 * It only ever reads the samples. The caller's buffer is never modified, so the bytes that go on to
 * the server are untouched. Not thread-safe: one capture loop owns one meter.
 */
class LevelMeter {
    private var hpIn = 0.0
    private var hpOut = 0.0
    private var floor = 0.0
    private var floorKnown = false
    private var zeroBytes = 0L
    private var gate = 0.0
    private var held = 0.0

    /** The microphone has been delivering exact zeros for at least [SILENT_NOW_BYTES]. */
    var silentNow = false
        private set

    fun reset() {
        hpIn = 0.0
        hpOut = 0.0
        floor = 0.0
        floorKnown = false
        zeroBytes = 0L
        silentNow = false
        gate = 0.0
        held = 0.0
    }

    /** Takes one read of 16-bit little-endian mono PCM and returns the display level, 0..1. */
    fun update(buffer: ByteArray, read: Int): Float {
        val numSamples = read / 2
        if (numSamples <= 0) return 0f

        var allZero = true
        for (i in 0 until read) {
            if (buffer[i] != 0.toByte()) {
                allZero = false
                break
            }
        }
        if (allZero) {
            zeroBytes += read
            if (zeroBytes >= SILENT_NOW_BYTES) silentNow = true
        } else {
            zeroBytes = 0L
            silentNow = false
        }

        // The whole read goes through the filter so its state stays continuous, but only the
        // freshest quarter is measured, as in the raw level: that is what keeps the waves quick.
        val tailStart = (numSamples * 3) / 4
        var squares = 0.0
        var rawSquares = 0.0
        var x1 = hpIn
        var y1 = hpOut
        for (i in 0 until numSamples) {
            val b1 = buffer[2 * i].toInt() and 0xFF
            val b2 = buffer[2 * i + 1].toInt() and 0xFF
            val x = ((b2 shl 8) or b1).toShort() / 32768.0
            val y = HP_COEFFICIENT * (y1 + x - x1)
            x1 = x
            y1 = y
            if (i >= tailStart) {
                squares += y * y
                rawSquares += x * x
            }
        }
        hpIn = x1
        hpOut = y1
        val rms = sqrt(squares / (numSamples - tailStart))
        val rawRms = sqrt(rawSquares / (numSamples - tailStart))

        val seconds = numSamples / SAMPLE_RATE
        if (!floorKnown) {
            // Nothing to learn from digital silence; wait for the first real sound, so that a
            // microphone that starts late does not leave a floor of zero for the noise to climb.
            if (rms > 0.0) {
                floor = rms
                floorKnown = true
            }
        } else {
            val tau = when {
                rms < floor -> FLOOR_FALL_SECONDS
                // Several times the floor is a voice, not the room getting louder. Letting it in at
                // the normal rate made the floor creep up under a long vowel and the waves sag
                // while the user was still talking; a room that really got that much louder is
                // still learned, just over tens of seconds.
                rms > floor * SPEECH_OVER_FLOOR -> FLOOR_RISE_OVER_SPEECH_SECONDS
                else -> FLOOR_RISE_SECONDS
            }
            floor += (rms - floor) * (1.0 - exp(-seconds / tau))
        }

        val gateNow = if (floor <= 0.0) 1.0 else {
            val factor = noiseFloorFactor
            val t = ((rms / floor - factor) / factor.coerceAtLeast(MIN_GATE_SPAN)).coerceIn(0.0, 1.0)
            t * t * (3 - 2 * t) // smoothstep, so the waves fade in and out instead of switching
        }
        // Opens at once, closes slowly: the gap between two words is room noise, and a gate that
        // shut on it made the waves collapse between every word.
        gate = maxOf(gateNow, gate * exp(-seconds / GATE_RELEASE_SECONDS))
        val now = sqrt(rawRms / LEVEL_FULL_SCALE).coerceIn(0.0, 1.0) * gate
        // The same for the level itself: a syllable's end sinks over a fraction of a second rather
        // than dropping to the room. A dead microphone still goes flat at once.
        held = if (allZero) 0.0 else maxOf(now, held * exp(-seconds / LEVEL_RELEASE_SECONDS))
        if (allZero) gate = 0.0
        return held.toFloat()
    }

    companion object {
        /** Same full scale as [AudioCapture.level], so speech keeps its look. */
        const val LEVEL_FULL_SCALE = 0.15
        private const val SAMPLE_RATE = 16000.0

        /** One-pole high-pass at about 150 Hz: RC / (RC + dt) with RC = 1 / (2 pi 150). */
        private val HP_COEFFICIENT = run {
            val rc = 1.0 / (2.0 * Math.PI * 150.0)
            rc / (rc + 1.0 / SAMPLE_RATE)
        }

        /** Down within about a tenth of a second, so a room that goes quiet stops being "speech" at once. */
        private const val FLOOR_FALL_SECONDS = 0.1
        /** Up over seconds, so a long syllable is not learned as noise but steady noise is. */
        private const val FLOOR_RISE_SECONDS = 3.0
        private const val FLOOR_RISE_OVER_SPEECH_SECONDS = 12.0
        private const val SPEECH_OVER_FLOOR = 3.0

        /** The gate opens over at least this ratio of level to floor, even with a factor near 0. */
        private const val MIN_GATE_SPAN = 0.5
        /** The gate takes about this long to close after speech stops, so word gaps keep their waves. */
        private const val GATE_RELEASE_SECONDS = 0.5
        /** The drawn level falls with this time constant instead of dropping between syllables. */
        private const val LEVEL_RELEASE_SECONDS = 0.25

        /** 300 ms of 16 kHz 16-bit mono. */
        const val SILENT_NOW_BYTES = 9_600L

        /**
         * How far above the floor a sound must be to show at all; at twice this it shows in full.
         * A little over 1 keeps the ripple of steady noise below the line. Written by [VoiceWaveView] from the debug slider, otherwise
         * this default. A plain field rather than a constructor argument because the meter is
         * created deep inside the capture, which has no business reading preferences.
         */
        const val DEFAULT_NOISE_FLOOR_FACTOR = 1.75f
        @Volatile var noiseFloorFactor = DEFAULT_NOISE_FLOOR_FACTOR.toDouble()
    }
}
