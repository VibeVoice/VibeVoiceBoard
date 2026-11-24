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
        assertEquals("vibevoice", prefs.getString(Settings.PREF_THEME_COLORS, null))
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

        assertEquals(1.063f, prefs.getFloat(heightKey, 0f), 0.001f)
        assertEquals(0.928f, prefs.getFloat(gapKey, 0f), 0.001f)
        assertEquals(0.188f, prefs.getFloat(paddingKey, 0f), 0.001f)
        assertEquals("functional_keys_samsung", prefs.getString("layout_FUNCTIONAL", null))
        assertEquals("vibevoice", prefs.getString(Settings.PREF_THEME_COLORS, null))
        assertEquals("vibevoice", prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, null))
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
        assertEquals("vibevoice", prefs.getString(Settings.PREF_THEME_COLORS, null))
        assertEquals("vibevoice", prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, null))

        // the user changes things afterwards; a later upgrade must not take them back
        prefs.edit().putFloat(heightKey, 0.95f).putBoolean(Settings.PREF_SHOW_NUMBER_ROW, false)
            .putInt(Settings.PREF_VERSION_CODE, 600071).commit()
        checkVersionUpgrade(context)
        assertEquals(0.95f, prefs.getFloat(heightKey, 0f), 0.001f)
        assertFalse(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, true))
    }

    @Test
    fun upgrade_layoutMigrationGivesGboardOnlyTheThemeAndLeavesOwnAlone() {
        for (preset in listOf(KeyboardPreset.GBOARD, KeyboardPreset.OWN)) {
            storePreviousSamsung(preset)
            checkVersionUpgrade(context)
            assertEquals(1.17f, prefs.getFloat(heightKey, 0f), 0.001f)
            assertFalse(prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, true))
            assertEquals("functional_keys_gboard", prefs.getString("layout_FUNCTIONAL", null))
            if (preset == KeyboardPreset.GBOARD) assertEquals("vibevoice", prefs.getString(Settings.PREF_THEME_COLORS, null))
            else assertFalse(prefs.contains(Settings.PREF_THEME_COLORS))
        }
    }

    @Test
    fun upgrade_movesTheFirstSamsungPaletteOverToOurOwn() {
        // a phone that got the v3 migration: Samsung preset, old palette name, v3 flag set, v4 not yet
        storePreviousSamsung(KeyboardPreset.SAMSUNG)
        prefs.edit().putBoolean(KeyboardPresets.PREF_PRESET_LAYOUT_V3, true).putString(Settings.PREF_THEME_COLORS, "samsung")
            .putString(Settings.PREF_THEME_COLORS_NIGHT, "samsung").putFloat(heightKey, 1.138f).commit()
        checkVersionUpgrade(context)
        assertEquals("vibevoice", prefs.getString(Settings.PREF_THEME_COLORS, null))
        assertEquals("vibevoice", prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, null))
        assertEquals(1.063f, prefs.getFloat(heightKey, 0f), 0.001f)
    }

    @Test
    fun choosingOwnDropsThePresetTheme() {
        KeyboardPresets.apply(context, KeyboardPreset.SAMSUNG)
        KeyboardPresets.apply(context, KeyboardPreset.GBOARD)
        assertEquals("vibevoice", prefs.getString(Settings.PREF_THEME_COLORS, null))
        KeyboardPresets.apply(context, KeyboardPreset.OWN)
        assertFalse(prefs.contains(Settings.PREF_THEME_COLORS))
        assertFalse(prefs.contains(Settings.PREF_THEME_COLORS_NIGHT))
    }

    private fun actionColor(): Int =
        helium314.keyboard.keyboard.KeyboardTheme.getColorsForCurrentTheme(context).get(helium314.keyboard.latin.common.ColorType.ACTION_KEY_BACKGROUND)

    private fun shiftColor(): Int =
        helium314.keyboard.keyboard.KeyboardTheme.getColorsForCurrentTheme(context).get(helium314.keyboard.latin.common.ColorType.SHIFT_KEY_ICON)

    @Test
    fun accent_gboardAndSamsungDefaultToVioletAndTakeTheChoice() {
        for (preset in listOf(KeyboardPreset.GBOARD, KeyboardPreset.SAMSUNG)) {
            prefs.edit().clear().commit()
            KeyboardPresets.apply(context, preset)
            assertEquals(0xFF8B5CF6.toInt(), actionColor())
            assertEquals(0xFF8B5CF6.toInt(), shiftColor())
            for (accent in KeyboardAccent.PALETTE) {
                KeyboardPresets.setAccent(context, accent)
                assertEquals(accent.color, actionColor())
                if (accent != KeyboardAccent.VIBEVOICE) assertEquals(accent.color, shiftColor()) // light blue hardly shows on a light key: see the glyph test
            }
        }
    }

    @Test
    fun accent_ownKeepsItsThemeColorUntilAChoiceIsMade() {
        prefs.edit().clear().commit()
        val before = actionColor()
        KeyboardPresets.apply(context, KeyboardPreset.OWN)
        assertEquals("unset pref: OWN looks like before", before, actionColor())
        assertFalse(prefs.contains(Settings.PREF_KEYBOARD_ACCENT))
        KeyboardPresets.setAccent(context, KeyboardAccent.ORANGE)
        assertEquals(KeyboardAccent.ORANGE.color, actionColor())
        assertEquals(KeyboardAccent.ORANGE.color, shiftColor())
    }

    @Test
    fun accent_ownOnlyRecolorsTheAccent() {
        prefs.edit().clear().commit()
        val colors = { helium314.keyboard.keyboard.KeyboardTheme.getColorsForCurrentTheme(context) }
        val types = helium314.keyboard.latin.common.ColorType.entries.filter {
            it != helium314.keyboard.latin.common.ColorType.ACTION_KEY_BACKGROUND && it != helium314.keyboard.latin.common.ColorType.SHIFT_KEY_ICON
        }
        val before = types.associateWith { runCatching { colors().get(it) }.getOrNull() }
        KeyboardPresets.setAccent(context, KeyboardAccent.PINK)
        val after = types.associateWith { runCatching { colors().get(it) }.getOrNull() }
        val changed = types.filter { before[it] != after[it] }
        // only accent-derived types may differ (gesture trail, toolbar enabled, popup action key, pin, emoji selected ...)
        val allowed = setOf("GESTURE_TRAIL", "TOOL_BAR_KEY_ENABLED_BACKGROUND", "EMOJI_CATEGORY_SELECTED", "CLIPBOARD_PIN",
            "ACTION_KEY_POPUP_KEYS_BACKGROUND")
        assertTrue("unexpected changes: $changed", changed.all { it.name in allowed })
    }

    @Test
    fun accent_choiceSurvivesPresetSwitchAndUpgrade() {
        prefs.edit().clear().commit()
        KeyboardPresets.setAccent(context, KeyboardAccent.GREEN)
        for (preset in listOf(KeyboardPreset.SAMSUNG, KeyboardPreset.GBOARD, KeyboardPreset.OWN, KeyboardPreset.SAMSUNG)) {
            KeyboardPresets.apply(context, preset)
            assertEquals(KeyboardAccent.GREEN, KeyboardPresets.readAccent(prefs))
            assertEquals(KeyboardAccent.GREEN.color, actionColor())
        }
        storePreviousSamsung(KeyboardPreset.SAMSUNG)
        KeyboardPresets.setAccent(context, KeyboardAccent.PINK)
        checkVersionUpgrade(context)
        assertEquals(KeyboardAccent.PINK, KeyboardPresets.readAccent(prefs))
        assertEquals(KeyboardAccent.PINK.color, actionColor())
    }

    @Test
    fun accent_unknownStoredValueIsTreatedAsUnset() {
        prefs.edit().clear().commit()
        prefs.edit().putString(Settings.PREF_KEYBOARD_ACCENT, "blue").commit()
        assertEquals(null, KeyboardPresets.readAccent(prefs))
        KeyboardPresets.apply(context, KeyboardPreset.GBOARD)
        assertEquals(KeyboardAccent.VIOLET.color, actionColor())
    }

    private fun themeColors() = helium314.keyboard.keyboard.KeyboardTheme.getColorsForCurrentTheme(context)
    private val actionIcon = helium314.keyboard.latin.common.ColorType.ACTION_KEY_ICON

    @Test
    fun accent_vibevoiceBlueIsSelectableForEveryPreset() {
        assertEquals(0xFFB0C6FF.toInt(), KeyboardAccent.VIBEVOICE.color)
        assertEquals(KeyboardAccent.VIBEVOICE, KeyboardAccent.PALETTE.first())
        for (preset in KeyboardPreset.entries) {
            prefs.edit().clear().commit()
            KeyboardPresets.apply(context, preset)
            KeyboardPresets.setAccent(context, KeyboardAccent.VIBEVOICE)
            assertEquals(0xFFB0C6FF.toInt(), actionColor())
        }
    }

    @Test
    fun accent_customColorPerPresetSurvivesPresetSwitchAndUpgrade() {
        prefs.edit().clear().commit()
        KeyboardPresets.setCustomAccent(context, 0x00FFD60A) // alpha is forced opaque
        assertEquals(KeyboardAccent.CUSTOM, KeyboardPresets.readAccent(prefs))
        assertEquals(0xFFFFD60A.toInt(), KeyboardPresets.readAccentColor(prefs))
        for (preset in listOf(KeyboardPreset.SAMSUNG, KeyboardPreset.GBOARD, KeyboardPreset.OWN)) {
            KeyboardPresets.apply(context, preset)
            assertEquals(0xFFFFD60A.toInt(), actionColor())
            assertEquals(KeyboardAccent.CUSTOM, KeyboardPresets.readAccent(prefs))
        }
        storePreviousSamsung(KeyboardPreset.SAMSUNG)
        KeyboardPresets.setCustomAccent(context, 0xFF1E3A8A.toInt())
        checkVersionUpgrade(context)
        assertEquals(0xFF1E3A8A.toInt(), KeyboardPresets.readAccentColor(prefs))
        assertEquals(0xFF1E3A8A.toInt(), actionColor())
    }

    @Test
    fun accent_customValuesAreValidated() {
        // CUSTOM selected, no color stored: unset
        prefs.edit().clear().putString(Settings.PREF_KEYBOARD_ACCENT, "CUSTOM").commit()
        assertEquals(null, KeyboardPresets.readAccent(prefs))
        assertEquals(null, KeyboardPresets.readAccentColor(prefs))
        KeyboardPresets.apply(context, KeyboardPreset.GBOARD)
        assertEquals(KeyboardAccent.VIOLET.color, actionColor())
        // a stored color of the wrong type: unset
        prefs.edit().putString(Settings.PREF_KEYBOARD_ACCENT_CUSTOM, "red").commit()
        assertEquals(null, KeyboardPresets.readAccentColor(prefs))
        // choosing CUSTOM without a stored color does nothing
        prefs.edit().clear().commit()
        KeyboardPresets.setAccent(context, KeyboardAccent.CUSTOM)
        assertFalse(prefs.contains(Settings.PREF_KEYBOARD_ACCENT))
        // a stored color without alpha still comes out opaque
        prefs.edit().putInt(Settings.PREF_KEYBOARD_ACCENT_CUSTOM, 0x00123456).putString(Settings.PREF_KEYBOARD_ACCENT, "CUSTOM").commit()
        assertEquals(0xFF123456.toInt(), KeyboardPresets.readAccentColor(prefs))
    }

    @Test
    fun accent_glyphIsWhiteOrNearBlackByLuminance() {
        for (accent in listOf(KeyboardAccent.VIOLET, KeyboardAccent.GREEN, KeyboardAccent.ORANGE, KeyboardAccent.PINK, KeyboardAccent.GRAPHITE))
            assertEquals("${accent.name} keeps white", android.graphics.Color.WHITE, helium314.keyboard.latin.utils.accentGlyphColor(accent.color))
        for (light in listOf(KeyboardAccent.VIBEVOICE.color, 0xFFFFD60A.toInt(), 0xFFA7F3D0.toInt()))
            assertEquals(helium314.keyboard.latin.utils.GLYPH_DARK, helium314.keyboard.latin.utils.accentGlyphColor(light))
        assertEquals(android.graphics.Color.WHITE, helium314.keyboard.latin.utils.accentGlyphColor(0xFF1E3A8A.toInt()))
        // and the theme uses it for the label / icon on the action key
        prefs.edit().clear().commit()
        KeyboardPresets.apply(context, KeyboardPreset.SAMSUNG)
        KeyboardPresets.setAccent(context, KeyboardAccent.VIBEVOICE)
        assertEquals(helium314.keyboard.latin.utils.GLYPH_DARK, themeColors().get(actionIcon))
        KeyboardPresets.setCustomAccent(context, 0xFFFFD60A.toInt())
        assertEquals(helium314.keyboard.latin.utils.GLYPH_DARK, themeColors().get(actionIcon))
        KeyboardPresets.setCustomAccent(context, 0xFF1E3A8A.toInt())
        assertEquals(android.graphics.Color.WHITE, themeColors().get(actionIcon))
        KeyboardPresets.setAccent(context, KeyboardAccent.GREEN)
        assertEquals(android.graphics.Color.WHITE, themeColors().get(actionIcon))
        // Own without a choice keeps the old white glyph
        prefs.edit().clear().commit()
        assertEquals(android.graphics.Color.WHITE, themeColors().get(actionIcon))
    }

    @Test
    fun accent_activeShiftGlyphStaysReadable() {
        val shift = helium314.keyboard.latin.common.ColorType.SHIFT_KEY_ICON
        val text = helium314.keyboard.latin.common.ColorType.KEY_TEXT
        for (preset in listOf(KeyboardPreset.SAMSUNG, KeyboardPreset.GBOARD)) {
            prefs.edit().clear().commit()
            KeyboardPresets.apply(context, preset)
            for (accent in KeyboardAccent.PALETTE) {
                KeyboardPresets.setAccent(context, accent)
                val c = themeColors()
                assertTrue("${accent.name}: shift is the accent or the key text", c.get(shift) == accent.color || c.get(shift) == c.get(text))
            }
        }
    }
}
