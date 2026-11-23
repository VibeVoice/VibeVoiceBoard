# Walkthrough: Visible Key Caps ("Draw keys") Look

## Summary of Changes

We implemented the "Draw keys" (visible keycaps) feature for VibeVoiceBoard, providing an optional modern keycaps appearance inspired by Gboard and Samsung Keyboard.

- **Default state:** Off (`false`). When toggled off, the existing floating-keys look is 100% preserved.
- **Preference Key:** `"draw_keys"`
- **Preference Constant:** `helium314.keyboard.latin.settings.Defaults.PREF_DRAW_KEYS` (type `Boolean`, value `false`)
- **Settings Helper:** `helium314.keyboard.latin.settings.Settings.readDrawKeys(prefs)` (reads `draw_keys` with transparent fallback to legacy `theme_key_borders`)
- **UI Location:** Keyboard Settings -> Appearance -> "Draw keys" ("Visible key caps") toggle, which triggers instant keyboard theme reload on change.

---

## Screen Measurement Analysis

Reference screenshots from `/Users/schneider/.claude-business/jobs/553d0a47/tmp/kbref/` were inspected and measured as fractions of total screen width (`%p`) rather than raw pixels to ensure screen-density and resolution independence:

| Reference Screenshot | Screen Width | Key Horizontal Gap (% of Screen Width) | Corner Radius (% of Screen Width / dp) | Two-Tone Functional Keys |
| :--- | :--- | :--- | :--- | :--- |
| `gboard-pixel8-dark-de-en.png` | 1080 px | 14 px = **1.296%p** | ~14 px (~1.3%p / ~5.2dp) | Yes (darker slate functional vs normal key) |
| `gboard-s25ultra-light-numrow-de-en.png` | 1080 px | 14 px = **1.296%p** | ~15 px (~1.4%p / ~5.5dp) | Yes (secondary grayish functional vs white key) |
| `gboard-de-qwertz.jpg` | 1080 px | 14 px = **1.296%p** | ~14 px (~1.3%p / ~5.2dp) | Yes |
| `samsung-de.jpg` | 1080 px | 13–14 px = **1.20%p–1.29%p** | ~14 px (~1.3%p / ~5.2dp) | Yes |
| `samsung-tester-dark-de-umlaut-keys.png` | 1080 px | 14 px = **1.296%p** | ~14 px (~1.3%p / ~5.2dp) | Yes |
| `samsung-s24-light-qwerty-us.png` | 1080 px | 13 px = **1.203%p** | ~13 px (~1.2%p / ~5.0dp) | Yes |
| `vibevoice-en.jpg` | 1080 px | 0 px = **0.00%p** | N/A (floating letters, no caps) | No |

### Dimension Values Implemented
- **Horizontal gap between keys (`config_key_caps_horizontal_gap`):** `1.28%p` (fraction of parent screen width).
- **Vertical gap between rows (`config_key_caps_vertical_gap`):** `4.8%p` (fraction of keyboard height, translating to ~2.6% of screen width).
- **Corner radius (`config_key_caps_corner_radius`):** `5.5dp` (clean rounded rectangle matching both Gboard and Samsung Keyboard).

### Color Palette Tuning
- **Gboard Light Theme:**
  - Keyboard background: `#E8EAED`
  - Normal keys & spacebar: `#FFFFFF`
  - Functional keys (Shift, Delete, ?123, Comma, Period): `#CCCED5`
  - Action / Enter key: `#1A73E8` (Google Blue accent)
  - Text: `#37474F`
- **Gboard Dark Theme:**
  - Keyboard background: `#263238`
  - Normal keys & spacebar: `#364248`
  - Functional keys (Shift, Delete, ?123, Comma, Period): `#45525B` (lighter secondary slate)
  - Action / Enter key: `#5E97F6` (Google Light Blue accent)
  - Text: `#FFFFFF`
- **Modernization of Drawables:**
  - Removed legacy Android 5.0 bevel layer (`#A9ABAD` bottom line) from `btn_keyboard_key_normal_lxx_base_border.xml` and `btn_keyboard_key_normal_rounded_base_border.xml`.
  - Keys are now clean flat rounded rectangles with dynamic pressed-state feedback.
  - Added dedicated `btn_keyboard_key_functional_lxx_base_border.xml` for functional keys.
  - Updated `TextKeyData.kt` so bottom-row comma and period keys receive `BACKGROUND_TYPE_FUNCTIONAL`, matching Gboard and Samsung Keyboard hierarchy.

---

## Preference Contract for Other Agents & Setup Wizard

The preference contract is exposed consistently across Java, Kotlin, and XML:

1. **Defaults constant:**
   ```kotlin
   // helium314.keyboard.latin.settings.Defaults
   const val PREF_DRAW_KEYS = false
   ```
2. **Preference key:**
   ```java
   // helium314.keyboard.latin.settings.Settings
   public static final String PREF_DRAW_KEYS = "draw_keys";
   ```
3. **Programmatic reading:**
   ```java
   // helium314.keyboard.latin.settings.Settings
   public static boolean readDrawKeys(final SharedPreferences prefs) {
       return prefs.getBoolean(PREF_DRAW_KEYS, prefs.getBoolean(PREF_THEME_KEY_BORDERS, Defaults.PREF_DRAW_KEYS));
   }
   ```
4. **Strings:**
   - English (`app/src/main/res/values/strings.xml`):
     - `draw_keys`: "Draw keys"
     - `draw_keys_summary`: "Visible key caps"
   - German (`app/src/main/res/values-de/strings_vibevoice.xml`):
     - `draw_keys`: "Tasten anzeigen"
     - `draw_keys_summary`: "Sichtbare Tastenkappen anzeigen"

---

## Files Modified and Added

| File | Type | Changes |
| :--- | :--- | :--- |
| `app/src/main/java/helium314/keyboard/latin/settings/Defaults.kt` | Modified | Added `PREF_DRAW_KEYS = false`. |
| `app/src/main/java/helium314/keyboard/latin/settings/Settings.java` | Modified | Added `PREF_DRAW_KEYS = "draw_keys"` and `readDrawKeys(prefs)` helper with fallback. |
| `app/src/main/java/helium314/keyboard/latin/settings/SettingsValues.java` | Modified | Added `mDrawKeys` field initialized via `Settings.readDrawKeys(prefs)`. |
| `app/src/main/java/helium314/keyboard/keyboard/KeyboardTheme.kt` | Modified | Integrated `Settings.readDrawKeys(prefs)` in theme and color lookups; tuned dark functional key color. |
| `app/src/main/java/helium314/keyboard/settings/screens/AppearanceScreen.kt` | Modified | Exposed `PREF_DRAW_KEYS` switch in appearance settings with dynamic keyboard reload. |
| `app/src/main/java/helium314/keyboard/settings/screens/ColorsScreen.kt` | Modified | Updated fallback and color pref filters to use `Settings.readDrawKeys(prefs)`. |
| `app/src/main/java/helium314/keyboard/keyboard/internal/keyboard_parser/floris/TextKeyData.kt` | Modified | Set comma and period keys to `BACKGROUND_TYPE_FUNCTIONAL`. |
| `app/src/main/res/values/config.xml` | Modified | Added `config_key_caps_horizontal_gap` (`1.28%p`), `config_key_caps_vertical_gap` (`4.8%p`), and `config_key_caps_corner_radius` (`5.5dp`). |
| `app/src/main/res/values/strings.xml` | Modified | Added English strings `draw_keys` and `draw_keys_summary`. |
| `app/src/main/res/values-de/strings_vibevoice.xml` | Modified | Added German strings `draw_keys` and `draw_keys_summary`. |
| `app/src/main/res/values/themes-lxx-base-border.xml` | Modified | Wired horizontal/vertical gap dimensions and functional key background drawable. |
| `app/src/main/res/values/themes-rounded-base-border.xml` | Modified | Wired horizontal/vertical gap dimensions. |
| `app/src/main/res/drawable/btn_keyboard_key_functional_lxx_base_border.xml` | Added | State selector for functional key caps. |
| `app/src/main/res/drawable/btn_keyboard_key_normal_lxx_base_border.xml` | Modified | Modern flat rounded rectangle drawable (removed bevel). |
| `app/src/main/res/drawable/btn_keyboard_key_pressed_lxx_base_border.xml` | Modified | Modern flat rounded rectangle pressed state drawable. |
| `app/src/main/res/drawable/btn_keyboard_key_normal_rounded_base_border.xml` | Modified | Modern flat rounded rectangle (removed bevel). |
| `app/src/main/res/drawable/btn_keyboard_key_normal_functional_rounded_base_border.xml` | Modified | Modern flat rounded rectangle (removed bevel). |
| `app/src/test/java/helium314/keyboard/DrawKeysThemeTest.kt` | Added | Unit tests for defaults contract, SharedPreferences reading, theme selection, and colors border flag. |

---

## Verification & Test Results

### 1. Dedicated Unit Tests (`DrawKeysThemeTest.kt`)
Ran:
```bash
./gradlew testDebugUnitTest --tests "helium314.keyboard.DrawKeysThemeTest"
```
**Result:** 6/6 tests passed:
- `defaultsContract`: Confirmed `Defaults.PREF_DRAW_KEYS == false` and `Settings.PREF_DRAW_KEYS == "draw_keys"`.
- `settingsReadDrawKeys_defaultIsFalse`: Confirmed default without preferences is `false`.
- `settingsReadDrawKeys_legacyKeyBordersFallback`: Confirmed legacy `theme_key_borders` preference is honored if present.
- `settingsReadDrawKeys_explicitValuePrecedence`: Confirmed explicit `draw_keys` takes precedence over legacy values.
- `keyboardTheme_switchesOnDrawKeys`: Confirmed `THEME_ID_LXX_BASE` (1) when off, and `THEME_ID_LXX_BASE_BORDER` (2) when on.
- `keyboardTheme_roundedStyleSwitchesOnDrawKeys`: Confirmed `THEME_ID_ROUNDED_BASE` (3) when off, and `THEME_ID_ROUNDED_BASE_BORDER` (4) when on.
- `colors_hasKeyBordersReflectsDrawKeys`: Confirmed `Colors.hasKeyBorders` is `false` when off and `true` when on.

### 2. Full Test Suite (`testDebugUnitTest`)
Ran:
```bash
./gradlew testDebugUnitTest
```
**Result:** 212 tests completed with exactly the 3 known upstream failures specified in task instructions:
- `XLinkTest > knownDictionaries FAILED` (known network failure)
- `InputLogicTest > insertLetterIntoWordHangulFails FAILED` (known upstream test)
- `StringUtilsTest > detectEmojisAtEndFails FAILED` (known upstream test)
All 209 other tests passed.

### 3. Assembly Build (`assembleDebug`)
Ran:
```bash
./gradlew -q assembleDebug
```
**Result:** Build succeeded with exit code 0.

### 4. Git Delivery
- Changes committed on branch `feat/keycaps-theme` (commit `3712ebc5687e6e0c5966a103423f211e59203f47`) with conventional commit message `feat(theme): add draw keys switch for visible keycaps look`.
- Version code automatically incremented by pre-commit hook to `600057` (`1.0.4`).
- Branch successfully pushed to remote repository:
  ```bash
  SKIP_APK_BUILD=1 git push -u origin HEAD:feat/keycaps-theme
  ```

---

## Device Verification Checklist for Team Lead

When testing on a physical device:
1. **Default Look (Off):**
   - Open keyboard. Letters must float on the background without visible keycaps or gaps (unchanged original appearance).
2. **Toggle On:**
   - Go to Settings -> Appearance.
   - Verify the switch "Draw keys" (summary "Visible key caps") is present and initially toggled off.
   - Toggle "Draw keys" to ON.
   - Return to a text field. The keyboard should instantly reflect rounded-rectangular key caps with gaps.
3. **Visual Verification (Light & Dark):**
   - In Light mode: Letter keys are white, functional keys (Shift, Backspace, ?123, comma, period) are soft slate gray (`#CCCED5`), Enter key is vibrant blue accent (`#1A73E8`).
   - In Dark mode: Letter keys are dark slate (`#364248`), functional keys are distinct secondary slate (`#45525B`), Enter key is accent blue (`#5E97F6`).
   - Pressing any key should display an active pressed state within the rounded cap boundaries.
4. **German Localization:**
   - Switch system language to German (`de`).
   - Verify setting is labeled "Tasten anzeigen" with summary "Sichtbare Tastenkappen anzeigen".
5. **Programmatic / Setup Wizard Interaction:**
   - Verify that setting `draw_keys` = `true` in SharedPreferences programmatically correctly activates the visible keycaps look.
