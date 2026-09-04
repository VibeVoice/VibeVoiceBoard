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
import android.graphics.RectF
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

    /**
     * Measured once per artwork. Keyed rather than a single slot: two drawables go through here,
     * and a shared slot would hand one of them the other's bounds.
     */
    private val inkCache = HashMap<Any, RectF>()

    /**
     * The fraction of a drawable's viewport that it actually paints, as a 0..1 rectangle.
     *
     * Rasterised and scanned rather than derived from the vector's path data: path data is
     * relative, control points lie outside the curve they describe, and both make a computed
     * bounding box wrong in exactly the direction that matters. One 96x96 bitmap, once.
     */
    fun inkBounds(drawable: Drawable): RectF {
        val key: Any = drawable.constantState ?: drawable.javaClass
        inkCache[key]?.let { return it }
        val n = 96
        val result = RectF(0f, 0f, 1f, 1f)
        try {
            val bitmap = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val saved = Rect(drawable.bounds)
            drawable.setBounds(0, 0, n, n)
            drawable.draw(canvas)
            drawable.bounds = saved
            var minX = n; var minY = n; var maxX = -1; var maxY = -1
            val row = IntArray(n)
            for (y in 0 until n) {
                bitmap.getPixels(row, 0, n, 0, y, n, 1)
                for (x in 0 until n) {
                    if ((row[x] ushr 24) > 8) {
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }
            bitmap.recycle()
            if (maxX >= minX && maxY >= minY) {
                result.set(minX / n.toFloat(), minY / n.toFloat(), (maxX + 1) / n.toFloat(), (maxY + 1) / n.toFloat())
            }
        } catch (e: Exception) {
            VibeVoiceDebugLogger.log("Could not measure the mark: ${e.message}")
        }
        inkCache[key] = result
        return result
    }

    /**
     * [drawable] rendered so that what it actually paints spans [inkPx], centred on a transparent
     * square of that size.
     *
     * The launcher artwork carries the padding an adaptive icon needs, so drawn at its own bounds
     * it comes out about a third smaller than it looks. Sizing by the ink instead makes it match
     * whatever it stands next to, and keeps "how big is the mark" a question about the visible
     * shape rather than about a viewBox.
     */
    fun renderMark(drawable: Drawable, inkPx: Int): Bitmap? {
        if (inkPx <= 0) return null
        return try {
            val ink = inkBounds(drawable)
            val span = maxOf(ink.width(), ink.height()).coerceAtLeast(0.01f)
            val box = (inkPx / span).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(inkPx, inkPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val saved = Rect(drawable.bounds)
            val left = (inkPx / 2f - box * (ink.left + ink.width() / 2f)).toInt()
            val top = (inkPx / 2f - box * (ink.top + ink.height() / 2f)).toInt()
            drawable.setBounds(left, top, left + box, top + box)
            drawable.draw(canvas)
            drawable.bounds = saved
            bitmap
        } catch (e: Exception) {
            VibeVoiceDebugLogger.log("Could not render the mark: ${e.message}")
            null
        }
    }
}
