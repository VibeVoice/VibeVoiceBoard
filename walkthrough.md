# Walkthrough - Review Findings: Upgrade Safety, Trial Retry Resilience, and Styling

This document reports the resolution of all 7 defect findings identified during review of the combined branch in repository `VibeVoiceBoard`.

---

## Finding -> Commit -> Change Summary

| Finding | Commit | Description of Changes |
| :--- | :--- | :--- |
| **1. Wizard Active In-Memory Flag & Retry Fallback** | `d4f74df04` | Dropped persisted preference lookup for `PREF_SETUP_WIZARD_ACTIVE` in `VibeVoiceClient.isSetupWizardActive(context)`. Replaced with in-memory `@Volatile @JvmStatic var isWizardActive` and added `setSetupWizardActive(active: Boolean)`. In `LatinIME.handleVoiceInput`, when `apiKey == null`: only trigger retry if `isSetupWizardActive(this)`, `!isTrialSpent(this)`, and `triggerTrialRetry(this)` succeeds (has active collector); otherwise fall back to toast `vibevoice_not_linked` + `launchSettings("vibevoice")`. In `VibeVoiceClient.triggerTrialRetry`, check `_trialRetryFlow.subscriptionCount.value == 0` and return `false` if uncollected. |
| **2. Existing User Vibration Preservation on Upgrade** | `913a9cd05` | Added upgrade migration block in `AppUpgrade.onUpgrade` gated by `oldVersion <= LAST_VERSION_WITH_OLD_VIBRATE_AND_DRAW_KEYS_DEFAULTS` (version code `600063`). Fresh installs return early at the start of `onUpgrade` (`oldVersion == 0`), preserving `Defaults.PREF_VIBRATE_ON = false`. On upgrade, if `Settings.PREF_VIBRATE_ON` is not stored, writes `true`. Replaced no-op tests with real upgrade tests in `VibrationSettingsTest.kt`. |
| **3. 'Draw keys' Switch Migration from Key Borders** | `0dad6dc65` | In `AppUpgrade.onUpgrade`, if `Settings.PREF_DRAW_KEYS` is not stored and `Settings.PREF_THEME_KEY_BORDERS` is stored, copies `theme_key_borders` to `draw_keys`. Added tests in `DrawKeysThemeTest.kt` verifying upgrade migration, explicit choice preservation, and unset defaults. |
| **4. Restore Comma & Period Styling Without Draw Keys** | `3f6ab224f` | In `TextKeyData.kt` `getDefaultBackground`, restored the requirement that comma and period (`GROUP_COMMA` / `GROUP_PERIOD`) only receive `BACKGROUND_TYPE_FUNCTIONAL` on `SYMBOLS` / `SYMBOLS_SHIFTED` layouts or when `Settings.getValues().mDrawKeys` is active. Standard alphabet layouts retain `BACKGROUND_TYPE_NORMAL`, preserving the default appearance and avoiding unnecessary popup hints in `KeyboardView`. |
| **5. Do Not Retry on RateLimited & Move Misplaced KDoc** | `6b333eeab` | In `VibeVoiceClient.requestTrialKeyWithRetry`, changed retry loop condition to exit on any non-failed result (`lastResult !is TrialResult.Failed`), preventing redundant retries when HTTP 429 (`TrialResult.RateLimited`) is encountered. Moved misplaced KDoc from `trialKeyEndpoint` back to `requestTrialKey`. Added test `trialKeyRequestDoesNotRetryOnRateLimited` in `TrialResilienceAndLinkingTest.kt`. |
| **6. Custom Tab Launch Task Flag for Activity Context** | `8afe37e72` | In `VibeVoiceLinkPanel.kt` `openLinkInBrowser`, only add `Intent.FLAG_ACTIVITY_NEW_TASK` if `context !is Activity`. When launched from an Activity, Custom Tab remains in the wizard/settings task. Added tests `openLinkInBrowserFromActivityDoesNotStartNewTask` and `openLinkInBrowserFromApplicationStartsNewTask`. |
| **7. Replace No-Op Tests with Real Path Assertions** | Covered across `d4f74df04`, `913a9cd05`, `6b333eeab` | Replaced no-op tests in `VibrationSettingsTest.kt` (`wizardAnswerYesEnablesVibration`, `wizardAnswerNoDisablesVibration`, `existingUserSettingPreserved`) with real tests calling `checkVersionUpgrade` for fresh installs and upgrades. In `TrialResilienceAndLinkingTest.kt`, replaced `triggerTrialRetryUpdatesPreferenceAndFlow` with tests asserting real `trialRetryFlow` coroutine collection, delivery reporting, collector absence fallback, and trial spent fallback. |

---

## Detailed Notes & Code Locations

### 1. In-Memory Wizard Flag & Trial Retry Fallback
- **Files**:
  - `app/src/main/java/helium314/keyboard/latin/LatinIME.java`
  - `app/src/main/java/helium314/keyboard/latin/vibevoice/VibeVoiceClient.kt`
  - `app/src/test/java/helium314/keyboard/latin/vibevoice/TrialResilienceAndLinkingTest.kt`
- **Rationale**: A killed process leaves SharedPreferences flags set; relying on `PREF_SETUP_WIZARD_ACTIVE` in SharedPreferences causes subsequent mic key presses to be swallowed silently. Since IME and settings share the process (no `android:process` in `AndroidManifest.xml`), `@Volatile @JvmStatic var isWizardActive` is sufficient and resilient to process crashes. Furthermore, `triggerTrialRetry` verifies `_trialRetryFlow.subscriptionCount.value > 0`. If no collector is listening or the trial is already spent, `handleVoiceInput` falls through to launching settings.
- **Note for Parallel Wizard Agent (`WelcomeWizard.kt` / `SwitcherWizard.kt`)**:
  - `WelcomeWizard.kt` and `SwitcherWizard.kt` were **not modified** per task scope.
  - In `WelcomeWizard.kt` line 152, writing `KeySettings.PREF_SETUP_WIZARD_ACTIVE` into `prefs()` is now redundant; setting `VibeVoiceClient.isWizardActive = true` (or `VibeVoiceClient.setSetupWizardActive(true)`) and clearing it on dispose is all that is required.

### 2 & 3. Upgrade Safety for Vibration & Draw Keys
- **Files**:
  - `app/src/main/java/helium314/keyboard/latin/AppUpgrade.kt`
  - `app/src/test/java/helium314/keyboard/VibrationSettingsTest.kt`
  - `app/src/test/java/helium314/keyboard/DrawKeysThemeTest.kt`
- **Rationale**: Fresh installs must start with typing vibration off (`Defaults.PREF_VIBRATE_ON = false`), while existing users who upgraded from previous releases (where default was on) must retain vibration if they never stored an explicit setting. Fresh installs exit `AppUpgrade.onUpgrade` early when `oldVersion == 0`. On upgrade from `oldVersion <= LAST_VERSION_WITH_OLD_VIBRATE_AND_DRAW_KEYS_DEFAULTS (600063)`, unconfigured vibration is explicitly set to `true`. Similarly, users who had `theme_key_borders` enabled have that preference migrated to `draw_keys`.

### 4. Functional Styling for Comma and Period
- **Files**:
  - `app/src/main/java/helium314/keyboard/keyboard/internal/keyboard_parser/floris/TextKeyData.kt`
- **Rationale**: Comma and period (`GROUP_COMMA` and `GROUP_PERIOD`) previously had their `KeyboardElement.SYMBOLS` restriction removed, causing them to receive `BACKGROUND_TYPE_FUNCTIONAL` on all keyboards. The check was restored so that standard alphabet layouts keep `BACKGROUND_TYPE_NORMAL`, and functional styling is only applied when on symbols layouts, when `Settings.getValues().mDrawKeys` is active, or via preset/custom JSON layouts. In `KeyboardView.java`, popup-hint drawing (`drawKeyPopupHint`) is drawn only when keys have functional or action backgrounds, which is now respected.

### 5. Rate-Limit Handling in Trial Key Request
- **Files**:
  - `app/src/main/java/helium314/keyboard/latin/vibevoice/VibeVoiceClient.kt`
  - `app/src/test/java/helium314/keyboard/latin/vibevoice/TrialResilienceAndLinkingTest.kt`
- **Rationale**: An HTTP 429 response (`TrialResult.RateLimited`) signals that the client must pause requests; immediately retrying with exponential backoff within a second violates the server's rate limit instructions. `requestTrialKeyWithRetry` now only retries on `TrialResult.Failed`. The misplaced KDoc explaining trial key acquisition was moved above `requestTrialKey`.

### 6. Chrome Custom Tab Launch Flags
- **Files**:
  - `app/src/main/java/helium314/keyboard/settings/VibeVoiceLinkPanel.kt`
  - `app/src/test/java/helium314/keyboard/latin/vibevoice/TrialResilienceAndLinkingTest.kt`
- **Rationale**: `Intent.FLAG_ACTIVITY_NEW_TASK` separates the Custom Tab into an independent task. When launched from an Activity context (such as `SettingsActivity` or `WelcomeWizard`), omitting `FLAG_ACTIVITY_NEW_TASK` ensures the browser tab stays within the caller's task stack.

---

## Verification & Test Results

Executed full unit test suite:
```bash
env JAVA_HOME=/opt/homebrew/opt/openjdk@21 ANDROID_HOME=/Users/schneider/repos/VibeVoiceBoard/android-sdk ./gradlew testDebugUnitTest
```

### Result:
- Total tests executed: 248
- Total passed: 245
- Failures: 3
  1. `StringUtilsTest.detectEmojisAtEndFails` (expected upstream failure)
  2. `InputLogicTest.insertLetterIntoWordHangulFails` (expected upstream failure)
  3. `XLinkTest.knownDictionaries` (expected test hitting external network)

All failures match the exact list of acceptable failures specified in task guidelines. Zero regressions.
