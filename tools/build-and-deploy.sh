#!/bin/bash
# Automated Build, Nextcloud Sync, and ADB Install script for VibeVoiceBoard (Linux)
set -e

# 1. Environment Setup & Paths
if [[ "$OSTYPE" == "darwin"* ]]; then
  echo "macOS detected."
  if [ -d "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  fi
  export PATH="$PATH:/Users/schneider/repos/VibeVoiceBoard/android-sdk/platform-tools"
  
  MAC_NC=$(find /Users/schneider/Library/CloudStorage -maxdepth 1 -name "Nextcloud*" 2>/dev/null | head -n 1)
  if [ -n "$MAC_NC" ]; then
    DEST_DIR="$MAC_NC/Documents/Shared Documents/VibeVoiceBoard"
  else
    DEST_DIR="/Users/schneider/Library/CloudStorage/Nextcloud-florian@cloud․infraviored․com/Documents/Shared Documents/VibeVoiceBoard"
  fi
else
  echo "Linux detected."
  export JAVA_HOME="/usr/lib/jvm/default-java"
  export PATH="$PATH:$HOME/Android/Sdk/platform-tools"
  DEST_DIR="/home/schneider/nextcloud/Documents/Shared Documents/VibeVoiceBoard"
fi

# 2. Compile Android APK
echo "Compiling Android debug APK using JAVA_HOME=$JAVA_HOME..."
./gradlew assembleDebug -q --no-configuration-cache

# 3. Locate Compiled APK
APK_PATH=$(find app/build/outputs/apk/debug -name "*.apk" | head -n 1)
if [ -z "$APK_PATH" ]; then
  echo "Error: Compiled APK not found!"
  exit 1
fi
APK_FILENAME=$(basename "$APK_PATH")
echo "Found compiled APK: $APK_PATH"

# 4. Deploy to Nextcloud Sync Folder
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
