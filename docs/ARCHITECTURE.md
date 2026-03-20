# QNN-GCam Architecture (2026)

## Overview

Modern architecture using LiteRT in Google Play Services (replaces deprecated NNAPI).

## Module Structure

```
com.qnncamera/
├── MainActivity.kt          # UI only, delegates to modules
├── engine/
│   ├── DepthInferenceEngine.kt   # LiteRT inference with Play Services
│   └── BokehProcessor.kt         # RenderScript GPU blur
└── camera/
    └── CameraManager.kt          # CameraX management
```

## Key Technologies

### LiteRT in Play Services (NOT standalone TFLite)
```kotlin
// Correct dependencies (2026)
implementation("com.google.android.gms:play-services-tflite-java:16.4.0")
implementation("com.google.android.gms:play-services-tflite-gpu:16.4.0")
```

Benefits:
- Auto-updates via Play Services
- Better hardware acceleration support
- Smaller APK size
- No need for bundled native libs

### Acceleration Priority
1. **GPU** - Via Play Services TFLite delegate
2. **CPU (Play Services)** - Multi-threaded fallback
3. **CPU (Bundled)** - Last resort if Play Services unavailable

### Why NOT NNAPI
NNAPI was deprecated in Android 15. Google recommends:
- LiteRT in Play Services for custom models
- AICore for Gemini Nano foundation models

## Data Flow

```
Camera Frame → CameraManager → DepthInferenceEngine → BokehProcessor → Display
     ↓              ↓                    ↓                    ↓
  YUV420         Bitmap           FloatArray[256²]      Blurred Bitmap
```

## Device Compatibility

### Pixel OS on Qualcomm (like Redmi 9)
- Uses standard TFLite runtime from Play Services
- No TPU shim needed - MagicPortrait etc use `.tflite` models
- GPU delegate works via Adreno OpenCL

### Native Tensor Devices
- Can use AICore for Gemini Nano
- Edge TPU access via proprietary libs (not portable)

## Files Changed

| Old (Monolithic) | New (Modular) |
|------------------|---------------|
| MainActivity.kt (600+ lines) | MainActivity.kt (~100 lines) |
| - | engine/DepthInferenceEngine.kt |
| - | engine/BokehProcessor.kt |
| - | camera/CameraManager.kt |
