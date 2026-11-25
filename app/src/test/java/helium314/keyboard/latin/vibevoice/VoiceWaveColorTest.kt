package helium314.keyboard.latin.vibevoice

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.settings.KeyboardAccent
import helium314.keyboard.latin.settings.KeyboardPreset
import helium314.keyboard.latin.settings.KeyboardPresets
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The waves have one colour of their own: not the theme's gesture trail, not the preset's palette, not the accent.
 * Draws the real [VoiceWaveView] and samples its pixels; writes PNGs to $STRIP_SHOT_DIR when set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w384dp-h832dp-night-600dpi")
class VoiceWaveColorTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private class FakeSession(override val currentLevel: Float) : VoiceSession {
        override val isLinkDegraded = false
        override fun startStreaming() {}
        override fun stopStreaming() {}
        override fun cancel() {}
    }

    /** Draws the running waves a few frames in, over [background] (transparent for sampling), at a speech level. */
    private fun draw(background: Int): Bitmap {
        val view = VoiceWaveView(context)
        view.start(FakeSession(0.8f))
        view.measure(View.MeasureSpec.makeMeasureSpec(1182, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1182, 420)
        val bmp = Bitmap.createBitmap(1182, 420, Bitmap.Config.ARGB_8888)
        repeat(12) { // the level eases in over a few frames
            bmp.eraseColor(background)
            view.draw(Canvas(bmp))
        }
        view.stop()
        return bmp
    }

    /** The colour of the most opaque wave pixel: the line's own colour, where the anti-aliasing does not blend it. */
    private fun waveColor(bmp: Bitmap): Pair<Int, Int> {
        var bestAlpha = 0
        var best = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
            val c = bmp.getPixel(x, y)
            if (Color.alpha(c) > bestAlpha) { bestAlpha = Color.alpha(c); best = c }
        }
        return best and 0xFFFFFF to bestAlpha
    }

    private fun save(name: String, bmp: Bitmap) {
        System.getenv("STRIP_SHOT_DIR")?.let {
            File(it).mkdirs()
            File(it, "$name.png").outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.PNG, 100, o) }
        }
    }

    private fun hue(rgb: Int): Float = FloatArray(3).also { Color.colorToHSV(rgb or 0xFF000000.toInt(), it) }[0]

    @Test
    fun wavesUseTheirOwnColourWhateverThePresetOrAccent() {
        val colours = mutableListOf<Int>()
        for ((name, setup) in listOf<Pair<String, () -> Unit>>(
            "own_unset" to { context.prefs().edit(commit = true) { clear() } },
            "samsung_orange" to { context.prefs().edit(commit = true) { clear() }; KeyboardPresets.apply(context, KeyboardPreset.SAMSUNG); KeyboardPresets.setAccent(context, KeyboardAccent.ORANGE) },
            "gboard_custom_navy" to { context.prefs().edit(commit = true) { clear() }; KeyboardPresets.apply(context, KeyboardPreset.GBOARD); KeyboardPresets.setCustomAccent(context, 0xFF1E3A8A.toInt()) },
            "own_pink" to { context.prefs().edit(commit = true) { clear() }; KeyboardPresets.setAccent(context, KeyboardAccent.PINK) },
        )) {
            setup()
            val (rgb, alpha) = waveColor(draw(Color.TRANSPARENT))
            println("WAVECOLOR $name rgb=#%06X alpha=$alpha hue=%.1f".format(rgb, hue(rgb)))
            assertTrue("$name: most opaque wave pixel only had alpha $alpha", alpha >= 150)
            colours.add(rgb)
            save("wave_${name}_dark", draw(0xFF16141A.toInt()))
            save("wave_${name}_light", draw(0xFFECE8F0.toInt()))
        }
        // one colour for all of them, within the rounding of the alpha blend
        val first = colours.first()
        for (c in colours) {
            assertTrue("wave colours differ: ${colours.map { "#%06X".format(it) }}",
                Math.abs(Color.red(c) - Color.red(first)) <= 3 && Math.abs(Color.green(c) - Color.green(first)) <= 3 && Math.abs(Color.blue(c) - Color.blue(first)) <= 3)
        }
        // and it is the blue-turquoise
        val want = VoiceGlow.RECORDING_COLOR
        assertTrue("#%06X is not #%06X".format(first, want and 0xFFFFFF),
            Math.abs(Color.red(first) - Color.red(want)) <= 4 && Math.abs(Color.green(first) - Color.green(want)) <= 4 && Math.abs(Color.blue(first) - Color.blue(want)) <= 4)
        assertTrue("hue ${hue(first)}", hue(first) in 185f..205f)
    }

    @Test
    fun glowAndFloatingMarkShareTheWaveColour() {
        assertEquals(VoiceGlow.RECORDING_COLOR, VoiceOverlay(context).barColor)
    }
}
