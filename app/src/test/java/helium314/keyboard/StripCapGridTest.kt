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
    fun tenCaps_lineUpWithTenKeyColumnsAndHaveTheKeyGap() {
        val caps = (0 until 10).map { grid.capRect(it) }
        assertEquals(grid.wideCapRect().left, caps.first().left)
        assertEquals(grid.wideCapRect().right, caps.last().right)
        for (i in 0 until 9) assertEquals("gap between cap $i and ${i + 1}", 18, caps[i + 1].left - caps[i].right)
        caps.forEach { assertEquals(140 - 18, it.width()) }
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
}
