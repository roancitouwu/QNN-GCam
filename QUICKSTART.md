# QNN-GCam - Quick Start

## Estado del Proyecto
✅ **Estructura creada**
✅ **GCam Arnova clonado** (14,372 archivos)
✅ **SDK versions verificadas** (NDK r29, QAIRT 2.33.0)
✅ **Código base JNI/Smali creado**
⏳ **VM DigitalOcean** (pendiente tu acción)

---

## Paso 1: Crear VM DigitalOcean

### Opción A: PowerShell Script
```powershell
cd QNN-GCam\scripts

# Necesitas tu token de DO: https://cloud.digitalocean.com/account/api/tokens
.\do_create_vm.ps1 -DOToken "dop_v1_tu_token_aqui"
```

### Opción B: Dashboard Manual
1. https://cloud.digitalocean.com/droplets/new
2. Ubuntu 24.04 → CPU-Optimized 8 vCPUs ($96/mes) → NYC1 → Create

---

## Paso 2: Configurar VM

```bash
# Conectar
ssh root@<IP_DEL_DROPLET>

# Clonar este proyecto en la VM
git clone https://github.com/TU_USUARIO/QNN-GCam.git /opt/qnn-gcam/project

# Ejecutar setup
cd /opt/qnn-gcam/project/scripts
chmod +x *.sh
./vm_init.sh
```

---

## Paso 3: Descargar QAIRT SDK (Manual)

1. Ve a: https://softwarecenter.qualcomm.com
2. Busca: "Qualcomm AI Runtime SDK"
3. Descarga versión 2.33.0 o superior
4. Sube a la VM: `scp qairt-sdk.zip root@<IP>:/opt/qnn-gcam/qairt/`
5. Descomprime en la VM

---

## Paso 4: Build Test

```bash
# En la VM
cd /opt/qnn-gcam/project
./scripts/build.sh
```

---

## Estructura del Proyecto

```
QNN-GCam/
├── docs/
│   ├── phases/           # Documentación por fases
│   │   ├── FASE_1_SETUP.md
│   │   ├── FASE_2_ANALISIS.md
│   │   ├── FASE_3_JNI.md
│   │   ├── FASE_4_MODELOS.md
│   │   └── FASE_5_INTEGRACION.md
│   ├── PROGRESO.md       # Estado actual
│   └── VERSION_LOCK.md   # Versiones SDK
├── gcam_base/            # GCam Arnova clonado
├── scripts/              # Scripts de setup/build
├── src/
│   ├── jni/              # Código C++ (QNN wrapper)
│   └── smali_qnn/        # Clases Smali
├── models/               # Modelos DLC (vacío)
└── lib/                  # Libs compiladas (vacío)
```

---

## Versiones Verificadas (Marzo 2026)

| Componente | Versión |
|------------|---------|
| Android NDK | r29 (stable) |
| QAIRT SDK | 2.33.0 |
| LiteRT QNN | Latest |
| Ubuntu | 24.04 LTS |

---

## Contacto / Issues

Si encuentras problemas, documenta en `docs/PROGRESO.md`
