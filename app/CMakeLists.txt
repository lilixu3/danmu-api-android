cmake_minimum_required(VERSION 3.22.1)
project("danmuapiapp")

# libnode headers (copied by the GitHub Actions workflow)
include_directories(libnode/include/node/)

add_library(
        native-lib
        SHARED
        src/main/cpp/native-lib.cpp
)

# Import prebuilt libnode.so (copied by the GitHub Actions workflow)
add_library(
        libnode
        SHARED
        IMPORTED
)

set_target_properties(
        libnode
        PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/libnode/bin/${ANDROID_ABI}/libnode.so
)

find_library(log-lib log)

target_link_libraries(
        native-lib
        libnode
        ${log-lib}
)
