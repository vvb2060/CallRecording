LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE           := dex_helper
LOCAL_STATIC_LIBRARIES := dex_helper_static
LOCAL_SRC_FILES        := wrapper.cc
LOCAL_LDLIBS           := -llog
include $(BUILD_SHARED_LIBRARY)

include $(LOCAL_PATH)/dex_helper/Android.mk
