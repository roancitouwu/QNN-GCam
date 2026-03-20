#!/bin/bash
# VM Initialization Script - Ejecutar en el droplet
set -e

echo "=== QNN-GCam Build Environment Setup ==="
echo "Started: $(date)"

# Variables
WORK_DIR="/opt/qnn-gcam"
NDK_VERSION="r29"
JAVA_VERSION="17"

# 1. Update system
echo "[1/8] Actualizando sistema..."
apt update && apt upgrade -y

# 2. Instalar dependencias base
echo "[2/8] Instalando dependencias..."
apt install -y \
    git \
    wget \
    curl \
    unzip \
    zip \
    build-essential \
    cmake \
    ninja-build \
    python3 \
    python3-pip \
    python3-venv \
    openjdk-${JAVA_VERSION}-jdk \
    android-sdk \
    apktool \
    zipalign \
    adb

# 3. Crear directorio de trabajo
echo "[3/8] Creando estructura..."
mkdir -p $WORK_DIR/{sdk,ndk,qairt,gcam_base,output}
cd $WORK_DIR

# 4. Descargar Android NDK
echo "[4/8] Descargando Android NDK ${NDK_VERSION}..."
cd $WORK_DIR/ndk
# NDK r29 uses different naming convention
wget -q https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip || \
    wget -q https://dl.google.com/android/repository/android-ndk-r29-linux.zip
unzip -q android-ndk-*-linux.zip
rm -f android-ndk-*-linux.zip

# 5. Configurar variables de entorno
echo "[5/8] Configurando entorno..."
cat >> /etc/profile.d/qnn-gcam.sh << 'EOF'
export ANDROID_NDK=/opt/qnn-gcam/ndk/android-ndk-r29
export ANDROID_HOME=/opt/qnn-gcam/sdk
export QAIRT_SDK=/opt/qnn-gcam/qairt
export PATH=$PATH:$ANDROID_NDK:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
EOF
source /etc/profile.d/qnn-gcam.sh

# 6. Clonar repositorio GCam
echo "[6/8] Clonando GCam Arnova..."
cd $WORK_DIR
git clone https://github.com/Arnova8G2/Gcam_8.7.250.44.git gcam_base || echo "Clone may have failed, check manually"

# 7. Crear keystore de desarrollo
echo "[7/8] Creando keystore..."
keytool -genkey -v \
    -keystore $WORK_DIR/debug.keystore \
    -alias gcam \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass android \
    -keypass android \
    -dname "CN=QNN-GCam, OU=Dev, O=Dev, L=Unknown, ST=Unknown, C=US"

# 8. Verificar instalación
echo "[8/8] Verificando instalación..."
echo ""
echo "=== VERSIONES INSTALADAS ==="
echo "Java: $(java -version 2>&1 | head -1)"
echo "NDK: $(cat $ANDROID_NDK/source.properties | grep Pkg.Revision)"
echo "APKTool: $(apktool --version 2>/dev/null || echo 'check manually')"
echo "Python: $(python3 --version)"
echo ""

# Resumen
echo "=== SETUP COMPLETADO ==="
echo "Directorio de trabajo: $WORK_DIR"
echo ""
echo "PENDIENTE MANUAL:"
echo "1. Descargar QAIRT SDK de https://softwarecenter.qualcomm.com"
echo "   Colocar en: $WORK_DIR/qairt/"
echo ""
echo "2. Clonar proyecto QNN-GCam:"
echo "   git clone <tu-repo> $WORK_DIR/project"
echo ""
echo "Finished: $(date)"
