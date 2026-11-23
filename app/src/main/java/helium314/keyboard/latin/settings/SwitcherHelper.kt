// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.settings

import android.content.Context
import android.view.inputmethod.InputMethodSubtype
import androidx.core.content.edit
import helium314.keyboard.compat.locale
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.prefs
import java.util.Locale

object SwitcherHelper {
    private const val TAG = "SwitcherHelper"

    /**
     * Applies the switcher wizard answers.
     *
     * @param context Application / Activity context.
     * @param preset The selected keyboard preset (GBOARD, SAMSUNG, or null/OWN for default).
     * @param mainLocale The primary language chosen, or null if skipped.
     * @param secondLocale The secondary language chosen, or null if single-language or skipped.
     */
    fun applyAnswers(
        context: Context,
        preset: KeyboardPreset?,
        mainLocale: Locale?,
        secondLocale: Locale?
    ) {
        // 1. Keyboard Preset
        if (preset != null) {
            KeyboardPresets.apply(context, preset)
        }

        // 2. Languages / Subtypes
        if (mainLocale != null) {
            applyLanguages(context, mainLocale, secondLocale)
        }
    }

    /**
     * Configures the enabled and selected subtypes and multilingual typing.
     */
    fun applyLanguages(
        context: Context,
        mainLocale: Locale,
        secondLocale: Locale?
    ) {
        val prefs = context.prefs()

        val currentEnabledString = prefs.getString(
            Settings.PREF_ENABLED_SUBTYPES,
            Defaults.PREF_ENABLED_SUBTYPES
        )!!
        val enabledSettings = SubtypeSettings.createSettingsSubtypes(currentEnabledString).toMutableList()

        val currentAdditionalString = prefs.getString(
            Settings.PREF_ADDITIONAL_SUBTYPES,
            Defaults.PREF_ADDITIONAL_SUBTYPES
        )!!
        val additionalSubtypes = SubtypeSettings.createSettingsSubtypes(currentAdditionalString).toMutableList()

        // 1. Find existing subtype for mainLocale, preserving custom/additional layouts if present
        val existingMain = enabledSettings.firstOrNull { it.locale == mainLocale }
            ?: additionalSubtypes.firstOrNull { it.locale == mainLocale }
            ?: enabledSettings.firstOrNull { it.locale.language == mainLocale.language }
            ?: additionalSubtypes.firstOrNull { it.locale.language == mainLocale.language }

        val baseMainSettings = (existingMain ?: getBestSubtypeForLocale(mainLocale).toSettingsSubtype())
            .without(Constants.Subtype.ExtraValue.SECONDARY_LOCALES)

        val targetMainSettings = if (secondLocale != null && secondLocale != mainLocale) {
            baseMainSettings.with(
                Constants.Subtype.ExtraValue.SECONDARY_LOCALES,
                secondLocale.toLanguageTag()
            )
        } else {
            baseMainSettings
        }

        // 2. Update additionalSubtypes:
        // If targetMainSettings is a custom layout (not default resource) or has SecondaryLocales,
        // it must be in additionalSubtypes. Otherwise if it's default resource without SecondaryLocales,
        // it must not be in additionalSubtypes.
        val existingAddIndex = if (existingMain != null) {
            additionalSubtypes.indexOfFirst {
                it == existingMain || (it.locale == existingMain.locale && it.mainLayoutName() == existingMain.mainLayoutName())
            }
        } else {
            additionalSubtypes.indexOfFirst { it.locale.language == mainLocale.language }
        }

        if (targetMainSettings.isSameAsDefault()) {
            if (existingAddIndex >= 0) {
                additionalSubtypes.removeAt(existingAddIndex)
            }
        } else {
            if (existingAddIndex >= 0) {
                additionalSubtypes[existingAddIndex] = targetMainSettings
            } else {
                additionalSubtypes.add(targetMainSettings)
            }
        }

        // 3. Update enabledSettings:
        // Keep every existing enabled subtype. Replace existing main subtype or add targetMainSettings.
        val existingEnabledIndex = if (existingMain != null) {
            enabledSettings.indexOfFirst {
                it == existingMain || (it.locale == existingMain.locale && it.mainLayoutName() == existingMain.mainLayoutName())
            }
        } else {
            enabledSettings.indexOfFirst { it.locale.language == mainLocale.language }
        }

        if (existingEnabledIndex >= 0) {
            enabledSettings[existingEnabledIndex] = targetMainSettings
        } else {
            enabledSettings.add(targetMainSettings)
        }

        val newAdditionalString = SubtypeSettings.createPrefSubtypes(additionalSubtypes)
        val newEnabledString = SubtypeSettings.createPrefSubtypes(enabledSettings)
        val newSelectedString = targetMainSettings.toPref()

        prefs.edit {
            putString(Settings.PREF_ADDITIONAL_SUBTYPES, newAdditionalString)
            putString(Settings.PREF_ENABLED_SUBTYPES, newEnabledString)
            putString(Settings.PREF_SELECTED_SUBTYPE, newSelectedString)
        }

        SubtypeSettings.reloadEnabledSubtypes(context)

        try {
            val chosenSubtype = SubtypeSettings.getSelectedSubtype(prefs)
            KeyboardSwitcher.getInstance().switchToSubtype(chosenSubtype)
        } catch (t: Throwable) {
            Log.d(TAG, "KeyboardSwitcher.switchToSubtype not available: ${t.message}")
        }
    }

    /**
     * Resolves the best available resource subtype for [locale], falling back to default subtype.
     */
    fun getBestSubtypeForLocale(locale: Locale): InputMethodSubtype {
        val direct = SubtypeSettings.getResourceSubtypesForLocale(locale)
        if (direct.isNotEmpty()) return direct.first()

        val available = SubtypeSettings.getAvailableSubtypeLocales()
        val matchedLocale = LocaleUtils.getBestMatch(locale, available) { it }
        if (matchedLocale != null) {
            val matchedSubtypes = SubtypeSettings.getResourceSubtypesForLocale(matchedLocale)
            if (matchedSubtypes.isNotEmpty()) return matchedSubtypes.first()
        }

        return SubtypeUtilsAdditional.createDefaultSubtype(locale)
    }

    /**
     * Finds the system language matching available subtypes.
     */
    fun getSystemSubtypeLocale(context: Context): Locale {
        val systemLocales = SubtypeSettings.getSystemLocales()
        val availableLocales = SubtypeSettings.getAvailableSubtypeLocales()
        for (sysLoc in systemLocales) {
            val match = LocaleUtils.getBestMatch(sysLoc, availableLocales) { it }
            if (match != null) return match
        }
        val confLocale = context.resources.configuration.locale()
        val match = LocaleUtils.getBestMatch(confLocale, availableLocales) { it }
        if (match != null) return match

        return if (availableLocales.contains(Locale.US)) Locale.US
        else availableLocales.firstOrNull() ?: Locale.getDefault()
    }

    /**
     * Returns all available subtype locales sorted by their localized display name,
     * placing system locales first.
     */
    fun getSortedAvailableLocales(context: Context): List<Locale> {
        val systemLocales = SubtypeSettings.getSystemLocales()
        val available = SubtypeSettings.getAvailableSubtypeLocales()

        return available.sortedWith(
            compareBy(
                { it !in systemLocales },
                { getLocaleDisplayName(context, it) }
            )
        )
    }

    /**
     * Generates a user-facing label for a locale.
     */
    fun getLocaleDisplayName(context: Context, locale: Locale): String {
        val nativeName = StringUtils.capitalizeFirstCodePoint(
            locale.getDisplayName(locale),
            locale
        )
        val localizedName = locale.localizedDisplayName(context.resources)
        return if (nativeName.equals(localizedName, ignoreCase = true)) {
            nativeName
        } else {
            "$nativeName ($localizedName)"
        }
    }
}
