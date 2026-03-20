# PROGRESO - QNN-GCam

## Estado Actual
**Fecha:** 2026-03-20
**Fase:** 1 - Setup (90% completado)

## Log de Actividad

### 2026-03-20 - Inicio
- [x] Estructura de proyecto creada
- [x] Documentación de fases creada (5 fases)
- [x] Scripts de setup creados (vm_init.sh, build.sh, do_create_vm.ps1)
- [x] Código base JNI wrapper creado (qnn_wrapper.cpp)
- [x] Clase QnnManager.smali creada
- [x] Repo GCam Arnova clonado (14,372 archivos)
- [x] SDK versions verificadas (NDK r29, QAIRT 2.33.0, LiteRT QNN)
- [ ] VM DigitalOcean creada
- [ ] QAIRT SDK descargado manualmente

## Estructura GCam Clonada
```
gcam_base/
├── smali/           (9,580 items) - Código principal
├── smali_classes2/  (1,611 items) - Código secundario
├── smali_classes3/  (689 items)   - Código terciario
├── lib/             (17 items)    - Librerías nativas
├── res/             (2,040 items) - Recursos
└── assets/          (52 items)    - Assets
```

## Próximos Pasos
1. **USUARIO:** Crear VM en DigitalOcean (ver scripts/SETUP_VM.md)
2. **USUARIO:** Descargar QAIRT SDK de https://softwarecenter.qualcomm.com
3. Ejecutar vm_init.sh en la VM
4. Continuar con FASE_2 (Análisis)

## Decisión Arquitectural IMPORTANTE
**LiteRT QNN Accelerator** es ahora el método recomendado (reemplaza TFLite delegate):
- 100x speedup vs CPU
- 90 ops soportadas
- Workflow simplificado

## Bloqueos
- Ninguno actualmente

## Decisiones Tomadas
1. **VM specs:** CPU-Optimized 8 vCPUs, 16GB RAM ($96/mes)
2. **Target:** Snapdragon 685
3. **NDK:** r29 (stable)
4. **QAIRT:** 2.33.0
5. **Backend preferido:** Hexagon HTP (NPU)
6. **Fallback:** Adreno GPU → CPU
