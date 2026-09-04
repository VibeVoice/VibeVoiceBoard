// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.vibevoice

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * The light around the VibeVoice mark while a session is running.
 *
 * A glow in the shape of the thing that glows, not a circle behind it. The mark's own silhouette is
 * blurred and drawn underneath it in the accent colour, so the light follows the strokes and shows
 * only in the few pixels around them -- the mark stays crisp because it is painted on top, opaque.
 * A radial gradient cannot do this: it is a circle behind a letterform, and made bright enough to
 * read it washes the letterform out, which is exactly what happened when this was tried that way.
 *
 * Rendered once and kept, never per frame. [BlurMaskFilter] forces a paint onto the software
 * pipeline, which is not somewhere a keyboard's draw loop should go; a bitmap prepared in advance
 * draws on the hardware canvas like anything else. The result is also deliberately static: in the
 * floating mark the ring of bars already carries the level, and two things pulsing at once compete
 * rather than agree.
 */
object VoiceGlow {

    /** Blur as a fraction of the mark's box. Enough to read as light, not so much as to be fog. */
    private const val BLUR_FRACTION = 0.16f
    private const val MIN_BLUR_PX = 2f
    /**
     * How many times the blurred silhouette is laid over itself.
     *
     * A blur spreads a fixed amount of coverage over a larger area, so one pass of it is faint
     * however high the alpha goes -- there is simply not much alpha in any one pixel. Stacking the
     * same bitmap builds the density back up where the shape is dense and leaves the thin outer
     * edge thin, which is what a glow looks like.
     */
    const val PASSES = 3

    /**
     * The blurred silhouette of [drawable] at [box] pixels square.
     *
     * The returned bitmap is larger than [box] -- the blur needs room -- and [outOffset] receives
     * how far up and left of the mark's own top-left corner it must be drawn. Returns null when
     * there is nothing to render.
     */
    fun silhouette(drawable: Drawable, box: Int, outOffset: IntArray): Bitmap? {
        if (box <= 0) return null
        return try {
            val source = Bitmap.createBitmap(box, box, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(source)
            val saved = Rect(drawable.bounds)
            drawable.setBounds(0, 0, box, box)
            drawable.draw(canvas)
            drawable.bounds = saved
            val blur = (box * BLUR_FRACTION).coerceAtLeast(MIN_BLUR_PX)
            val paint = Paint().apply { maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) }
            // extractAlpha gives back the shape's coverage with the blur applied, and reports how
            // much bigger it had to be. Drawn with a coloured paint, that alpha is the glow.
            val glow = source.extractAlpha(paint, outOffset)
            source.recycle()
            glow
        } catch (e: Exception) {
            VibeVoiceDebugLogger.log("Could not render the glow: ${e.message}")
            null
        }
    }

    /**
     * The mark with its glow already behind it, as one bitmap.
     *
     * For places that can only be handed a single image -- the toolbar key is an ImageButton, and
     * layering there would mean fighting its scale type and its background.
     */
    fun markWithGlow(drawable: Drawable, box: Int, glowColor: Int, markColor: Int, glowAlpha: Int): Bitmap? {
        if (box <= 0) return null
        // The mark is rendered at exactly [box] and the bitmap grows around it, so whatever draws
        // this must not scale it -- at ScaleType.CENTER the mark comes out the size it would have
        // been with no glow at all. FIT_CENTER would shrink it by the width of the padding, which
        // is the whole point of returning the size in the bitmap rather than a scale factor.
        val offset = IntArray(2)
        val glow = silhouette(drawable, box, offset) ?: return null
        return try {
            // The padding is however much room the blur asked for, which is why it is taken from
            // the offset rather than guessed.
            val pad = maxOf(-offset[0], -offset[1], 0)
            val size = box + pad * 2
            val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = glowColor
                alpha = glowAlpha
            }
            repeat(PASSES) {
                canvas.drawBitmap(glow, (pad + offset[0]).toFloat(), (pad + offset[1]).toFloat(), glowPaint)
            }
            glow.recycle()

            val saved = Rect(drawable.bounds)
            if (markColor == Color.TRANSPARENT) {
                drawable.setBounds(pad, pad, pad + box, pad + box)
                drawable.draw(canvas)
                drawable.bounds = saved
            } else {
                // Tinted on its own layer and then composited. Tinting the view instead would have
                // coloured the glow along with the mark, which is the coloured-glyph-on-its-own-
                // colour problem again.
                val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val layerCanvas = Canvas(layer)
                drawable.setBounds(pad, pad, pad + box, pad + box)
                drawable.draw(layerCanvas)
                drawable.bounds = saved
                val tint = Paint().apply {
                    xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                    color = markColor
                }
                layerCanvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), tint)
                canvas.drawBitmap(layer, 0f, 0f, null)
                layer.recycle()
            }
            result
        } catch (e: Exception) {
            VibeVoiceDebugLogger.log("Could not compose the glowing mark: ${e.message}")
            null
        }
    }
}
