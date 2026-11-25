package helium314.keyboard.latin.utils

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric ships a default Material You palette, so the selection logic (which shade, night or day, and the
 * fallback below Android 12) is tested for real; the colours it returns are Robolectric's, not those of a phone
 * with a wallpaper seed such as #81B2C6.
 */
@RunWith(RobolectricTestRunner::class)
class SystemAccentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    @Config(sdk = [34])
    fun recordingColorIsAccent1_100AtNightAndAccent1_600ByDay() {
        assertEquals(ContextCompat.getColor(context, android.R.color.system_accent1_100), SystemAccent.recordingColor(context, night = true))
        assertEquals(ContextCompat.getColor(context, android.R.color.system_accent1_600), SystemAccent.recordingColor(context, night = false))
        assertNotEquals(SystemAccent.recordingColor(context, night = true), SystemAccent.recordingColor(context, night = false))
    }

    @Test
    @Config(sdk = [34])
    fun keyboardAccentIsAccent1_200AtNightAndAccent1_600ByDay() {
        assertEquals(ContextCompat.getColor(context, android.R.color.system_accent1_200), SystemAccent.keyboardAccent(context, night = true))
        assertEquals(ContextCompat.getColor(context, android.R.color.system_accent1_600), SystemAccent.keyboardAccent(context, night = false))
    }

    @Test
    @Config(sdk = [30])
    fun belowAndroid12ThereIsNoSystemAccent() {
        assertEquals(SystemAccent.FALLBACK_RECORDING_COLOR, SystemAccent.recordingColor(context, night = true))
        assertEquals(SystemAccent.FALLBACK_RECORDING_COLOR, SystemAccent.recordingColor(context, night = false))
        assertNull(SystemAccent.keyboardAccent(context, night = true))
        assertNull(SystemAccent.keyboardAccent(context, night = false))
    }
}
