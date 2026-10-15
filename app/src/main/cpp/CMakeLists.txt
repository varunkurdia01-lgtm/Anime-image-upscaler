cmake_minimum_required(VERSION 3.22.1)

project("MNNBenchmark" CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Prebuilt MNN library from jniLibs
add_library(mnn_prebuilt SHARED IMPORTED)
set_target_properties(mnn_prebuilt PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libMNN.so"
)

# Prebuilt MNN Vulkan library from jniLibs
add_library(mnn_vulkan_prebuilt SHARED IMPORTED)
set_target_properties(mnn_vulkan_prebuilt PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libMNN_Vulkan.so"
)

# Shared JNI library for the benchmark
add_library(MNNBenchmark SHARED
    MNNBenchmark.cpp
)

# MNN 3.6.1 development headers
target_include_directories(MNNBenchmark PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../mnn_3.6.1_development/include"
)

target_link_libraries(MNNBenchmark PRIVATE
    mnn_prebuilt
    mnn_vulkan_prebuilt
    android
    jnigraphics
    log
)
