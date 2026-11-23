package helium314.keyboard.latin.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.utils.prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyboardPresetsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = context.prefs()

    @Test
    fun testApplyGboardPreset() {
        KeyboardPresets.apply(context, KeyboardPreset.GBOARD)

        assertEquals("GBOARD", prefs.getString(Settings.PREF_KEYBOARD_PRESET, null))
        assertEquals(KeyboardPreset.GBOARD, Settings.readKeyboardPreset(prefs))
        assertTrue(prefs.getBoolean(Defaults.PREF_DRAW_KEYS, false))
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, true))
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_HINTS, false))
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_EMOJI_KEY, false))
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, true))

        val heightKey = createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2)
        val gapKey = createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2)
        val paddingKey = createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 3)

        assertEquals(1.05f, prefs.getFloat(heightKey, 0f), 0.001f)
        assertEquals(1.10f, prefs.getFloat(gapKey, 0f), 0.001f)
        assertEquals(0.14f, prefs.getFloat(paddingKey, 0f), 0.001f)
        assertEquals("functional_keys_gboard", prefs.getString("layout_FUNCTIONAL", null))
    }

    @Test
    fun testApplySamsungPreset() {
        KeyboardPresets.apply(context, KeyboardPreset.SAMSUNG)

        assertEquals("SAMSUNG", prefs.getString(Settings.PREF_KEYBOARD_PRESET, null))
        assertEquals(KeyboardPreset.SAMSUNG, Settings.readKeyboardPreset(prefs))
        assertTrue(prefs.getBoolean(Defaults.PREF_DRAW_KEYS, false))
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, false))
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_HINTS, true))
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_EMOJI_KEY, true))
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, true))

        val heightKey = createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2)
        val gapKey = createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2)
        val paddingKey = createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 3)

        assertEquals(1.05f, prefs.getFloat(heightKey, 0f), 0.001f)
        assertEquals(1.35f, prefs.getFloat(gapKey, 0f), 0.001f)
        assertEquals(0.26f, prefs.getFloat(paddingKey, 0f), 0.001f)
        assertEquals("functional_keys_samsung", prefs.getString("layout_FUNCTIONAL", null))
    }

    @Test
    fun testApplyOwnRestoresDefaults() {
        // First apply Samsung to dirty the preferences
        KeyboardPresets.apply(context, KeyboardPreset.SAMSUNG)
        assertEquals(KeyboardPreset.SAMSUNG, Settings.readKeyboardPreset(prefs))

        // Now restore defaults using OWN
        KeyboardPresets.apply(context, KeyboardPreset.OWN)

        assertEquals("OWN", prefs.getString(Settings.PREF_KEYBOARD_PRESET, null))
        assertEquals(KeyboardPreset.OWN, Settings.readKeyboardPreset(prefs))
        assertFalse(prefs.getBoolean(Defaults.PREF_DRAW_KEYS, true))
        assertEquals(Defaults.PREF_SHOW_NUMBER_ROW, prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, !Defaults.PREF_SHOW_NUMBER_ROW))
        assertEquals(Defaults.PREF_SHOW_HINTS, prefs.getBoolean(Settings.PREF_SHOW_HINTS, !Defaults.PREF_SHOW_HINTS))
        assertEquals(Defaults.PREF_SHOW_EMOJI_KEY, prefs.getBoolean(Settings.PREF_SHOW_EMOJI_KEY, !Defaults.PREF_SHOW_EMOJI_KEY))
        assertEquals(Defaults.PREF_SHOW_LANGUAGE_SWITCH_KEY, prefs.getBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, !Defaults.PREF_SHOW_LANGUAGE_SWITCH_KEY))

        val heightKey = createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2)
        val gapKey = createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2)
        val paddingKey = createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 3)

        assertEquals(Defaults.PREF_KEYBOARD_HEIGHT_SCALE[0], prefs.getFloat(heightKey, 0f), 0.001f)
        assertEquals(Defaults.PREF_KEY_GAP_SCALE[0], prefs.getFloat(gapKey, 0f), 0.001f)
        assertEquals(Defaults.PREF_SIDE_PADDING_SCALE[0], prefs.getFloat(paddingKey, -1f), 0.001f)
        // Functional layout key should be removed so default functional layout is used
        assertFalse(prefs.contains("layout_FUNCTIONAL"))
    }
}
