# Walkthrough: Keyboard Switcher Wizard & Multilingual Setup

## 1. Where the New Steps Sit in the Flow and Why

### Flow Placement
The new switcher flow is placed immediately after **Step 1 ("Turn it on")** and before **Step 2 ("Try it")** in `WelcomeWizard.kt`:
- **Step 0**: Hero (welcome & brand)
- **Step 1**: Turn it on (Enable VibeVoice Keyboard + Select it as active IME)
- **Switcher Flow**: 3 quick, one-tap questions to adapt presets and languages
- **Step 2**: Try it (Typing field -> Mic permission -> Voice dictation)
- **Step 3**: Your minutes (10 free trial minutes / Link account)
- **Step 4**: The floating mark (Background dictation overlay)
- **Step 5**: Done (Closing slogan & finish)

### Rationale
1. **Try-it experience**: Step 2 is the user's first tactile interaction with the keyboard. If the switcher ran after Step 2, the user would test-type on the unconfigured default layout, and changing their layout and languages *after* they already tried it would be jarring and counter-intuitive.
2. **Keyboard readiness**: Before Step 1, the keyboard is not yet enabled or selected in Android. Once enabled and selected at the end of Step 1, configuring muscle memory presets and languages is the most natural next step.
3. **Preserving existing architecture**: The 4 numbered steps (1 to 4) defined in `docs/onboarding_concept.md` remain completely untouched in numbering, progress indicator (`StepNumbers(total = 4)`), and state machine. The switcher acts as a streamlined customization transition between Step 1 and Step 2.
4. **Complete skippability**: Users who skip the switcher questions transition directly to Step 2 with preferences and subtypes left in today's exact default state.

---

## 2. Exact Subtype & Preference Changes Per Answer

All configuration logic is encapsulated in `SwitcherHelper.kt`.

### Question 1: "Which keyboard did you use before?"
- **Gboard**: Calls `KeyboardPresets.apply(context, KeyboardPreset.GBOARD)`.
- **Samsung Keyboard**: Calls `KeyboardPresets.apply(context, KeyboardPreset.SAMSUNG)`.
- **Something else / Skip (Default)**: Does nothing. Standard HeliBoard layout is preserved.

### Question 2: "Which language do you mostly type in?"
- **Preselected**: Detects system language matching available resource subtypes (e.g. `de-DE` on German system, `en-US` on US English system).
- **Single language choice (when Q3 is "No")**:
  - Main resource subtype resolved via `SwitcherHelper.getBestSubtypeForLocale(mainLocale)`.
  - `Settings.PREF_ENABLED_SUBTYPES`: set to the single main subtype pref string (e.g., `de-DE§KeyboardLayoutSet=MAIN:qwertz,SupportTouchPositionCorrection`).
  - `Settings.PREF_SELECTED_SUBTYPE`: set to the main subtype pref string.
  - `Settings.PREF_ADDITIONAL_SUBTYPES`: cleaned of any previous custom subtype for `mainLocale`.
  - `SubtypeSettings.reloadEnabledSubtypes(context)` is called to refresh subtype caches.
  - `KeyboardSwitcher.getInstance().switchToSubtype(...)` updates the active keyboard if running.

### Question 3: "Do you also type in a second language?"
- **"No, just one language" / Skip (Default)**:
  - Leaves the single language configured in Q2 enabled and active without secondary locales.
- **Pick a language (e.g. Second Locale $L_2$, Main Locale $L_1$)**:
  1. Main subtype receives the extra value `SecondaryLocales=<secondLocaleTag>` (e.g., `SecondaryLocales=en-US` on German subtype).
  2. Because it contains `SecondaryLocales`, the main subtype is now an **additional subtype** (`SettingsSubtype.isSameAsDefault() == false`).
  3. `Settings.PREF_ADDITIONAL_SUBTYPES`: Updated to include `mainWithMultilingual.toPref()`.
  4. `Settings.PREF_ENABLED_SUBTYPES`: Updated with both `mainWithMultilingual.toPref()` and `secondSubtype.toPref()`, joined by `Separators.SETS` (`";"`).
  5. `Settings.PREF_SELECTED_SUBTYPE`: Set to `mainWithMultilingual.toPref()`.
  6. `SubtypeSettings.reloadEnabledSubtypes(context)`:
     - `loadAdditionalSubtypes` creates the `InputMethodSubtype` with `ExtraValue.IS_ADDITIONAL_SUBTYPE`.
     - `loadEnabledSubtypes` loads both enabled subtypes: the customized main subtype and the resource second subtype.
     - `RichInputMethodManager.getInstance().refreshSubtypeCaches()` refreshes internal caches.
  7. When typing on $L_1$, `SettingsValues.mSecondaryLocales` loads $L_2$, so dictionary predictions and suggestions consider both languages concurrently without requiring manual layout switching.
  8. Because $L_2$ is also an enabled subtype, users can still toggle to its dedicated keyboard layout via spacebar swipe or language key.

### Switching Back from Bilingual to Single Language:
- When a user runs the switcher again from settings and selects a single language:
  - `PREF_ADDITIONAL_SUBTYPES` is stripped of the previous multilingual subtype.
  - `PREF_ENABLED_SUBTYPES` retains only the single main subtype.
  - Subtype settings reload cleanly.

---

## 3. Dependency Contract: KeyboardPresets Stub

Created `app/src/main/java/helium314/keyboard/latin/settings/KeyboardPresets.kt` strictly adhering to the contract:

```kotlin
// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.settings

import android.content.Context
import helium314.keyboard.latin.utils.Log

// stub, replaced by the presets branch
enum class KeyboardPreset { OWN, GBOARD, SAMSUNG }

// stub, replaced by the presets branch
object KeyboardPresets {
    fun apply(context: Context, preset: KeyboardPreset) {
        // stub, replaced by the presets branch
        Log.d("KeyboardPresets", "Stub KeyboardPresets.apply called with preset: $preset")
    }
}
```

No real preset logic or preference key `draw_keys` was added or modified. The presets branch replaces this stub cleanly at merge time.

---

## 4. Reusable Switcher in Settings

- Added `SwitcherWizard.kt`:
  - `SwitcherFlow`: Reusable composable for the 3 questions with 1-tap selections, bounded scrollable language pickers, search filtering, and BackHandler.
  - `SwitcherScreen`: Full-screen standalone settings destination with scaffold and BackButton.
- Added `SettingsDestination.Switcher = "switcher"` to `SettingsNavHost.kt`.
- Added `onClickSwitcher` preference row on `MainSettingsScreen.kt` right above `Languages & layouts`:
  - Title: *"Switching from another keyboard?"*
  - Summary: *"Match presets and set up languages"*
  - Icon: `R.drawable.ic_ime_switcher`
- All strings added in English (`values/strings.xml`) and translated to German (`values-de/strings_vibevoice.xml`). No upstream locale files were modified.

---

## 5. Test Results

### Build Verification
- `./gradlew -q assembleDebug`: **SUCCEEDED** (exit code 0).

### Unit Test Suite
- `./gradlew testDebugUnitTest`: **211 tests completed, 2 failed**.
  - Known upstream failures (unrelated, expected per `CLAUDE.md` and instructions):
    - `InputLogicTest.insertLetterIntoWordHangulFails`
    - `StringUtilsTest.detectEmojisAtEndFails`
  - All 6 new unit tests in `SwitcherHelperTest` **PASSED**:
    1. `singleLanguageSelectionSetsEnabledAndSelectedSubtype`: Verified enabled and selected subtype is German without `SecondaryLocales`.
    2. `secondLanguageSelectionEnablesBothSubtypesAndSetsMultilingualTyping`: Verified German subtype has `SecondaryLocales=en-US`, `PREF_ADDITIONAL_SUBTYPES` stores the subtype, both German and English are enabled, and `SubtypeUtils.getSecondaryLocales(...)` parses `[Locale.US]`.
    3. `switchingFromBilingualBackToSingleLanguageCleansUpSecondaryLocales`: Verified clean reset from multilingual back to single language.
    4. `skippingEverythingPreservesCurrentPreferencesAndSubtypes`: Verified zero preference mutation on skip.
    5. `systemSubtypeLocaleDetectionReturnsValidAvailableLocale`: Verified accurate locale detection.
    6. `availableLocalesListIsPopulatedAndFormatted`: Verified locale listing and native/localized name display.
  - Existing `SubtypeTest` **PASSED** (100% green).

---

## 6. What Could Not Be Verified Locally

1. **Physical touch interactions on Android device**: The test environment has no physical display or live device connected.
2. **Actual keycap spacing / preset layout changes**: The `KeyboardPresets` file is a minimal stub as dictated by the contract; visual layout preset changes depend on the parallel `feat/keyboard-presets` branch.

---

## 7. What the Lead Must Check on the Phone

1. **Initial Onboarding Wizard**:
   - Complete Step 1 ("Turn it on" and "Select as keyboard").
   - Verify the switcher appears with Question 1 ("Which keyboard did you use before?").
   - Test picking Gboard or Samsung Keyboard (1 tap) -> verify it advances to Question 2.
   - Verify the system language is highlighted at the top of Question 2.
   - Tap system language or another language (1 tap) -> verify it advances to Question 3.
   - Pick a second language (or tap "No, just one language").
   - Verify Step 2 ("Try it") opens with the test typing field.
   - Test typing: verify suggestions show words from both languages when bilingual typing was selected.
2. **Settings Entry**:
   - Open keyboard settings.
   - Verify the entry *"Switching from another keyboard?"* is visible on the main settings screen.
   - Tap it -> verify it opens the switcher flow.
   - Select different options and tap finish; verify settings update accordingly.
3. **Skip & Back Behavior**:
   - Verify pressing Android Back on Question 2/3 returns to Question 1/2.
   - Verify tapping "Skip" skips cleanly without resetting unrelated preferences.
