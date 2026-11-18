# Swipe typing: options

Status: research, nothing built. Decided 2026-11-02: start after streaming protocol v2 is
released on Play.

## Where we stand

HeliBoard, and therefore this fork, does gesture ("swipe", "glide") typing only through Google's
proprietary `libjni_latinimegoogle.so`, which the user has to supply. We publish the `nouserlib`
build to Play, so for our users swipe typing does not work at all. The library cannot fill that
gap for us:

- **Bundling it** is not allowed. It is closed-source Google code that is not licensed for
  redistribution. Custom ROMs ship it separately in GApps ("swypelibs"), and NLnet calls it
  "abandoned closed source code by Google" (https://nlnet.nl/project/GestureTyping/).
- **Downloading it at runtime** breaks Play's Device and Network Abuse policy. That policy
  forbids apps that download native code from anywhere other than Google Play
  (https://support.google.com/googleplay/android-developer/answer/9888379). The HeliBoard fork
  LeanType works this way, so it is not a model for us.

We found no HeliBoard or OpenBoard fork that ships a free swipe engine ready to use. Two options
remain.

## Option 1: FUTO Swipe (recommended, now)

FUTO has published the swipe engine from FUTO Keyboard as a separate library.

| | |
|---|---|
| Library | https://gitlab.futo.org/keyboard/swipe-library (GPL-3.0-or-later, same as ours) |
| Models | https://huggingface.co/futo-org/futo-swipe, under the FUTO Model License |
| Training data | https://huggingface.co/datasets/futo-org/swipe.futo.org (MIT) |
| Docs | https://swipe.futo.tech/, technical report https://arxiv.org/abs/2606.25247 |
| Quality | FUTO reports 7.38 % top-1 error. It runs in production in FUTO Keyboard (v0.1.29 and later). |
| Maturity | The library repo is new: created 2026-05-27, 7 commits, last on 2026-06-18. |

**License condition.** The FUTO Model License allows any use, commercial included, but the app
must show users a notice that it is "powered by FUTO Swipe", for example in settings or on the
about screen. Leaving the notice out counts as a material breach. This condition applies to data
loaded at runtime, not to our code, so it is generally considered compatible with GPL-3.0. That
is not settled law, so a quick legal check before release is worthwhile.

The FUTO Keyboard app itself is under the FUTO Source First License, which forbids commercial
modification. Take nothing from its code; use only the separate library.

**Integration.**

- The engine is C++ on Meta's ExecuTorch runtime, with a JNI binding and a Kotlin wrapper
  (`SwipeDecoder.kt`). `make android` builds `libswipe_jni.so` for four ABIs.
- It reads AOSP `.combined` dictionaries, which are the format HeliBoard already ships.
- The work is to route HeliBoard's gesture points into the library's `recognize(x, y, t)` call
  and show the candidates it returns in the suggestion strip. HeliBoard's native dictionary code
  does not change.
- Put it behind a small gesture-recognizer interface so the engine can be swapped (see option 2).
- Start with English and QWERTY. The encoder works with any layout or language; the optional
  decoder covers English QWERTY only.
- Effort: moderate. The risk sits in the young library and in APK size (native runtime plus
  model).

## Option 2: HeliBoard's own engine (later, via upstream)

HeliBoard is building a free replacement for Google's library, funded by NLnet ("Gesture Typing
for AOSP-derived Keyboards").

| | |
|---|---|
| Project | https://nlnet.nl/project/GestureTyping/, tracked in https://github.com/HeliBorg/HeliBoard/issues/2226 |
| License | Probably Apache-2.0; the developer says "most likely", not final |
| Status | Unreleased. Gesture data is being collected (HeliBoard 3.7 to 3.9) until 2026-11-30: https://github.com/HeliBorg/HeliBoard/wiki/Tutorial:-How-to-Contribute-Gesture-Data |
| Plan | A drop-in replacement for Google's library, opt-in until it is as good |

**Integration.** Almost free. It would arrive with our normal `git merge main` from upstream.
There is no release date.

## Recommendation

Build option 1 behind a swappable interface after the v2 release, English first, with the "powered
by FUTO Swipe" notice in settings. When HeliBoard's engine ships, compare the two on the same
dictionaries and keep the better one.

## Considered and rejected

- **CleverKeys** (https://github.com/tribixbite/cleverkeys, GPL-3.0): free neural swipe typing
  that works well, and the fallback if the FUTO model terms turn out to be unacceptable. It is a
  Kotlin rewrite of Unexpected Keyboard, a different architecture, so the decoder would have to be
  extracted. The provenance of its training data is also unclear: the training repo has no
  license.
- **AnySoftKeyboard** (Apache-2.0): gesture typing is still marked experimental, and it does not
  handle contractions (issue #2163). Not worth the porting work.
- **FlorisBoard**: removed its glide typing; a rewrite is on the roadmap. **Unexpected
  Keyboard**: has no whole-word swipe typing. Neither has anything to reuse.
- **LeanType**: downloads Google's library at runtime, which is forbidden on Play.
