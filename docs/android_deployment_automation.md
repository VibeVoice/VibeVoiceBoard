# Android Deployment Automation Guide (Persistent Wireless ADB & Nextcloud Sync)

This guide describes how to configure stable wireless ADB installation and automatic APK deployment to Nextcloud. It is tailored for the VibeVoiceBoard native Android repository on Linux.

---

## 1. Establishing a Persistent Wireless ADB Connection

By default, Android 11+ wireless debugging assigns a random port each time wireless debugging is enabled or the device reconnects. To use static scripts or CI automation:

1. **Assign a Static IP**: Set a static DHCP lease/reservation for the Android device on your local router (e.g., `192.168.178.70`).
2. **First-time USB Setup**:
   - Connect the phone to the workstation via USB.
   - Run the following command to bind the ADB daemon on the phone to a fixed TCP port (default `5555`):
     ```bash
     adb tcpip 5555
     ```
3. **Connect Wirelessly**:
   - Disconnect the USB cable.
   - Connect wirelessly to the fixed port:
     ```bash
     adb connect 192.168.178.70:5555
     ```
   The device will now remain accessible at `192.168.178.70:5555` without dynamic port rotation as long as wireless debugging is not toggled off.

---

## 2. APK Compilation & Nextcloud Deployment Flow

To compile the application and upload it to Nextcloud, you can run the automated script or follow these steps:

```bash
# 1. Compile Debug APK
./gradlew assembleDebug -q

# 2. Deploy to Local Nextcloud Sync Folder
DEST_DIR="/home/schneider/nextcloud/Documents/Shared Documents/VibeVoiceBoard"
mkdir -p "$DEST_DIR"
cp app/build/outputs/apk/debug/VibeVoiceBoard_3.9-debug.apk "$DEST_DIR/"
```

---

## 3. Automated Installation via ADB (Conditional Check)

To install the APK automatically only when the device is available:

```bash
DEVICE_IP="192.168.178.70:5555"

# Query adb to see if the device is connected
if adb devices | grep -q "$DEVICE_IP.*device"; then
  echo "Device $DEVICE_IP is online. Installing APK..."
  adb -s "$DEVICE_IP" install -r app/build/outputs/apk/debug/VibeVoiceBoard_3.9-debug.apk
else
  echo "Device $DEVICE_IP is offline/unavailable. Skipping installation."
fi
```

---

## 4. Run the Automated Script

An automated script is available at `tools/build-and-deploy.sh`. It automatically:
1. Runs the Gradle build.
2. Locates the built APK dynamically.
3. Copies the APK to `/home/schneider/nextcloud/Documents/Shared Documents/VibeVoiceBoard`.
4. Installs the APK over wireless ADB if the phone is connected.

To run it:
```bash
./tools/build-and-deploy.sh
```
