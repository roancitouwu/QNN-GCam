# VERSION LOCK - QNN-GCam

**Última actualización:** 2026-03-20
**Estado:** ✅ Verificado

## Versiones Confirmadas

| Componente | Versión | Estado | Fecha Verificación |
|------------|---------|--------|-------------------|
| QAIRT SDK | 2.33.0 | ✅ Latest | 2026-03-20 |
| Android NDK | r29 (stable) / r27d (LTS) | ✅ Verificado | 2026-03-20 |
| LiteRT QNN | Latest (reemplaza TFLite delegate) | ✅ Verificado | 2026-03-20 |
| Hexagon SDK | Incluido en QAIRT | ✅ | 2026-03-20 |
| Ubuntu | 24.04 LTS | ✅ | 2026-03-20 |
| Java | 17 | ✅ | 2026-03-20 |

## URLs de Descarga

### QAIRT SDK
```
URL: https://softwarecenter.qualcomm.com
Producto: Qualcomm AI Runtime SDK
Versión: 2.33.0 (latest), 2.32.6 (default en AI Hub)
```

### Android NDK
```
URL: https://developer.android.com/ndk/downloads
Versión LTS: r27d (27.3.13750724)
Versión Stable: r29 (29.0.14206865)
Beta: r30 beta 1
RECOMENDADO: r29 (stable)
```

### LiteRT QNN Accelerator (NUEVO - reemplaza TFLite delegate)
```
GitHub: https://github.com/google-ai-edge/LiteRT
Modelos pre-entrenados: https://aihub.qualcomm.com/models?runtime=tflite
Docs: https://ai.google.dev/edge/litert
```

## Breaking Changes Importantes

### LiteRT vs TFLite QNN Delegate
- **TFLite QNN delegate está DEPRECADO**
- Usar nuevo **LiteRT QNN Accelerator** en su lugar
- Ventajas:
  - No necesitas interactuar con SDKs vendor-specific
  - Abstrae fragmentación entre SoCs
  - 100x speedup vs CPU, 10x vs GPU
  - Soporta 90 ops LiteRT
  - 64/72 modelos benchmark delegan completamente a NPU

### Nuevo Workflow (3 pasos)
```python
# 1. AOT Compilation (opcional pero recomendado)
from ai_edge_litert.aot import aot_compile as aot_lib
from ai_edge_litert.aot.vendors.qualcomm import target as qnn_target

# Compilar para Snapdragon específico
sm8850_target = qnn_target.Target(qnn_target.SocModel.SM8850)
compiled_models = aot_lib.aot_compile(tflite_model_path, target=[sm8850_target])

# 2. Deploy via Google Play AI Pack
from ai_edge_litert.aot.ai_pack import export_lib as ai_pack_export
ai_pack_export.export(compiled_models, ai_pack_dir, ai_pack_name, litert_model_name)

# 3. Runtime en Android
# Usar LiteRT Interpreter con QNN backend
```

## Notas de Compatibilidad

- Target: Android 14+ (API 34+)
- ABI: arm64-v8a
- Min SDK: 30
- Target SDK: 34
- SoCs soportados: Snapdragon 8 Elite, 8 Gen 3, 8 Gen 2, 7+ Gen series, 6xx series

## Decisión Arquitectural

**CAMBIO DE ESTRATEGIA:** Dado que LiteRT QNN Accelerator es el nuevo estándar oficial:

1. **Opción A (Recomendada):** Usar LiteRT + .tflite models
   - Workflow más simple
   - Modelos pre-optimizados en AI Hub
   - Mejor mantenibilidad

2. **Opción B:** QNN SDK directo con DLC
   - Más control pero más complejo
   - Requiere conversión manual
   - Útil si necesitas ops custom
