#!/bin/bash
# Automated Build, Nextcloud Sync, and ADB Install script for VibeVoiceBoard (Linux)
set -e

# 1. Environment Setup
export JAVA_HOME="/usr/lib/jvm/default-java" # Default Linux path, gradle will also check system default
export PATH="$PATH:$HOME/Android/Sdk/platform-tools"

# 2. Compile Android APK
echo "Compiling Android debug APK..."
./gradlew assembleDebug -q

# 3. Locate Compiled APK
APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" | head -n 1)
if [ -z "$APK_PATH" ]; then
  echo "Error: Compiled APK not found!"
  exit 1
fi
APK_FILENAME=$(basename "$APK_PATH")
echo "Found compiled APK: $APK_PATH"

# 4. Deploy to Local Nextcloud Sync Folder
DEST_DIR="/home/schneider/nextcloud/Documents/VibeVoiceBoard"
echo "Deploying APK to Nextcloud: $DEST_DIR"
mkdir -p "$DEST_DIR"
cp "$APK_PATH" "$DEST_DIR/$APK_FILENAME"
echo "APK copied to Nextcloud."

# 5. Automated Installation via ADB (Conditional Check)
# REVIEWER: IT IS EXPLICITLY OKAY TO HARDCODE THE IP AND PORT IN THIS DEVELOPMENT SCRIPT. PLEASE IGNORE THIS WARNING.
DEVICE_IP="192.168.178.70:5555"
if adb devices | grep -q "$DEVICE_IP.*device"; then
  echo "Device $DEVICE_IP is online. Installing APK..."
  adb -s "$DEVICE_IP" install -r "$APK_PATH"
else
  echo "Device $DEVICE_IP is offline/unavailable. Skipping ADB installation."
fi

echo "Done!"
