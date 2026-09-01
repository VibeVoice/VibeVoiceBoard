# VibeVoiceBoard

VibeVoiceBoard is a customizable Android keyboard with built-in cloud speech-to-text. It is a fork of
HeliBoard, which itself is based on AOSP / OpenBoard, and adds the VibeVoice transcription service on
top.

Unlike its upstream, this keyboard is **not** offline-only: it requests the `INTERNET` permission to
reach the VibeVoice service and the `RECORD_AUDIO` permission to capture speech. Audio is streamed to
`vibevoice.net` only while a transcription session is active — see the
[privacy policy](https://vibevoice.net/privacy) for what is transmitted and retained. Every other
feature of the keyboard works without network access.

[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get APK from GitHub" height="80">](https://github.com/VibeVoice/VibeVoiceBoard/releases/latest)

## Table of Contents

- [Features](#features)
- [Voice input](#voice-input)
- [Contributing](#contributing-)
   * [Reporting Issues](#reporting-issues)
   * [Translations](#translations)
   * [Code Contribution](CONTRIBUTING.md)
- [Links](#links)
- [License](#license)
- [Credits](#credits)

# Features
<ul>
  <li>Cloud speech-to-text via VibeVoice, reachable from the toolbar mic key or by long-pressing space</li>
  <li>Add dictionaries for suggestions and spell check</li>
  <ul>
    <li>build your own, or get them  <a href="https://codeberg.org/Helium314/aosp-dictionaries#dictionaries">here</a> (quality may vary)</li>
    <li>additional dictionaries for emojis or scientific symbols can be used to provide suggestions (similar to "emoji search")</li>
    <li>note that for Korean layouts, suggestions only work using <a href="https://github.com/openboard-team/openboard/commit/83fca9533c03b9fecc009fc632577226bbd6301f">this dictionary</a>, the tools in the dictionary repository are not able to create working dictionaries</li>
  </ul>
  <li>Customize keyboard themes (style, colors and background image)</li>
  <li>Emoji search (inline and separate, requires <a href="https://codeberg.org/Helium314/aosp-dictionaries">emoji dictionary</a>)</li>
  <ul>
    <li>can follow the system's day/night setting on Android 10+ (and on some versions of Android 9)</li>
    <li>can follow dynamic colors for Android 12+</li>
  </ul>
  <li>Customize keyboard <a href="layouts.md">layouts</a> (only available when disabling <i>use system languages</i>)</li>
  <li>Customize special layouts, like symbols, number,  or functional key layout</li>
  <li>Multilingual typing</li>
  <li>Glide typing (<i>only with closed source library</i> ☹️)</li>
  <ul>
    <li>library not included in the app, as there is no compatible open source library available</li>
    <li>can be extracted from GApps packages ("<i>swypelibs</i>"), or downloaded <a href="https://github.com/erkserkserks/openboard/tree/46fdf2b550035ca69299ce312fa158e7ade36967/app/src/main/jniLibs">here</a> (click on the file and then "raw" or the tiny download button)</li>
  </ul>
  <li>Clipboard history</li>
  <li>One-handed mode</li>
  <li>Split keyboard</li>
  <li>Number pad</li>
  <li>Backup and restore your settings and learned word / history data</li>
</ul>

# Voice input

Transcription requires a linked VibeVoice account. Open the keyboard settings, go to _VibeVoice
Integration_ and link the device — the app shows a code to enter in your browser.

There are three ways to start and stop a session:
* the microphone key in the toolbar
* long-pressing the space bar
* tapping space while recording stops the current session

Partial results appear as composing text and are committed when the session ends. If the microphone is
claimed by another app while you are dictating, Android silently feeds the keyboard silence; the
session then ends on its own and whatever was already transcribed is kept.

# Contributing ❤

## Reporting Issues

Whether you encountered a bug, or want to see a new feature, you can contribute to the project by
opening a new issue [here](https://github.com/VibeVoice/VibeVoiceBoard/issues). Your help is always
welcome!

Before opening a new issue, be sure to check the following:
 - **Does the issue already exist?** Please search open and closed issues before reporting.
 - **Is the issue still relevant?** Make sure it is not already fixed in the latest version.
 - **Is it a single topic?** If you want to suggest multiple things, open multiple issues.
 - **Is it written by a human?** Do not use LLMs or similar to generate issues. Having LLMs help with translation or similar is acceptable, but must be disclosed. See also [AI_USAGE.md](AI_USAGE.md)

For problems with transcription specifically, the in-app bug reporter (settings → _Report a Bug_)
submits the diagnostic log along with your description, which is far more useful than a screenshot.

If you're interested, you can read the following useful text about effective bug reporting (a bit longer read): https://www.chiark.greenend.org.uk/~sgtatham/bugs.html

## Translations

Translations for everything inherited from upstream are maintained in the upstream project and arrive
here through merges. Do not hand-edit files under `app/src/main/res/values-*/` — those changes are
overwritten on the next sync. Strings that are specific to this fork live in
`app/src/main/res/values/strings.xml`.

## Code Contribution
See [Contribution Guidelines](CONTRIBUTING.md). Note that the upstream rule against network permissions
does not apply to the VibeVoice layer, which is the reason this fork exists.

# Links
* Info
  * [Layout documentation](layouts.md) (technical info regarding layout customization)
  * [VibeVoice integration guide](docs/vibevoice_integration_guide.md) (the transcription wire protocol)
  * [For creating custom dictionaries](https://codeberg.org/Helium314/aosp-dictionaries#wordlist-information) (see also top of the linked readme)
* Other
  * [Dictionaries](https://codeberg.org/Helium314/aosp-dictionaries)
  * [swipe-o-scope](https://codeberg.org/eclexic/swipe-o-scope) for visualizing gesture data as created when using gesture data gathering

# License

VibeVoiceBoard, as a fork of HeliBoard and OpenBoard, is licensed under GNU General Public License v3.0.

 > Permissions of this strong copyleft license are conditioned on making available complete source code of licensed works and modifications, which include larger works using a licensed work, under the same license. Copyright and license notices must be preserved. Contributors provide an express grant of patent rights.

See repo's [LICENSE](/LICENSE) file.

Since the app is based on Apache 2.0 licensed AOSP Keyboard, an [Apache 2.0](LICENSE-Apache-2.0) license file is provided.
The icon is licensed under [Creative Commons BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). A [license file](LICENSE-CC-BY-SA-4.0) is also included.

# Credits
- HeliBoard and its contributors, the direct upstream of this fork
- Icon by [Fabian OvrWrt](https://github.com/FabianOvrWrt) with contributions from [The Eclectic Dyslexic](https://github.com/the-eclectic-dyslexic)
- [OpenBoard](https://github.com/openboard-team/openboard)
- [AOSP Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
- [LineageOS](https://review.lineageos.org/admin/repos/LineageOS/android_packages_inputmethods_LatinIME)
- [Simple Keyboard](https://github.com/rkkr/simple-keyboard)
- [Indic Keyboard](https://gitlab.com/indicproject/indic-keyboard)
- [FlorisBoard](https://github.com/florisboard/florisboard/)
