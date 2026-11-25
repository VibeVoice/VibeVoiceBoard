// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import android.content.Context
import android.os.Vibrator
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.LAST_VERSION_WITH_OLD_VIBRATE_AND_DRAW_KEYS_DEFAULTS
import helium314.keyboard.latin.checkVersionUpgrade
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
    fun freshInstallKeepsVibrationOff() {
        // no stored version: onUpgrade returns early, so nothing writes vibrate_on
        checkVersionUpgrade(context)

        val prefs = context.prefs()
        assertFalse(prefs.contains(Settings.PREF_VIBRATE_ON))
        assertFalse(Settings.readVibrationEnabled(prefs))
    }

    @Test
    fun upgradeKeepsVibrationOnForUserWhoNeverStoredIt() {
        val prefs = context.prefs()
        prefs.edit().putInt(Settings.PREF_VERSION_CODE, LAST_VERSION_WITH_OLD_VIBRATE_AND_DRAW_KEYS_DEFAULTS - 1).apply()
        assertFalse(prefs.contains(Settings.PREF_VIBRATE_ON))

        checkVersionUpgrade(context)

        assertTrue(prefs.getBoolean(Settings.PREF_VIBRATE_ON, false))
        assertTrue(Settings.readVibrationEnabled(prefs))
    }

    @Test
    fun upgradeDoesNotOverrideExplicitChoice() {
        val prefs = context.prefs()
        prefs.edit()
            .putInt(Settings.PREF_VERSION_CODE, LAST_VERSION_WITH_OLD_VIBRATE_AND_DRAW_KEYS_DEFAULTS - 1)
            .putBoolean(Settings.PREF_VIBRATE_ON, false)
            .apply()

        checkVersionUpgrade(context)

        assertFalse(prefs.getBoolean(Settings.PREF_VIBRATE_ON, true))
    }

    @Test
    fun upgradeFromNewDefaultsDoesNotEnableVibration() {
        // an install that already ran with vibration off by default must stay off on later upgrades
        val prefs = context.prefs()
        prefs.edit().putInt(Settings.PREF_VERSION_CODE, LAST_VERSION_WITH_OLD_VIBRATE_AND_DRAW_KEYS_DEFAULTS + 1).apply()

        checkVersionUpgrade(context)

        assertFalse(prefs.contains(Settings.PREF_VIBRATE_ON))
    }
}
