# FASE 4: Conversión de Modelos

**Duración:** Días 8-10
**Estado:** ⏳ Pendiente
**Dependencia:** FASE_3 completada

## Objetivo
Obtener modelos optimizados en formato DLC para Snapdragon 685.

## 4.1 Modelos Target

| Modelo | Uso | Tamaño Max | Latencia Target |
|--------|-----|------------|-----------------|
| night_enhance.dlc | Low-light | 5MB | <8ms |
| portrait_seg.dlc | Segmentación | 3MB | <5ms |
| super_res.dlc | Upscaling | 8MB | <15ms |
| scene_detect.dlc | Clasificación | 2MB | <3ms |

## 4.2 Fuentes de Modelos

### Qualcomm AI Hub (preferido)
```
URL: https://aihub.qualcomm.com
- Modelos ya optimizados para Snapdragon
- Formato: DLC listo
- Buscar: "image enhancement", "low light"
```

### Conversión desde otros formatos

**PyTorch → ONNX → DLC:**
```bash
# 1. Export PyTorch a ONNX
torch.onnx.export(model, dummy_input, "model.onnx")

# 2. ONNX a DLC (usar QAIRT converter)
qnn-onnx-converter \
    --input_network model.onnx \
    --output_path model.dlc \
    --input_dim input 1,3,224,224
```

**TFLite → DLC:**
```bash
qnn-tflite-converter \
    --input_network model.tflite \
    --output_path model.dlc
```

## 4.3 Quantización

```bash
# INT8 quantization (mejor rendimiento en NPU)
qnn-net-run \
    --model model.dlc \
    --input_list calibration_inputs.txt \
    --output_path model_quantized.dlc \
    --quantization_overrides quantization_config.json
```

**Config quantización:**
```json
{
  "activation_encodings": {
    "encoding": "int8",
    "symmetric": false
  },
  "param_encodings": {
    "encoding": "int8"
  }
}
```

## 4.4 Optimización SD685

```bash
# Verificar compatibilidad HTP
qnn-profile \
    --model model.dlc \
    --backend libQnnHtp.so \
    --output_path profile_report.json

# Si hay ops no soportadas, ver fallback a GPU
qnn-net-run --backend libQnnGpu.so ...
```

## 4.5 Modelos Candidatos

### Low-light Enhancement
- Zero-DCE++ (lightweight)
- EnlightenGAN mobile
- SCI (Self-Calibrated Illumination)

### Portrait Segmentation  
- DeepLabV3+ MobileNetV3
- BiSeNet V2
- PP-LiteSeg

### Super Resolution
- Real-ESRGAN mobile
- FSRCNN
- ESPCN

## 4.6 Benchmark

```bash
# Test latencia en dispositivo
adb push model.dlc /data/local/tmp/
adb shell /data/local/tmp/qnn_benchmark \
    --model /data/local/tmp/model.dlc \
    --iterations 100
```

**Formato resultado:**
```
Model: night_enhance.dlc
Backend: HTP
Avg latency: 7.2ms
P95 latency: 9.1ms
Memory: 12MB
```

## Entregables
- [ ] `models/night_enhance.dlc`
- [ ] `models/portrait_seg.dlc`
- [ ] `docs/MODEL_BENCHMARKS.md`
