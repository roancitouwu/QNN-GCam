# FASE 1: Setup y Verificación

**Duración:** Día 1
**Estado:** 🔄 En progreso

## Checklist

### 1.1 DigitalOcean VM
- [ ] Crear droplet (ver `scripts/do_setup.sh`)
- [ ] Configurar SSH
- [ ] Instalar dependencias base

**Especificaciones VM:**
```
Tipo: CPU-Optimized
vCPUs: 8
RAM: 16GB (32GB si presupuesto permite)
Storage: 100GB NVMe SSD
OS: Ubuntu 24.04 LTS
Región: NYC1 (menor latencia)
```

### 1.2 Verificar Versiones SDK (CRÍTICO)

| Herramienta | Versión Base | Buscar Actualización |
|-------------|--------------|----------------------|
| QAIRT SDK | 2.35 | "QAIRT 2.36 2.37 2026" |
| Android NDK | r25c | "NDK r26 r27 2026" |
| LiteRT | 2.x | "LiteRT latest 2026" |
| Hexagon SDK | 5.x | "Hexagon SDK 6.x 2026" |
| APKTool | 2.9.x | "APKTool latest" |

### 1.3 Descargas Necesarias

```bash
# QAIRT SDK
# URL: https://softwarecenter.qualcomm.com
# Buscar: Qualcomm AI Runtime SDK

# Android NDK
# URL: https://developer.android.com/ndk/downloads
# Descargar: Latest stable (r26+)

# Hexagon SDK  
# URL: https://developer.qualcomm.com/software/hexagon-dsp-sdk
```

### 1.4 Test Compilación Base

```bash
# Clonar Arnova
git clone https://github.com/Arnova8G2/Gcam_8.7.250.44.git gcam_base

# Instalar APKTool
apt install apktool

# Decompile
apktool d original.apk -o decompiled/

# Recompile (sin cambios)
apktool b decompiled/ -o test_rebuild.apk

# Sign
apksigner sign --ks debug.keystore test_rebuild.apk
```

## Comandos VM

```bash
# Ejecutar setup completo
./scripts/vm_init.sh

# Solo verificar versiones
./scripts/check_versions.sh
```

## Criterios de Éxito
- [x] VM operativa con SSH
- [ ] Todas las SDK descargadas
- [ ] APK base recompila sin errores
- [ ] Versiones documentadas en `VERSION_LOCK.md`
