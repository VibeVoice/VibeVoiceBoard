// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.vibevoice

import java.util.Locale

/**
 * Debug-only log of what the waves are fed while a session records, so that "the waves are too flat / too
 * busy" can be judged from numbers instead of from a screen recording.
 *
 * One line per [intervalMs] while recording (raw RMS of the whole read, RMS of the freshest quarter, the
 * high-passed RMS the gate decides on, the level fed to the waves, the noise floor, the gate, and the level
 * the wave view actually draws), plus a line at the start and one at the end with min / average / max.
 *
 * Cheap on purpose: a read only updates a few running statistics, and the one string that is built is built
 * at most every [intervalMs]. The caller decides whether it exists at all (debug builds only), so a release
 * build never creates one. The audio loop is the only caller of [begin], [onRead] and [end].
 */
class LevelLog(private val sink: (String) -> Unit, private val intervalMs: Long = 500L) {
    /** Running minimum, average and maximum without keeping the samples. */
    class Stats {
        var count = 0L
            private set
        var min = Double.POSITIVE_INFINITY
            private set
        var max = Double.NEGATIVE_INFINITY
            private set
        private var sum = 0.0
        val avg: Double get() = if (count == 0L) 0.0 else sum / count

        fun add(v: Double) {
            count++
            sum += v
            if (v < min) min = v
            if (v > max) max = v
        }

        fun reset() {
            count = 0L
            sum = 0.0
            min = Double.POSITIVE_INFINITY
            max = Double.NEGATIVE_INFINITY
        }

        /** "min/avg/max", or "-" while nothing was added. */
        fun format(digits: Int = 4): String =
            if (count == 0L) "-" else String.format(Locale.US, "%.${digits}f/%.${digits}f/%.${digits}f", min, avg, max)
    }

    private val rawStats = Stats()
    private val fedStats = Stats()
    private val drawnStats = Stats()
    private var reads = 0L
    private var gateOpenReads = 0L
    private var lastEmitMs = 0L
    @Volatile private var active = false

    fun begin(nowMs: Long) {
        rawStats.reset()
        fedStats.reset()
        drawnStats.reset()
        reads = 0L
        gateOpenReads = 0L
        lastEmitMs = nowMs
        active = true
        sink("[LEVEL] session start, interval=${intervalMs}ms")
    }

    /**
     * One read of audio. [gate] is 0..1 and counts as open above [GATE_OPEN]. Emits a line when [intervalMs]
     * has passed since the last one.
     */
    fun onRead(nowMs: Long, rawRms: Double, tailRms: Double, filteredRms: Double, fed: Float, floor: Double, gate: Double, fullScale: Double = 0.0) {
        if (!active) return
        val drawn = drawnLevel
        reads++
        if (gate > GATE_OPEN) gateOpenReads++
        rawStats.add(rawRms)
        fedStats.add(fed.toDouble())
        drawnStats.add(drawn.toDouble())
        if (nowMs - lastEmitMs < intervalMs) return
        lastEmitMs = nowMs
        sink(line(rawRms, tailRms, filteredRms, fed, floor, gate, drawn, fullScale))
    }

    fun end() {
        if (!active) return
        active = false
        val open = if (reads == 0L) 0 else (100 * gateOpenReads / reads).toInt()
        sink("[LEVEL] session end, reads=$reads raw(min/avg/max)=${rawStats.format()} fed=${fedStats.format(3)} " +
            "drawn=${drawnStats.format(3)} gateOpen=$open%")
    }

    companion object {
        /** The gate counts as open above this. */
        const val GATE_OPEN = 0.5

        /**
         * What [VoiceWaveView] last drew from (its smoothed level), written from the UI thread in debug builds.
         * A plain volatile float: the log only samples it.
         */
        @Volatile @JvmField var drawnLevel = 0f

        internal fun line(rawRms: Double, tailRms: Double, filteredRms: Double, fed: Float, floor: Double, gate: Double, drawn: Float, fullScale: Double): String =
            String.format(
                Locale.US,
                "[LEVEL] raw=%.4f tail=%.4f hp=%.4f fed=%.3f floor=%.4f gate=%s(%.2f) fs=%.4f drawn=%.3f",
                rawRms, tailRms, filteredRms, fed, floor, if (gate > GATE_OPEN) "open" else "closed", gate, fullScale, drawn
            )
    }
}
