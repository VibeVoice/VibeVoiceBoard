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
For more information, see [VIBEVOICE_DEBUGGING.md](file:///Users/schneider/repos/VibeVoiceBoard/VIBEVOICE_DEBUGGING.md).


## Versioning

Version is managed automatically via the [`VERSION`](./VERSION) file at the repo root. This is the **single source of truth** — `build.gradle.kts` reads it at build time.

### Rules
- **Patch** (`x.y.Z`) — bumped automatically after every commit via git hook
- **Minor** (`x.Y.0`) — bumped automatically (patch reset to 0) on any merge landing on `main`, `master`, or `feature/vibevoice-integration`
- **Major** — bumped manually by editing `VERSION` directly

### After cloning — install hooks once
Git hooks are not committed to `.git/` automatically. After cloning, run:
```bash
bash tools/hooks/install-hooks.sh
```

This installs:
- `post-commit` — auto-bumps patch after every non-version commit
- `post-merge` — auto-bumps minor (resets patch) on merges to primary branches
- `post-push` — builds APK and uploads to Nextcloud after every push (set `SKIP_APK_BUILD=1` to suppress)

### Manual version override
Edit `VERSION` directly if you need to set a specific version, then commit:
```bash
echo "4.1.0" > VERSION
git add VERSION && git commit -m "chore(version): manual bump to 4.1.0"
```
