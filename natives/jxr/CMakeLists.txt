cmake_minimum_required(VERSION 3.20)
project(woka_jxr C)

# ---- pinned jxrlib source -------------------------------------------------------------
# 4creators/jxrlib, a mirror of Microsoft's jxrlib 1.1 (BSD-2-Clause) with the VC14 project
# files. Pinned by commit and by archive hash; override with -DJXRLIB_SOURCE_DIR=<path>.
set(JXRLIB_COMMIT f7521879862b9085318e814c6157490dd9dbbdb4)
set(JXRLIB_URL https://github.com/4creators/jxrlib/archive/${JXRLIB_COMMIT}.tar.gz)
set(JXRLIB_SHA256 8ab20a21f6c3f8cfdedd2511b03a2009e52cce7fa6550202c355063d0d83213e)

set(JXRLIB_SOURCE_DIR "" CACHE PATH "Pre-fetched jxrlib source tree (skips the download)")
if(NOT JXRLIB_SOURCE_DIR)
  include(FetchContent)
  FetchContent_Declare(jxrlib
    URL ${JXRLIB_URL}
    URL_HASH SHA256=${JXRLIB_SHA256}
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
  # jxrlib at this commit has no CMakeLists.txt, so this only downloads, verifies and extracts.
  FetchContent_MakeAvailable(jxrlib)
  set(JXRLIB_SOURCE_DIR ${jxrlib_SOURCE_DIR})
endif()
set(J ${JXRLIB_SOURCE_DIR})

# ---- one shared library: shim + jxrlib (decoder, encoder, glue) -----------------------
# The glue layer references encoder symbols, so the encoder is compiled in too.
set(JXR_SOURCES
  ${J}/image/sys/adapthuff.c
  ${J}/image/sys/image.c
  ${J}/image/sys/strcodec.c
  ${J}/image/sys/strPredQuant.c
  ${J}/image/sys/strTransform.c
  ${J}/image/sys/perfTimerANSI.c
  ${J}/image/decode/decode.c
  ${J}/image/decode/postprocess.c
  ${J}/image/decode/segdec.c
  ${J}/image/decode/strdec.c
  ${J}/image/decode/strInvTransform.c
  ${J}/image/decode/strPredQuantDec.c
  ${J}/image/decode/JXRTranscode.c
  ${J}/image/encode/encode.c
  ${J}/image/encode/segenc.c
  ${J}/image/encode/strenc.c
  ${J}/image/encode/strFwdTransform.c
  ${J}/image/encode/strPredQuantEnc.c
  ${J}/jxrgluelib/JXRGlue.c
  ${J}/jxrgluelib/JXRGlueJxr.c
  ${J}/jxrgluelib/JXRGluePFC.c
  ${J}/jxrgluelib/JXRMeta.c)
if(MSVC)
  # x86 helpers, as in the VC14 projects. On x64 their SSE/inline-asm bodies are compiled out
  # (strcodec.h enables WMP_OPT_* only for WIN32 && !_WIN64), so they add nothing there.
  list(APPEND JXR_SOURCES ${J}/image/decode/strdec_x86.c ${J}/image/encode/strenc_x86.c)
endif()

add_library(woka_jxr SHARED woka_jxr.c ${JXR_SOURCES})
target_include_directories(woka_jxr PRIVATE
  ${CMAKE_CURRENT_SOURCE_DIR} ${J}/common/include ${J}/image/sys ${J}/jxrgluelib)
target_compile_definitions(woka_jxr PRIVATE DISABLE_PERF_MEASUREMENT)
set_target_properties(woka_jxr PROPERTIES C_VISIBILITY_PRESET hidden)

if(MSVC)
  # jxrlib's native Windows configuration (as in its VC14 projects): WIN32 defined, no __ANSI__.
  target_compile_definitions(woka_jxr PRIVATE WIN32 _CRT_SECURE_NO_WARNINGS)
  target_compile_options(woka_jxr PRIVATE /W1 /wd4996)
  # Static CRT: no vcruntime/ucrt redistributable needed.
  set_property(TARGET woka_jxr PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
else()
  # jxrlib's portable configuration (as in its Makefile). The code predates C99 strictness, so
  # newer GCC/Clang errors are demoted to warnings and then silenced.
  target_compile_definitions(woka_jxr PRIVATE __ANSI__)
  target_compile_options(woka_jxr PRIVATE -w
    -Wno-error=implicit-function-declaration -Wno-error=implicit-int
    -Wno-error=incompatible-pointer-types -Wno-error=int-conversion)
  find_library(M_LIB m)
  if(M_LIB)
    target_link_libraries(woka_jxr PRIVATE ${M_LIB})
  endif()
  if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    # Keep libgcc out of the dependency list; libc/libm are system libraries.
    target_link_options(woka_jxr PRIVATE -static-libgcc)
  endif()
  if(APPLE)
    set_target_properties(woka_jxr PROPERTIES INSTALL_NAME_DIR "@rpath")
  endif()
endif()
