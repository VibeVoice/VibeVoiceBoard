# VibeVoice Board — HTML replica

A pixel-faithful HTML/CSS/JS copy of the Android keyboard, for the `/keyboard` page on vibevoice.net
and for marketing stills and videos. Every number comes from the app's source (listed below), not from
screenshots; screenshots were only used afterwards to check the result.

No dependencies, no network, no fonts fetched, no `localStorage`/cookies. It works from `file://`.

```
web-replica/
  keyboard.js    the library (icon geometry inlined, so nothing is loaded at runtime)
  keyboard.css   styles; all lengths scale with the container width
  index.html     demo page: a button for every state, theme toggle, width slider
  assets/        the SVG icons, converted from the Android vector drawables (source of the inlined copy)
  compare/       side-by-sides against real phone screenshots + stills of every state
  tools/         regenerate icons / renders / comparisons (Python 3 + Pillow, Chrome)
```

## Use

```html
<link rel="stylesheet" href="keyboard.css">
<div id="kb" style="width: 390px"></div>
<script src="keyboard.js"></script>
<script>
  const kb = VibeVoiceKeyboard.mount(document.getElementById("kb"), { theme: "dark" });
  kb.play({ text: "I just found the bakery you told me about." });
</script>
```

The keyboard fills the width of its container (390 px phone upward); everything scales from the 384 dp
reference width. `window.VibeVoiceKeyboard` is the global; `module.exports` is set too when a CommonJS
loader is present.

### `VibeVoiceKeyboard.mount(el, options)` returns a keyboard

| option | default | |
|---|---|---|
| `theme` | `"dark"` | `"dark"` or `"light"` |
| `accent` | code default | `"screenshots"` (the `#87D1EA` of the owner's phone, see below), or any `#RRGGBB` |
| `field` | `true` | draw the fake text field above the keyboard that dictation lands in |
| `suggestions` | `["And","I","Then"]` | words in the strip; `[]` for the empty strip |
| `actionIcon` | `"return"` | `"return"`, `"go"`, `"search"`, `"send"`, `"done"` (the enter key) |
| `animate` | `true` | `false` = no timers or animation loop, for stills |
| `placeholder` | `"Message"` | hint text of the field |
| `respectReducedMotion` | `true` | with `prefers-reduced-motion` the waves are one static frame, like the app with system animations off |

### methods

| | |
|---|---|
| `play({text, speakMs=3600, via="mic"\|"space", lead=300})` | the real flow: press the mic (or long-press space), listening with waves, stop, **the whole text block lands in the field 0.4 s later**, back to idle. Returns a Promise. |
| `setState(name, {seconds, frames, level})` | still frames, see states. `seconds` = number on the space bar, `frames` = how many 30 fps wave frames to fast-forward (default `seconds*30`), `level` = fixed mic level 0..1 instead of the demo generator. |
| `getState()` | current state name |
| `setTheme("light"\|"dark", accent?)`, `setAccent(c)` | |
| `setSuggestions([...])` | |
| `setText(s)`, `getText()`, `commit(s)` | the field; `commit` appends one block like a dictation result |
| `showToast(idOrText, ms)`, `hideToast()` | the app's toasts, `VibeVoiceKeyboard.TOASTS` lists the ids |
| `toggleVoice()`, `stop()` | what the mic key does / abort everything |
| `onvoice` | callback `"start" \| "stop" \| "commit"` |
| `destroy()` | removes everything, listeners and timers included |

By hand: tap the mic key in the strip, or hold the space bar for 0.3 s to start; tap either to stop.
Letters, space, delete, shift and enter also type into the field.

### states

| state | what the app shows | source |
|---|---|---|
| `idle` | plain VibeVoice glyph in the strip, space bar says "Hold to dictate" | `updateVoiceKeyButton`, `strings.xml` |
| `listening` | the two-tone logo with accent glow in the strip, waves behind the keys, space bar counts "Transcribing… 0:12" | `updateVoiceKeyButton`, `VoiceGlow`, `VoiceWaveView`, `MainKeyboardView.drawLanguageOnSpacebar` |
| `stopping` | identical to `listening` (the app keeps recording state until the final text arrives); the commit follows 0.4 s later | `LatinIME.mIsStoppingVoice` |
| `degraded` | listening, but the space bar says "Waiting for connection…" instead of the count | `LatinIME.isVoiceLinkDegraded` |
| `pulse` | idle mic key blinking between glyph and logo every 500 ms, gives up after 15 s | `SuggestionStripView.startVoicePulse` |
| `connecting` | **alias of `listening` at 0:00** — the app has no separate connecting visual | `LatinIME.handleVoiceInput` |
| `toast:<id>` / `showToast(id)` | `noNetwork`, `notLinked`, `trialOver`, `micBlocked`, `privateField`, `connectionLost`, `micBusy`, `micUnavailable`, `micSilent`, `nothingRecognized`, `commitLost`, `copied`, `tailMissing`, `error`, `backgroundHint` | `LatinIME` toasts, `strings.xml` |

## Where each value comes from

All paths are under `app/src/main/`.

**Theme** — `java/helium314/keyboard/latin/settings/Defaults.kt`: Material style, no key borders, colour
theme `light` by day and `dark` by night, following the system. `java/…/keyboard/KeyboardTheme.kt`
`getThemeColors` and `res/values/colors.xml` give the palette:

| | light | dark |
|---|---|---|
| background | `#E8EAED` | `#263238` |
| key text | `#37474F` | `#FFFFFF` |
| hint text | `#B337474F` | `#80FFFFFF` |
| space bar | `#FFFFFF` | `#364248` |
| accent / action key / shift / waves | `#1A73E8` | `#5E97F6` |

Derived colours (strip, expand-key disc, suggestion text, pressed states, divider) use the same HSL maths as
`java/…/latin/utils/ColorUtil.kt` (`brighten` +0.06 L, `darken` −0.06 L, `isDarkColor`) applied as in
`latin/common/Colors.kt` `DefaultColors`. Key backgrounds are transparent with borders off.

**Geometry** (dp at 384 dp width; the key area is 0.618 of the width, because
`ResourceUtils.getDefaultKeyboardHeight` lets the minimum height of 61.8 % of the width win over
`config_default_keyboard_height` 205.6 dp on a phone):

- strip 40 dp `res/values/config.xml` `config_suggestions_strip_height`; expand key 36 dp, 1 dp margin
  (`layout/suggestions_strip.xml`), its disc `drawable/toolbar_expand_key_background.xml`
- key area padding top 2.335 %, bottom 4.669 %, row gap 3.527 % (of the key area height), column gap
  1.089 % (of the width): `config_keyboard_*_holo`, `config_key_*_gap_holo`, applied by `KeyboardParams.readAttributes`;
  side padding 0 (`Defaults.PREF_SIDE_PADDING_SCALE`); 4 rows of 25 % (`KeyboardParser`)
- rows: 10 letters, 9 letters offset by half a key, shift 0.15 + 7 + delete 0.15
  (`assets/layouts/main/qwerty.txt`, `layouts/functional/functional_keys.json`); bottom row `?123` 0.15,
  comma, emoji (`PREF_SHOW_EMOJI_KEY` true, language key false), space, period, action 0.15. The d-pad key
  only exists on the symbols layout
- text: letter 55 %, label 34 %, hint 25 % of the key height (`config_key_*_ratio_lxx`), hint padding 1 dp,
  hint characters as in the HeliBoard QWERTY popup keys (digits, `@#€_&-+()`, `*"':;!?`), space bar text
  33.735 % (`config_language_on_spacebar_text_ratio`), suggestion text 18 dp, "…" hint 27 dp
- space bar `drawable-v26/btn_keyboard_spacebar_base.xml`: inset 25 % top and bottom, 5 dp corners;
  action key `drawable/btn_keyboard_key_action_normal_lxx_base.xml`: oval 35 dp
- suggestion slots: centre word 36 % (`config_center_suggestion_percentile`), dividers 1×24 dp
  `#26080B0B` (`drawable/suggestions_strip_divider.xml`)

**Icons** (`assets/*.svg`, converted by `tools/vec2svg.py`): `sym_keyboard_{shift,delete,return,go,search,send,done,smiley}_lxx`,
`ic_arrow_right` (expand key), `sym_keyboard_voice_lxx` (idle mic, 28×26 dp), `ic_launcher_foreground` /
`ic_launcher_foreground_dark` as `vv_mark_white.svg` / `vv_mark_dark.svg`, cropped to the ink bounds like
`VoiceGlow.renderMark` does and drawn 28 dp wide (`VoiceGlow.markFor`: dark mark on a light keyboard).

**Glow** `VoiceGlow.kt`, `Defaults.PREF_GLOW_SIZE` 0.20, `PREF_GLOW_GAIN` 1.0: the mark's silhouette blurred with
radius 0.20 × mark size (Skia sigma = 0.57735 r + 0.5 px), painted in the accent. Tried against the phone
screenshot with other sigma/gain values; these code values fit best.

**Waves** `VoiceWaveView.kt` ported line by line: constants `Defaults.PREF_WAVE_*` (amplitude 0.19, reaction
4.0, cycles 1.2, speed 0.024, spread 1.0, jitter 0.21, count 6, attack 0.70, damping 0.25) and the private
companion constants; 30 fps state steps; stroke 1.2 dp; canvas = the key area (`KeyboardWrapperView.onLayout`);
colour = `ColorType.GESTURE_TRAIL` = accent. The level comes from `demoLevel()` (`DEMO_*` constants), the
app's own stand-in for a voice.

**Voice behaviour** `LatinIME.handleVoiceInput`, `updateVoiceInputState` (timer on the space bar, 1 s tick),
long-press on space 300 ms (`Defaults.PREF_KEY_LONGPRESS_TIMEOUT`), pulse 500 ms / 15 s
(`SuggestionStripView`). The 0.4 s commit delay and the one-block arrival are the product owner's
specification for the demo.

## Comparison with the app

The phone (Galaxy S24 Ultra, 1440×3120, 600 dpi = 384 dp wide) had its screen off, so no fresh capture was
possible. The raw captures in `marketing/shots/*/raw.jpg` (screenshots straight off that phone) were used:

| image in `compare/` | app screenshot | what it proves |
|---|---|---|
| `listening-dark_vs_app-gmail.png` | `marketing/shots/gmail/raw.jpg` (listening 1:02, empty strip) | geometry, text, icons, colours |
| `listening-suggestions-dark_vs_app-whatsapp.png` | `marketing/shots/whatsapp/raw.jpg` (listening 0:31, suggestions) | strip, suggestion layout, dots |

Left = app, middle = replica, right = difference ×3. Measured on the 1440 px render: glyph and icon bounding
boxes agree within 1–3 px, background / strip / space bar / action key colours within 1 of 255, glow and
logo match visually. The waves cannot match line for line: the app draws them from the live microphone,
the replica from `demoLevel()` (phase and amplitude differ, shape and look are the same code).

`compare/states/` has stills of every state in dark and light at 390 px and 1440 px (and four toasts). These
have **no app screenshot to compare against** yet; to close that gap the owner needs to put the phone in the state
and capture it:

- `idle` (dark, light), mic glyph and "Hold to dictate" — no capture exists
- light theme in any state
- `degraded` ("Waiting for connection…"), `pulse`
- the toasts (these are Android system UI; the replica's toast is an approximation)

## Not found in the code / known differences

- **Accent colour.** The code says `#5E97F6` (dark) and `#1A73E8` (light). The marketing captures show
  `#87D1EA` on the dark theme (action key, shift, waves, glow). Nothing in the source sets that colour, so it
  is a setting or Android's dynamic colours on that phone. The replica defaults to the code value;
  `accent: "screenshots"` gives the phone's look. Decide which one the website should show.
- **Microphone level.** Real input is not reproducible; `demoLevel()` is used (the app has it for the wizard).
- **Font.** The app uses the system face (Roboto on the S24, verified in `/system/fonts`). The replica asks for
  `Roboto, "Segoe UI", system-ui, sans-serif` and fetches nothing; on a machine without Roboto the glyphs
  are a few px off. The comparison images were rendered with the phone's Roboto injected by `tools/render.py --font`.
- **Toasts** are Android system UI, not in the code; shape and colour are approximated.
- **No "connecting" state, no AI actions** exist in the app's code (searched `LatinIME`, `SuggestionStripView`,
  `VoiceSession*`, `strings.xml`); the floating mark / overlay (`VoiceOverlay`) is not part of the keyboard and not replicated.
- Not replicated: key preview and long-press popups, symbols / emoji / clipboard layouts, the expanded toolbar,
  one-handed mode, the navigation bar.

## Regenerating

```bash
python3 tools/inline-icons.py                 # assets/*.svg -> keyboard.js
python3 tools/make_compare.py --font ~/Roboto-Regular.ttf   # compare/ (needs Chrome and Pillow)
python3 tools/render.py out.png --w 1440 --state listening --seconds 62 --accent screenshots
```
