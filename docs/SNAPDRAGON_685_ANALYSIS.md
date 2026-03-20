# Análisis: Snapdragon 685 + QNN para Video/Cámara

## Especificaciones del SoC

| Componente | Detalle |
|------------|---------|
| **CPU** | Kryo 265 (8 núcleos @ 2.8GHz) |
| **GPU** | Adreno 610 |
| **DSP** | Hexagon 686 |
| **ISP** | Spectra 346T (Triple ISP) |
| **AI Engine** | 6th Gen (7 TOPS estimado) |
| **Proceso** | 11nm |

## Capacidades DSP/NPU Relevantes

### Hexagon 686 DSP
- **Vector Extensions (HVX):** 1024-bit SIMD
- **Soporta:** INT8, INT16, FP16 quantization
- **Ideal para:** Procesamiento de imagen en tiempo real

### Triple ISP (Spectra 346T)
- Hasta 64MP single / 25+16MP dual
- HDR10+ video capture
- Multi-frame noise reduction (MFNR)
- Zero Shutter Lag (ZSL)

## Optimizaciones Posibles para Tu Dispositivo

### 1. Video Enhancement (Alta Prioridad)
```
Pipeline propuesto:
Camera2 → YUV_420_888 → [QNN: Denoise + Enhance] → Encoder → MP4

Modelos recomendados:
- Video denoising temporal (frame stacking)
- Super-resolution ligera (1.5x upscale)
- HDR tone mapping en tiempo real
```

**Beneficio esperado:** 
- Reducción de ruido en low-light: 40-60%
- Mejor detalle en sombras
- Latencia: ~8-12ms por frame (viable para 30fps)

### 2. Macro Blur con AI (Depth Estimation)
```
Pipeline propuesto:
Macro Lens → Frame → [QNN: MiDaS/DPT depth] → Depth Map → Blur Shader → Output

Modelos candidatos:
- MiDaS v3.1 Small (optimizado móvil)
- DPT-Hybrid Lite
- FastDepth (MIT, muy ligero)
```

**Ventaja del macro:**
- Mayor rango de profundidad visible
- Depth estimation más precisa en distancias cortas
- Blur más natural que portrait mode estándar

### 3. Scene Detection para Auto-Config
```
MobileNetV3-Small → Scene Classification → Auto-ajustes

Escenas detectables:
- Low-light → Aumentar ISO virtual, denoise agresivo
- Macro/Close-up → Activar depth blur
- Landscape → HDR, saturación
- Portrait → Segmentación + blur
- Document → Sharpening, contrast
```

### 4. Estabilización AI (Video)
```
Gyro data + Frame analysis → [QNN: Motion estimation] → Warp Transform

Modelo: PWC-Net Lite o RAFT-Small
Beneficio: Estabilización más suave que EIS nativo
```

## Comparación: APKTool vs Gradle/SDK

| Aspecto | APKTool | Gradle + Android SDK |
|---------|---------|---------------------|
| **Uso** | Modificar APKs existentes | Desarrollo desde código fuente |
| **NDK/JNI** | ❌ No puede compilar | ✅ Soporta CMake/ndk-build |
| **QNN libs** | Solo copiar .so | ✅ Linkeo correcto con CMake |
| **Debugging** | ❌ Sin símbolos | ✅ Completo con AS |
| **Optimización** | ❌ Limitada | ✅ ProGuard, R8, etc |
| **Resultado** | Mod de app existente | App nativa optimizada |

**Conclusión:** Para QNN necesitamos Gradle obligatoriamente.

## Plan de Integración con Edge Impulse Repo

### Estructura Base (de qnn-hardware-acceleration)
```
app/
├── src/main/
│   ├── cpp/
│   │   ├── CMakeLists.txt       # Build nativo
│   │   ├── native-lib.cpp       # JNI bridge
│   │   ├── edge-impulse-sdk/    # SDK de EI
│   │   └── tflite-model/        # Modelos .tflite
│   ├── java/.../
│   │   └── MainActivity.kt      # Camera2 + preview
│   └── jniLibs/arm64-v8a/
│       ├── libQnnHtp.so         # QNN runtime
│       └── libQnnTFLiteDelegate.so
└── build.gradle.kts
```

### Adaptación para Nuestro Caso
1. **Reemplazar modelo EI** → Modelos de video enhancement
2. **Modificar pipeline** → Procesar frames de video, no solo fotos
3. **Agregar recording** → MediaCodec + Surface
4. **Macro blur** → Agregar MiDaS depth + shader de blur

## Modelos Recomendados (Qualcomm AI Hub)

| Modelo | Tamaño | Latencia Est. | Uso |
|--------|--------|---------------|-----|
| `zero-dce-lite` | 2.5MB | 6ms | Low-light enhance |
| `midas-v3-small` | 8MB | 15ms | Depth estimation |
| `mobilenet-v3-small` | 2.2MB | 3ms | Scene detection |
| `realsr-mobile` | 4MB | 20ms | Super-resolution |

**URLs:**
- https://aihub.qualcomm.com/models?runtime=tflite
- https://github.com/isl-org/MiDaS (depth)
- https://github.com/Li-Chongyi/Zero-DCE (low-light)

## Manifest Requerido para QNN

```xml
<application android:extractNativeLibs="true">
    <!-- Acceso a DSP -->
    <uses-native-library 
        android:name="libcdsprpc.so" 
        android:required="false"/>
</application>

<!-- Permisos -->
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>

<!-- Features -->
<uses-feature android:name="android.hardware.camera2.full"/>
```

## Variables de Entorno (Runtime)

```kotlin
// En Application.onCreate() o MainActivity
Os.setenv("ADSP_LIBRARY_PATH", 
    "${applicationInfo.nativeLibraryDir}:/system/lib64:/vendor/lib64/dsp", 
    true)
Os.setenv("LD_LIBRARY_PATH", 
    applicationInfo.nativeLibraryDir, 
    true)
```

## Próximos Pasos Técnicos

1. **Compilar proyecto Edge Impulse** en VM con Android SDK
2. **Descargar QNN libs** del Qualcomm SDK (requiere cuenta)
3. **Reemplazar modelo** por uno de video enhancement
4. **Adaptar Camera2 pipeline** para video recording
5. **Agregar MiDaS** para macro depth blur
6. **Benchmark** en dispositivo real

## Recursos

- [Edge Impulse QNN](https://github.com/edgeimpulse/qnn-hardware-acceleration)
- [Qualcomm AI Hub](https://aihub.qualcomm.com)
- [MiDaS Depth](https://github.com/isl-org/MiDaS)
- [Zero-DCE++](https://github.com/Li-Chongyi/Zero-DCE_extension)
- [LiteRT QNN Accelerator](https://ai.google.dev/edge/litert)
