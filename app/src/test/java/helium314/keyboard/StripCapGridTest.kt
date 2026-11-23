// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import helium314.keyboard.latin.suggestions.StripCapGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StripCapGridTest {
    // 1440 px wide, 10 cells of 140 px, 18 px gap, 20 px side padding
    private val grid = StripCapGrid.from(
        boxLeft = 20, boxRight = 1420, hGap = 18, firstRowTop = 25, vGap = 50, stripHeight = 110, minCapHeight = 40
    )

    @Test
    fun wideCap_hasTheKeyGridsEdges() {
        val wide = grid.wideCapRect()
        assertEquals(20 + 9, wide.left) // first key's drawn left edge
        assertEquals(1420 - 9, wide.right) // last key's drawn right edge
    }

    @Test
    fun tenSlots_coverTheWholeGridEvenly() {
        assertEquals(140, grid.slotWidth)
        assertEquals(20, grid.slotLeft(0))
        assertEquals(1420, grid.slotLeft(10))
        for (i in 0 until 10) assertEquals(140, grid.slotLeft(i + 1) - grid.slotLeft(i))
    }

    @Test
    fun capsLeaveTheRowGapToTheFirstKeyRow() {
        // the strip sits right above the keyboard: the first key row starts firstRowTop below its bottom edge
        assertEquals(50, (110 - grid.capBottom) + 25)
        assertTrue(grid.capTop >= 0)
        assertTrue(grid.capBottom - grid.capTop >= 40)
    }

    @Test
    fun capNeverLeavesTheStripOrCollapses() {
        val tall = StripCapGrid.from(0, 1000, 10, firstRowTop = 200, vGap = 20, stripHeight = 100, minCapHeight = 40)
        assertTrue(tall.capBottom <= 100)
        assertTrue(tall.capBottom - tall.capTop >= 40)
    }

    @Test
    fun stripHeight_givesACapTallerThanTheMinimumWithTheRowGapBelow() {
        // key 130 px tall: 0.62 of it is 80 px, the 100 px minimum wins; gap above is half a row gap, below a full one
        val h = StripCapGrid.stripHeightFor(keyHeight = 130, vGap = 32, firstRowTop = 16, minCapHeight = 100)
        val g = StripCapGrid.from(0, 1000, 14, firstRowTop = 16, vGap = 32, stripHeight = h, minCapHeight = 100, slots = 11)
        assertEquals(100, g.capBottom - g.capTop)
        assertEquals(32, (h - g.capBottom) + 16)
        assertEquals(11, g.slots)
    }
}
