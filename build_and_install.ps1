# QNN-GCam Build and Install Script
# Usage: .\build_and_install.ps1

$SSH_KEY = "$env:USERPROFILE\.ssh\do_qnn_gcam"
$VM_HOST = "root@159.223.142.195"
$VM_PROJECT = "/opt/qnn-gcam-build"
$LOCAL_APK = ".\app-debug.apk"

Write-Host "=== QNN-GCam Build & Install ===" -ForegroundColor Cyan

# Step 1: Push local changes
Write-Host "`n[1/4] Pushing local changes..." -ForegroundColor Yellow
git add .
git commit -m "Auto-commit from build script" 2>$null
git push

# Step 2: Build on VM
Write-Host "`n[2/4] Building on VM..." -ForegroundColor Yellow
ssh -i $SSH_KEY $VM_HOST "cd $VM_PROJECT && git pull && export ANDROID_HOME=/opt/android-sdk && ./gradlew assembleDebug --no-daemon"

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

# Step 3: Download APK
Write-Host "`n[3/4] Downloading APK..." -ForegroundColor Yellow
scp -i $SSH_KEY "${VM_HOST}:${VM_PROJECT}/app/build/outputs/apk/debug/app-debug.apk" $LOCAL_APK

# Step 4: Install on device
Write-Host "`n[4/4] Installing on device..." -ForegroundColor Yellow
adb install -r $LOCAL_APK

Write-Host "`n=== Done! ===" -ForegroundColor Green
Write-Host "APK installed. Opening app..."
adb shell am start -n com.qnncamera/.MainActivity
