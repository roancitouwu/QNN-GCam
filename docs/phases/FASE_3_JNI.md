# FASE 3: Desarrollo JNI Wrapper

**Duración:** Días 4-7
**Estado:** ⏳ Pendiente
**Dependencia:** FASE_2 completada

## Objetivo
Crear libqnn_wrapper.so que exponga QNN a Java/Smali via JNI.

## 3.1 Estructura JNI

```
src/jni/
├── Android.mk
├── Application.mk
├── qnn_wrapper.cpp        # Implementación principal
├── qnn_wrapper.h
├── image_utils.cpp        # Conversiones YUV/RGB
└── include/
    └── QNN headers del SDK
```

## 3.2 API Wrapper

```cpp
// qnn_wrapper.h
extern "C" {
    // Inicialización
    JNIEXPORT jint JNICALL
    Java_ai_qnn_QnnManager_init(JNIEnv* env, jobject obj);
    
    // Cleanup
    JNIEXPORT void JNICALL
    Java_ai_qnn_QnnManager_release(JNIEnv* env, jobject obj);
    
    // Procesamiento principal
    JNIEXPORT jobject JNICALL
    Java_ai_qnn_QnnManager_enhanceImage(
        JNIEnv* env, 
        jobject obj,
        jobject bitmap,      // Input Bitmap
        jint mode            // 0=night, 1=portrait, 2=hdr
    );
    
    // Info
    JNIEXPORT jboolean JNICALL
    Java_ai_qnn_QnnManager_isNpuAvailable(JNIEnv* env, jobject obj);
}
```

## 3.3 Flujo QNN Interno

```cpp
// Pseudocódigo
bool QnnWrapper::init() {
    // 1. Load QNN backend
    QnnInterface_getProviders(&providers, &numProviders);
    
    // 2. Create context (HTP preferido)
    QnnContext_create(backend, nullptr, &context);
    
    // 3. Load model from DLC
    QnnModel_create(context, modelPath, &model);
    
    return true;
}

Bitmap QnnWrapper::enhance(Bitmap input, int mode) {
    // 1. Convert Bitmap → QNN Tensor
    QnnTensor inputTensor = bitmapToTensor(input);
    
    // 2. Execute graph
    QnnGraph_execute(graph, &inputTensor, 1, &outputTensor, 1);
    
    // 3. Convert back
    return tensorToBitmap(outputTensor);
}
```

## 3.4 Android.mk

```makefile
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := qnn_wrapper
LOCAL_SRC_FILES := qnn_wrapper.cpp image_utils.cpp

# QNN SDK paths (ajustar según versión)
QNN_SDK := /opt/qnn/2.36
LOCAL_C_INCLUDES := $(QNN_SDK)/include
LOCAL_LDLIBS := -llog -ljnigraphics
LOCAL_SHARED_LIBRARIES := QnnHtp QnnSystem

include $(BUILD_SHARED_LIBRARY)
```

## 3.5 Compilación

```bash
# Configurar NDK
export ANDROID_NDK=/opt/android-ndk-r26b
export PATH=$PATH:$ANDROID_NDK

# Build
cd src/jni
$ANDROID_NDK/ndk-build NDK_PROJECT_PATH=. APP_BUILD_SCRIPT=Android.mk

# Output: libs/arm64-v8a/libqnn_wrapper.so
```

## 3.6 Test Unitario

```bash
# Push a dispositivo
adb push libs/arm64-v8a/libqnn_wrapper.so /data/local/tmp/

# Test con app simple (crear test_qnn.apk)
adb install test_qnn.apk
adb shell am start -n com.test.qnn/.MainActivity
```

## Criterios de Éxito
- [ ] libqnn_wrapper.so compila sin errores
- [ ] Carga en dispositivo sin crash
- [ ] QnnManager.isNpuAvailable() retorna true en SD685
- [ ] Benchmark: init() < 500ms
