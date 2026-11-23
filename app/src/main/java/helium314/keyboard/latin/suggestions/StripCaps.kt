// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.suggestions

import android.graphics.Rect
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

    /** One cap over the whole grid, from the first key's left edge to the last key's right edge. */
    fun wideCapRect(): Rect = Rect(left + hGap / 2, capTop, right - hGap / 2, capBottom)

    companion object {
        const val SLOTS = 10

        /** The cap is about this share of a key's height ... */
        const val CAP_TO_KEY_HEIGHT = 0.62f

        /**
         * Strip height that gives a cap of [capHeight] (at least [minCapHeight]) with half a row gap above
         * it and a full row gap between it and the first key row.
         */
        fun stripHeightFor(keyHeight: Int, vGap: Int, firstRowTop: Int, minCapHeight: Int): Int {
            val cap = maxOf((keyHeight * CAP_TO_KEY_HEIGHT).toInt(), minCapHeight)
            return vGap / 2 + cap + (vGap - firstRowTop).coerceAtLeast(0)
        }

        /**
         * @param boxLeft left edge of the first top-row key's cell (its drawn edge minus half a gap)
         * @param boxRight right edge of the last top-row key's cell
         * @param firstRowTop top of the first key row, in keyboard coordinates
         * @param vGap vertical gap between key rows
         * @param stripHeight height of the strip, which sits directly above the keyboard
         */
        fun from(
            boxLeft: Int, boxRight: Int, hGap: Int, firstRowTop: Int, vGap: Int, stripHeight: Int, minCapHeight: Int, slots: Int = SLOTS,
        ): StripCapGrid {
            val top = (vGap / 2).coerceAtLeast(0)
            var bottom = (stripHeight + firstRowTop - vGap).coerceAtMost(stripHeight)
            val capTop = if (bottom - top < minCapHeight) (bottom - minCapHeight).coerceAtLeast(0) else top
            bottom = bottom.coerceAtLeast(capTop + 1)
            return StripCapGrid(boxLeft, boxRight, capTop, bottom, hGap, slots)
        }

        /**
         * The grid of [keyboard]'s top key row, or null if there is no usable row. The strip is made as
         * tall as [stripHeight] says: pass [stripHeightOf] for the height the caps need.
         */
        fun fromKeyboard(keyboard: Keyboard, stripHeight: Int, minCapHeight: Int, slots: Int = SLOTS): StripCapGrid? {
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
            return from(boxLeft, boxRight, hGap, firstRowTop, first.verticalGap, stripHeight, minCapHeight, slots)
        }

        /** The strip height that fits the caps above [keyboard]'s top key row, or null if there is no usable row. */
        fun stripHeightOf(keyboard: Keyboard, minCapHeight: Int): Int? {
            val keys = keyboard.sortedKeys.filter { !it.isSpacer && it.width > 0 }
            if (keys.isEmpty()) return null
            val firstRowTop = keys.minOf { it.y }
            val first = keys.filter { it.y == firstRowTop }.minBy { it.x }
            return stripHeightFor(first.height, first.verticalGap, firstRowTop, minCapHeight)
        }
    }
}
