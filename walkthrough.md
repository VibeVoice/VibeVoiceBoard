# Setup Wizard Rebuild Walkthrough

## Summary
Rebuilt `WelcomeWizard.kt` and unified with `SwitcherWizard.kt` to create a seamless first-run setup wizard with 6 numbered steps (8 total including hero and closing screens).

## Step Breakdown
1. **Hero Screen (0, unnumbered)**: Brand wordmark, slogan, animated background waves, and "Start" button.
2. **Step 1 - Turn it on**: Enable and select VibeVoice keyboard as current IME with dynamic status updates.
3. **Step 2 - Language**:
   - Single combined step answering "Coming from another keyboard?" (Gboard, Samsung, Something else, This is my first).
   - Main language (pre-selected from system locale).
   - Optional second language (defaults to "None").
   - Filterable, searchable `LazyColumn` showing deduplicated languages in their native language with single-bracket regional disambiguation (e.g. `English (US)`, `Deutsch (Schweiz)`).
   - Preserves custom layout variants (e.g., neo2, dvorak).
4. **Step 3 - Practice**:
   - Live sandbox with trial key request and dictation practice.
   - Microphone permission disclosure and launcher.
   - Toolbar microphone pulse animation.
5. **Step 4 - Account Linking**:
   - Live link token generation and minute balance display.
   - Connected celebration with chime.
6. **Step 5 - Floating Mark**:
   - System overlay permission toggle for persistent dictation bar.
   - Soft-guard explanation dialog when choosing "Not now".
7. **Step 6 - Style and Feel**:
   - Shows real keyboard with practice text field auto-focused.
   - Compact controls above keyboard: style selection (`VibeVoice own`, `Gboard`, `Samsung`) and vibration toggle (defaults to off).
   - Pre-selects style based on Step 2 answer (`Gboard` -> Gboard preset, `Samsung` -> Samsung preset, else `VibeVoice own`).
   - Live application via `KeyboardPresets.apply`, `prefChanged()`, and `reloadKeyboard()`.
8. **Closing Screen (7, unnumbered)**: "You're set" closing typography and finish action.

## Review Findings Addressed
- **Finding (a)**: Removed writing of persisted `PREF_SETUP_WIZARD_ACTIVE` preference. The wizard lifecycle now manages only the in-memory flag `VibeVoiceClient.isWizardActive`.
- **Finding (b)**: Centralized trial key request state in `TrialState` (`IDLE`, `LOADING`, `GRANTED`, `FAILED`). The trial card displays a connecting spinner during requests and prevents parallel duplicate requests. The card disappears cleanly when a trial key or account is active.
- **Finding (c)**: `PreviousKeyboard.OTHER` and `PreviousKeyboard.FIRST` map to `null` preset in `toPreset()`. On entry to Step 6, `KeyboardPresets.apply` is only called if a preset is pre-selected (`GBOARD` or `SAMSUNG`), preserving existing custom settings (key heights, gaps, number rows) for users coming from other keyboards or first-time users. Explicitly selecting `VibeVoice own` applies `KeyboardPreset.OWN`.

## Test Results
All `SwitcherHelperTest` unit tests pass cleanly:
- `previousKeyboardToPresetMapping`: Verifies `GBOARD -> GBOARD`, `SAMSUNG -> SAMSUNG`, `OTHER -> null`, `FIRST -> null`.
- `applyingOtherOrFirstPresetPreservesCustomKeyboardPreferences`: Verifies custom gap and number row preferences are preserved when applying `OTHER` or `FIRST`.
- `languageOptionsFormattedWithSingleBracketAndNoDuplicateLanguages`: Verifies clean native language labels, no duplicate locales, and single-bracket region qualifiers.
- Subtype and bilingual switching tests: Verified multilingual typing configuration and custom layout preservation.

Full suite verification:
- 238 unit tests run with only the 3 known/acceptable baseline failures (`StringUtilsTest.detectEmojisAtEndFails`, `InputLogicTest.insertLetterIntoWordHangulFails`, `XLinkTest.knownDictionaries`).
