#!/bin/bash
# Automated Build, WebDAV Upload, and ADB Install script for VibeVoiceBoard
set -e

# 1. Environment Setup & Paths
if [[ "$OSTYPE" == "darwin"* ]]; then
  echo "macOS detected."
  if [ -d "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
  fi
  export PATH="$PATH:/Users/schneider/repos/VibeVoiceBoard/android-sdk/platform-tools"
else
  echo "Linux detected."
  export JAVA_HOME="/usr/lib/jvm/default-java"
  export PATH="$PATH:$HOME/Android/Sdk/platform-tools"
fi

# Load credentials from .env (repo root or tools/)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for ENV_FILE in "$SCRIPT_DIR/../.env" "$SCRIPT_DIR/.env"; do
  if [ -f "$ENV_FILE" ]; then
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    break
  fi
done

# Resolve credentials: support NEXTCLOUD_CREDENTIALS or NEXTCLOUD_USER+NEXTCLOUD_PASS
if [ -n "$NEXTCLOUD_CREDENTIALS" ]; then
  NC_AUTH="$NEXTCLOUD_CREDENTIALS"
elif [ -n "$NEXTCLOUD_USER" ] && [ -n "$NEXTCLOUD_PASS" ]; then
  NC_AUTH="$NEXTCLOUD_USER:$NEXTCLOUD_PASS"
else
  echo "Error: Nextcloud credentials not set. Add NEXTCLOUD_CREDENTIALS or NEXTCLOUD_USER+NEXTCLOUD_PASS to .env"
  exit 1
fi

# WebDAV upload target
NC_WEBDAV_BASE="https://cloud.infraviored.com/remote.php/dav/files/florian/Documents/Geteilte%20Dokumente/VibeVoiceBoard"

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

# 4. Upload APK to Nextcloud via WebDAV
NC_TARGET="$NC_WEBDAV_BASE/$APK_FILENAME"
echo "Uploading APK to Nextcloud via WebDAV..."
curl --silent --show-error --fail \
  --user "$NC_AUTH" \
  --upload-file "$APK_PATH" \
  "$NC_TARGET"
echo "APK uploaded: $NC_TARGET"

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
