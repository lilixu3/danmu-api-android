cmake_minimum_required(VERSION 3.22.1)
project("danmuapi-native")

add_library(native-lib SHARED src/main/cpp/native-lib.cpp)

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
