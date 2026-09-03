# Getting Started

VibeVoiceBoard is built with Gradle and the Android Gradle Plugin. Clone this repository
(`https://github.com/VibeVoice/VibeVoiceBoard`) and open it in [Android Studio](https://developer.android.com/studio),
or work with a text editor and the command line — any compatible IDE will do.

A few things that are specific to this repo:

* **JDK.** The build runs on Java 17 or 21. Newer JDKs currently fail during Gradle script compilation
  with a misleading `does not specify compileSdk` error, because the embedded Kotlin compiler cannot
  parse their version string. Set `JAVA_HOME` accordingly if your default `java` is newer.
* **Android SDK.** A vendored SDK lives in `./android-sdk` and is referenced from `local.properties`.
* **Git hooks.** Version bumps are automated by hooks that a clone does not install. Run
  `bash tools/hooks/install-hooks.sh` once, and let the hooks manage the `VERSION` file rather than
  bumping it by hand.
* **Building and testing.** `./gradlew assembleDebugNoMinify` for quick iteration,
  `./gradlew testDebugUnitTest` for the full test suite, `./gradlew lint` before opening a PR.

If you have difficulties implementing some functionality, you're welcome to ask for help. No one will write the code for you, but often other contributors can give you very useful hints.

# About the Code

This app is a fork of HeliBoard, which is itself based on AOSP keyboard, and in many places still
contains mostly the original code. There are some extensions, and some parts have been replaced
completely. When working on this app, you will likely notice its rather large size, and quite different
code styles and often ancient comments and _TODO_s, where the latter are typically untouched since AOSP
times. Unfortunately a lot of the old code is hard to read or to fully understand with all of its
intended (and unintended) consequences.

Everything outside the `vibevoice` packages is inherited code. Keep changes there minimal and local:
this fork merges from upstream regularly, and every line touched in a shared file is a line that can
conflict later.

Some hints for finding what you're looking for:
* Layouts: stored in `layouts` folder in assets, interpreted by `KeyboardParser` and `TextKeyData`
  * Popups: either on layouts, or in `locale_key_texts` (mostly letter variations for specific languages that are not dependent on layout)
* Touch and swipe input handling: `PointerTracker`
* Handling of keycode / text inputs: `InputLogic`
  * chain: `PointerTracker` -> `KeyboardActionListenerImpl` -> `LatinIME` -> `InputLogic`
* Suggestions: `DictionaryFacilitatorImpl`, `Suggest`, `InputLogic`, and `SuggestionStripView` (in order from creation to display, omitting the native library)
* Communication with the app / text field (inputs, reading current text): `RichInputConnection`
* Receiving events and information from the app / text field: `LatinIME`
* Settings are in `SettingsValues`, with some functionality in `Settings` and the default values in `Default`
* Voice input: `helium314/keyboard/latin/vibevoice/`
  * `VibeVoiceClient` owns audio capture, the WebSocket, and account linking
  * session state lives in `LatinIME.handleVoiceInput()` and the `VibeVoiceListener` implementation next to it
  * the wire protocol is documented in [docs/vibevoice_integration_guide.md](docs/vibevoice_integration_guide.md) — read it before touching the reconnect or buffering logic

# Guidelines

When contributing to the app, please:
* Be careful when modifying core components, as it's easy to trigger unintended consequences
* When introducing a feature or change that might not be wanted by everyone, make it optional
* Keep code simple where possible. Complex code is harder to review and to maintain, so the complexity should also add a clear benefit
* Avoid noticeable performance impact. Some parts of the code are executed very frequently, and the keyboard should stay responsive even on older devices.
* Try making use of in-place mechanisms instead of re-inventing the wheel. Your contribution should only add as much complexity as necessary, the code is overly complicated already 😶.
* Keep your changes to few places, as opposed to sprinkling them over many parts of the code. This helps with keeping down complexity during review, and with maintainability of the app.
* Make a draft PR when you intend to still work on it. Submitting an unfinished PR can be a good idea when you're not sure how to best continue and would like some comments.
* When you fix a bug without opening an issue, please provide a way to reproduce the bug (see [bug report template](.github/ISSUE_TEMPLATE/bug_report.md))
* Noticeable adjustments (keyboard UI, default layouts, ...) should either provide a benefit for everyone, or be optional.
* If your contribution contains code that is not your own, provide a link to the source
  * This is especially relevant to be sure the code's license is compatible to this project's GPL3
  * Note that with LLM generated PRs you might add code with an incompatible license. Better make sure the LLM you're using is trained only with GPL3 compatible code.

Further things to consider (though irrelevant for most PRs):
* APK size:
  * Large increases should be discussed first, and will only be added when it's considered worth the increase for a majority of users. It might be possible to avoid size increase by importing optional parts, like it's done for dictionaries.
  * Small increases like when adding code or layouts are never an issue
* Do not add proprietary code or binary blobs. If it turns out to be necessary for a feature you want to add, it might be acceptable when the user opts in and imports those parts, like it's done for glide typing.
* Privacy: this keyboard holds the `INTERNET` and `RECORD_AUDIO` permissions for the sake of voice
  input, and that is the only thing they may be used for. Audio and text must leave the device only
  during an active transcription session that the user started. Any new network traffic, telemetry or
  data collection needs to be discussed first.

## Necessary

Some parts of the guidelines are necessary to fulfill for facilitating code review. It doesn't need to be perfect from the start, but consider it for your future PRs when you're reminded of these guidelines. Note that the larger / more complex your PR is, the more relevant these guidelines are.
Your PR should:
- **Be only about a single thing**. Mixing unrelated or semi-related contributions into a single PR is hard to review and can get messy. As a general rule: if one part doesn't need the other one(s), it should be separate PRs. If one feature builds on top of another one, but the base is usable on its own, do a PR for the base and then a follow-up once it's merged.
- **Have a proper description**. A good description helps _a lot_ for understanding what you intend to achieve with the changes, and for understanding the code. This is relevant for separating wanted from unintended changes in behavior during review.
- **Not contain translations**. See below — locale resources are synced from upstream. Exception is when you add new resource strings, those can be added right away in `app/src/main/res/values/strings.xml`.
- **Not be LLM generated**. LLMs enable contributors to quickly generate code that often is bulky and contains parts that are hard to understand. When the you do not understand the code, it's not possible to discuss such parts. See also [AI_USAGE.md](AI_USAGE.md).
- **Not contain LLM generated discussion / description**. LLMs typically generate verbose and useless descriptions. Please save us some time and write it yourself, otherwise actual discussion is impossible.

Please leave dependency upgrades to the maintainers, unless you state a good reason why they should be done now.

# Adding / Adjusting Layouts

See [layouts.md](layouts.md#adding-new-layouts--languages) for how to add new layouts to the app. Please stay in line with other layouts regarding the popup keys.

When editing existing layouts, please consider that people should should still get what they're used to. In case of doubt it might be better to add a new layout instead of overhauling existing layouts.
`locale_key_texts` files should only contain letters that are actually part of the language, with exception of the optional `more_popups_<...>.txt` files.

# Update Emojis

See make-emoji-keys tool [README](tools/make-emoji-keys/README.md).

# Translations

Everything under `app/src/main/res/values-*/` is inherited from upstream and arrives here through
merges. Do not hand-edit those files and do not submit translations in a PR — the changes are
overwritten on the next sync. Strings that are specific to this fork are added to
`app/src/main/res/values/strings.xml` only, in English.

# Dictionaries
No new dictionaries will be added to this app. Please submit dictionaries and the wordlist to the [dictionaries repository](https://codeberg.org/Helium314/aosp-dictionaries)
