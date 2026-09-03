// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.vibevoice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.Settings as AndroidSettings
import android.util.AttributeSet
import android.view.View
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import java.lang.ref.WeakReference
import kotlin.math.cos
import kotlin.math.sin

/**
 * The sound waves that fill the keyboard background while a dictation session is running.
 *
 * Ported from the website's hero canvas (`website_react/src/components/ui/TopographicAudioWaves.jsx`),
 * minus everything that only makes sense with a pointer: the mouse warp, the proximity falloff and
 * the global move listeners, which are about half of the original component.
 *
 * The view sits below [helium314.keyboard.keyboard.MainKeyboardView] inside the keyboard wrapper, so
 * it paints over the keyboard's base colour and under the keys, and shows through the gaps between
 * them. It deliberately keeps its own invalidate loop instead of asking the keyboard view to redraw:
 * under hardware acceleration `KeyboardView.onDraw` redraws every key, and doing that thirty times a
 * second while the microphone and the socket are busy is exactly what this separation avoids.
 */
class VoiceWaveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val density = context.resources.displayMetrics.density

    /**
     * The client whose microphone level is drawn, held weakly. A method reference would have kept
     * the client alive, and through its listener the whole [helium314.keyboard.latin.LatinIME]
     * instance, for as long as this view held it -- so any path that failed to call [stop] would
     * turn a stuck animation into a leaked service. Weak, the worst case is a still line.
     */
    @Volatile private var client: WeakReference<VibeVoiceClient>? = null

    /** The wave colour, resolved once per session rather than per frame. */
    private var baseColor = Color.GRAY
    private var phase = 0f
    private var level = 0f
    private var running = false

    // The tuning values, read once per session in [start] rather than once per frame. Reading them
    // in onDraw meant eight synchronized lookups on SharedPreferencesImpl thirty times a second, on
    // the UI thread, while the user is typing. The sliders only exist in debug builds and are set
    // from the settings screen with the keyboard closed, so a value picked up at the next session
    // is soon enough.
    private var attack = Defaults.PREF_WAVE_ATTACK
    private var damping = Defaults.PREF_WAVE_DAMPING
    private var ampFraction = Defaults.PREF_WAVE_AMPLITUDE
    private var reaction = Defaults.PREF_WAVE_REACTION
    private var cycles = Defaults.PREF_WAVE_CYCLES
    private var speed = Defaults.PREF_WAVE_SPEED
    private var spread = Defaults.PREF_WAVE_SPREAD
    private var jitter = Defaults.PREF_WAVE_JITTER
    private var waveCount = Defaults.PREF_WAVE_COUNT.toInt()

    init {
        isClickable = false
        isFocusable = false
        visibility = GONE
    }

    /**
     * Starts the animation. [source] is polled once per frame rather than pushed, so the capture
     * coroutine never has to touch the UI thread just to move a wave.
     */
    fun start(source: VibeVoiceClient) {
        client = WeakReference(source)
        readTuning()
        // Resolved here and not in onDraw: the settings may legitimately not be loaded yet, and a
        // try/catch is the honest way to say so -- but it belongs on a once-per-session call, not
        // on a path that runs thirty times a second. A theme change re-inflates the input view, so
        // a new view with a freshly resolved colour is what follows it anyway.
        baseColor = try {
            Settings.getValues().mColors.get(ColorType.GESTURE_TRAIL)
        } catch (e: Exception) {
            Color.GRAY
        }
        if (running) return
        running = true
        phase = 0f
        level = 0f
        visibility = VISIBLE
        VibeVoiceDebugLogger.log("VoiceWaveView start, size=${width}x${height}, animated=${animationsEnabled()}")
        invalidate()
    }

    /**
     * Stops it and leaves nothing behind. Must be reached on every path that ends a session,
     * including the ones that end it abnormally — a surviving frame callback here would keep the
     * keyboard redrawing after the microphone is long gone.
     */
    fun stop() {
        running = false
        client = null
        level = 0f
        visibility = GONE
        VibeVoiceDebugLogger.log("VoiceWaveView stop")
    }

    /**
     * Contributes nothing to the parent's measurement, on purpose. The keyboard wrapper is a
     * `wrap_content` FrameLayout, so a child that answers an AT_MOST spec with the full available
     * size claims the whole window and drags the keyboard's height up with it — the keyboard grew to
     * fill the screen the moment a session started. [KeyboardWrapperView] gives this view its real
     * bounds in `onLayout`, once the keys have been measured.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // An EXACTLY spec is a different question from the one above: it is KeyboardWrapperView
        // telling us our bounds in onLayout, not asking how big we would like to be. Honouring it
        // keeps measuredWidth/measuredHeight in agreement with what is actually drawn, instead of
        // leaving a permanent 0x0 for anything that inspects this view.
        setMeasuredDimension(
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(widthMeasureSpec) else 0,
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) MeasureSpec.getSize(heightMeasureSpec) else 0
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // The bounds arrive from KeyboardWrapperView.onLayout, after onDraw may already have run
        // and rescheduled itself. Draw now rather than sitting out the rest of that frame interval.
        if (running && w > 0 && h > 0) invalidate()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (!running) return
        val width = width.toFloat()
        val height = height.toFloat()
        // Not laid out yet. This happens when a session is resumed on a freshly inflated input view
        // -- the window is shown before the wrapper has given us our bounds. Keep the loop alive
        // instead of returning into silence, or the animation never starts for that session.
        if (width <= 0f || height <= 0f) {
            if (animationsEnabled()) postInvalidateDelayed(FRAME_INTERVAL_MS)
            return
        }

        val raw = client?.get()?.currentLevel ?: 0f
        // Asymmetric, the way a level meter behaves: jump at the onset of a syllable, fall back
        // slowly. The web component uses one constant for both directions, which is what made the
        // swell arrive late and then linger. Attack is more than three times the release here.
        level += (raw - level) * (if (raw > level) attack else damping)

        // Louder speech travels faster as well as reaching higher; without this the amplitude grows
        // but the motion still reads as idle.
        phase += speed * (1f + level * PHASE_LEVEL_GAIN)

        val red = Color.red(baseColor)
        val green = Color.green(baseColor)
        val blue = Color.blue(baseColor)

        paint.strokeWidth = STROKE_WIDTH_DP * density
        val spacing = height / (waveCount + 1)
        val step = (width / 120f).coerceAtLeast(4f)
        // Everything below is expressed in cycles across the width rather than radians per pixel.
        // The web constants are radians per pixel on a 350 px canvas, where the fundamental works
        // out to 0.84 cycles — under one period, which is why it reads as a swell. Copied verbatim
        // into a 1080 px keyboard the same numbers gave four periods and looked like wallpaper.
        // In these units the picture is also identical on a phone and on a tablet.
        val cycle = (2.0 * Math.PI / width).toFloat()

        for (w in 0 until waveCount) {
            val baseOffsetY = spacing * 0.9f + w * spacing * 1.05f
            // Offsets by the golden angle rather than an even slice of the cycle. An even slice is
            // a linear progression in w, so with every wave the same shape the crests stepped along
            // by a constant amount and lined up on a straight diagonal. The golden angle is the
            // least well approximated by any fraction, which is exactly the property that stops
            // repeating alignment — the same reason leaves grow at it.
            //
            // The drift term does the rest: each wave advances its phase at a slightly different
            // rate, so any alignment that does occur pulls apart again instead of standing still.
            // Both scale with spread, so at 0 the waves are still exactly parallel.
            val wavePhase = phase * (1f + w * spread * PHASE_DRIFT) +
                    w * spread * GOLDEN_ANGLE

            // Resting alpha fades with depth; speech brightens the whole set. The level term is 1
            // at silence, so a quiet keyboard looks exactly as the theme intends.
            val restAlpha = REST_ALPHA - w * (REST_ALPHA_FALLOFF / waveCount)
            val alpha = (restAlpha * (1f + level * ALPHA_LEVEL_GAIN)).coerceIn(0.02f, 0.85f)
            paint.color = Color.argb((alpha * 255).toInt(), red, green, blue)

            // Amplitude as a fraction of the gap between waves, not an absolute size. That is the
            // only way neighbours can be made to cross: on the web a loud syllable puts the
            // excursion just past one full spacing, and they interleave. The three terms sum to at
            // most 1.7, so the peak here is 1.7 * amp * envelope — roughly a quarter of a spacing
            // in silence and a little over a full spacing when the voice is loud.
            val amp = spacing * ampFraction * (1f + level * reaction)

            // Each wave is detuned a little. Without this every line is the same curve shifted in
            // phase; under one visible period that passes for organic, at four periods it reads as
            // a repeated pattern.
            val detune = 1f + w * WAVE_DETUNE

            var first = true
            var x = 0f
            while (x <= width) {
                val nx = x * cycle
                val sin1 = sin(nx * cycles * detune + wavePhase)
                val sin2 = sin(nx * cycles * SECOND_RATIO * detune - wavePhase * (0.7f + (w % 2) * 0.2f)) * 0.45f
                val cos1 = cos(nx * cycles * THIRD_RATIO * detune + wavePhase * 0.6f) * 0.25f
                // Fine roughness, present only while there is sound. Small by default — a large one
                // is what made the whole picture look like wallpaper two versions ago.
                val fine = sin(nx * cycles * JITTER_RATIO + wavePhase * 2.1f) * jitter * level
                // A slow swell along the width, so the line breathes instead of running at one
                // height from edge to edge. This is what a waveform looks like, and it replaces the
                // short ripple that was here before — that added texture but also four more
                // periods, which is the opposite of what was wanted.
                val envelope = ENVELOPE_FLOOR + (1f - ENVELOPE_FLOOR) *
                        (0.5f + 0.5f * sin(nx * CYCLES_ENVELOPE + wavePhase * 0.3f))
                val y = baseOffsetY + (sin1 + sin2 + cos1 + fine) * amp * envelope
                if (first) {
                    wavePath.moveTo(x, y)
                    first = false
                } else {
                    wavePath.lineTo(x, y)
                }
                x += step
            }
            canvas.drawPath(wavePath, paint)
            wavePath.rewind()
        }

        // Honouring the system "remove animations" setting, the Android counterpart of the
        // prefers-reduced-motion check the web component makes: one static frame, no loop.
        if (animationsEnabled()) postInvalidateDelayed(FRAME_INTERVAL_MS)
    }

    private val wavePath = android.graphics.Path()

    /**
     * Debug builds only, deliberately. The sliders that write these keys are gated on
     * [BuildConfig.DEBUG] in the settings screen, so in a Play build there is nothing that could
     * have written them and nothing that could reset them either -- reading them there would mean
     * a restored backup or a stray key silently changing what every user sees, with no way back.
     * R8 drops the whole body from the release variant.
     */
    private fun readTuning() {
        if (!BuildConfig.DEBUG) return
        val p = context.prefs()
        attack = p.getFloat(Settings.PREF_WAVE_ATTACK, Defaults.PREF_WAVE_ATTACK)
        damping = p.getFloat(Settings.PREF_WAVE_DAMPING, Defaults.PREF_WAVE_DAMPING)
        ampFraction = p.getFloat(Settings.PREF_WAVE_AMPLITUDE, Defaults.PREF_WAVE_AMPLITUDE)
        reaction = p.getFloat(Settings.PREF_WAVE_REACTION, Defaults.PREF_WAVE_REACTION)
        cycles = p.getFloat(Settings.PREF_WAVE_CYCLES, Defaults.PREF_WAVE_CYCLES)
        speed = p.getFloat(Settings.PREF_WAVE_SPEED, Defaults.PREF_WAVE_SPEED)
        spread = p.getFloat(Settings.PREF_WAVE_SPREAD, Defaults.PREF_WAVE_SPREAD)
        jitter = p.getFloat(Settings.PREF_WAVE_JITTER, Defaults.PREF_WAVE_JITTER)
        waveCount = p.getFloat(Settings.PREF_WAVE_COUNT, Defaults.PREF_WAVE_COUNT)
            .toInt().coerceIn(1, 12)
    }

    private fun animationsEnabled(): Boolean = try {
        AndroidSettings.Global.getFloat(
            context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
    } catch (e: Exception) {
        true
    }

    companion object {
        private const val FRAME_INTERVAL_MS = 33L // ~30fps; 60 buys nothing here and costs battery
        // Multiples of the fundamental, which is a setting. Not whole numbers on purpose: an exact
        // double beats visibly and the composite starts to read as a repeat.
        private const val SECOND_RATIO = 1.92f
        private const val THIRD_RATIO = 0.75f
        private const val JITTER_RATIO = 7.5f
        private const val CYCLES_ENVELOPE = 0.6f
        private const val ENVELOPE_FLOOR = 0.45f
        private const val WAVE_DETUNE = 0.11f
        /** 2*pi * (1 - 1/phi). The angle that never settles into a repeating pattern. */
        private const val GOLDEN_ANGLE = 2.39996f
        private const val PHASE_DRIFT = 0.09f
        private const val PHASE_LEVEL_GAIN = 3.2f
        private const val ALPHA_LEVEL_GAIN = 3.0f
        private const val REST_ALPHA = 0.30f
        private const val REST_ALPHA_FALLOFF = 0.15f
        private const val STROKE_WIDTH_DP = 1.2f
    }
}
