# Keyboard Presets: Measurements and Mapping

This document details the real-phone screenshot measurements from the reference devices, the geometric assumptions, and the mapping of these dimensions onto HeliBoard / VibeVoiceBoard preferences.

---

## 1. Device and Scaling Assumptions

All reference images are full-screen device captures. Because screenshot pixel resolutions vary across models (720px, 921px, 945px, 1182px), all physical metrics are calculated and reported as a **fraction of screen width ($W$)**. 

To convert these fractions into density-independent pixels ($\text{dp}$):
- **Google Pixel 8**: Standard screen width is **$411\text{ dp}$**. On a $720\text{ px}$ screenshot, $1\text{ px} \approx 0.5708\text{ dp}$ ($1\text{ dp} \approx 1.7518\text{ px}$).
- **Samsung Galaxy S24 Ultra / S25 Ultra**: Standard screen width at default display scaling is **$412\text{ dp}$** (ranges between $384\text{ dp}$ and $412\text{ dp}$ depending on user zoom; $412\text{ dp}$ assumed canonically). On an $1182\text{ px}$ screenshot, $1\text{ px} \approx 0.3486\text{ dp}$ ($1\text{ dp} \approx 2.8689\text{ px}$). On a $945\text{ px}$ screenshot, $1\text{ px} \approx 0.4360\text{ dp}$.

---

## 2. Measurement Data Table

All measurements below were derived from raw pixel analysis of the reference screenshots:
- `gboard-pixel8-dark-de-en.png` ($720 \times 1600$, Pixel 8, default Gboard without number row)
- `gboard-s25ultra-light-numrow-de-en.png` ($720 \times 1560$, S25 Ultra, Gboard with number row enabled)
- `gboard-de-qwertz.jpg` ($1182 \times 2560$, S24 Ultra, Gboard with number row enabled)
- `samsung-de.jpg` ($1182 \times 2560$, S24 Ultra, Samsung Keyboard default with number row enabled)
- `samsung-s24-light-qwerty-us.png` ($945 \times 2048$, S24, Samsung Keyboard light theme)
- `vibevoice-en.jpg` ($1182 \times 2560$, S24, HeliBoard / VibeVoiceBoard default today)

### 2.1 Geometry & Key Widths (Fraction of Screen Width $W$)

| Metric / Key | Gboard (No Num Row) | Gboard (Num Row On) | Samsung Keyboard (Default) | VibeVoice / HeliBoard (Own) |
| :--- | :--- | :--- | :--- | :--- |
| **Side Padding (Left / Right)** | $0.0111\,W$ ($4.6\text{ dp}$) | $0.0111\,W$ ($4.6\text{ dp}$) | $0.0212\,W$ ($8.7\text{ dp}$) | $0.0000\,W$ ($0.0\text{ dp}$) |
| **Horizontal Key Gap** | $0.0118\,W$ ($4.8\text{ dp}$) | $0.0118\,W$ ($4.8\text{ dp}$) | $0.0148\,W$ ($6.1\text{ dp}$) | $0.0109\,W$ ($4.5\text{ dp}$) |
| **Vertical Key Gap** | $0.0278\,W$ ($11.4\text{ dp}$) | $0.0208\,W$ ($8.6\text{ dp}$) | $0.0338\,W$ ($13.9\text{ dp}$) | $0.0109\,W$ ($4.5\text{ dp}$) |
| **Letter Key Width (Row 1 & 2)** | $0.0868\,W$ ($35.7\text{ dp}$) | $0.0868\,W$ ($35.7\text{ dp}$) | $0.0825\,W$ ($34.0\text{ dp}$) | $0.1000\,W$ ($41.2\text{ dp}$) |
| **Number Key Width (Row 0)** | N/A (Hints on Row 1) | $0.0868\,W$ ($35.7\text{ dp}$) | $0.0825\,W$ ($34.0\text{ dp}$) | $0.1000\,W$ ($41.2\text{ dp}$) |
| **Row 2 Horizontal Offset** | $0.0500\,W$ ($20.6\text{ dp}$) | $0.0500\,W$ ($20.6\text{ dp}$) | $0.0491\,W$ ($20.2\text{ dp}$) | $0.0500\,W$ ($20.6\text{ dp}$) |
| **Shift Key Width** | $0.1361\,W$ ($55.9\text{ dp}$) | $0.1361\,W$ ($55.9\text{ dp}$) | $0.1159\,W$ ($47.8\text{ dp}$) | $0.1500\,W$ ($61.8\text{ dp}$) |
| **Delete Key Width** | $0.1361\,W$ ($55.9\text{ dp}$) | $0.1361\,W$ ($55.9\text{ dp}$) | $0.1159\,W$ ($47.8\text{ dp}$) | $0.1500\,W$ ($61.8\text{ dp}$) |
| **Symbol Key Width (?123 / !#1)** | $0.1361\,W$ ($55.9\text{ dp}$) | $0.1361\,W$ ($55.9\text{ dp}$) | $0.1184-0.1323\,W$ ($48.8-54.5\text{ dp}$) | $0.1500\,W$ ($61.8\text{ dp}$) |
| **Comma Key Width** | $0.0875\,W$ ($36.0\text{ dp}$) | $0.0875\,W$ ($36.0\text{ dp}$) | $0.0821\,W$ ($33.8\text{ dp}$) | $0.1000\,W$ ($41.2\text{ dp}$) |
| **Emoji Key (Bottom Row)** | $0.0861\,W$ ($35.4\text{ dp}$) | $0.0861\,W$ ($35.4\text{ dp}$) | *None by default* | $0.1000\,W$ ($41.2\text{ dp}$) |
| **Space Bar Width** | $0.3847\,W$ ($158.1\text{ dp}$) | $0.3847\,W$ ($158.1\text{ dp}$) | $0.4720\,W$ ($194.5\text{ dp}$) | $0.4000\,W$ ($164.8\text{ dp}$) |
| **Period Key Width** | $0.0875\,W$ ($36.0\text{ dp}$) | $0.0875\,W$ ($36.0\text{ dp}$) | $0.0821\,W$ ($33.8\text{ dp}$) | $0.1000\,W$ ($41.2\text{ dp}$) |
| **Action / Enter Key Width** | $0.1375\,W$ ($56.5\text{ dp}$) | $0.1375\,W$ ($56.5\text{ dp}$) | $0.1311\,W$ ($54.0\text{ dp}$) | $0.1500\,W$ ($61.8\text{ dp}$) |

### 2.2 Row Heights & Keyboard Heights (Fraction of Screen Width $W$)

| Metric / Row | Gboard (No Num Row) | Gboard (Num Row On) | Samsung Keyboard (Default) | VibeVoice / HeliBoard (Own) |
| :--- | :--- | :--- | :--- | :--- |
| **Row 0 (Numbers)** | N/A | $0.0944-0.1058\,W$ | $0.0973\,W$ ($40.1\text{ dp}$) | $0.1100\,W$ ($45.3\text{ dp}$) |
| **Row 1 (Letters)** | $0.1153\,W$ ($47.4\text{ dp}$) | $0.1000-0.1058\,W$ | $0.1176\,W$ ($48.5\text{ dp}$) | $0.1250\,W$ ($51.5\text{ dp}$) |
| **Row 2 (Letters)** | $0.1153\,W$ ($47.4\text{ dp}$) | $0.0972-0.1066\,W$ | $0.1176\,W$ ($48.5\text{ dp}$) | $0.1250\,W$ ($51.5\text{ dp}$) |
| **Row 3 (Letters)** | $0.1167\,W$ ($48.0\text{ dp}$) | $0.0986-0.1083\,W$ | $0.1176\,W$ ($48.5\text{ dp}$) | $0.1250\,W$ ($51.5\text{ dp}$) |
| **Row 4 (Bottom Row)** | $0.1167\,W$ ($48.0\text{ dp}$) | $0.1083\,W$ ($44.5\text{ dp}$) | $0.1184\,W$ ($48.8\text{ dp}$) | $0.1250\,W$ ($51.5\text{ dp}$) |
| **Total Key Area Height** | $0.5472\,W$ ($224.9\text{ dp}$) | $0.6277\,W$ ($258.0\text{ dp}$) | $0.7039\,W$ ($290.0\text{ dp}$) | $0.5000-0.6650\,W$ |
| **Bottom Navigation Bar Chin**| $0.1639\,W$ ($67.4\text{ dp}$) | $0.1500\,W$ ($61.7\text{ dp}$) | $0.1574\,W$ ($64.8\text{ dp}$) | Inset-dependent |
| **Main Letter Font Size** | $0.0400\,W$ ($16.5\text{ dp}$) | $0.0400\,W$ ($16.5\text{ dp}$) | $0.0400\,W$ ($16.5\text{ dp}$) | $1.0\times$ default |
| **Hint Font Size** | $0.0190\,W$ ($7.8\text{ dp}$, $\approx 45\%$) | $0.0190\,W$ | None (Hints disabled) | $1.0\times$ default |

---

## 3. Mapping to HeliBoard Preferences

### 3.1 Preference Mappings

| Setting / Preference | HeliBoard Key / Constant | Preset: `OWN` | Preset: `GBOARD` | Preset: `SAMSUNG` |
| :--- | :--- | :--- | :--- | :--- |
| **Active Preset** | `PREF_KEYBOARD_PRESET` ("keyboard_preset") | `"OWN"` | `"GBOARD"` | `"SAMSUNG"` |
| **Draw Key Caps** | `"draw_keys"` | `false` | `true` | `true` |
| **Number Row** | `Settings.PREF_SHOW_NUMBER_ROW` | `false` | `false` | `true` |
| **Hints on Keys** | `Settings.PREF_SHOW_HINTS` | `true` | `true` | `false` |
| **Emoji Key on Bottom Row** | `Settings.PREF_SHOW_EMOJI_KEY` | `true` | `true` | `false` |
| **Language Switch Key** | `Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY` | `false` | `false` | `false` |
| **Height Scale (Portrait)** | `keyboard_height_scale_false_false` | `1.00f` | `1.05f` | `1.05f` |
| **Key Gap Scale (Portrait)** | `key_gap_scale_false_false` | `1.00f` | `1.10f` | `1.35f` |
| **Side Padding Scale** | `side_padding_scale_false_false_false` | `0.00f` | `0.14f` | `0.26f` |
| **Bottom Padding Scale** | `bottom_padding_scale_false_false` | `1.00f` | `1.00f` | `1.00f` |
| **Bottom Row Scale** | `bottom_row_scale_false_false` | `1.00f` | `1.00f` | `1.00f` |
| **Functional Layout** | `layout_FUNCTIONAL` | Default (`"functional_keys"`) | `"functional_keys_gboard"` | `"functional_keys_samsung"` |
| **Space Bar Idle Label** | Rendered in `MainKeyboardView` | `"Long-press to dictate"` | Language / `"DE • EN"` | `< Language >` |

### 3.2 Additions Beyond Existing Preferences

Where existing preferences alone could not capture the layout differences:
1. **Dedicated Functional Layouts (`functional_keys_gboard.json`, `functional_keys_samsung.json`)**:
   - `functional_keys_gboard.json`: Implements Gboard bottom row ratios (`?123` at 0.15, `,` at 0.10, emoji at 0.10, space at `-1`, `.` at 0.10, enter at 0.15).
   - `functional_keys_samsung.json`: Implements Samsung bottom row (`!#1` symbol toggle with label `!#1` and keycode `-10001` at width 0.135; comma at 0.085; space at `-1` expanding across ~50% of the row; period at 0.085; enter at 0.135).
2. **Space Bar Labeling Modes**:
   - `GBOARD`: Renders active language name when single-language (e.g. `English`, `Deutsch`) or capitalized language codes with bullet separator `DE • EN` when multilingual.
   - `SAMSUNG`: Renders swipe indicator arrows `< Deutsch >` (or `< English (US) >`) indicating horizontal space swipe for language switching.
   - `OWN`: Preserves existing VibeVoice idle label (`R.string.vibevoice_hold_to_transcribe` / "Long-press to dictate").
   - During voice recording, all three presets consistently show the active transcribing timer.
