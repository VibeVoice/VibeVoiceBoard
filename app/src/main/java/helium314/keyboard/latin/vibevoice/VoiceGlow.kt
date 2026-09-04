// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.vibevoice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs

/**
 * The light behind the VibeVoice mark while a session is running.
 *
 * A glow shaped like the thing that glows. The mark's own silhouette is blurred and painted on the
 * layer underneath it, so the light follows the strokes rather than sitting behind them as a disc --
 * a circle behind a letterform is either too faint to see or bright enough to swallow it, and there
 * is no setting of a radial gradient that avoids both.
 *
 * This produces only the glow. The mark itself is never touched, re-rendered or re-tinted: callers
 * draw the ordinary drawable on top, exactly as they would with no glow at all, which is the only
 * way to guarantee it looks the same either way. Rebuilding the mark into the same bitmap is what
 * made it come out a different size once already.
 *
 * Rendered once and kept. [BlurMaskFilter] forces a paint onto the software pipeline, which is not
 * somewhere a keyboard's draw loop should go; a bitmap prepared in advance is an ordinary
 * hardware-canvas draw.
 */
object VoiceGlow {

    private const val MIN_BLUR_PX = 1.5f

    /**
     * How much the blurred coverage is multiplied before it is painted, clipped at full.
     *
     * A blur spreads a fixed amount of alpha over a larger area, so one pass of it stays faint
     * however high the paint's alpha goes -- there is not enough coverage in any one pixel to
     * raise. This raises the coverage itself, which makes the light denser near the mark without
     * moving the radius, keeping density and size as separate knobs.
     */


    /**
     * The blurred silhouette of [drawable], rendered at [box] pixels square in [color].
     *
     * The bitmap is larger than [box] on every side; [outMargin] receives by how much, so the
     * caller can line it up with the mark. The margin exists **before** the blur runs -- blurring
     * inside the mark's own bounds clips the light at its edge, and a clipped glow looks exactly
     * like what it is, a hard rectangle.
     */
    fun render(context: Context, drawable: Drawable, box: Int, color: Int, outMargin: IntArray): Bitmap? {
        if (box <= 0) return null
        return try {
            val prefs = context.prefs()
            val fraction = prefs.getFloat(Settings.PREF_GLOW_SIZE, Defaults.PREF_GLOW_SIZE)
            val gain = prefs.getFloat(Settings.PREF_GLOW_GAIN, Defaults.PREF_GLOW_GAIN)
            val radius = (box * fraction).coerceAtLeast(MIN_BLUR_PX)
            // Three sigma out, a Gaussian has nothing left worth drawing.
            val margin = Math.ceil((radius * 3f).toDouble()).toInt()
            val size = box + margin * 2
            val padded = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(padded)
            val saved = Rect(drawable.bounds)
            drawable.setBounds(margin, margin, margin + box, margin + box)
            drawable.draw(canvas)
            drawable.bounds = saved

            val blurPaint = Paint().apply {
                maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
            }
            val offset = IntArray(2)
            val alpha = padded.extractAlpha(blurPaint, offset)
            padded.recycle()

            val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val out = Canvas(result)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                // Scaling alpha past 1 in a colour matrix clamps rather than wraps, which is the
                // multiply-and-clip this wants.
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(1f, 1f, 1f, gain) })
            }
            out.drawBitmap(alpha, offset[0].toFloat(), offset[1].toFloat(), paint)
            alpha.recycle()
            outMargin[0] = margin
            result
        } catch (e: Exception) {
            VibeVoiceDebugLogger.log("Could not render the glow: ${e.message}")
            null
        }
    }
}
