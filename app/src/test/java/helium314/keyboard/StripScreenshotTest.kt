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

    private fun render(name: String) {
        ShadowLog.setupLogging()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.prefs().edit(commit = true) {
            clear()
            putBoolean(Settings.PREF_DRAW_KEYS, true)
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
        val voice = strip.findViewById<ViewGroup>(R.id.voice_anchor)
        val loc = IntArray(2); voice.getLocationInWindow(loc)
        var brightest = 0
        for (y in loc[1] until loc[1] + voice.height) for (x in loc[0] until (loc[0] + voice.width).coerceAtMost(bmp.width)) {
            val c = bmp.getPixel(x, y); brightest = maxOf(brightest, minOf(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c)))
        }
        println("LOGO brightest pixel min-channel=$brightest")
        assertTrue("logo must be pure white, was $brightest", brightest >= 254)
        assertFalse("toolbar must not scroll with draw keys on", scroll.isScrollingEnabled)
        assertTrue(toolbar.width <= scroll.width)
    }
}
