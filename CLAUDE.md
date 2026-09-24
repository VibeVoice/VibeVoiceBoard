# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

VibeVoiceBoard is a **fork of HeliBoard** (an AOSP/OpenBoard-derived Android keyboard) that adds
cloud speech-to-text via the VibeVoice service. Everything outside the `vibevoice` packages is
upstream HeliBoard code.

Two consequences that trip people up:
- The Java/Kotlin namespace is still `helium314.keyboard.*`, while the app id is `org.vibevoice.board`
  (debug builds install as `org.vibevoice.board.debug`). Do not rename packages.
- Upstream is 100% offline by design; this fork deliberately adds `INTERNET` and `RECORD_AUDIO`
  permissions. Upstream's "no internet permission" rules in `README.md`/`CONTRIBUTING.md` apply to
  HeliBoard, not to the VibeVoice layer.

`AGENTS.md` holds the fork-maintenance rules and is the authority on branching/versioning; this file
summarizes them.

## Branching (from AGENTS.md)

- `main` is a **pure mirror of upstream** `HeliBorg/HeliBoard`. Never commit to it.
- All work happens on feature branches; the live one is `feature/vibevoice-integration`.
- Sync with **`git merge main`, never rebase** — the fork carries large diffs in `LatinIME.java`
  and `PointerTracker.java`, and rebasing them repeatedly is error-prone.

## Versioning

`VERSION` holds the name of the next release and only a person changes it; `VERSION_CODE` holds
the `versionCode` and the `pre-commit` hook bumps it on every commit. The split exists because Play
users see one step between releases however many commits it took, while Play consumes every code
permanently and never accepts a lower one (it has seen `403001` from the pre-release 4.x builds).
Debug builds are named `<VERSION>-dev.<VERSION_CODE>`. `AGENTS.md` has the release procedure. Hooks
are **not** installed by a clone:

```bash
bash tools/hooks/install-hooks.sh
```

- `pre-commit` — bumps `VERSION_CODE` and stages it into the same commit (skipped if already
  staged, or during a merge). Do not hand-bump it in a normal commit; just commit.
- `post-merge` — a no-op; it used to bump the minor version.
- `pre-push` — kicks off a background debug build + Nextcloud upload (`tools/build-and-deploy.sh`).
  Set `SKIP_APK_BUILD=1` to suppress; log lands at `tools/last-build.log`.

## Build & test

The Android SDK is vendored at `./android-sdk` (see `local.properties`). Java 17 target; CI uses
JDK 17, `tools/build-and-deploy.sh` prefers Homebrew `openjdk@21` on macOS.

```bash
./gradlew assembleDebug              # minified debug APK, id org.vibevoice.board.debug
./gradlew assembleDebugNoMinify      # faster iteration build, no minify
./gradlew lint                       # lint.abortOnError = true

./gradlew testDebugUnitTest          # the one that exists -- see below

./gradlew testDebugUnitTest --tests "helium314.keyboard.latin.InputLogicTest"
./gradlew testDebugUnitTest --tests "*.InputLogicTest.testX"
```

Build types beyond the usual: `nouserlib` (release without user-supplied glide lib), `runTests`
(non-minified CI variant), `debugNoMinify`. Tests are Robolectric-based and branch on
`BuildConfig.BUILD_TYPE == "runTests"` to skip cases known to fail or that hit the network
(`XLinkTest`, parts of `InputLogicTest`, `StringUtilsTest`).

**`testRunTestsUnitTest` no longer exists.** AGP 9, which arrived with the upstream merge, generates
unit-test tasks only for the `testBuildType` (default `debug`), so the `runTests` variant has no test
task at all. `.github/workflows/build-test-auto.yml` — ours and upstream's — still invokes it and
fails at task selection. Fixing that means choosing which variant tests run against, which is a
decision rather than a rename, so it has been left open.

Use `testDebugUnitTest`. It runs the cases `runTests` would skip, so **two failures are expected and
are not regressions**: `StringUtilsTest.detectEmojisAtEndFails` and
`InputLogicTest.insertLetterIntoWordHangulFails` — both carry the failure in their name, and
upstream marks both as known. 176 of 178 is a clean run.

Gradle also stops at the first failed task, so `./gradlew lint testDebugUnitTest` never reaches lint
when those two fail. Run `lint` on its own.

Full build + WebDAV upload + optional ADB install:

```bash
./tools/build-and-deploy.sh   # needs NEXTCLOUD_CREDENTIALS (or NEXTCLOUD_USER/PASS) in .env
```

## Releasing to Play

`tools/play-upload.py` uploads a bundle through the Play Developer API, so a release costs no
clicking. It is **not** automatic: run it only when Florian asks for a release. "Manual" means the
decision is his; the typing is ours.

```bash
tools/.play-venv/bin/python tools/play-upload.py --list-tracks
tools/.play-venv/bin/python tools/play-upload.py <bundle.aab> --track alpha            # draft
tools/.play-venv/bin/python tools/play-upload.py <bundle.aab> --track alpha --publish  # goes live
```

- Credentials: `play-service-account.json` in the repository root, gitignored — the key for
  `claude@vibevoice-play.iam.gserviceaccount.com` (project `vibevoice-play`, Release manager).
- Dependencies live in `tools/.play-venv` (gitignored). To recreate it:
  `python3 -m venv tools/.play-venv && tools/.play-venv/bin/pip install google-auth requests`.
- Tracks: `alpha` is the closed test the testers are opted into, `internal`, `beta`, `production`.
- **Play takes `.aab` only.** An APK is rejected at upload; `bundleNouserlib` is the build that
  produces one.
- `--notes-file` takes `[{"language": "en-US", "text": "..."}, ...]`; 500 characters per locale.
- Without `--publish` the release sits as a draft and no tester sees it. With it, the release goes
  to review and then to the track.

## On-device debugging

The device is reached over wireless ADB; its IP moves around, so find it with `arp -a` first
(`docs/android_deployment_automation.md` covers the static-port setup).

```bash
./android-sdk/platform-tools/adb connect <IP>:5555
./pull_vibevoice_logs.sh                 # or: ./pull_vibevoice_logs.sh <serial>
```

`VibeVoiceDebugLogger` writes a persistent, 1 MB self-rotating log to
`/data/data/org.vibevoice.board.debug/files/vibevoice_debug.log`. Grep it for `[EMPTY_RESULT]`,
`WS Open`, `WS Failure`, `Total bytes read` — `VIBEVOICE_DEBUGGING.md` explains what each marker
means.

## Architecture

### Upstream input chain (unchanged, worth knowing)

`PointerTracker` → `KeyboardActionListenerImpl` → `LatinIME` → `InputLogic` → `RichInputConnection`.
Layouts live in `app/src/main/assets/layouts` and are parsed by `KeyboardParser` / `TextKeyData`
(see `layouts.md`). Settings values are read through `SettingsValues`, with defaults in
`latin/settings/Defaults.kt`; the Compose settings UI lives in `helium314/keyboard/settings/`.
`CONTRIBUTING.md` has a fuller map of upstream subsystems.

### VibeVoice layer

`helium314/keyboard/latin/vibevoice/`:
- **`VibeVoiceClient.kt`** — the whole networking/audio engine. Owns the `AudioRecord` capture
  (16 kHz, 16-bit, mono PCM), the `wss://vibevoice.net/stream` WebSocket, and the OAuth 2.0 Device
  Authorization Grant used for account linking. Its companion object stores the API key in
  `EncryptedSharedPreferences` (`vibevoice_secure_prefs`), silently falling back to plaintext
  `vibevoice_prefs` when the crypto provider is unavailable.
- **`VibeVoiceDebugLogger.java`** — the on-device log described above; initialized from
  `LatinIME.onCreate`.
- **`VoiceSessionService.kt`** — microphone-typed foreground service that keeps a session alive
  after the keyboard is dismissed ("background dictation"), with a notification and a stop action.
- **`VoiceOverlay.kt`** / **`DismissTarget.kt`** — the floating mark shown while a background session
  runs, ended by dragging it onto the X; needs "draw over other apps".
- **`VoiceWaveView.kt`** / **`VoiceGlow.kt`** — the waves behind the keyboard and the glow around the
  recording mark.
- **`settings/WelcomeWizard.kt`** + **`VibeVoiceLinkPanel.kt`** — the six-step setup (enable, select,
  practice with trial minutes, link account, floating mark, done); `docs/onboarding_concept.md`.
- **`VibeVoiceBugReporter.kt`**, **`PermissionActivity.kt`** — bug-report upload and the
  RECORD_AUDIO permission prompt (an IME cannot request runtime permissions itself).

Resilience behavior lives in the client and is easy to break accidentally: a 30-second rolling audio
buffer, a capped pre-connection FIFO flushed after the auth frame, `audioConfirmedBytes =
dur * 32000` tracked from server messages so unconfirmed audio is re-sent after a reconnect
(3 retries, 500/1000/2000 ms backoff), and zero-buffer detection that re-initializes the mic.
`docs/vibevoice_integration_guide.md` is the protocol spec — read it before touching the wire format
or the reconnect logic.

### Keyboard-side session state

`LatinIME.handleVoiceInput()` is the single entry point, reached from three places:
- the `VOICE` toolbar key (`ToolbarUtils` → `KeyCode.VOICE_INPUT` → `InputLogic`),
- **long-press on space** (`KeyboardActionListenerImpl.onLongPressKey`),
- tapping space while recording (stops the session).

`LatinIME` holds `mIsRecordingVoice` / `mIsStoppingVoice` / `mVoiceComposingText` and implements
`VibeVoiceListener`. Partial results become composing text; a result that no longer starts with
`lastFullText` marks a new segment and commits the previous one. On finish, an empty final result
falls back to committing the cached composing text so nothing is lost.

`SuggestionStripView.updateVoiceKey()` reads `LatinIME.isRecordingVoice` and drives the toolbar mic
animation; this fork keeps the VOICE key always visible (upstream gates it on a system voice IME)
and blocks unpinning it. The animation state must stay strictly coupled to the recording state —
that coupling has been a repeated source of bugs.

## Conventions

- `.editorconfig`: 4 spaces, LF, 140-column limit, trailing whitespace trimmed.
- Commits follow Conventional Commits with a scope, e.g. `feat(voice):`, `fix(settings):`,
  `chore(version):`.
- `.env`, `android-sdk/`, `*.log`, `crash_reports/` are gitignored; `tools/*.sh` is gitignored except
  `build-and-deploy.sh` and `tools/hooks/*.sh`.
- New user-facing strings go in `app/src/main/res/values/strings.xml` only. The upstream locale
  files are managed by Weblate and must not be hand-edited; the fork's own translations live in
  `values-<locale>/strings_vibevoice.xml` (German today), and the product is English first.
