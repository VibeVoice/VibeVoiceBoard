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
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.SubtypeUtilsAdditional
import helium314.keyboard.latin.utils.prefs
import java.text.Collator
import java.util.Locale

/**
 * The answer to "Coming from another keyboard?" in the setup wizard.
 *
 * It is remembered between the language step, where it is asked, and the style step, where it
 * pre-selects the keyboard style: Gboard users get the Gboard preset, Samsung users the Samsung one.
 * Other or first-time keyboard users get no preset pre-selection so custom heights/gaps are not wiped.
 */
enum class PreviousKeyboard {
    GBOARD, SAMSUNG, OTHER, FIRST;

    /** The keyboard style this answer pre-selects, or null if no preset should be applied. */
    fun toPreset(): KeyboardPreset? = when (this) {
        GBOARD -> KeyboardPreset.GBOARD
        SAMSUNG -> KeyboardPreset.SAMSUNG
        OTHER, FIRST -> null
    }

    companion object {
        /** What a user who answers nothing gets: a first-time keyboard user. */
        val DEFAULT = FIRST

        fun fromName(name: String?): PreviousKeyboard? = entries.firstOrNull { it.name == name }
    }
}

/**
 * One row of the language list: a language shown once, under a clean name in its own language.
 *
 * [locale] is the locale that gets applied when the row is chosen. [aliases] are all locales the row
 * stands for, always including [locale]: `de` and `de-DE` are one row, not two. [searchText] holds
 * every name a user might type, including the English one and the phone-language one.
 */
data class LanguageOption(
    val locale: Locale,
    val label: String,
    val aliases: Set<Locale>,
    val searchText: String
) {
    fun matches(query: String): Boolean = query.isBlank() || searchText.contains(query.trim(), ignoreCase = true)
}

object SwitcherHelper {
    private const val TAG = "SwitcherHelper"

    /** The style a previous-keyboard answer pre-selects, or null if no preset should be applied. */
    fun presetFor(previous: PreviousKeyboard?): KeyboardPreset? = previous?.toPreset()

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
     * The rows of the language list, ready to show: each language once, named in its own language
     * (Deutsch, English, Français), phone languages first and the rest alphabetical.
     *
     * A region is added in one bracket, and only where it tells rows of the same language apart:
     * `Deutsch` and `Deutsch (Schweiz)`, `English (US)` and `English (UK)`. A language with a single
     * row, or with a row that carries no region of its own, gets no bracket there. `de` and `de-DE`
     * collapse into one row. The "no language" subtype is not a language and is left out.
     */
    fun getLanguageOptions(context: Context): List<LanguageOption> {
        val resources = context.resources
        val systemLocales = SubtypeSettings.getSystemLocales()
        val locales = SubtypeSettings.getAvailableSubtypeLocales()
            .filter { it.language.isNotEmpty() && it.language != SubtypeLocaleUtils.NO_LANGUAGE }

        val options = mutableListOf<LanguageOption>()
        locales.groupBy { it.language to it.script }.values.forEach { group ->
            // de and de-DE are one language to a person. The home-region locale represents the pair,
            // as it is what a German phone reports; applying it still finds an enabled plain "de".
            val home = group.firstOrNull { it.country.equals(it.language, ignoreCase = true) }
            val plain = group.firstOrNull { it.country.isEmpty() }
            val merged = if (home != null && plain != null) listOf(home to setOf(home, plain)) else emptyList()
            val entries = merged + group.filter { l -> merged.none { l in it.second } }.map { it to setOf(it) }
            val plainEntry = entries.firstOrNull { it.first.country.isEmpty() || it.second.size > 1 }
            for (entry in entries) {
                val (locale, aliases) = entry
                val showRegion = entries.size > 1 && entry != plainEntry
                val label = languageLabel(context, locale, showRegion)
                val searchText = listOf(
                    label,
                    locale.localizedDisplayName(resources),
                    locale.getDisplayName(Locale.ENGLISH),
                    aliases.joinToString(" ") { it.toLanguageTag() }
                ).joinToString(" ")
                options.add(LanguageOption(locale, label, aliases, searchText))
            }
        }

        val collator = Collator.getInstance(resources.configuration.locale())
        return options
            .sortedWith(
                compareBy<LanguageOption> { option -> option.aliases.none { it in systemLocales } }
                    .thenComparator { a, b -> collator.compare(a.label, b.label) }
            )
            .distinctBy { it.label.lowercase() } // never two rows with the same name
    }

    /** The row that stands for [locale]: an exact or alias match, else the same language and script. */
    fun findLanguageOption(options: List<LanguageOption>, locale: Locale): LanguageOption? =
        options.firstOrNull { locale in it.aliases }
            ?: options.firstOrNull { it.locale.language == locale.language && it.locale.script == locale.script }
            ?: options.firstOrNull { it.locale.language == locale.language }

    /** The row the language list starts on: the phone's own language, else the first row. */
    fun getDefaultMainOption(context: Context, options: List<LanguageOption>): LanguageOption? =
        findLanguageOption(options, getSystemSubtypeLocale(context)) ?: options.firstOrNull()

    /**
     * A clean name for [locale] in its own language, with at most one bracket.
     *
     * Languages whose name the platform does not know, and the few the app names itself (`en-US` as
     * "English (US)", `en-GB` as "English (UK)", `hi-Latn` as "Hinglish"), take the app's own
     * wording, which already carries its qualifier. The Spanish and Serbian variants are left out
     * on purpose: the app's wording for them is an English name in every non-English language. Everything else is the language's name in that language, plus a script and,
     * if [showRegion], a region, both inside the same bracket.
     */
    private fun languageLabel(context: Context, locale: Locale, showRegion: Boolean): String {
        val resources = context.resources
        val own = Locale.Builder().setLanguage(locale.language).build()
        val appName = locale.localizedDisplayName(resources, own)
        if (hasAppOwnedName(locale)) return StringUtils.capitalizeFirstCodePoint(appName, own)

        val language = own.getDisplayLanguage(own).takeIf { it.isNotBlank() && it != locale.language }
            ?: own.getDisplayLanguage(Locale.ENGLISH).takeIf { it.isNotBlank() }
            ?: locale.language
        val qualifiers = listOfNotNull(
            locale.script.takeIf { it.isNotEmpty() }?.let { locale.getDisplayScript(own) }?.takeIf { it.isNotBlank() },
            locale.country.takeIf { showRegion && it.isNotEmpty() }
                ?.let { Locale.Builder().setRegion(it).build().getDisplayCountry(own).takeIf { name -> name.isNotBlank() } ?: it }
        )
        val name = StringUtils.capitalizeFirstCodePoint(language, own)
        return if (qualifiers.isEmpty()) name else "$name (${qualifiers.joinToString(", ")})"
    }

    private fun hasAppOwnedName(locale: Locale): Boolean = when (locale.toLanguageTag()) {
        "en-US", "en-GB", "hi-Latn", "mns", "tok", "xdq", "dru", "st", "dag", "mhr" -> true
        else -> false
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
        return languageLabel(context, locale, showRegion = true)
    }
}
