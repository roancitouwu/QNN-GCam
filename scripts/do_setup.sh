#!/bin/bash
# DigitalOcean VM Setup Script
# Ejecutar desde máquina local con doctl configurado

set -e

# Configuración
DROPLET_NAME="qnn-gcam-build"
REGION="nyc1"
SIZE="c-8"  # CPU-Optimized 8 vCPUs, 16GB RAM
IMAGE="ubuntu-24-04-x64"
SSH_KEY_NAME="default"  # Cambiar según tu key

echo "=== QNN-GCam Build Server Setup ==="

# 1. Crear droplet
echo "[1/4] Creando droplet..."
doctl compute droplet create $DROPLET_NAME \
    --region $REGION \
    --size $SIZE \
    --image $IMAGE \
    --ssh-keys $(doctl compute ssh-key list --format ID --no-header | head -1) \
    --wait

# 2. Obtener IP
echo "[2/4] Obteniendo IP..."
DROPLET_IP=$(doctl compute droplet get $DROPLET_NAME --format PublicIPv4 --no-header)
echo "IP: $DROPLET_IP"

# 3. Esperar SSH disponible
echo "[3/4] Esperando SSH..."
sleep 30
until ssh -o ConnectTimeout=5 -o StrictHostKeyChecking=no root@$DROPLET_IP "echo ready" 2>/dev/null; do
    echo "  Esperando..."
    sleep 10
done

# 4. Copiar script de inicialización
echo "[4/4] Copiando scripts..."
scp -o StrictHostKeyChecking=no scripts/vm_init.sh root@$DROPLET_IP:/root/
ssh root@$DROPLET_IP "chmod +x /root/vm_init.sh"

echo ""
echo "=== DROPLET CREADO ==="
echo "IP: $DROPLET_IP"
echo "SSH: ssh root@$DROPLET_IP"
echo ""
echo "Siguiente paso:"
echo "  ssh root@$DROPLET_IP"
echo "  ./vm_init.sh"
