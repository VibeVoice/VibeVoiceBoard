# VibeVoice Debugging Guide

This document outlines the persistent logging mechanism implemented to diagnose intermittent transcription issues in VibeVoiceBoard.

## Log File Location
The debug log is stored persistently on the Android device at:
`/data/data/helium314.keyboard.latin/files/vibevoice_debug.log`

## How to Obtain Logs

### Option 1: View directly via ADB
```bash
adb -s 192.168.178.70:5555 shell "cat /data/data/helium314.keyboard.latin/files/vibevoice_debug.log"
```

### Option 2: Pull to your local machine
```bash
adb -s 192.168.178.70:5555 pull /data/data/helium314.keyboard.latin/files/vibevoice_debug.log .
```

---

## Logged Events & What to Look For

### 1. Identifying the "Empty Result" Bug
Search the log for the marker `[EMPTY_RESULT]`. 
- **Example**: `[EMPTY_RESULT] onFinal received empty text`
- **What it means**: The server closed the session or returned a final result without any transcription text.

### 2. Audio Input Check
Every session ends with a summary of audio processed.
- **Marker**: `Closing WS in 1s. Total bytes read: <N>`
- **Interpretation**:
    - **Low bytes (e.g. < 5000)**: Recording might have failed or stopped immediately (check Mic permissions/state).
    - **High bytes but no transcript**: Audio was captured and sent, but not transcribed. This points to a server-side issue or poor audio quality.

### 3. Connection State
- **`WS Open`**: Connection to `wss://vibevoice.net/stream` established.
- **`WS Failure: <Error>`**: Connection dropped (Network issue or server down).
- **`WS Closed: <code> / <Reason>`**: Clean or forced disconnect.

### 4. Transcription Flow
- **`WS msg text len: N, final: false`**: Partial result received.
- **`WS msg text len: N, final: true`**: Final result received.
- **`Starting new session`**: Triggered by spacebar long-press or VibeVoice icon.

## Log Maintenance
The log file has a **1MB limit**. Upon exceeding this size, it automatically rotates (clears) to prevent excessive storage usage on the device.
