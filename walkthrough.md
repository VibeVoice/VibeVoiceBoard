# Walkthrough - Setup Wizard Trial Resilience and Account Linking

## Overview

This implementation delivers client-side enhancements for VibeVoiceBoard onboarding and account linking based on audit sections 3A, 3B, and 3E from `tmp/registration-audit.md`:
1. **Trial Resilience (Section 3A)**: Automatic retry with exponential backoff on wizard start, on-demand retry when tapping the microphone in wizard step 2, suppression of settings redirection from `LatinIME.handleVoiceInput()` while the wizard is active, and an inline retry status card in the wizard step.
2. **Step 3 Action Hierarchy (Section 3B)**: Promoted "Next" to the primary prominent action row and positioned "Link an account" as the secondary action row.
3. **Account Linking Custom Tabs & Foreground Return (Section 3E)**: Chrome Custom Tab intent extras applied to verification URI launches, and automatic return to the keyboard settings/wizard app (`FLAG_ACTIVITY_REORDER_TO_FRONT`) when device polling succeeds.

---

## Detailed Changes

### 1. Trial Key Resilience & Wizard State (`VibeVoiceClient.kt`, `LatinIME.java`, `Settings.java`, `Defaults.kt`)
- **`VibeVoiceClient.requestTrialKeyWithRetry`**:
  - Implemented 3 retry attempts with exponential backoff (`initialDelayMs = 1000L`, doubling on failure).
  - Short-circuits immediately if a key is already present or if the server returns `TrialResult.AlreadyUsed`.
- **Wizard Active State**:
  - Maintained via `@Volatile @JvmStatic var isWizardActive` in memory and backed by `Settings.PREF_SETUP_WIZARD_ACTIVE` in SharedPreferences.
  - Excluded from `Settings.reloadOnChanged` to prevent unnecessary keyboard resets.
  - Automatically enabled when `WelcomeWizard` is composed and cleared on disposal.
- **Microphone Tap Retry Trigger**:
  - `VibeVoiceClient.trialRetryFlow` (a `MutableSharedFlow`) and `triggerTrialRetry(context)`.
  - Step 2 in `WelcomeWizard` collects `trialRetryFlow` to initiate an immediate retry if no API key is yet present.
- **`LatinIME.handleVoiceInput()`**:
  - When `apiKey == null`: checks `VibeVoiceClient.isSetupWizardActive(this)`.
  - If active: calls `VibeVoiceClient.triggerTrialRetry(this)` and returns immediately without opening settings or showing the unlinked toast.
  - If inactive: retains the original behavior (displaying `vibevoice_not_linked` toast and calling `launchSettings("vibevoice")`).

### 2. Setup Wizard UI Updates (`WelcomeWizard.kt`, `strings.xml`, `strings_vibevoice.xml`)
- **Step 2 Inline Trial State**:
  - Added an inline card below the test input field when no API key is present and the device is not linked.
  - When retrying: displays a `CircularProgressIndicator` with `setup_try_trial_connecting` ("Setting up your free trial minutes…").
  - When connection fails: displays an `ic_redo` icon with `setup_try_trial_retry` ("Couldn’t connect to VibeVoice. Tap to retry.") and triggers `retryTrialKey()` on click.
  - Hides automatically once the trial key is granted.
- **Step 3 ("Your minutes") Hierarchy**:
  - "Next" (`setup_next_action`) is now the prominent primary row (`primary = true`, `active = true`).
  - "Link an account" is now secondary (`primary = false`, `brand = true`).
  - Removed the superfluous "Later" button.
- **Translations**:
  - Added English strings to `app/src/main/res/values/strings.xml`.
  - Added complete German translations to `app/src/main/res/values-de/strings_vibevoice.xml`.

### 3. Account Linking & Foreground Restoration (`VibeVoiceLinkPanel.kt`)
- **`openLinkInBrowser`**:
  - Configures `Intent.ACTION_VIEW` with `FLAG_ACTIVITY_NEW_TASK` and Custom Tabs extras:
    - `android.support.customtabs.extra.SESSION`
    - `android.support.customtabs.extra.TOOLBAR_COLOR` (dark brand tone `#171717`)
    - `androidx.browser.customtabs.extra.COLOR_SCHEME` (`2` = DARK)
  - Opens in Chrome Custom Tabs without requiring external network dependencies.
- **`bringAppToForeground`**:
  - Called upon successful OAuth device polling (`tokenRes.has("api_key")`).
  - Launches `SettingsActivity` with `FLAG_ACTIVITY_REORDER_TO_FRONT or FLAG_ACTIVITY_NEW_TASK` to pull the setup wizard back into the foreground from the browser.

---

## Verification & Test Results

### 1. New Unit Tests (`TrialResilienceAndLinkingTest.kt`)
Added 10 Robolectric unit tests covering all components:
1. `trialKeyRequestSucceedsImmediatelyWhenServerReturns200`: Verifies single request on immediate success.
2. `trialKeyRequestRetriesWithBackoffAndSucceedsOnSecondAttempt`: Simulates HTTP 500 followed by HTTP 200 and validates backoff retry.
3. `trialKeyRequestExhaustsRetriesAndReturnsFailed`: Simulates HTTP 500 on all attempts and validates failure return.
4. `trialKeyRequestDoesNotRetryOnAlreadyUsed`: Simulates HTTP 409 and confirms no redundant retries.
5. `wizardActiveTrackingWorksViaFlagAndPreference`: Verifies dual-layer tracking (memory flag and preference).
6. `triggerTrialRetryUpdatesPreferenceAndFlow`: Validates trigger emission and preference increment.
7. `latinIMEHandleVoiceInputDoesNotLaunchSettingsWhenWizardActive`: Asserts `launchSettings` is suppressed and trial retry is triggered when `apiKey == null` during wizard.
8. `latinIMEHandleVoiceInputLaunchesSettingsWhenWizardInactive`: Asserts normal toast and settings launch occur outside the wizard.
9. `openLinkInBrowserAddsCustomTabExtras`: Verifies Custom Tab intent bundle extras and toolbar colors.
10. `bringAppToForegroundLaunchesSettingsActivityWithReorderFlag`: Verifies `FLAG_ACTIVITY_REORDER_TO_FRONT` on foreground intent.

**Result**: All 10 tests passed:
```
BUILD SUCCESSFUL in 10s
32 actionable tasks: 1 executed, 31 up-to-date
```

### 2. Lint Verification
Ran `./gradlew lint`:
```
BUILD SUCCESSFUL in 1m 4s
31 actionable tasks: 9 executed, 22 up-to-date
```
Zero lint warnings or errors.

---

## Git Deliverable
- Committed to `feat/wizard-trial-resilience` with Conventional Commits (commit hash: `289fd626d`, `VERSION_CODE` bumped to 600059).
- Pushed to `origin/feat/wizard-trial-resilience` via `SKIP_APK_BUILD=1 git push origin HEAD:feat/wizard-trial-resilience`.
