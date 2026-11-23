package helium314.keyboard.latin.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.keyboard_parser.LayoutParser
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.prefs

enum class KeyboardPreset { OWN, GBOARD, SAMSUNG }

/** The size related values a preset stores (portrait, not split): one place, used when a preset is chosen and when it is migrated. */
data class PresetSizes(val height: Float, val gap: Float, val bottomPadding: Float, val sidePadding: Float, val bottomRow: Float)

object KeyboardPresets {
    /** Stored once the sizes of the stored Gboard/Samsung preset have been brought up to the current values. */
    const val PREF_PRESET_SIZES_V2 = "preset_sizes_v2"

    /** Stored once the full Samsung preset (layout, language switch key, number row, colors, sizes) has been re-applied. */
    const val PREF_PRESET_LAYOUT_V3 = "preset_layout_v3"

    fun sizesOf(preset: KeyboardPreset): PresetSizes = when (preset) {
        KeyboardPreset.OWN -> PresetSizes(
            Defaults.PREF_KEYBOARD_HEIGHT_SCALE[0], Defaults.PREF_KEY_GAP_SCALE[0], Defaults.PREF_BOTTOM_PADDING_SCALE[0],
            Defaults.PREF_SIDE_PADDING_SCALE[0], Defaults.PREF_BOTTOM_ROW_SCALE[0]
        )
        // measured against the real Gboard / Samsung keyboard at 1440 px wide: see the render tests
        KeyboardPreset.GBOARD -> PresetSizes(height = 0.865f, gap = 1.03f, bottomPadding = 1.00f, sidePadding = 0.06f, bottomRow = 1.00f)
        KeyboardPreset.SAMSUNG -> PresetSizes(height = 1.138f, gap = 0.925f, bottomPadding = 1.00f, sidePadding = 0.20f, bottomRow = 1.00f)
    }

    private fun SharedPreferences.Editor.writeSizes(sizes: PresetSizes) {
        putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2), sizes.height)
        putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2), sizes.gap)
        putFloat(createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, 0, 2), sizes.bottomPadding)
        putFloat(createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 3), sizes.sidePadding)
        putFloat(createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_ROW_SCALE_PREFIX, 0, 2), sizes.bottomRow)
    }

    /**
     * Applying a preset stores its sizes once, so a phone that chose Gboard or Samsung before the sizes were
     * corrected keeps the old ones. This brings them up to date, once, and leaves every other setting alone.
     * Whatever the user changes afterwards stays: the flag is set and the sizes are never rewritten again.
     */
    fun migrateSizes(prefs: SharedPreferences) {
        if (prefs.getBoolean(PREF_PRESET_SIZES_V2, false)) return
        val preset = runCatching { KeyboardPreset.valueOf(prefs.getString(Settings.PREF_KEYBOARD_PRESET, "")!!) }.getOrNull()
        prefs.edit {
            if (preset == KeyboardPreset.GBOARD || preset == KeyboardPreset.SAMSUNG) writeSizes(sizesOf(preset))
            putBoolean(PREF_PRESET_SIZES_V2, true)
        }
    }

    /**
     * The Samsung preset was rebuilt to copy the real keyboard (neutral colors, flatter number row, bottom row, capital labels).
     * Re-applies all of it once for a stored Samsung preset; Gboard and Own are left alone. Later user changes stay.
     */
    fun migrateLayout(prefs: SharedPreferences) {
        if (prefs.getBoolean(PREF_PRESET_LAYOUT_V3, false)) return
        val preset = runCatching { KeyboardPreset.valueOf(prefs.getString(Settings.PREF_KEYBOARD_PRESET, "")!!) }.getOrNull()
        prefs.edit {
            if (preset == KeyboardPreset.SAMSUNG) writePreset(KeyboardPreset.SAMSUNG)
            putBoolean(PREF_PRESET_LAYOUT_V3, true)
        }
    }

    private fun SharedPreferences.Editor.clearSamsungTheme(prefs: SharedPreferences) {
        if (prefs.getString(Settings.PREF_THEME_COLORS, null) == KeyboardTheme.THEME_SAMSUNG) remove(Settings.PREF_THEME_COLORS)
        if (prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, null) == KeyboardTheme.THEME_SAMSUNG) remove(Settings.PREF_THEME_COLORS_NIGHT)
    }

    fun apply(context: Context, preset: KeyboardPreset) {
        val prefs = context.prefs()
        prefs.edit {
            if (preset != KeyboardPreset.SAMSUNG) clearSamsungTheme(prefs)
            writePreset(preset)
            putBoolean(PREF_PRESET_SIZES_V2, true) // chosen now: already on the current sizes
            putBoolean(PREF_PRESET_LAYOUT_V3, true) // and on the current layout
        }
        LayoutParser.clearCache()
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    private fun SharedPreferences.Editor.writePreset(preset: KeyboardPreset) {
        run {
            putString(Settings.PREF_KEYBOARD_PRESET, preset.name)
            writeSizes(sizesOf(preset))
            when (preset) {
                KeyboardPreset.OWN -> {
                    putBoolean(Settings.PREF_DRAW_KEYS, false)
                    putBoolean(Settings.PREF_SHOW_NUMBER_ROW, Defaults.PREF_SHOW_NUMBER_ROW)
                    putBoolean(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS)
                    putBoolean(Settings.PREF_SHOW_EMOJI_KEY, Defaults.PREF_SHOW_EMOJI_KEY)
                    putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, Defaults.PREF_SHOW_LANGUAGE_SWITCH_KEY)
                    remove(Settings.PREF_LAYOUT_PREFIX + LayoutType.FUNCTIONAL.name)
                }
                KeyboardPreset.GBOARD -> {
                    putBoolean(Settings.PREF_DRAW_KEYS, true)
                    putBoolean(Settings.PREF_SHOW_NUMBER_ROW, false)
                    putBoolean(Settings.PREF_SHOW_HINTS, true)
                    putBoolean(Settings.PREF_SHOW_EMOJI_KEY, true)
                    putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, false)
                    putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.FUNCTIONAL.name, "functional_keys_gboard")
                }
                KeyboardPreset.SAMSUNG -> {
                    putBoolean(Settings.PREF_DRAW_KEYS, true)
                    putBoolean(Settings.PREF_SHOW_NUMBER_ROW, true)
                    putBoolean(Settings.PREF_SHOW_HINTS, false)
                    putBoolean(Settings.PREF_SHOW_EMOJI_KEY, false)
                    putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, true)
                    putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.FUNCTIONAL.name, "functional_keys_samsung")
                    putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_SAMSUNG)
                    putString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_SAMSUNG)
                }
            }
        }
    }
}
