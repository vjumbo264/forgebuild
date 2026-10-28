LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libmotionsalt_ir_bridge
LOCAL_SRC_FILES := motionsalt_ir_bridge.cpp
LOCAL_SHARED_LIBRARIES := \
    liblog \
    libutils \
    libcutils \
    libhidlbase \
    android.hardware.ir@1.0
LOCAL_CFLAGS += -Wall -Werror -Wno-unused-parameter
include $(BUILD_SHARED_LIBRARY)
