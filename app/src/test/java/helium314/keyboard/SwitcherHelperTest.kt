// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.KeyboardPreset
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.settings.SwitcherHelper
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getSecondaryLocales
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.util.Locale
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowInputMethodManager2::class
])
class SwitcherHelperTest {
    private val latinIME = Robolectric.setupService(LatinIME::class.java)

    init {
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
    }

    @BeforeTest
    fun setUp() {
        SubtypeSettings.init(latinIME)
    }

    @Test
    fun singleLanguageSelectionSetsEnabledAndSelectedSubtype() {
        val prefs = latinIME.prefs()
        val deLocale = Locale.GERMAN

        SwitcherHelper.applyAnswers(
            context = latinIME,
            preset = KeyboardPreset.GBOARD,
            mainLocale = deLocale,
            secondLocale = null
        )

        val selectedPref = prefs.getString(Settings.PREF_SELECTED_SUBTYPE, "")!!
        val selectedSubtype = selectedPref.toSettingsSubtype()
        assertEquals("de", selectedSubtype.locale.language)
        assertFalse(selectedSubtype.hasExtraValueOf(ExtraValue.SECONDARY_LOCALES))

        val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(false)
        assertEquals(1, enabledSubtypes.size)
        assertEquals("de", enabledSubtypes.first().locale().language)
    }

    @Test
    fun secondLanguageSelectionEnablesBothSubtypesAndSetsMultilingualTyping() {
        val prefs = latinIME.prefs()
        val deLocale = Locale.GERMAN
        val enLocale = Locale.US

        SwitcherHelper.applyAnswers(
            context = latinIME,
            preset = KeyboardPreset.SAMSUNG,
            mainLocale = deLocale,
            secondLocale = enLocale
        )

        // Selected subtype must be main language (German)
        val selectedPref = prefs.getString(Settings.PREF_SELECTED_SUBTYPE, "")!!
        val selectedSettingsSubtype = selectedPref.toSettingsSubtype()
        assertEquals("de", selectedSettingsSubtype.locale.language)

        // Multilingual typing: SecondaryLocales extra value must contain en-US
        val secondaryLocalesExtra = selectedSettingsSubtype.getExtraValueOf(ExtraValue.SECONDARY_LOCALES)
        assertNotNull(secondaryLocalesExtra)
        assertEquals(enLocale.toLanguageTag(), secondaryLocalesExtra)

        // getSecondaryLocales parses it into List<Locale>
        val secondaryLocales = getSecondaryLocales(selectedSettingsSubtype.extraValues)
        assertEquals(1, secondaryLocales.size)
        assertEquals("en", secondaryLocales.first().language)

        // PREF_ADDITIONAL_SUBTYPES must include the multilingual subtype
        val additionalPref = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!
        val additionalSubtypes = SubtypeSettings.createSettingsSubtypes(additionalPref)
        assertTrue(additionalSubtypes.any { it.locale.language == "de" && it.hasExtraValueOf(ExtraValue.SECONDARY_LOCALES) })

        // Enabled subtypes must contain both German (multilingual) and English
        val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(false)
        assertEquals(2, enabledSubtypes.size)
        val enabledLanguages = enabledSubtypes.map { it.locale().language }.toSet()
        assertTrue(enabledLanguages.contains("de"))
        assertTrue(enabledLanguages.contains("en"))

        // The active selected InputMethodSubtype has secondary locales
        val currentSubtype = SubtypeSettings.getSelectedSubtype(prefs)
        val parsedSecondaries = getSecondaryLocales(currentSubtype.extraValue)
        assertEquals(1, parsedSecondaries.size)
        assertEquals("en", parsedSecondaries.first().language)
    }

    @Test
    fun switchingFromBilingualBackToSingleLanguageCleansUpSecondaryLocales() {
        val prefs = latinIME.prefs()
        val deLocale = Locale.GERMAN
        val enLocale = Locale.US

        // First apply bilingual setup
        SwitcherHelper.applyAnswers(
            context = latinIME,
            preset = KeyboardPreset.GBOARD,
            mainLocale = deLocale,
            secondLocale = enLocale
        )
        assertEquals(2, SubtypeSettings.getEnabledSubtypes(false).size)

        // Then switch back to single language (no second language)
        SwitcherHelper.applyAnswers(
            context = latinIME,
            preset = KeyboardPreset.OWN,
            mainLocale = deLocale,
            secondLocale = null
        )

        val selectedPref = prefs.getString(Settings.PREF_SELECTED_SUBTYPE, "")!!
        val selectedSubtype = selectedPref.toSettingsSubtype()
        assertEquals("de", selectedSubtype.locale.language)
        assertFalse(selectedSubtype.hasExtraValueOf(ExtraValue.SECONDARY_LOCALES))

        // Enabled subtypes must now only contain German
        val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(false)
        assertEquals(1, enabledSubtypes.size)
        assertEquals("de", enabledSubtypes.first().locale().language)

        // Additional subtypes must not contain secondary locales for German
        val additionalPref = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!
        val additionalSubtypes = SubtypeSettings.createSettingsSubtypes(additionalPref)
        assertFalse(additionalSubtypes.any { it.locale.language == "de" && it.hasExtraValueOf(ExtraValue.SECONDARY_LOCALES) })
    }

    @Test
    fun skippingEverythingPreservesCurrentPreferencesAndSubtypes() {
        val prefs = latinIME.prefs()
        val initialSelected = prefs.getString(Settings.PREF_SELECTED_SUBTYPE, null)
        val initialEnabled = prefs.getString(Settings.PREF_ENABLED_SUBTYPES, null)
        val initialAdditional = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, null)

        SwitcherHelper.applyAnswers(
            context = latinIME,
            preset = null,
            mainLocale = null,
            secondLocale = null
        )

        assertEquals(initialSelected, prefs.getString(Settings.PREF_SELECTED_SUBTYPE, null))
        assertEquals(initialEnabled, prefs.getString(Settings.PREF_ENABLED_SUBTYPES, null))
        assertEquals(initialAdditional, prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, null))
    }

    @Test
    fun systemSubtypeLocaleDetectionReturnsValidAvailableLocale() {
        val detected = SwitcherHelper.getSystemSubtypeLocale(latinIME)
        assertNotNull(detected)
        val available = SubtypeSettings.getAvailableSubtypeLocales()
        assertTrue(available.contains(detected))
    }

    @Test
    fun availableLocalesListIsPopulatedAndFormatted() {
        val sorted = SwitcherHelper.getSortedAvailableLocales(latinIME)
        assertTrue(sorted.isNotEmpty())

        val displayName = SwitcherHelper.getLocaleDisplayName(latinIME, Locale.GERMANY)
        assertTrue(displayName.isNotBlank())
    }
}
