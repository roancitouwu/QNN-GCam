# Guía Rápida: Crear VM DigitalOcean

## Opción 1: Script PowerShell (Recomendado)

```powershell
# 1. Obtén tu token de DigitalOcean:
#    https://cloud.digitalocean.com/account/api/tokens

# 2. Ejecuta el script:
.\scripts\do_create_vm.ps1 -DOToken "tu_token_aqui"
```

## Opción 2: Manual via Dashboard

1. Ve a https://cloud.digitalocean.com/droplets/new
2. Configura:
   - **Image:** Ubuntu 24.04 LTS
   - **Plan:** CPU-Optimized → 8 vCPUs / 16GB RAM ($96/mes)
   - **Region:** NYC1 o el más cercano
   - **SSH Key:** Selecciona tu key existente
   - **Hostname:** qnn-gcam-build

3. Click "Create Droplet"

## Opción 3: doctl CLI

```bash
# Instalar doctl si no está instalado
# https://docs.digitalocean.com/reference/doctl/how-to/install/

doctl auth init  # Login con tu token

doctl compute droplet create qnn-gcam-build \
    --region nyc1 \
    --size c-8 \
    --image ubuntu-24-04-x64 \
    --ssh-keys $(doctl compute ssh-key list --format ID --no-header | head -1) \
    --wait
```

## Post-Creación

Una vez creado el droplet:

```bash
# 1. Conectar via SSH
ssh root@<DROPLET_IP>

# 2. Descargar scripts de setup
curl -L https://raw.githubusercontent.com/<tu-repo>/main/scripts/vm_init.sh -o vm_init.sh
chmod +x vm_init.sh

# 3. Ejecutar setup
./vm_init.sh
```

## Costos Estimados

| Configuración | Precio | Recomendación |
|---------------|--------|---------------|
| c-8 (8 vCPUs, 16GB) | $96/mes | ✅ Recomendado |
| c-16 (16 vCPUs, 32GB) | $192/mes | Para builds muy pesados |
| c-4 (4 vCPUs, 8GB) | $48/mes | Mínimo funcional |

**Tip:** Puedes destruir el droplet cuando no lo uses para ahorrar costos.
Comando: `doctl compute droplet delete qnn-gcam-build --force`
