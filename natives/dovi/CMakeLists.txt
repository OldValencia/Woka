cmake_minimum_required(VERSION 3.20)
project(woka_dovi C)

set(FFMPEG_SOURCE_DIR "" CACHE PATH "Official FFmpeg 8.1.2 source tree")
if(NOT EXISTS "${FFMPEG_SOURCE_DIR}/libavutil/dovi_meta.h")
    message(FATAL_ERROR "FFMPEG_SOURCE_DIR must contain FFmpeg 8.1.2 headers")
endif()

# Only pixfmt.h's public endian aliases require this generated FFmpeg header.
# No FFmpeg implementation is compiled; detect the target byte order with CMake.
include(TestBigEndian)
test_big_endian(WOKA_BIG_ENDIAN)
file(MAKE_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/include/libavutil")
file(WRITE "${CMAKE_CURRENT_BINARY_DIR}/include/libavutil/avconfig.h"
    "#define AV_HAVE_BIGENDIAN ${WOKA_BIG_ENDIAN}\n")

add_library(woka_dovi SHARED woka_dovi.c)
target_include_directories(woka_dovi PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/include")
# FFmpeg's public metadata types use anonymous unions (MSVC C4201). Keep /WX
# for the shim while treating the unmodified upstream headers as external.
target_include_directories(woka_dovi SYSTEM PRIVATE "${FFMPEG_SOURCE_DIR}")
set_target_properties(woka_dovi PROPERTIES C_VISIBILITY_PRESET hidden)
if(MSVC)
    set_property(TARGET woka_dovi PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
    target_compile_options(woka_dovi PRIVATE /W4 /WX)
else()
    target_compile_options(woka_dovi PRIVATE -Wall -Wextra -Werror)
endif()
