// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import helium314.keyboard.keyboard.Keyboard

/**
 * Where the strip's key caps go when keys are drawn (draw_keys on): the strip is laid out on the
 * key grid of the keyboard below it.
 *
 * [left] and [right] bound the ten columns of the top key row *including* the half gap on each
 * side, so a cap is its column minus [hGap]/2 on both sides and its edges are exactly the first
 * key's left edge and the last key's right edge. [capTop] and [capBottom] are in the strip's own
 * coordinates; [capBottom] leaves the same gap to the first key row as there is between key rows.
 */
data class StripCapGrid(
    val left: Int,
    val right: Int,
    val capTop: Int,
    val capBottom: Int,
    val hGap: Int,
    val slots: Int = SLOTS,
) {
    val slotWidth: Int get() = (right - left) / slots

    fun slotLeft(index: Int): Int = left + (right - left) * index / slots

    fun slotRight(index: Int): Int = slotLeft(index + 1)

    /** Cap of one slot: the column minus the half gap on each side. */
    fun capRect(index: Int): Rect = Rect(slotLeft(index) + hGap / 2, capTop, slotRight(index) - hGap / 2, capBottom)

    /** One cap over the whole grid, from the first key's left edge to the last key's right edge. */
    fun wideCapRect(): Rect = Rect(left + hGap / 2, capTop, right - hGap / 2, capBottom)

    companion object {
        const val SLOTS = 10

        /**
         * @param boxLeft left edge of the first top-row key's cell (its drawn edge minus half a gap)
         * @param boxRight right edge of the last top-row key's cell
         * @param firstRowTop top of the first key row, in keyboard coordinates
         * @param vGap vertical gap between key rows
         * @param stripHeight height of the strip, which sits directly above the keyboard
         */
        fun from(boxLeft: Int, boxRight: Int, hGap: Int, firstRowTop: Int, vGap: Int, stripHeight: Int, minCapHeight: Int): StripCapGrid {
            val top = (vGap / 2).coerceAtLeast(0)
            var bottom = (stripHeight + firstRowTop - vGap).coerceAtMost(stripHeight)
            val capTop = if (bottom - top < minCapHeight) (bottom - minCapHeight).coerceAtLeast(0) else top
            bottom = bottom.coerceAtLeast(capTop + 1)
            return StripCapGrid(boxLeft, boxRight, capTop, bottom, hGap)
        }

        /** The grid of [keyboard]'s top key row, or null if there is no usable row. */
        fun fromKeyboard(keyboard: Keyboard, stripHeight: Int, minCapHeight: Int): StripCapGrid? {
            val keys = keyboard.sortedKeys.filter { !it.isSpacer && it.width > 0 }
            if (keys.isEmpty()) return null
            val firstRowTop = keys.minOf { it.y }
            val row = keys.filter { it.y == firstRowTop }
            val first = row.minBy { it.x }
            val last = row.maxBy { it.x + it.width }
            val hGap = first.horizontalGap
            val boxLeft = first.x - hGap / 2
            val boxRight = last.x + last.width + hGap / 2
            if (boxRight <= boxLeft) return null
            return from(boxLeft, boxRight, hGap, firstRowTop, first.verticalGap, stripHeight, minCapHeight)
        }
    }
}

/** Mutable numbers shared by all [KeyCapDrawable]s of one strip, so a new grid reaches them without rebuilding. */
class CapMetrics {
    var hInset = 0
    var top = 0
    var bottom = 0

    /** True while the toolbar icons are shown: each icon has its own cap. Otherwise one wide cap is drawn by the strip. */
    var perKeyCaps = false
}

/**
 * A toolbar key's background when keys are drawn: the keyboard's own key drawable (same colour,
 * alpha, corner radius and pressed state), inset to the cap's rectangle, plus an optional
 * [extra] layer (pinned highlight, recording glow) over the full view bounds.
 */
class KeyCapDrawable(private val cap: Drawable, private val extra: Drawable?, private val metrics: CapMetrics) : Drawable() {
    override fun draw(canvas: Canvas) {
        val b = bounds
        if (metrics.perKeyCaps) {
            cap.setBounds(b.left + metrics.hInset, b.top + metrics.top, b.right - metrics.hInset, b.bottom - metrics.bottom)
            cap.draw(canvas)
        }
        extra?.let {
            it.setBounds(b)
            it.draw(canvas)
        }
    }

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val changed = cap.setState(state)
        val extraChanged = extra?.setState(state) ?: false
        return changed || extraChanged
    }

    override fun setAlpha(alpha: Int) {
        cap.alpha = alpha
        extra?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        extra?.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
