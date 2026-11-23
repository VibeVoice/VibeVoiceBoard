package helium314.keyboard.latin.settings

import android.content.Context
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.LayoutParser
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.prefs

enum class KeyboardPreset { OWN, GBOARD, SAMSUNG }

object KeyboardPresets {
    fun apply(context: Context, preset: KeyboardPreset) {
        val prefs = context.prefs()
        prefs.edit {
            putString(Settings.PREF_KEYBOARD_PRESET, preset.name)
            when (preset) {
                KeyboardPreset.OWN -> {
                    putBoolean(Settings.PREF_DRAW_KEYS, false)
                    putBoolean(Settings.PREF_SHOW_NUMBER_ROW, Defaults.PREF_SHOW_NUMBER_ROW)
                    putBoolean(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS)
                    putBoolean(Settings.PREF_SHOW_EMOJI_KEY, Defaults.PREF_SHOW_EMOJI_KEY)
                    putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, Defaults.PREF_SHOW_LANGUAGE_SWITCH_KEY)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2), Defaults.PREF_KEYBOARD_HEIGHT_SCALE[0])
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2), Defaults.PREF_KEY_GAP_SCALE[0])
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, 0, 2), Defaults.PREF_BOTTOM_PADDING_SCALE[0])
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 3), Defaults.PREF_SIDE_PADDING_SCALE[0])
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_ROW_SCALE_PREFIX, 0, 2), Defaults.PREF_BOTTOM_ROW_SCALE[0])
                    remove(Settings.PREF_LAYOUT_PREFIX + LayoutType.FUNCTIONAL.name)
                }
                KeyboardPreset.GBOARD -> {
                    putBoolean(Settings.PREF_DRAW_KEYS, true)
                    putBoolean(Settings.PREF_SHOW_NUMBER_ROW, false)
                    putBoolean(Settings.PREF_SHOW_HINTS, true)
                    putBoolean(Settings.PREF_SHOW_EMOJI_KEY, true)
                    putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, false)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2), 1.05f)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2), 1.10f)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, 0, 2), 1.00f)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 3), 0.14f)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_ROW_SCALE_PREFIX, 0, 2), 1.00f)
                    putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.FUNCTIONAL.name, "functional_keys_gboard")
                }
                KeyboardPreset.SAMSUNG -> {
                    putBoolean(Settings.PREF_DRAW_KEYS, true)
                    putBoolean(Settings.PREF_SHOW_NUMBER_ROW, true)
                    putBoolean(Settings.PREF_SHOW_HINTS, false)
                    putBoolean(Settings.PREF_SHOW_EMOJI_KEY, false)
                    putBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, false)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEYBOARD_HEIGHT_SCALE_PREFIX, 0, 2), 1.05f)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_KEY_GAP_SCALE_PREFIX, 0, 2), 1.35f)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_PADDING_SCALE_PREFIX, 0, 2), 1.00f)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_SIDE_PADDING_SCALE_PREFIX, 0, 3), 0.26f)
                    putFloat(createPrefKeyForBooleanSettings(Settings.PREF_BOTTOM_ROW_SCALE_PREFIX, 0, 2), 1.00f)
                    putString(Settings.PREF_LAYOUT_PREFIX + LayoutType.FUNCTIONAL.name, "functional_keys_samsung")
                }
            }
        }
        LayoutParser.clearCache()
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }
}
