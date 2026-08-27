cmake_minimum_required(VERSION 3.22.1)
project("danmuapi-native")

# Node 24 headers 要求 C++20；16KB 页对齐与 libnode.so（digidem 构建）一致
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(native-lib SHARED src/main/cpp/native-lib.cpp)

target_link_options(native-lib PRIVATE "-Wl,-z,max-page-size=16384")

# Prebuilt libnode
add_library(libnode SHARED IMPORTED)
set_target_properties(libnode PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/libnode/bin/${ANDROID_ABI}/libnode.so
)

target_include_directories(native-lib PRIVATE
    ${CMAKE_SOURCE_DIR}/libnode/include/node
)

find_library(log-lib log)

target_link_libraries(native-lib libnode ${log-lib})
