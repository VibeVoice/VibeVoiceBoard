// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.SettingsActivity

/**
 * The phone's Material You accent, for the things that should wear the user's wallpaper and nothing else.
 *
 * Two uses, two colours from the same palette (android.R.color.system_accent1_*), the way the dynamic
 * keyboard theme picks them:
 *  - [recordingColor]: the waves behind the keys, the glow behind the toolbar mark and the floating mark.
 *    system_accent1_100 at night, system_accent1_600 by day -- exactly the dynamic theme's gesture colour,
 *    which is what the waves always wore until the presets brought palettes of their own. It does not follow
 *    the keyboard theme, the preset or the accent the user picked.
 *  - [keyboardAccent]: the "Auto" accent of the keys (enter key, active shift). system_accent1_200 at night
 *    (the 100 shade is almost white and would leave the enter key without a body) and system_accent1_600 by
 *    day, so that the enter key is a solid colour in both.
 *
 * Below Android 12 there is no system palette; [recordingColor] then falls back to a blue-turquoise of our own
 * and [keyboardAccent] to null (the caller picks violet).
 */
@SuppressLint("InlinedApi") // the system_accent1_* ids are inlined constants, only read on Android 12+
object SystemAccent {
    /** Where there is no system accent: a blue-turquoise, close to what the system accent gives on a teal wallpaper. */
    const val FALLBACK_RECORDING_COLOR = 0xFF35B6E8.toInt()

    /** The keyboard's own day/night rule, so the colours match the keys they sit beside. */
    @JvmStatic
    fun isNight(context: Context): Boolean {
        SettingsActivity.forceNight?.let { return it }
        val prefs = context.prefs()
        return ResourceUtils.isNight(context.resources) && prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT)
    }

    private fun systemColor(context: Context, night: Boolean, nightRes: Int, dayRes: Int): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            ContextCompat.getColor(Settings.getDayNightContext(context, night), if (night) nightRes else dayRes)
        } catch (_: Exception) {
            null
        }
    }

    /** Waves, toolbar glow and floating mark. Always has a value. */
    @JvmStatic
    @JvmOverloads
    fun recordingColor(context: Context, night: Boolean = isNight(context)): Int =
        systemColor(context, night, android.R.color.system_accent1_100, android.R.color.system_accent1_600) ?: FALLBACK_RECORDING_COLOR

    /** The "Auto" accent, or null where there is no system palette. */
    @JvmStatic
    @JvmOverloads
    fun keyboardAccent(context: Context, night: Boolean = isNight(context)): Int? =
        systemColor(context, night, android.R.color.system_accent1_200, android.R.color.system_accent1_600)
}
