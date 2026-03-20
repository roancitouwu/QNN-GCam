#!/bin/bash
# Build Script - Compila QNN-GCam APK
set -e

WORK_DIR="/opt/qnn-gcam"
PROJECT_DIR="$WORK_DIR/project"
OUTPUT_DIR="$WORK_DIR/output"
VERSION="1.0.0"
DATE=$(date +%Y%m%d)

echo "=== QNN-GCam Build ==="
echo "Version: $VERSION"
echo "Date: $DATE"

# Verificar entorno
source /etc/profile.d/qnn-gcam.sh 2>/dev/null || true

# 1. Build JNI wrapper
echo "[1/5] Compilando JNI wrapper..."
cd $PROJECT_DIR/src/jni
$ANDROID_NDK/ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk APP_ABI=arm64-v8a
cp -r libs/arm64-v8a/*.so $PROJECT_DIR/lib/

# 2. Preparar GCam base
echo "[2/5] Preparando GCam base..."
cd $WORK_DIR
rm -rf gcam_modified
cp -r gcam_base gcam_modified

# 3. Inyectar código QNN
echo "[3/5] Inyectando código QNN..."
# Copiar smali
cp -r $PROJECT_DIR/src/smali_qnn/* gcam_modified/smali/

# Copiar libs nativas
mkdir -p gcam_modified/lib/arm64-v8a
cp $PROJECT_DIR/lib/*.so gcam_modified/lib/arm64-v8a/

# Copiar modelos
mkdir -p gcam_modified/assets/qnn_models
cp $PROJECT_DIR/models/*.dlc gcam_modified/assets/qnn_models/ 2>/dev/null || echo "No models yet"

# 4. Rebuild APK
echo "[4/5] Rebuilding APK..."
cd $WORK_DIR
apktool b gcam_modified -o $OUTPUT_DIR/QNN-GCam-unsigned.apk

# Align
zipalign -f 4 $OUTPUT_DIR/QNN-GCam-unsigned.apk $OUTPUT_DIR/QNN-GCam-aligned.apk

# 5. Sign
echo "[5/5] Firmando APK..."
apksigner sign \
    --ks $WORK_DIR/debug.keystore \
    --ks-key-alias gcam \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out $OUTPUT_DIR/QNN-GCam-${VERSION}-${DATE}.apk \
    $OUTPUT_DIR/QNN-GCam-aligned.apk

# Cleanup
rm -f $OUTPUT_DIR/QNN-GCam-unsigned.apk $OUTPUT_DIR/QNN-GCam-aligned.apk

echo ""
echo "=== BUILD COMPLETADO ==="
echo "APK: $OUTPUT_DIR/QNN-GCam-${VERSION}-${DATE}.apk"
echo "Size: $(du -h $OUTPUT_DIR/QNN-GCam-${VERSION}-${DATE}.apk | cut -f1)"
