cmake_minimum_required(VERSION 3.22.1)
project(lersembarreiras_whisper)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "Build shared libraries" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_SYCL OFF CACHE BOOL "" FORCE)
set(GGML_RPC OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/whisper.cpp)
if (NOT EXISTS "${WHISPER_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "whisper.cpp não encontrado em ${WHISPER_DIR}. Execute a tarefa Gradle fetchWhisperCpp.")
endif()

set(_saved_library_output ${CMAKE_LIBRARY_OUTPUT_DIRECTORY})
set(_saved_runtime_output ${CMAKE_RUNTIME_OUTPUT_DIRECTORY})

add_subdirectory(${WHISPER_DIR} ${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp)

if (_saved_library_output)
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${_saved_library_output})
endif()
if (_saved_runtime_output)
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${_saved_runtime_output})
endif()

add_library(lersembarreiras_whisper SHARED jni.cpp)
target_include_directories(lersembarreiras_whisper PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/ggml/include
)
target_link_libraries(lersembarreiras_whisper whisper android log)
target_compile_options(lersembarreiras_whisper PRIVATE -O3 -fvisibility=hidden)
