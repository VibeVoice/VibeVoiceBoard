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
import helium314.keyboard.latin.settings.KeyboardAccent
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
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun gboard_1182_migrated() = render("gboard_1182_migrated", null, oldGboard = true)

    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun samsung_1182_migrated() = render("samsung_1182_migrated", null, oldSamsung = true)

    @Test @Config(qualifiers = "w384dp-h832dp-notnight-600dpi") fun gboard_phone_light() = render("gboard_phone_light", null, oldGboard = true)

    @Test @Config(qualifiers = "w384dp-h832dp-notnight-600dpi") fun samsung_phone_light() = render("samsung_phone_light", null, oldSamsung = true)

    @Test @Config(qualifiers = "w411dp-h891dp-night-560dpi") fun samsung_1440() = render("samsung_1440", KeyboardPreset.SAMSUNG)

    // the accent colors, on the real phone config (384 dp, 600 dpi = 1440 px wide scale of the Samsung / Gboard presets)
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_samsung_violet_dark() = render("accent_samsung_violet_dark", KeyboardPreset.SAMSUNG, accent = KeyboardAccent.VIOLET)
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_samsung_green_dark() = render("accent_samsung_green_dark", KeyboardPreset.SAMSUNG, accent = KeyboardAccent.GREEN)
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_samsung_orange_dark() = render("accent_samsung_orange_dark", KeyboardPreset.SAMSUNG, accent = KeyboardAccent.ORANGE)
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_samsung_pink_dark() = render("accent_samsung_pink_dark", KeyboardPreset.SAMSUNG, accent = KeyboardAccent.PINK)
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_samsung_graphite_dark() = render("accent_samsung_graphite_dark", KeyboardPreset.SAMSUNG, accent = KeyboardAccent.GRAPHITE)
    @Test @Config(qualifiers = "w384dp-h832dp-notnight-600dpi") fun accent_gboard_green_light() = render("accent_gboard_green_light", KeyboardPreset.GBOARD, accent = KeyboardAccent.GREEN)
    @Test @Config(qualifiers = "w384dp-h832dp-notnight-600dpi") fun accent_gboard_orange_light() = render("accent_gboard_orange_light", KeyboardPreset.GBOARD, accent = KeyboardAccent.ORANGE)
    @Test @Config(qualifiers = "w384dp-h832dp-notnight-600dpi") fun accent_gboard_graphite_light() = render("accent_gboard_graphite_light", KeyboardPreset.GBOARD, accent = KeyboardAccent.GRAPHITE)
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_own_orange_dark() = render("accent_own_orange_dark", KeyboardPreset.OWN, accent = KeyboardAccent.ORANGE)
    @Test @Config(qualifiers = "w384dp-h832dp-notnight-600dpi") fun accent_own_pink_light() = render("accent_own_pink_light", KeyboardPreset.OWN, accent = KeyboardAccent.PINK)
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_own_unset_dark() = render("accent_own_unset_dark", KeyboardPreset.OWN)

    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_samsung_auto_dark() = render("accent_samsung_auto_dark", KeyboardPreset.SAMSUNG, accent = KeyboardAccent.AUTO)
    @Test @Config(qualifiers = "w384dp-h832dp-notnight-600dpi") fun accent_gboard_auto_light() = render("accent_gboard_auto_light", KeyboardPreset.GBOARD, accent = KeyboardAccent.AUTO)
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_own_auto_dark() = render("accent_own_auto_dark", KeyboardPreset.OWN, accent = KeyboardAccent.AUTO)
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_samsung_vibevoice_dark() = render("accent_samsung_vibevoice_dark", KeyboardPreset.SAMSUNG, accent = KeyboardAccent.VIBEVOICE)
    @Test @Config(qualifiers = "w384dp-h832dp-notnight-600dpi") fun accent_gboard_vibevoice_light() = render("accent_gboard_vibevoice_light", KeyboardPreset.GBOARD, accent = KeyboardAccent.VIBEVOICE)
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_samsung_customyellow_dark() = render("accent_samsung_customyellow_dark", KeyboardPreset.SAMSUNG, custom = 0xFFFFD60A.toInt())
    @Test @Config(qualifiers = "w384dp-h832dp-notnight-600dpi") fun accent_gboard_customyellow_light() = render("accent_gboard_customyellow_light", KeyboardPreset.GBOARD, custom = 0xFFFFD60A.toInt())
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_samsung_customnavy_dark() = render("accent_samsung_customnavy_dark", KeyboardPreset.SAMSUNG, custom = 0xFF1E3A8A.toInt())
    @Test @Config(qualifiers = "w384dp-h832dp-notnight-600dpi") fun accent_gboard_customnavy_light() = render("accent_gboard_customnavy_light", KeyboardPreset.GBOARD, custom = 0xFF1E3A8A.toInt())
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_own_vibevoice_dark() = render("accent_own_vibevoice_dark", KeyboardPreset.OWN, accent = KeyboardAccent.VIBEVOICE)
    @Test @Config(qualifiers = "w384dp-h832dp-night-600dpi") fun accent_own_customyellow_dark() = render("accent_own_customyellow_dark", KeyboardPreset.OWN, custom = 0xFFFFD60A.toInt())

    private fun render(name: String, preset: KeyboardPreset? = null, oldGboard: Boolean = false, oldSamsung: Boolean = false, accent: KeyboardAccent? = null, custom: Int? = null) {
        ShadowLog.setupLogging()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.prefs().edit(commit = true) {
            clear()
            putBoolean(Settings.PREF_DRAW_KEYS, true)
        }
        if (preset != null) KeyboardPresets.apply(context, preset)
        if (accent != null) KeyboardPresets.setAccent(context, accent)
        if (custom != null) KeyboardPresets.setCustomAccent(context, custom)
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
        if (preset == KeyboardPreset.OWN) {
            // the Own preset has no cap grid (draw keys off): only sample the accent keys
            val sTop = IntArray(2).also { strip.getLocationInWindow(it) }[1] + strip.height
            val keys = switcher.keyboard!!.sortedKeys.filter { !it.isSpacer }
            val enter = keys.first { it.code == helium314.keyboard.latin.common.Constants.CODE_ENTER }
            val shift = keys.first { it.code == helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.SHIFT }
            fun px(k: helium314.keyboard.keyboard.Key, dx: Int) = bmp.getPixel(k.x + dx, sTop + k.y + k.height / 2) and 0xFFFFFF
            // the shift glyph is the accent: take the pixel farthest from the key's own edge color
            var shiftGlyph = px(shift, 5); var bestD = 0
            val edge = bmp.getPixel(shift.x + 5, sTop + shift.y + shift.height / 2)
            for (x in shift.x + shift.width / 4 until shift.x + shift.width * 3 / 4) for (y in sTop + shift.y + shift.height / 4 until sTop + shift.y + shift.height * 3 / 4) {
                val c = bmp.getPixel(x, y)
                val d = Math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(edge)) + Math.abs(android.graphics.Color.green(c) - android.graphics.Color.green(edge)) + Math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(edge))
                if (d > bestD) { bestD = d; shiftGlyph = c and 0xFFFFFF }
            }
            run {
                // glyph: pixel farthest from the circle color within the circle's middle
                val ecx = enter.x + enter.width / 2; val ecy = sTop + enter.y + enter.height / 2
                val circle = bmp.getPixel(ecx, ecy - 45)
                var best = circle; var bd = 0
                for (x in ecx - 30 until ecx + 30) for (y in ecy - 20 until ecy + 20) {
                    val c = bmp.getPixel(x, y)
                    val d = Math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(circle)) + Math.abs(android.graphics.Color.green(c) - android.graphics.Color.green(circle)) + Math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(circle))
                    if (d > bd) { bd = d; best = c }
                }
                println("OWNGLYPH $name enterGlyph=#%06X".format(best and 0xFFFFFF))
            }
            println("OWNCOLOR $name bg=#%06X enter=#%06X shiftKey=#%06X shiftGlyph=#%06X".format(bmp.getPixel(2, sTop + 2) and 0xFFFFFF, bmp.getPixel(enter.x + enter.width / 2, sTop + enter.y + enter.height / 2 - 45) and 0xFFFFFF, px(shift, 5), shiftGlyph))
            return
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
        run {
            val sTop2 = stripLoc[1] + strip.height
            fun px(k: helium314.keyboard.keyboard.Key) = "#%06X".format(bmp.getPixel(k.x + 5, sTop2 + k.y + k.height / 2) and 0xFFFFFF)
            val keys = kbAll.sortedKeys.filter { !it.isSpacer }
            val q = keys.first { it.label == "q" }
            val out = StringBuilder("KEYCOLOR $name bg=#%06X".format(bmp.getPixel(2, sTop2 + 2) and 0xFFFFFF))
            out.append(" letter=${px(q)}")
            keys.firstOrNull { it.code == helium314.keyboard.latin.common.Constants.CODE_SPACE }?.let { out.append(" space=${px(it)}") }
            keys.firstOrNull { it.code == helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.SHIFT }?.let { out.append(" shift=${px(it)}") }
            keys.firstOrNull { it.code == helium314.keyboard.latin.common.Constants.CODE_ENTER }?.let { out.append(" enter=${px(it)}") }
            keys.firstOrNull { it.code == helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.SYMBOL_ALPHA }?.let { out.append(" symbols=${px(it)}") }
            keys.firstOrNull { it.code == helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.LANGUAGE_SWITCH }?.let { out.append(" globe=${px(it)}") }
            keys.firstOrNull { it.code == ','.code }?.let { out.append(" comma=${px(it)}") }
            keys.firstOrNull { it.code == helium314.keyboard.latin.common.Constants.CODE_ENTER }?.let { k ->
                // glyph: the pixel farthest from the key color inside the key's middle third; its distance to the key color is the contrast cue
                val bgPx = bmp.getPixel(k.x + 5, sTop2 + k.y + k.height / 2)
                var best = bgPx; var bestD = 0
                for (x in k.x + k.width / 3 until k.x + k.width * 2 / 3) for (y in sTop2 + k.y + k.height / 3 until sTop2 + k.y + k.height * 2 / 3) {
                    val c = bmp.getPixel(x, y)
                    val d = Math.abs(android.graphics.Color.red(c) - android.graphics.Color.red(bgPx)) + Math.abs(android.graphics.Color.green(c) - android.graphics.Color.green(bgPx)) + Math.abs(android.graphics.Color.blue(c) - android.graphics.Color.blue(bgPx))
                    if (d > bestD) { bestD = d; best = c }
                }
                fun lum(c: Int): Double { fun f(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
                    return 0.2126 * f(android.graphics.Color.red(c)) + 0.7152 * f(android.graphics.Color.green(c)) + 0.0722 * f(android.graphics.Color.blue(c)) }
                val l1 = lum(best); val l2 = lum(bgPx)
                println("ENTERGLYPH $name key=#%06X glyph=#%06X contrast=%.2f".format(bgPx and 0xFFFFFF, best and 0xFFFFFF, (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)))
            }
            println(out)
            println("KEYS $name bottom=${keys.filter { it.y == keys.maxOf { k -> k.y } }.sortedBy { it.x }.map { "${it.label ?: it.code}:${it.x}+${it.width}" }}")
            println("KEYS $name numberRowKeyH=${keys.firstOrNull { it.label == "1" }?.height} qKeyH=${q.height} shiftW=${keys.firstOrNull { it.code == helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.SHIFT }?.width}")
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
