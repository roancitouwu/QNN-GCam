# QNN-GCam: Google Camera con Aceleración NPU Qualcomm

## Objetivo
Integrar Qualcomm QNN (AI Engine) en GCam 8.7.250.44 (Arnova) para procesamiento IA en NPU/DSP.

## Target Hardware
- **SoC:** Snapdragon 685
- **DSP:** Hexagon (SNPE/QNN compatible)
- **GPU:** Adreno (fallback)
- **NPU:** Hexagon Tensor Processor

## Estructura
```
QNN-GCam/
├── docs/phases/          # Documentación por fases
├── scripts/              # Scripts de setup y build
├── src/
│   ├── jni/              # Código nativo C++ (QNN wrapper)
│   └── smali_qnn/        # Clases Smali para QNN
├── models/               # Modelos DLC convertidos
├── lib/                  # Librerías compiladas
└── gcam_base/            # Repo Arnova clonado
```

## Fases del Proyecto
1. **FASE_1:** Setup VM + Verificación versiones SDK
2. **FASE_2:** Análisis código Arnova + Research
3. **FASE_3:** Desarrollo JNI wrapper
4. **FASE_4:** Conversión modelos a DLC
5. **FASE_5:** Integración Smali + Build final

## Quick Start
```bash
# 1. Setup VM DigitalOcean
./scripts/do_setup.sh

# 2. En la VM, ejecutar setup
./scripts/vm_init.sh

# 3. Build
./scripts/build.sh
```

## Métricas Objetivo
- Latencia inference: < 10ms/frame
- PSNR nocturno: +3dB vs original
- Batería: < 15% incremento
- Compatibilidad: 90%+ Snapdragon 6xx/7xx/8xx
