package helium314.keyboard.latin.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.checkVersionUpgrade
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
        assertTrue(prefs.getBoolean(Settings.PREF_DRAW_KEYS, false))
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, true))
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_HINTS, false))
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_EMOJI_KEY, false))
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, true))

        val heightKey = createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2)
        val gapKey = createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2)
        val paddingKey = createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 3)

        assertEquals(0.865f, prefs.getFloat(heightKey, 0f), 0.001f)
        assertEquals(1.03f, prefs.getFloat(gapKey, 0f), 0.001f)
        assertEquals(0.06f, prefs.getFloat(paddingKey, 0f), 0.001f)
        assertEquals("functional_keys_gboard", prefs.getString("layout_FUNCTIONAL", null))
    }

    @Test
    fun testApplySamsungPreset() {
        KeyboardPresets.apply(context, KeyboardPreset.SAMSUNG)

        assertEquals("SAMSUNG", prefs.getString(Settings.PREF_KEYBOARD_PRESET, null))
        assertEquals(KeyboardPreset.SAMSUNG, Settings.readKeyboardPreset(prefs))
        assertTrue(prefs.getBoolean(Settings.PREF_DRAW_KEYS, false))
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, false))
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_HINTS, true))
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_EMOJI_KEY, true))
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, true))

        val heightKey = createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2)
        val gapKey = createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2)
        val paddingKey = createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 3)

        assertEquals(1.138f, prefs.getFloat(heightKey, 0f), 0.001f)
        assertEquals(0.925f, prefs.getFloat(gapKey, 0f), 0.001f)
        assertEquals(0.20f, prefs.getFloat(paddingKey, 0f), 0.001f)
        assertEquals("functional_keys_samsung", prefs.getString("layout_FUNCTIONAL", null))
        assertEquals("samsung", prefs.getString(Settings.PREF_THEME_COLORS, null))
        assertEquals("samsung", prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, null))
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
        assertFalse(prefs.getBoolean(Settings.PREF_DRAW_KEYS, true))
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

    private val heightKey = createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2)
    private val gapKey = createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2)
    private val sideKey = createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 3)

    /** A phone that chose the preset before the sizes were corrected: the old values, no migration flag. */
    private fun storeOldSizes(preset: KeyboardPreset, height: Float, gap: Float, side: Float) {
        prefs.edit().clear().commit()
        prefs.edit()
            .putString(Settings.PREF_KEYBOARD_PRESET, preset.name)
            .putFloat(heightKey, height).putFloat(gapKey, gap).putFloat(sideKey, side)
            .putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, false) // not a size: must survive
            .putInt(Settings.PREF_VERSION_CODE, 600063)
            .commit()
    }

    @Test
    fun upgrade_bringsStoredGboardSizesUpToTheCurrentOnes_once() {
        storeOldSizes(KeyboardPreset.GBOARD, 1.05f, 1.10f, 0.14f)
        checkVersionUpgrade(context)
        val now = KeyboardPresets.sizesOf(KeyboardPreset.GBOARD)
        assertEquals(now.height, prefs.getFloat(heightKey, 0f), 0.001f)
        assertEquals(now.gap, prefs.getFloat(gapKey, 0f), 0.001f)
        assertEquals(now.sidePadding, prefs.getFloat(sideKey, 0f), 0.001f)
        assertFalse("other settings stay as they were", prefs.getBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, true))

        // the user changes the height afterwards; a later upgrade must not take it back
        prefs.edit().putFloat(heightKey, 0.95f).putInt(Settings.PREF_VERSION_CODE, 600064).commit()
        checkVersionUpgrade(context)
        assertEquals(0.95f, prefs.getFloat(heightKey, 0f), 0.001f)
    }

    @Test
    fun upgrade_bringsStoredSamsungSizesUpToTheCurrentOnes() {
        storeOldSizes(KeyboardPreset.SAMSUNG, 1.05f, 1.35f, 0.26f)
        checkVersionUpgrade(context)
        val now = KeyboardPresets.sizesOf(KeyboardPreset.SAMSUNG)
        assertEquals(now.height, prefs.getFloat(heightKey, 0f), 0.001f)
        assertEquals(now.gap, prefs.getFloat(gapKey, 0f), 0.001f)
        assertEquals(now.sidePadding, prefs.getFloat(sideKey, 0f), 0.001f)
    }

    @Test
    fun upgrade_leavesTheOwnPresetAlone() {
        storeOldSizes(KeyboardPreset.OWN, 1.2f, 1.5f, 0.3f)
        checkVersionUpgrade(context)
        assertEquals(1.2f, prefs.getFloat(heightKey, 0f), 0.001f)
        assertEquals(1.5f, prefs.getFloat(gapKey, 0f), 0.001f)
        assertEquals(0.3f, prefs.getFloat(sideKey, 0f), 0.001f)
    }

    /** What the previous Samsung preset (sizes already migrated, flag v2 set) stored on a phone. */
    private fun storePreviousSamsung(preset: KeyboardPreset) {
        prefs.edit().clear().commit()
        prefs.edit()
            .putString(Settings.PREF_KEYBOARD_PRESET, preset.name)
            .putBoolean(KeyboardPresets.PREF_PRESET_SIZES_V2, true)
            .putFloat(heightKey, 1.17f).putFloat(gapKey, 0.99f).putFloat(sideKey, 0.18f)
            .putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, false)
            .putBoolean(Settings.PREF_SHOW_NUMBER_ROW, false)
            .putString("layout_FUNCTIONAL", "functional_keys_gboard")
            .putInt(Settings.PREF_VERSION_CODE, 600070)
            .commit()
    }

    @Test
    fun upgrade_reappliesTheFullSamsungPresetOnce() {
        storePreviousSamsung(KeyboardPreset.SAMSUNG)
        checkVersionUpgrade(context)
        val now = KeyboardPresets.sizesOf(KeyboardPreset.SAMSUNG)
        assertEquals(now.height, prefs.getFloat(heightKey, 0f), 0.001f)
        assertEquals(now.gap, prefs.getFloat(gapKey, 0f), 0.001f)
        assertEquals(now.sidePadding, prefs.getFloat(sideKey, 0f), 0.001f)
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, false))
        assertTrue(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, false))
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_HINTS, true))
        assertEquals("functional_keys_samsung", prefs.getString("layout_FUNCTIONAL", null))
        assertEquals("samsung", prefs.getString(Settings.PREF_THEME_COLORS, null))
        assertEquals("samsung", prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, null))

        // the user changes things afterwards; a later upgrade must not take them back
        prefs.edit().putFloat(heightKey, 0.95f).putBoolean(Settings.PREF_SHOW_NUMBER_ROW, false)
            .putInt(Settings.PREF_VERSION_CODE, 600071).commit()
        checkVersionUpgrade(context)
        assertEquals(0.95f, prefs.getFloat(heightKey, 0f), 0.001f)
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, true))
    }

    @Test
    fun upgrade_layoutMigrationLeavesGboardAndOwnAlone() {
        for (preset in listOf(KeyboardPreset.GBOARD, KeyboardPreset.OWN)) {
            storePreviousSamsung(preset)
            checkVersionUpgrade(context)
            assertEquals(1.17f, prefs.getFloat(heightKey, 0f), 0.001f)
            assertFalse(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, true))
            assertEquals("functional_keys_gboard", prefs.getString("layout_FUNCTIONAL", null))
            assertFalse(prefs.contains(Settings.PREF_THEME_COLORS))
        }
    }

    @Test
    fun choosingAnotherPresetDropsTheSamsungTheme() {
        KeyboardPresets.apply(context, KeyboardPreset.SAMSUNG)
        KeyboardPresets.apply(context, KeyboardPreset.GBOARD)
        assertFalse(prefs.contains(Settings.PREF_THEME_COLORS))
        assertFalse(prefs.contains(Settings.PREF_THEME_COLORS_NIGHT))
    }
}
