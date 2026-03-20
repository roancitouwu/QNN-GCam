#!/bin/bash
# Verificación de versiones instaladas
echo "=== QNN-GCam Environment Check ==="
echo "Date: $(date)"
echo ""

# Java
echo "--- JAVA ---"
java -version 2>&1 | head -3
echo ""

# Android NDK
echo "--- ANDROID NDK ---"
if [ -f "$ANDROID_NDK/source.properties" ]; then
    cat $ANDROID_NDK/source.properties
else
    echo "NDK not found at \$ANDROID_NDK"
fi
echo ""

# QAIRT SDK
echo "--- QAIRT SDK ---"
if [ -d "$QAIRT_SDK" ]; then
    ls -la $QAIRT_SDK/ 2>/dev/null | head -5
    cat $QAIRT_SDK/version.txt 2>/dev/null || echo "Version file not found"
else
    echo "QAIRT SDK not installed at \$QAIRT_SDK"
fi
echo ""

# APKTool
echo "--- APKTOOL ---"
apktool --version 2>/dev/null || echo "APKTool not installed"
echo ""

# Python
echo "--- PYTHON ---"
python3 --version
pip3 --version
echo ""

# Disk space
echo "--- DISK SPACE ---"
df -h /opt 2>/dev/null || df -h /
echo ""

# Memory
echo "--- MEMORY ---"
free -h
echo ""

echo "=== Check Complete ==="
