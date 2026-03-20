LOCAL_PATH := $(call my-dir)

# QNN Wrapper library
include $(CLEAR_VARS)

LOCAL_MODULE := qnn_wrapper
LOCAL_SRC_FILES := qnn_wrapper.cpp image_utils.cpp

# QNN SDK (ajustar path según instalación)
QNN_SDK_PATH := /opt/qnn-gcam/qairt

LOCAL_C_INCLUDES := \
    $(QNN_SDK_PATH)/include \
    $(QNN_SDK_PATH)/include/QNN

LOCAL_CFLAGS := -std=c++17 -O3 -fPIC -DANDROID
LOCAL_CPPFLAGS := -std=c++17 -frtti -fexceptions

LOCAL_LDLIBS := -llog -ljnigraphics -landroid

# Prebuilt QNN libraries (copiar del SDK)
LOCAL_LDFLAGS := -L$(QNN_SDK_PATH)/lib/aarch64-android

include $(BUILD_SHARED_LIBRARY)
