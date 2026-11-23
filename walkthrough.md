# Keyboard Presets: "Like Gboard" and "Like Samsung Keyboard" Walkthrough

## 1. Overview & Objective

This implementation introduces keyboard presets:
- **`GBOARD` ("Like Gboard")**: Configures key proportions, bottom-row layout, gaps, side padding, hint behavior, and space bar labeling to match Google Gboard.
- **`SAMSUNG` ("Like Samsung Keyboard")**: Configures key proportions, bottom-row layout with `!#1` symbol toggle, number row, gaps, side padding, and space bar swipe indicator arrows to match Samsung Keyboard.
- **`OWN` ("Own")**: Preserves and restores today's default HeliBoard / VibeVoiceBoard behavior. This is the default for all users unless explicitly changed.

Branch: `feat/keyboard-presets` (base `origin/feature/vibevoice-integration`).

---

## 2. Reference Measurements & Geometry Table

Measurements were taken from real phone screenshots in `/Users/schneider/.claude-business/jobs/553d0a47/tmp/kbref/` normalized as fractions of screen width ($W$).
- **Pixel 8 reference**: Width = 411 dp, scaling = 2.625x (1080x2400 px, logical width 411 dp).
- **Samsung Galaxy S24 / S25 Ultra reference**: Width = 412 dp at default display scaling (1440x3120 px / 1080x2340 px, logical width 412 dp).

### 2.1 Measurement Table

| Metric / Key Dimension | HeliBoard (Base / `OWN`) | Gboard (`GBOARD`) | Samsung Keyboard (`SAMSUNG`) |
| :--- | :--- | :--- | :--- |
| **Reference Screenshot** | `vibevoice-en.jpg` | `gboard-pixel8-dark-de-en.png`, `gboard-s25ultra-light-numrow-de-en.png` | `samsung-de.jpg`, `samsung-tester-dark-de-umlaut-keys.png`, `samsung-s24-light-qwerty-us.png` |
| **Side Padding (Left / Right)** | 0.000 $W$ (0.0 dp) | 0.0111 $W$ (4.56 dp) | 0.0212 $W$ (8.73 dp) |
| **Horizontal Key Gap** | 0.0109 $W$ (4.48 dp) | 0.0118 $W$ (4.85 dp) | 0.0148 $W$ (6.10 dp) |
| **Vertical Row Gap** | ~0.020 $W$ (8.2 dp) | ~0.020 $W$ (8.2 dp) | ~0.021 $W$ (8.6 dp) |
| **Letter Key Width (Row 1)** | 0.0891 $W$ (36.6 dp) | 0.0868 $W$ (35.7 dp) | 0.0825 $W$ (34.0 dp) |
| **Row 2 Horizontal Offset** | 0.0445 $W$ (18.3 dp, 0.5 key) | 0.0434 $W$ (17.8 dp, 0.5 key) | 0.0412 $W$ (17.0 dp, 0.5 key) |
| **Shift Key Width (Row 3)** | 0.1336 $W$ (54.9 dp) | 0.1360 $W$ (55.9 dp) | 0.1160 $W$ (47.8 dp) |
| **Delete / Backspace Key Width** | 0.1336 $W$ (54.9 dp) | 0.1360 $W$ (55.9 dp) | 0.1350 $W$ (55.6 dp) |
| **Symbol Key Width (Bottom Row)** | 0.1500 $W$ (61.7 dp, `?123`) | 0.1500 $W$ (61.7 dp, `?123`) | 0.1350 $W$ (55.6 dp, `!#1`) |
| **Comma Key Width** | 0.1000 $W$ (41.1 dp) | 0.1000 $W$ (41.1 dp) | 0.0850 $W$ (35.0 dp) |
| **Emoji Key Width** | 0.1000 $W$ (41.1 dp, on row) | 0.1000 $W$ (41.1 dp, next to space) | Not present on bottom row (dedicated toolbar/hold) |
| **Period Key Width** | 0.1000 $W$ (41.1 dp) | 0.1000 $W$ (41.1 dp) | 0.0850 $W$ (35.0 dp) |
| **Enter / Action Key Width** | 0.1500 $W$ (61.7 dp) | 0.1500 $W$ (61.7 dp) | 0.1350 $W$ (55.6 dp) |
| **Space Bar Width** | ~0.380 $W$ (156 dp) | 0.3850 $W$ (158.2 dp) | 0.4720 $W$ (194.5 dp) |
| **Number Row** | Disabled (`false`) | Disabled (`false`) | Enabled (`true`) |
| **Hints on Letter Keys** | Enabled (`true`) | Enabled (`true`, top row digit hints) | Disabled (`false`) |
| **Total Height (Fraction of Width)** | 0.612 $W$ (251 dp) | 0.642 $W$ (264 dp) | 0.645 $W$ (265 dp without num row, ~0.76 $W$ with num row) |
| **Space Bar Idle Label** | `Long-press to dictate` | Language (`English` / `Deutsch`) or `DE • EN` | `< Language >` (with swipe indicators) |

---

## 3. What Each Preset Sets

Implementation entry point: `KeyboardPresets.apply(context: Context, preset: KeyboardPreset)`.

### 3.1 Preset: `GBOARD`
- `PREF_KEYBOARD_PRESET`: `"GBOARD"`
- `"draw_keys"`: `true` (enables rounded key-cap backgrounds when drawn)
- `Settings.PREF_SHOW_NUMBER_ROW`: `false`
- `Settings.PREF_SHOW_HINTS`: `true` (renders top row digits / punctuation hints)
- `Settings.PREF_SHOW_EMOJI_KEY`: `true`
- `Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY`: `false`
- `keyboard_height_scale_false_false`: `1.05f`
- `key_gap_scale_false_false`: `1.10f`
- `side_padding_scale_false_false_false`: `0.14f`
- `bottom_padding_scale_false_false`: `1.00f`
- `bottom_row_scale_false_false`: `1.00f`
- `layout_FUNCTIONAL`: `"functional_keys_gboard"`
- Space bar label: Displays active language or multilingual capitalized codes `DE • EN`.

### 3.2 Preset: `SAMSUNG`
- `PREF_KEYBOARD_PRESET`: `"SAMSUNG"`
- `"draw_keys"`: `true`
- `Settings.PREF_SHOW_NUMBER_ROW`: `true` (dedicated digit row at top)
- `Settings.PREF_SHOW_HINTS`: `false` (clean letter tops)
- `Settings.PREF_SHOW_EMOJI_KEY`: `false` (no bottom-row emoji key)
- `Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY`: `false`
- `keyboard_height_scale_false_false`: `1.05f`
- `key_gap_scale_false_false`: `1.35f` (wider horizontal separation)
- `side_padding_scale_false_false_false`: `0.26f` (distinct inset margins)
- `bottom_padding_scale_false_false`: `1.00f`
- `bottom_row_scale_false_false`: `1.00f`
- `layout_FUNCTIONAL`: `"functional_keys_samsung"`
- Space bar label: Displays language name flanked by swipe indicators `< Deutsch >`.

### 3.3 Preset: `OWN` (Default Behavior)
- `PREF_KEYBOARD_PRESET`: `"OWN"`
- `"draw_keys"`: `false`
- `Settings.PREF_SHOW_NUMBER_ROW`: `false` (restores default)
- `Settings.PREF_SHOW_HINTS`: `true` (restores default)
- `Settings.PREF_SHOW_EMOJI_KEY`: `true` (restores default)
- `Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY`: `false` (restores default)
- `keyboard_height_scale_false_false`: `1.00f`
- `key_gap_scale_false_false`: `1.00f`
- `side_padding_scale_false_false_false`: `0.00f`
- `bottom_padding_scale_false_false`: `1.00f`
- `bottom_row_scale_false_false`: `1.00f`
- `layout_FUNCTIONAL`: Removed from SharedPreferences, reverting to default `"functional_keys"`
- Space bar label: Preserves VibeVoice hint `vibevoice_hold_to_transcribe` ("Long-press to dictate").

---

## 4. Additions Beyond Existing Preferences

Where existing preferences alone could not achieve the exact physical layout:
1. **Dedicated Functional Layouts**:
   - `app/src/main/assets/layouts/functional/functional_keys_gboard.json`:
     Custom functional row with ratios matching Gboard bottom row (`?123` at 0.15, `,` at 0.10, emoji at 0.10, space at `-1`, `.` at 0.10, enter at 0.15).
   - `app/src/main/assets/layouts/functional/functional_keys_samsung.json`:
     Implements Samsung's specific bottom row with `!#1` key label and symbol keycode `-10001` (width 0.135), `,` at 0.085, wide space at `-1` (~47% row width), `.` at 0.085, and enter at 0.135.
2. **Preset-Aware Space Bar Rendering (`MainKeyboardView.java`)**:
   - Added `layoutGboardSpacebar` to render `DE • EN` when multiple languages or secondary locales are active, or the language name for single language.
   - Added `layoutSamsungSpacebar` to render `< Language >` with swipe indicator arrows.
   - Preserves active dictation timer display during voice transcription regardless of active preset.
3. **Appearance Settings UI Preference (`AppearanceScreen.kt`)**:
   - Added `Settings.PREF_KEYBOARD_PRESET` ListPreference under Appearance settings ("Keyboard like...").
   - Choices: `Own` (`OWN`), `Like Gboard` (`GBOARD`), `Like Samsung Keyboard` (`SAMSUNG`).
   - English strings added to `app/src/main/res/values/strings.xml`.
   - German translations added to `app/src/main/res/values-de/strings_vibevoice.xml`.
4. **Drawing Keys Hook**:
   - Presets write `"draw_keys"` (`Defaults.PREF_DRAW_KEYS` / `Settings.PREF_DRAW_KEYS`) to `true` for `GBOARD` and `SAMSUNG` and `false` for `OWN` for seamless integration with the keycap drawing agent.

---

## 5. Test Results

### 5.1 Unit Tests
- New test suite: `app/src/test/java/helium314/keyboard/latin/settings/KeyboardPresetsTest.kt`:
  - `testApplyGboardPreset`: PASS
  - `testApplySamsungPreset`: PASS
  - `testApplyOwnRestoresDefaults`: PASS
- Full test suite `./gradlew testDebugUnitTest`:
  - Result: 208 tests executed, exactly the 3 known upstream failures:
    1. `StringUtilsTest.detectEmojisAtEndFails` (expected known failure)
    2. `InputLogicTest.insertLetterIntoWordHangulFails` (expected known failure)
    3. `XLinkTest.knownDictionaries` (expected known failure due to network dependency)
  - Zero regressions.

### 5.2 Build Verification
- `./gradlew -q assembleDebug`:
  - Result: `BUILD SUCCESSFUL` (exit code 0).
  - APK generated successfully.

---

## 6. What Could Not Be Verified Automatically

1. **Physical On-Device Touch / Rendering**:
   Unit tests run via Robolectric; physical display rendering, touch target feel, and real device display metrics must be tested on live hardware by the lead.
2. **Visual Keycap Drawing**:
   The `draw_keys` preference is written to `SharedPreferences` by `KeyboardPresets.apply()`, but keycap drawing is owned by another agent and cannot be visually verified until merged with that feature branch.

---

## 7. Device Verification Guide for the Lead

When running on device (e.g. Pixel 8 or Galaxy S24/S25 Ultra):

### Step 1: Default Verification (`OWN`)
- Open keyboard settings -> Appearance.
- Confirm "Keyboard like…" defaults to **"Own"**.
- Confirm bottom row contains `?123`, `,`, emoji, space (showing "Long-press to dictate"), `.`, and enter.
- Confirm number row is off and keys occupy full keyboard width with 0 side padding.

### Step 2: "Like Gboard" Preset
- Select **"Like Gboard"**.
- Check bottom row layout:
  - `?123` (~15% width), `,` (~10%), emoji (~10%), space (~38.5%), `.` (~10%), enter (~15%).
- Check key padding and gaps:
  - Visible left/right side padding (~1.1% width, ~4.5 dp on Pixel 8).
  - Number row remains off; top row letter keys display small digit hints on their top corners.
- Check space bar label:
  - With German only: displays `Deutsch`.
  - With German + English enabled: displays `DE • EN`.

### Step 3: "Like Samsung Keyboard" Preset
- Select **"Like Samsung Keyboard"**.
- Check number row:
  - Top row of numbers (`1 2 3 4 5 6 7 8 9 0`) is visible above the letter keys.
- Check letter hints:
  - Letter keys are clean with no digit hints.
- Check bottom row layout:
  - Symbol toggle is labeled `!#1` (~13.5% width).
  - Emoji key is removed from bottom row.
  - Space bar is wider (~47% width) and displays `< Deutsch >` (or `< English >`).
  - Side margins are visibly wider (~2.1% width, ~8.7 dp on S24 Ultra).
  - Horizontal key gaps are wider (~1.35x base).

### Step 4: Revert to "Own"
- Select **"Own"**.
- Confirm keyboard immediately returns to standard HeliBoard geometry and "Long-press to dictate" space bar text.
