// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.vibevoice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.Settings as AndroidSettings
import android.util.AttributeSet
import android.view.View
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
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

    /** Reads the live microphone level, 0..1. Null while no session is running. */
    @Volatile private var levelSource: (() -> Float)? = null
    private var phase = 0f
    private var level = 0f
    private var running = false

    init {
        isClickable = false
        isFocusable = false
        visibility = GONE
    }

    /**
     * Starts the animation. [source] is polled once per frame rather than pushed, so the capture
     * coroutine never has to touch the UI thread just to move a wave.
     */
    fun start(source: () -> Float) {
        levelSource = source
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
        levelSource = null
        level = 0f
        visibility = GONE
        VibeVoiceDebugLogger.log("VoiceWaveView stop")
    }

    /**
     * Measures to nothing on purpose. The keyboard wrapper is a `wrap_content` FrameLayout, so a
     * `match_parent` child claims the whole window and drags the keyboard's height up with it — the
     * keyboard grew to fill the screen the moment a session started. [KeyboardWrapperView] gives
     * this view its real bounds in `onLayout`, once the keys have been measured.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(0, 0)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (!running) return
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        val raw = levelSource?.invoke() ?: 0f
        // Follows a spoken syllable rather than every click in the room. Same constant as the web
        // component, which arrived at it for the same reason.
        level += (raw - level) * LEVEL_SMOOTHING

        // Louder speech travels faster as well as reaching higher; without this the amplitude grows
        // but the motion still reads as idle.
        phase += PHASE_STEP * (1f + level * PHASE_LEVEL_GAIN)

        val baseColor = try {
            Settings.getValues().mColors.get(ColorType.GESTURE_TRAIL)
        } catch (e: Exception) {
            Color.GRAY // settings not loaded yet; one grey frame is better than a crash in onDraw
        }
        val red = Color.red(baseColor)
        val green = Color.green(baseColor)
        val blue = Color.blue(baseColor)

        paint.strokeWidth = STROKE_WIDTH_DP * density
        val spacing = height / (WAVE_COUNT + 1)
        val step = (width / 120f).coerceAtLeast(4f)

        for (w in 0 until WAVE_COUNT) {
            val baseOffsetY = spacing * 0.9f + w * spacing * 1.05f
            val wavePhase = phase + w * 0.6f

            // Resting alpha fades with depth; speech brightens the whole set. The level term is 1
            // at silence, so a quiet keyboard looks exactly as the theme intends.
            val restAlpha = REST_ALPHA - w * (REST_ALPHA_FALLOFF / WAVE_COUNT)
            val alpha = (restAlpha * (1f + level * ALPHA_LEVEL_GAIN)).coerceIn(0.02f, 0.85f)
            paint.color = Color.argb((alpha * 255).toInt(), red, green, blue)

            // Resting height in dp, scaled by the level. The web values are cut roughly in half:
            // they were drawn for a full-page hero, not for a few hundred pixels of keyboard.
            val amp = (AMP_BASE_DP + (w % 3) * AMP_STEP_DP) * density *
                    (1f + level * AMP_LEVEL_GAIN)

            var x = 0f
            var first = true
            while (x <= width) {
                val sin1 = sin(x * (0.015f + (w % 2) * 0.003f) + wavePhase)
                val sin2 = sin(x * 0.035f - wavePhase * (0.7f + (w % 2) * 0.2f)) * 0.45f
                val cos1 = cos(x * 0.012f + wavePhase * 0.6f) * 0.25f
                val y = baseOffsetY + (sin1 + sin2 + cos1) * amp
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

    private fun animationsEnabled(): Boolean = try {
        AndroidSettings.Global.getFloat(
            context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
    } catch (e: Exception) {
        true
    }

    companion object {
        private const val WAVE_COUNT = 5
        private const val FRAME_INTERVAL_MS = 33L // ~30fps; 60 buys nothing here and costs battery
        private const val LEVEL_SMOOTHING = 0.28f
        private const val PHASE_STEP = 0.025f
        private const val PHASE_LEVEL_GAIN = 3.2f
        private const val ALPHA_LEVEL_GAIN = 3.0f
        private const val AMP_LEVEL_GAIN = 4.0f
        private const val AMP_BASE_DP = 3.0f
        private const val AMP_STEP_DP = 1.4f
        private const val REST_ALPHA = 0.30f
        private const val REST_ALPHA_FALLOFF = 0.15f
        private const val STROKE_WIDTH_DP = 1.2f
    }
}
