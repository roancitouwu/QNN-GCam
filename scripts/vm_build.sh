#!/bin/bash
# QNN-GCam Build Script
# Usage: ./vm_build.sh [install]

set -e
cd /opt/qnn-gcam-build

echo '=== Pulling latest changes ==='
git pull

echo '=== Building APK ==='
export ANDROID_HOME=/opt/android-sdk
./gradlew assembleDebug --no-daemon

APK_PATH=app/build/outputs/apk/debug/app-debug.apk
echo ''
echo '=== Build Complete ==='
echo "APK: $APK_PATH"
ls -lh $APK_PATH

if [ "$1" == "install" ]; then
    echo ''
    echo '=== Installing via ADB ==='
    adb install -r $APK_PATH
fi
