cmake_minimum_required(VERSION 3.22.1)
project(whisper_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- 16 KB page alignment (Android 15 ready) -------------------------------
# Same linker flags as the FFmpegKit 8.1 reference build (function-android.sh).
add_link_options(
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
    "-Wl,--hash-style=both")

# --- whisper.cpp git submodule (repo_root/whisper.cpp) ---------------------
# jni dir = library/src/main/jni  →  four levels up is the repo root.
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../whisper.cpp)

# Build whisper.cpp + ggml as STATIC libs and link them into our single .so,
# so the AAR ships exactly one native library (libwhisper.so).
# Flags mirror the FFmpegKit 8.1 reference (scripts/android/whisper.sh).
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(WHISPER_STATIC         ON  CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_METAL             OFF CACHE BOOL "" FORCE)   # Apple-only backend
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)   # no OpenMP on Android NDK
set(GGML_NEON              ON  CACHE BOOL "" FORCE)   # ARM NEON (arm64-v8a)

add_subdirectory(${WHISPER_DIR} ${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp)

# --- JNI bridge → libwhisper.so --------------------------------------------
# whisper.cpp already owns the CMake target name `whisper`, so our target is
# `whisper_jni` but OUTPUT_NAME makes the artifact libwhisper.so.
add_library(whisper_jni SHARED whisper_jni.cpp)
set_target_properties(whisper_jni PROPERTIES OUTPUT_NAME whisper)

find_library(log-lib log)

target_include_directories(whisper_jni PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include)

target_link_libraries(whisper_jni
    whisper
    ${log-lib})
