cmake_minimum_required(VERSION 3.22.1)
set(ANDROID_STL "none" CACHE STRING "Use the source-built STL" FORCE)
project("danmuapi-native")

# Node 26 headers 要求 C++20；16KB 页对齐与固定 lite libnode.so 一致
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(native-lib SHARED src/main/cpp/native-lib.cpp)

target_link_options(native-lib PRIVATE "-Wl,-z,max-page-size=16384")

# prepareNativeRuntime 解包同一份 lite artifact，CMake 不读取历史 app/libnode。
if(NOT NODE_RUNTIME_ROOT)
    set(NODE_RUNTIME_ROOT "${CMAKE_SOURCE_DIR}/build/prepared-native-runtime")
endif()

# Prebuilt libnode
add_library(libnode SHARED IMPORTED)
set_target_properties(libnode PROPERTIES
    IMPORTED_LOCATION ${NODE_RUNTIME_ROOT}/libnode/bin/${ANDROID_ABI}/libnode.so
)

# Android STL=none keeps only source-built shared dependencies; use matching compiler headers.
get_filename_component(NDK_LLVM_BIN "${CMAKE_CXX_COMPILER}" DIRECTORY)
target_include_directories(native-lib PRIVATE "${NDK_LLVM_BIN}/../sysroot/usr/include/c++/v1")
target_include_directories(native-lib PRIVATE
    ${NODE_RUNTIME_ROOT}/include/node
)

add_library(libcxx SHARED IMPORTED)
set_target_properties(libcxx PROPERTIES
    IMPORTED_LOCATION ${NODE_RUNTIME_ROOT}/jni-current/${ANDROID_ABI}/libc++_shared.so
)
target_link_options(native-lib PRIVATE "-nostdlib++")

find_library(log-lib log)

target_link_libraries(native-lib libnode libcxx ${log-lib})
