#ifndef QNN_WRAPPER_H
#define QNN_WRAPPER_H

#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#define LOG_TAG "QNN_WRAPPER"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Enhancement modes
enum EnhanceMode {
    MODE_NIGHT = 0,
    MODE_PORTRAIT = 1,
    MODE_HDR = 2,
    MODE_SUPER_RES = 3
};

// QNN Backend types
enum QnnBackend {
    BACKEND_HTP = 0,  // Hexagon Tensor Processor (NPU)
    BACKEND_GPU = 1,  // Adreno GPU
    BACKEND_CPU = 2   // CPU fallback
};

#ifdef __cplusplus
extern "C" {
#endif

// JNI exports
JNIEXPORT jint JNICALL
Java_ai_qnn_QnnManager_nativeInit(JNIEnv* env, jobject obj, jstring modelPath);

JNIEXPORT void JNICALL
Java_ai_qnn_QnnManager_nativeRelease(JNIEnv* env, jobject obj);

JNIEXPORT jobject JNICALL
Java_ai_qnn_QnnManager_nativeEnhanceImage(JNIEnv* env, jobject obj, 
                                           jobject bitmap, jint mode);

JNIEXPORT jboolean JNICALL
Java_ai_qnn_QnnManager_nativeIsNpuAvailable(JNIEnv* env, jobject obj);

JNIEXPORT jstring JNICALL
Java_ai_qnn_QnnManager_nativeGetBackendInfo(JNIEnv* env, jobject obj);

#ifdef __cplusplus
}
#endif

#endif // QNN_WRAPPER_H
