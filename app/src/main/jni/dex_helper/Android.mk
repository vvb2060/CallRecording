LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE            := dex_helper_static
LOCAL_C_INCLUDES        := $(LOCAL_PATH)/include
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
LOCAL_SRC_FILES         := slicer/writer.cc slicer/reader.cc slicer/dex_ir.cc slicer/common.cc
LOCAL_SRC_FILES         += slicer/dex_format.cc slicer/dex_utf8.cc slicer/dex_bytecode.cc
LOCAL_SRC_FILES         += dex_helper.cc
LOCAL_EXPORT_LDLIBS     := -lz
include $(BUILD_STATIC_LIBRARY)
