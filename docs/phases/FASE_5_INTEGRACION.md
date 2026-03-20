# FASE 5: Integración Smali

**Duración:** Días 11-14
**Estado:** ⏳ Pendiente
**Dependencia:** FASE_3 + FASE_4 completadas

## Objetivo
Inyectar llamadas QNN en código Smali de GCam Arnova.

## 5.1 Archivos Smali a Crear

### QnnManager.smali
```smali
.class public Lai/qnn/QnnManager;
.super Ljava/lang/Object;

.field private static instance:Lai/qnn/QnnManager;
.field private initialized:Z

.method static constructor <clinit>()V
    .registers 1
    sget-object v0, Lai/qnn/QnnManager;->instance:Lai/qnn/QnnManager;
    if-nez v0, :already_init
    new-instance v0, Lai/qnn/QnnManager;
    invoke-direct {v0}, Lai/qnn/QnnManager;-><init>()V
    sput-object v0, Lai/qnn/QnnManager;->instance:Lai/qnn/QnnManager;
    :already_init
    return-void
.end method

.method public static getInstance()Lai/qnn/QnnManager;
    .registers 1
    sget-object v0, Lai/qnn/QnnManager;->instance:Lai/qnn/QnnManager;
    return-object v0
.end method

.method public native init()I
.end method

.method public native release()V
.end method

.method public native enhanceImage(Landroid/graphics/Bitmap;I)Landroid/graphics/Bitmap;
.end method

.method public native isNpuAvailable()Z
.end method
```

## 5.2 Puntos de Inyección

### CameraActivity.smali
```smali
# Buscar onCreate o similar
.method protected onCreate(Landroid/os/Bundle;)V
    # INYECTAR después de super.onCreate
    invoke-static {}, Lai/qnn/QnnManager;->getInstance()Lai/qnn/QnnManager;
    move-result-object v0
    invoke-virtual {v0}, Lai/qnn/QnnManager;->init()I
    # ... resto del código original
.end method
```

### ImageProcessor.smali
```smali
# Buscar método de procesamiento
.method public processImage(Landroid/graphics/Bitmap;)Landroid/graphics/Bitmap;
    .registers 4
    
    # INYECCIÓN: QNN enhancement antes del procesamiento original
    invoke-static {}, Lai/qnn/QnnManager;->getInstance()Lai/qnn/QnnManager;
    move-result-object v0
    
    # Mode 0 = night enhancement
    const/4 v1, 0x0
    invoke-virtual {v0, p1, v1}, Lai/qnn/QnnManager;->enhanceImage(Landroid/graphics/Bitmap;I)Landroid/graphics/Bitmap;
    move-result-object p1
    
    # Continuar con procesamiento original
    # ... código original de Arnova ...
    
    return-object p1
.end method
```

## 5.3 Modificar AndroidManifest.xml

```xml
<!-- Añadir permisos si necesario -->
<uses-permission android:name="android.permission.CAMERA"/>

<!-- Añadir native library -->
<application>
    <meta-data 
        android:name="qnn.enabled" 
        android:value="true"/>
</application>
```

## 5.4 Build Final

```bash
# 1. Copiar archivos
cp -r src/smali_qnn/* gcam_base/smali/

# 2. Copiar libs
cp lib/*.so gcam_base/lib/arm64-v8a/

# 3. Copiar modelos
mkdir -p gcam_base/assets/qnn_models/
cp models/*.dlc gcam_base/assets/qnn_models/

# 4. Rebuild
apktool b gcam_base/ -o QNN-GCam-unsigned.apk

# 5. Align
zipalign -v 4 QNN-GCam-unsigned.apk QNN-GCam-aligned.apk

# 6. Sign
apksigner sign \
    --ks release.keystore \
    --ks-key-alias gcam \
    --out QNN-GCam-v1.0.apk \
    QNN-GCam-aligned.apk
```

## 5.5 Testing

```bash
# Install
adb install -r QNN-GCam-v1.0.apk

# Check logs
adb logcat | grep -E "QNN|qnn_wrapper"

# Test funcionalidad
# 1. Abrir app
# 2. Tomar foto en modo nocturno
# 3. Verificar enhancement aplicado
```

## 5.6 Troubleshooting

| Error | Causa | Solución |
|-------|-------|----------|
| UnsatisfiedLinkError | .so no encontrado | Verificar path en lib/arm64-v8a |
| QNN init failed | DSP no disponible | Fallback a GPU backend |
| App crash on capture | Smali mal inyectado | Revisar registros y tipos |

## Entregables
- [ ] APK firmado funcional
- [ ] Logs de test exitoso
- [ ] Benchmark comparativo (antes/después)
