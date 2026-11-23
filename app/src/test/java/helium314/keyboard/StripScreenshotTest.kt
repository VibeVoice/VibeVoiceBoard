// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.checkVersionUpgrade
import helium314.keyboard.latin.settings.KeyboardPreset
import helium314.keyboard.latin.settings.createPrefKeyForBooleanSettings
import helium314.keyboard.latin.settings.KeyboardPresets
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.suggestions.ToolbarScrollView
import helium314.keyboard.latin.utils.prefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Renders the real input view with draw keys on, checks the toolbar needs no scrolling and writes a PNG
 * to $STRIP_SHOT_DIR (when set) so the look can be judged.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowInputMethodService::class, ShadowLooper::class], qualifiers = "w360dp-h800dp-night-xxhdpi")
class StripScreenshotTest {
    @Test fun xxhdpi_1080() = render("strip_1080")

    @Test @Config(qualifiers = "w360dp-h800dp-night-xhdpi") fun xhdpi_720() = render("strip_720")

    private fun layoutAll(view: View, w: Int, h: Int) {
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.AT_MOST))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    @Test @Config(qualifiers = "w411dp-h891dp-night-560dpi") fun gboard_1440() = render("gboard_1440", KeyboardPreset.GBOARD)

    @Test @Config(qualifiers = "w411dp-h891dp-notnight-560dpi") fun gboard_1440_light() = render("gboard_1440_light", KeyboardPreset.GBOARD)

    /** A phone that chose Gboard before the sizes were corrected: old stored values, then the upgrade runs, then we render at 1182 px. */
    @Test @Config(qualifiers = "w411dp-h891dp-night-460dpi") fun gboard_1182_migrated() = render("gboard_1182_migrated", null, oldGboard = true)

    @Test @Config(qualifiers = "w411dp-h891dp-night-460dpi") fun samsung_1182_migrated() = render("samsung_1182_migrated", null, oldSamsung = true)

    @Test @Config(qualifiers = "w411dp-h891dp-night-560dpi") fun samsung_1440() = render("samsung_1440", KeyboardPreset.SAMSUNG)

    private fun render(name: String, preset: KeyboardPreset? = null, oldGboard: Boolean = false, oldSamsung: Boolean = false) {
        ShadowLog.setupLogging()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.prefs().edit(commit = true) {
            clear()
            putBoolean(Settings.PREF_DRAW_KEYS, true)
        }
        if (preset != null) KeyboardPresets.apply(context, preset)
        if (oldGboard || oldSamsung) {
            val h = createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2)
            val g = createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2)
            val sd = createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 3)
            context.prefs().edit(commit = true) {
                putString(Settings.PREF_KEYBOARD_PRESET, if (oldGboard) "GBOARD" else "SAMSUNG")
                putBoolean(Settings.PREF_DRAW_KEYS, true)
                putBoolean(Settings.PREF_SHOW_NUMBER_ROW, oldSamsung)
                putFloat(h, 1.05f); putFloat(g, if (oldGboard) 1.10f else 1.35f); putFloat(sd, if (oldGboard) 0.14f else 0.26f)
                putInt(Settings.PREF_VERSION_CODE, 600063)
            }
            checkVersionUpgrade(context)
        }
        val latinIME = Robolectric.setupService(LatinIME::class.java)
        val switcher = KeyboardSwitcher.getInstance()
        val view = switcher.onCreateInputView(latinIME, true)
        latinIME.updateSuggestionStripView(view)
        switcher.reloadMainKeyboard()
        val strip = view.findViewById<SuggestionStripView>(R.id.suggestion_strip_view)
        strip.setToolbarVisibility(true)
        val w = context.resources.displayMetrics.widthPixels
        val h = context.resources.displayMetrics.heightPixels
        layoutAll(view, w, h)
        repeat(4) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            layoutAll(view, w, h)
        }
        // the view is not attached to a window, so the posted grid pass never runs: call it directly
        SuggestionStripView::class.java.getDeclaredMethod("applyKeyGrid").apply { isAccessible = true }.invoke(strip)
        repeat(3) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            layoutAll(view, w, h)
        }
        // same for the fit pass that a width change posts
        SuggestionStripView::class.java.getDeclaredMethod("fitToolbarKeys").apply { isAccessible = true }.invoke(strip)
        layoutAll(view, w, h)
        val bmp = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        System.getenv("STRIP_SHOT_DIR")?.let {
            File(it).mkdirs()
            File(it, "$name.png").outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.PNG, 100, o) }
        }
        for (f in listOf("grid", "useCaps")) { val fld = SuggestionStripView::class.java.getDeclaredField(f); fld.isAccessible = true; println("FIELD $f=${fld.get(strip)}") }
        val scroll = strip.findViewById<ToolbarScrollView>(R.id.toolbar_container)
        val toolbar = scroll.getChildAt(0) as ViewGroup
        println("SHOT $name ${bmp.width}x${bmp.height} strip h=${strip.height} icons=${toolbar.childCount} scrollW=${scroll.width} toolbarW=${toolbar.width} scrolling=${scroll.isScrollingEnabled}")
        // measure the icons in the rendered pixels: bright columns inside the cap, grouped into icons
        val gridField = SuggestionStripView::class.java.getDeclaredField("grid").apply { isAccessible = true }
        val g = gridField.get(strip) as helium314.keyboard.latin.suggestions.StripCapGrid
        val stripLoc = IntArray(2); strip.getLocationInWindow(stripLoc)
        val cap = g.wideCapRect()
        val bright = BooleanArray(bmp.width)
        for (x in cap.left + 2 until cap.right - 2) for (y in stripLoc[1] + cap.top + 2 until stripLoc[1] + cap.bottom - 2) {
            val c = bmp.getPixel(x, y)
            val ref = bmp.getPixel(cap.left + 3, stripLoc[1] + (cap.top + cap.bottom) / 2)
            val dd = Math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(ref)) + Math.abs(android.graphics.Color.green(c) - android.graphics.Color.green(ref)) + Math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(ref))
            if (dd > 250) { bright[x] = true; break }
        }
        val boxes = ArrayList<IntArray>()
        var x = cap.left
        while (x < cap.right) {
            if (bright[x]) {
                var end = x; var gap = 0; var i = x
                while (i < cap.right && gap <= 14) { if (bright[i]) { end = i; gap = 0 } else gap++; i++ }
                boxes.add(intArrayOf(x, end)); x = end + 1
            } else x++
        }
        val centers = boxes.map { (it[0] + it[1]) / 2 }
        println("MEASURE $name cap=${cap.left}..${cap.right} capH=${cap.height()} icons=${boxes.size}")
        println("MEASURE boxes=${boxes.map { "${it[0]}-${it[1]}" }}")
        println("MEASURE center-to-center=${centers.zipWithNext { a, b -> b - a }}")
        println("MEASURE edge gaps=${boxes.zipWithNext { a, b -> b[0] - a[1] }}")
        println("MEASURE padLeft=${boxes.first()[0] - cap.left} padRight=${cap.right - boxes.last()[1]}")
        val gaps = boxes.zipWithNext { a, b -> b[0] - a[1] }
        println("MEASURE logoW=${boxes.last()[1] - boxes.last()[0] + 1}")
        if (name.startsWith("strip")) {
            assertTrue("visible gaps must be equal: $gaps", gaps.max() - gaps.min() <= 3)
            assertTrue("edge paddings must be equal", Math.abs((boxes.first()[0] - cap.left) - (cap.right - boxes.last()[1])) <= 3)
        }
        run {
            val kb = switcher.keyboard!!
            val rows = kb.sortedKeys.filter { !it.isSpacer }.groupBy { it.y }.toSortedMap()
            val stripH = strip.height
            val sTop = stripLoc[1]
            val tops = rows.keys.toList()
            val heights = rows.values.map { it.first().height }
            println("GEOM $name W=${bmp.width} stripH=$stripH capTop=${g.capTop} capBottom=${g.capBottom} rowTops=${tops.map { it + sTop + stripH }} keyH=$heights " +
                "pitch=${tops.zipWithNext { a, b -> b - a }} lastRowBottom=${tops.last() + heights.last() + sTop + stripH} bmpH=${bmp.height} kbH=${kb.mOccupiedHeight} " +
                "bottomRowKeyW=${rows.values.last().sortedBy { it.x }.map { "%.3f".format(it.width / bmp.width.toFloat()) }} keyW=${rows.values.first().first().width} hGap=${rows.values.first().first().horizontalGap} vGap=${rows.values.first().first().verticalGap} sideL=${rows.values.first().minOf { it.x }}")
        }
        // colour: brightest pixel of a toolbar icon (the gear, box 2) against the brightest key glyph (a letter)
        // the pixel farthest from the background colour (so it works for white-on-dark and dark-on-white)
        fun brightest(x0: Int, x1: Int, y0: Int, y1: Int, bgPixel: Int): Int {
            var best = bgPixel
            fun d(c: Int) = Math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(bgPixel)) + Math.abs(android.graphics.Color.green(c) - android.graphics.Color.green(bgPixel)) + Math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(bgPixel))
            for (x in x0 until x1) for (y in y0 until y1) { val c = bmp.getPixel(x, y); if (d(c) > d(best)) best = c }
            return best and 0xFFFFFF
        }
        val gear = boxes[1]
        val capBg = bmp.getPixel(cap.left + 3, stripLoc[1] + (cap.top + cap.bottom) / 2)
        val iconColor = brightest(gear[0], gear[1] + 1, stripLoc[1] + cap.top, stripLoc[1] + cap.bottom, capBg)
        val kbAll = switcher.keyboard!!
        val sTopAll = stripLoc[1] + strip.height
        val letterKey = kbAll.sortedKeys.first { it.label == "q" || it.label == "Q" || it.label == "a" }
        val glyphColor = brightest(letterKey.x + letterKey.width / 4, letterKey.x + letterKey.width * 3 / 4, sTopAll + letterKey.y + letterKey.height / 4, sTopAll + letterKey.y + letterKey.height * 3 / 4, bmp.getPixel(letterKey.x + 6, sTopAll + letterKey.y + letterKey.height - 6))
        println("COLOR $name toolbarIcon=#${"%06X".format(iconColor)} keyGlyph=#${"%06X".format(glyphColor)} logo=#${"%06X".format(brightest(boxes.last()[0], boxes.last()[1] + 1, stripLoc[1] + cap.top, stripLoc[1] + cap.bottom, capBg))}")
        assertTrue("toolbar icons must have the key glyph colour", iconColor == glyphColor)
        // hints: distance of hint pixels (top right quadrant, differing from the cap colour) to the key's right and top edge
        run {
            val rowsByY = kbAll.sortedKeys.filter { !it.isSpacer && it.label?.length == 1 }.groupBy { it.y }.toSortedMap().values.toList()
            rowsByY.take(2).forEachIndexed { rowIndex, row ->
                var minRight = Int.MAX_VALUE; var minTop = Int.MAX_VALUE; var found = 0
                for (k in row) {
                    val bg = bmp.getPixel(k.x + 6, sTopAll + k.y + k.height - 6)
                    val outside = bmp.getPixel(k.x + k.width + 2, sTopAll + k.y + k.height / 2) // the gap beside the key: what the rounded corners show
                    fun diffTo(c: Int, ref: Int) = Math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(ref)) + Math.abs(android.graphics.Color.green(c) - android.graphics.Color.green(ref)) + Math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(ref))
                    fun diff(c: Int) = Math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(bg)) + Math.abs(android.graphics.Color.green(c) - android.graphics.Color.green(bg)) + Math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(bg))
                    for (x in k.x + k.width * 6 / 10 until k.x + k.width) for (y in sTopAll + k.y until sTopAll + k.y + k.height * 4 / 10) {
                        if (diff(bmp.getPixel(x, y)) > 90 && diffTo(bmp.getPixel(x, y), outside) > 40) { found++; minRight = minOf(minRight, k.x + k.width - 1 - x); minTop = minOf(minTop, y - (sTopAll + k.y)) }
                    }
                }
                println("HINT $name row$rowIndex minDistToRightEdge=$minRight minDistToTopEdge=$minTop pixels=$found")
            }
        }
        val voice = strip.findViewById<ViewGroup>(R.id.voice_anchor)
        (voice.getChildAt(0) as? android.widget.ImageView)?.let { println("VOICEDBG filter=${it.colorFilter} drawable=${it.drawable} tint=${it.imageTintList} activated=${it.isActivated}") }
        val loc = IntArray(2); voice.getLocationInWindow(loc)
        var brightest = 0
        for (y in loc[1] until loc[1] + voice.height) for (x in loc[0] until (loc[0] + voice.width).coerceAtMost(bmp.width)) {
            val c = bmp.getPixel(x, y); brightest = maxOf(brightest, minOf(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c)))
        }
        println("LOGO brightest pixel min-channel=$brightest")
        if (!name.contains("light")) assertTrue("logo must be pure white, was $brightest", brightest >= 254)
        assertFalse("toolbar must not scroll with draw keys on", scroll.isScrollingEnabled)
        assertTrue(toolbar.width <= scroll.width)
    }
}
