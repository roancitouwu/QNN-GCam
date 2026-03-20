# QNN-GCam Development Workflow

## Setup (One-time)

### 1. SSH Key
Your SSH key is at: `~/.ssh/do_qnn_gcam`

### 2. VM Connection
```bash
ssh -i ~/.ssh/do_qnn_gcam root@159.223.142.195
```

## Daily Development

### Option A: Simple Build & Download

1. **Edit code locally** in `QNN-GCam/`

2. **Commit and push**:
```bash
git add .
git commit -m "your changes"
git push
```

3. **SSH to VM and build**:
```bash
ssh -i ~/.ssh/do_qnn_gcam root@159.223.142.195
cd /opt/qnn-gcam-build
./build.sh
```

4. **Download APK locally**:
```bash
scp -i ~/.ssh/do_qnn_gcam root@159.223.142.195:/opt/qnn-gcam-build/app/build/outputs/apk/debug/app-debug.apk ./QNN-Camera.apk
```

5. **Install on phone**:
```bash
adb install -r QNN-Camera.apk
```

### Option B: Remote ADB via Tunnel (Advanced)

This lets you install directly from VM to your phone.

1. **On your phone**: Enable wireless debugging (Developer Options > Wireless debugging)

2. **On your PC**: Connect phone via ADB wireless
```bash
adb pair <phone-ip>:<pairing-port>  # Use code shown on phone
adb connect <phone-ip>:5555
```

3. **Start ADB server to listen on all interfaces**:
```bash
adb -a nodaemon server start
```

4. **Setup Cloudflare tunnel** (on VM):
```bash
ssh -i ~/.ssh/do_qnn_gcam root@159.223.142.195
./setup_tunnel.sh
```

5. **Connect VM to your ADB** (on PC):
```bash
# Forward VM's ADB to your local ADB server
ssh -i ~/.ssh/do_qnn_gcam -R 5037:localhost:5037 root@159.223.142.195
```

6. **Build and install from VM**:
```bash
cd /opt/qnn-gcam-build
./build.sh install
```

## Quick Commands

### Build on VM
```bash
ssh -i ~/.ssh/do_qnn_gcam root@159.223.142.195 "cd /opt/qnn-gcam-build && git pull && ./gradlew assembleDebug --no-daemon"
```

### One-liner: Build + Download + Install
```powershell
# PowerShell
ssh -i $env:USERPROFILE\.ssh\do_qnn_gcam root@159.223.142.195 "cd /opt/qnn-gcam-build && git pull && ./gradlew assembleDebug --no-daemon"; scp -i $env:USERPROFILE\.ssh\do_qnn_gcam root@159.223.142.195:/opt/qnn-gcam-build/app/build/outputs/apk/debug/app-debug.apk .\app.apk; adb install -r .\app.apk
```

## Project Structure

```
QNN-GCam/
├── app/
│   ├── build.gradle.kts      # App dependencies
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/           # midas.tflite model (on VM)
│       ├── jniLibs/          # QNN libs (on VM)
│       └── java/com/qnncamera/
│           └── MainActivity.kt
├── build.gradle.kts          # Root gradle
├── settings.gradle.kts
└── scripts/
    ├── vm_build.sh
    └── DEV_WORKFLOW.md
```

## GitHub Repo
https://github.com/roancitouwu/QNN-GCam

## VM Paths
- Project: `/opt/qnn-gcam-build`
- Android SDK: `/opt/android-sdk`
- QNN Libs: `/opt/qnn-libs/`
- Models: `/opt/models/`
