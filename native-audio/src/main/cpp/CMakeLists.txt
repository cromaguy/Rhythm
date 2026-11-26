cmake_minimum_required(VERSION 3.22.1)
project(rhythm_native_audio LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Required for 16 KB memory page alignment on Android 15+ devices.
add_link_options("-Wl,-z,max-page-size=16384")

add_library(
    rhythm_native_audio
    SHARED
    dsd_decimator.cpp
    sinc_resampler.cpp
    aaudio_direct_probe.cpp
    usb_audio_output.cpp
    native_audio_jni.cpp
)

target_include_directories(
    rhythm_native_audio
    PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}"
)

target_compile_options(
    rhythm_native_audio
    PRIVATE
    $<$<CONFIG:Debug>:-O2>
    -Wall
    -Wextra
    -Wpedantic
)

target_link_libraries(
    rhythm_native_audio
    PRIVATE
    aaudio
    android
    log
)
