# Fork Maintenance Guidelines

To maintain this repository (which is a fork of HeliBoard), follow these guidelines:

## Branch Strategy

1. **Mirror Branch (`main` / `master`)**:
   * The `main` (or `master`) branch on `origin` must remain a pure mirror of the `upstream` (`HeliBorg/HeliBoard`) repository.
   * **Never** commit directly to the `main` branch.
   * Keep it updated by fetching from upstream:
     ```bash
     git checkout main
     git pull upstream main
     git push origin main
     ```

2. **Feature Branches**:
   * All custom work (e.g. VibeVoice integrations) must be developed on dedicated feature branches (e.g. `feature/vibevoice-integration`).

## Keeping Branches Synced

* **Merge, Do Not Rebase**: 
  * To bring upstream updates into your active feature branches, always use **git merge** rather than rebasing. 
  * Rebasing feature branches continuously makes the integration process harder and more error-prone over time.
  * To update your branch with the latest upstream changes:
    ```bash
    git checkout feature/my-feature
    git merge main
    ```

## Local Development and Debugging

### Connecting to Devices
If the target physical device is connected to the same local subnet (e.g. `192.168.178.x`), its IP may change over time (e.g. from `192.168.178.70` to `192.168.178.189`). Use `arp -a` to locate the current IP of the device (such as `florian-s-s24-ultra.fritz.box`), then connect using:
```bash
./android-sdk/platform-tools/adb connect <IP>:5555
```

### Pulling VibeVoice Logs
The correct package name for the local debug keyboard app is `org.vibevoice.board.debug`. Pull the runtime logs from the device using the helper script:
```bash
./pull_vibevoice_logs.sh
```
For more information, see [VIBEVOICE_DEBUGGING.md](./VIBEVOICE_DEBUGGING.md).


## Strings and translations

The fork's own user-facing strings — every `vibevoice_*` key and the setup wizard's `setup_*` keys
— are ours. Upstream has never heard of them.

- **English originals** go in `app/src/main/res/values/strings.xml`, alongside upstream's.
- **Translations of them** go in `values-<locale>/strings_vibevoice.xml`, a separate file per locale.
- **Never** put a fork string in `values-<locale>/strings.xml`. Those files are Weblate's; they are
  overwritten from upstream, and a fork string in one is a string that will silently disappear on the
  next sync — or, worse, survive in a file nobody owns.

Adding a key means editing `values/strings.xml` first; a translation file only translates keys that
already exist there, and a key in a translation file with no English original is a build that
compiles and a string nobody can reach.

Currently translated: German, complete.

## Versioning

Two files at the repo root, read by `app/build.gradle.kts`:

- **`VERSION`** — the name of the next release (`versionName`), e.g. `1.0.1`. **Only a person changes
  it**, when a release is cut. For Play users everything between two releases is one step, however
  many commits it took, so the name must not count commits.
- **`VERSION_CODE`** — the ordering number (`versionCode`). The `pre-commit` hook bumps it by one on
  every commit. Play consumes a code permanently and never accepts a lower one, and a per-commit
  counter satisfies both; gaps between released codes are expected and invisible to users.

Debug builds are named `<VERSION>-dev.<VERSION_CODE>` (e.g. `1.0.1-dev.600014`), so the phone shows
which commit it runs. Build outputs are named `VibeVoiceKeyboard_<VERSION>-<VERSION_CODE>`.

### Cutting a release
1. `echo "1.0.2" > VERSION` and commit — the hook bumps `VERSION_CODE` in the same commit.
2. Tag it: `git tag v1.0.2 && git push origin v1.0.2`.
3. Build the Play bundle: `./gradlew --no-configuration-cache bundleNouserlib` (the configuration
   cache does not notice a changed `VERSION`), check it with `aapt2 dump badging` or bundletool.
4. Put the bundle in Nextcloud. **Uploading to Play is done by hand.**

A re-upload of the same release needs only a new code: any commit bumps it.

### After cloning — install hooks once
Git hooks are not committed to `.git/` automatically. After cloning, run:
```bash
bash tools/hooks/install-hooks.sh
```

This installs:
- `pre-commit` — bumps `VERSION_CODE` before every commit and includes it in the commit
- `post-merge` — a no-op, kept so re-installing replaces the old minor-bump hook
- `pre-push` — builds APK and uploads to Nextcloud in the background on every push (set `SKIP_APK_BUILD=1` to suppress)
