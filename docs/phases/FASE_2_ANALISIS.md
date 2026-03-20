# FASE 2: Análisis y Research

**Duración:** Días 2-3
**Estado:** ⏳ Pendiente
**Dependencia:** FASE_1 completada

## Objetivos

1. Entender estructura código Arnova
2. Identificar puntos de inyección
3. Investigar APIs QNN actualizadas

## 2.1 Análisis Código Arnova

### Estructura esperada (verificar):
```
Gcam_8.7.250.44/
├── smali/com/google/android/apps/camera/
│   ├── legacy/app/activity/main/
│   │   └── CameraActivity.smali      # PUNTO INYECCIÓN 1
│   └── processing/
│       └── ImageProcessor.smali       # PUNTO INYECCIÓN 2
├── lib/arm64-v8a/
│   └── libgcam.so
└── res/
```

### Archivos clave a analizar:
```bash
# Con JADX (solo lectura, más legible)
jadx-gui gcam_base/

# Buscar métodos de procesamiento
grep -r "processImage\|processHDR\|enhanceImage" smali/
```

## 2.2 Research APIs QNN 2026

### Queries prioritarias:
1. `"QNN API reference 2026 initialization"`
2. `"Hexagon HTP Android 14 15 integration"`
3. `"QAIRT breaking changes 2.35 2.36"`
4. `"QNN context creation best practices"`

### Documentación a obtener:
- [ ] QNN System Context API
- [ ] QNN Graph creation
- [ ] QNN Tensor operations
- [ ] QNN Backend selection (HTP vs GPU)

## 2.3 Mapeo Camera2 Pipeline

```
Camera2 Capture → YUV_420_888 → [QNN INJECT HERE] → JPEG Output
                      ↓
              ImageReader callback
                      ↓
              processImage() ← HOOK
```

### Puntos de intercepción:
1. **Pre-HDR:** Antes de procesamiento HDR+
2. **Post-capture:** Después de captura, antes de guardar
3. **Preview:** En tiempo real (más difícil)

## 2.4 Ejemplos Existentes

Buscar repositorios:
- GitHub: `"QNN Android camera example"`
- GitHub: `"Hexagon DSP image processing"`
- Qualcomm samples en SDK

## Entregables

- [ ] `docs/INJECTION_POINTS.md` - Puntos exactos con líneas
- [ ] `docs/QNN_API_NOTES.md` - APIs relevantes documentadas
- [ ] `docs/CAMERA_PIPELINE.md` - Flujo completo mapeado
