// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.content.Context
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class VibrationSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @BeforeTest
    fun setUp() {
        AudioAndHapticFeedbackManager.init(context)
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        shadowOf(vibrator).setHasVibrator(true)
        context.prefs().edit().clear().apply()
    }

    @Test
    fun defaultVibrationIsOff() {
        // PREF_VIBRATE_ON constant must be false
        assertFalse(Defaults.PREF_VIBRATE_ON)

        // On fresh preferences with no value stored, vibration is disabled
        val prefs = context.prefs()
        assertFalse(prefs.contains(Settings.PREF_VIBRATE_ON))
        assertFalse(prefs.getBoolean(Settings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON))
        assertFalse(Settings.readVibrationEnabled(prefs))
    }

    @Test
    fun existingUserSettingPreserved() {
        val prefs = context.prefs()
        // Simulate user who explicitly had vibration enabled previously
        prefs.edit().putBoolean(Settings.PREF_VIBRATE_ON, true).apply()
        assertTrue(prefs.getBoolean(Settings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON))
        assertTrue(Settings.readVibrationEnabled(prefs))

        // Simulate user who explicitly had vibration disabled previously
        prefs.edit().putBoolean(Settings.PREF_VIBRATE_ON, false).apply()
        assertFalse(prefs.getBoolean(Settings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON))
        assertFalse(Settings.readVibrationEnabled(prefs))
    }

    @Test
    fun wizardAnswerYesEnablesVibration() {
        val prefs = context.prefs()
        // Answering "Yes" in the setup wizard sets PREF_VIBRATE_ON to true
        prefs.edit().putBoolean(Settings.PREF_VIBRATE_ON, true).apply()
        assertTrue(prefs.contains(Settings.PREF_VIBRATE_ON))
        assertTrue(Settings.readVibrationEnabled(prefs))
    }

    @Test
    fun wizardAnswerNoDisablesVibration() {
        val prefs = context.prefs()
        // Answering "No" in the setup wizard explicitly sets PREF_VIBRATE_ON to false
        prefs.edit().putBoolean(Settings.PREF_VIBRATE_ON, false).apply()
        assertTrue(prefs.contains(Settings.PREF_VIBRATE_ON))
        assertFalse(Settings.readVibrationEnabled(prefs))
    }
}
