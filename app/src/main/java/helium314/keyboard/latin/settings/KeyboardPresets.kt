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

/**
 * The accent colors the user can choose from (enter key, active shift). No blue on purpose.
 * All of them carry a white glyph with enough contrast and sit well on both the dark and the light palette.
 */
enum class KeyboardAccent(val color: Int) {
    /** the light blue the Own preset always had */
    VIBEVOICE(0xFFB0C6FF.toInt()),
    VIOLET(0xFF8B5CF6.toInt()),
    GREEN(0xFF16A34A.toInt()),
    ORANGE(0xFFEA580C.toInt()),
    PINK(0xFFDB2777.toInt()),
    GRAPHITE(0xFF5B6472.toInt()),
    /** the color the user mixed himself, stored in [Settings.PREF_KEYBOARD_ACCENT_CUSTOM]; [color] is not used */
    CUSTOM(0);

    companion object {
        /** The fixed colors, in the order the dots show them (the custom color follows as the last dot). */
        val PALETTE: List<KeyboardAccent> = entries.filter { it != CUSTOM }

        /** The stored choice, or null if the user never picked one (or the value is unknown). */
        fun fromName(name: String?): KeyboardAccent? = entries.firstOrNull { it.name == name }
    }
}

/** The size related values a preset stores (portrait, not split): one place, used when a preset is chosen and when it is migrated. */
data class PresetSizes(val height: Float, val gap: Float, val bottomPadding: Float, val sidePadding: Float, val bottomRow: Float)

object KeyboardPresets {
    /** Stored once the sizes of the stored Gboard/Samsung preset have been brought up to the current values. */
    const val PREF_PRESET_SIZES_V2 = "preset_sizes_v2"

    /** Stored once the full Samsung preset was re-applied (v3: layout, language switch key, number row, sizes). */
    const val PREF_PRESET_LAYOUT_V3 = "preset_layout_v3"
    /** Stored once the Samsung preset was re-applied with sizes for a 384 dp wide phone and our own palette, and Gboard got the palette. */
    const val PREF_PRESET_LAYOUT_V4 = "preset_layout_v4"

    /** The stored custom color as an opaque ARGB int, or null when none is stored or the stored value is not an int. */
    fun readCustomColor(prefs: SharedPreferences): Int? =
        try {
            if (prefs.contains(Settings.PREF_KEYBOARD_ACCENT_CUSTOM)) prefs.getInt(Settings.PREF_KEYBOARD_ACCENT_CUSTOM, 0) or 0xFF000000.toInt()
            else null
        } catch (_: ClassCastException) { null }

    /**
     * The user's accent choice, or null while none was made. An unknown name is unset; CUSTOM without a valid
     * stored color is unset too.
     */
    fun readAccent(prefs: SharedPreferences): KeyboardAccent? {
        val accent = KeyboardAccent.fromName(prefs.getString(Settings.PREF_KEYBOARD_ACCENT, null)) ?: return null
        return if (accent == KeyboardAccent.CUSTOM && readCustomColor(prefs) == null) null else accent
    }

    /** The color of the chosen accent (also the custom one), or null while none was made. */
    fun readAccentColor(prefs: SharedPreferences): Int? = when (val accent = readAccent(prefs)) {
        null -> null
        KeyboardAccent.CUSTOM -> readCustomColor(prefs)
        else -> accent.color
    }

    /** Stores the accent and restyles the keyboard. Presets never touch this value, so the choice survives a preset switch. */
    fun setAccent(context: Context, accent: KeyboardAccent) {
        if (accent == KeyboardAccent.CUSTOM && readCustomColor(context.prefs()) == null) return // nothing to select
        context.prefs().edit { putString(Settings.PREF_KEYBOARD_ACCENT, accent.name) }
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    /** Stores [color] (alpha is forced opaque) as the custom accent and selects it. */
    fun setCustomAccent(context: Context, color: Int) {
        context.prefs().edit {
            putInt(Settings.PREF_KEYBOARD_ACCENT_CUSTOM, color or 0xFF000000.toInt())
            putString(Settings.PREF_KEYBOARD_ACCENT, KeyboardAccent.CUSTOM.name)
        }
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    fun sizesOf(preset: KeyboardPreset): PresetSizes = when (preset) {
        KeyboardPreset.OWN -> PresetSizes(
            Defaults.PREF_KEYBOARD_HEIGHT_SCALE[0], Defaults.PREF_KEY_GAP_SCALE[0], Defaults.PREF_BOTTOM_PADDING_SCALE[0],
            Defaults.PREF_SIDE_PADDING_SCALE[0], Defaults.PREF_BOTTOM_ROW_SCALE[0]
        )
        // measured against the real Gboard / Samsung keyboard at 1440 px wide: see the render tests
        KeyboardPreset.GBOARD -> PresetSizes(height = 0.865f, gap = 1.03f, bottomPadding = 1.00f, sidePadding = 0.06f, bottomRow = 1.00f)
        KeyboardPreset.SAMSUNG -> PresetSizes(height = 1.063f, gap = 0.928f, bottomPadding = 1.00f, sidePadding = 0.188f, bottomRow = 1.00f)
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
        if (prefs.getBoolean(PREF_PRESET_LAYOUT_V4, false)) return
        val preset = runCatching { KeyboardPreset.valueOf(prefs.getString(Settings.PREF_KEYBOARD_PRESET, "")!!) }.getOrNull()
        prefs.edit {
            when (preset) {
                KeyboardPreset.SAMSUNG -> writePreset(KeyboardPreset.SAMSUNG)
                KeyboardPreset.GBOARD -> writeTheme() // sizes and layout stay as they are
                else -> {}
            }
            putBoolean(PREF_PRESET_LAYOUT_V3, true)
            putBoolean(PREF_PRESET_LAYOUT_V4, true)
        }
    }

    private fun SharedPreferences.Editor.writeTheme() {
        putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_VIBEVOICE)
        putString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_VIBEVOICE)
    }

    private fun isPresetTheme(name: String?) = name == KeyboardTheme.THEME_VIBEVOICE || name == KeyboardTheme.THEME_SAMSUNG_LEGACY

    private fun SharedPreferences.Editor.clearPresetTheme(prefs: SharedPreferences) {
        if (isPresetTheme(prefs.getString(Settings.PREF_THEME_COLORS, null))) remove(Settings.PREF_THEME_COLORS)
        if (isPresetTheme(prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, null))) remove(Settings.PREF_THEME_COLORS_NIGHT)
    }

    fun apply(context: Context, preset: KeyboardPreset) {
        val prefs = context.prefs()
        prefs.edit {
            if (preset == KeyboardPreset.OWN) clearPresetTheme(prefs)
            writePreset(preset)
            putBoolean(PREF_PRESET_SIZES_V2, true) // chosen now: already on the current sizes
            putBoolean(PREF_PRESET_LAYOUT_V3, true) // and on the current layout
            putBoolean(PREF_PRESET_LAYOUT_V4, true)
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
                    writeTheme()
                }
                KeyboardPreset.SAMSUNG -> {
                    putBoolean(Settings.PREF_DRAW_KEYS, true)
                    putBoolean(Settings.PREF_SHOW_NUMBER_ROW, true)
                    putBoolean(Settings.PREF_SHOW_HINTS, false)
                    putBoolean(Settings.PREF_SHOW_EMOJI_KEY, false)
                    putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, true)
                    putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.FUNCTIONAL.name, "functional_keys_samsung")
                    writeTheme()
                }
            }
        }
    }
}
