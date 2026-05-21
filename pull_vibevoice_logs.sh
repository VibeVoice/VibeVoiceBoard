#!/bin/bash
DEVICE="${1:-${ADB_DEVICE_SERIAL:-}}"
PACKAGE="helium314.keyboard.debug"
FILENAME="vibevoice_debug.log"
LOCAL_PATH="./vibevoice_debug.log"
ADB_CMD=(adb)

if [ -n "$DEVICE" ]; then
    ADB_CMD+=( -s "$DEVICE" )
    echo "Attempting to pull logs from $DEVICE ($PACKAGE)..."
else
    echo "Attempting to pull logs from current ADB device ($PACKAGE)..."
fi

# Try direct pull first (works on some devices/root)
"${ADB_CMD[@]}" pull "/data/data/$PACKAGE/files/$FILENAME" "$LOCAL_PATH" 2>/dev/null

if [ $? -ne 0 ]; then
    echo "Direct pull failed. Trying via run-as..."
    # If direct pull fails, try to cat it via run-as and redirect
    "${ADB_CMD[@]}" shell "run-as $PACKAGE cat files/$FILENAME" > "$LOCAL_PATH"
fi

if [ -s "$LOCAL_PATH" ]; then
    echo "Logs pulled successfully to $LOCAL_PATH"
    echo "--- LAST 20 LINES ---"
    tail -n 20 "$LOCAL_PATH"
else
    echo "Failed to pull logs or log file is empty."
    echo "Checking if log file exists on device..."
    "${ADB_CMD[@]}" shell "run-as $PACKAGE ls -l files/$FILENAME"
fi
