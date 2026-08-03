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

