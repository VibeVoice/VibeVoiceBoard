# Walkthrough: Keyboard Vibration Default Off & Setup Wizard Step

## Summary

Keyboard vibration on keypress is now disabled by default for fresh installs. The first-run setup wizard (`WelcomeWizard.kt`) asks users whether to enable vibration with two one-tap answers ("No" highlighted as default, "Yes" unhighlighted) and a skip action.

## Changes

### 1. Vibration Default Off
- In [`app/src/main/java/helium314/keyboard/latin/settings/Defaults.kt`](file:///Users/schneider/repos/VibeVoiceBoard/.wt/vibration-default-off-plus-wizard-question/app/src/main/java/helium314/keyboard/latin/settings/Defaults.kt), changed `PREF_VIBRATE_ON` from `true` to `false`.
- Verified all callers:
  - [`Settings.java:readVibrationEnabled(prefs)`](file:///Users/schneider/repos/VibeVoiceBoard/.wt/vibration-default-off-plus-wizard-question/app/src/main/java/helium314/keyboard/latin/settings/Settings.java) uses `Defaults.PREF_VIBRATE_ON`.
  - [`PreferencesScreen.kt`](file:///Users/schneider/repos/VibeVoiceBoard/.wt/vibration-default-off-plus-wizard-question/app/src/main/java/helium314/keyboard/settings/screens/PreferencesScreen.kt) toggle switch and dependent settings (`vibration_duration_settings`, `vibrate_in_dnd_mode`) use `Defaults.PREF_VIBRATE_ON`.
  - Existing users who already have a value stored in preferences retain their choice without data migration; users without an explicit value get `false`.

### 2. Setup Wizard Question
- In [`app/src/main/java/helium314/keyboard/settings/WelcomeWizard.kt`](file:///Users/schneider/repos/VibeVoiceBoard/.wt/vibration-default-off-plus-wizard-question/app/src/main/java/helium314/keyboard/settings/WelcomeWizard.kt):
  - Incremented total progress steps in `StepNumbers` from 4 to 5.
  - Added step 5 to `steps()` displaying `StepHeader(5, ...)` and `VibrateStep`.
  - Moved final closing hero screen from step 5 to step 6.
  - Added isolated `@Composable fun VibrateStep` at the end of the file with:
    - Highlighted default answer: "No" (`Brand.accent` tint, check icon, semi-bold font).
    - Unhighlighted answer: "Yes" (neutral step background, select icon, medium font).
    - Centered skip action ("Skip this" / "Überspringen").
  - Answering "No" sets `PREF_VIBRATE_ON = false` and advances to step 6.
  - Answering "Yes" sets `PREF_VIBRATE_ON = true` and advances to step 6.
  - Tapping "Skip this" preserves the default (`false`) and advances to step 6.
- In [`app/src/main/res/values/strings.xml`](file:///Users/schneider/repos/VibeVoiceBoard/.wt/vibration-default-off-plus-wizard-question/app/src/main/res/values/strings.xml):
  - Added `setup_vibrate_title`, `setup_vibrate_instruction`, `setup_vibrate_no`, `setup_vibrate_yes`.
- In [`app/src/main/res/values-de/strings_vibevoice.xml`](file:///Users/schneider/repos/VibeVoiceBoard/.wt/vibration-default-off-plus-wizard-question/app/src/main/res/values-de/strings_vibevoice.xml):
  - Added German translations for the vibration setup strings.

### 3. Unit Tests
- Added [`app/src/test/java/helium314/keyboard/VibrationSettingsTest.kt`](file:///Users/schneider/repos/VibeVoiceBoard/.wt/vibration-default-off-plus-wizard-question/app/src/test/java/helium314/keyboard/VibrationSettingsTest.kt):
  - `defaultVibrationIsOff`: confirms `Defaults.PREF_VIBRATE_ON == false` and unconfigured preferences return `false`.
  - `existingUserSettingPreserved`: verifies previously stored `true` or `false` values are preserved.
  - `wizardAnswerYesEnablesVibration`: verifies selecting "Yes" enables vibration.
  - `wizardAnswerNoDisablesVibration`: verifies selecting "No" stores `false`.

## Verification Results

### Unit Tests
Executed with `./gradlew testDebugUnitTest`:
- 209 tests executed.
- Only the 3 known/expected test failures occurred:
  1. `InputLogicTest.insertLetterIntoWordHangulFails` (known failure)
  2. `StringUtilsTest.detectEmojisAtEndFails` (known failure)
  3. `XLinkTest.knownDictionaries` (network test; passed when connected)
- All 4 new tests in `VibrationSettingsTest` passed.

### Lint
Executed with `./gradlew lint`:
- `BUILD SUCCESSFUL` (0 lint errors).

### Parallel Branch Compatibility
Checked in-memory three-way tree merge against `origin/feat/switcher-wizard`:
- `WelcomeWizard.kt`, `strings.xml`, `strings_vibevoice.xml`, and all settings files merge cleanly without conflicts.
