#!/usr/bin/env bash
# Builds libjxl from source for macOS / Linux and copies the runtime libraries into
# desktopApp/appResources/<os>-<arch>/.
#
# UNTESTED: written against the v0.12.0 CMake sources on a Windows machine; it has never
# been run. Expect to fix small things on the first real run.
#
# Usage:
#   natives/jxl/build.sh                  # host OS + host arch
#   natives/jxl/build.sh --arch x86_64    # macOS only: cross-build Intel on Apple Silicon
#   WORK_DIR=/tmp/jxl natives/jxl/build.sh
#
# Needs: git, cmake >= 3.16, ninja or make, a C++17 compiler; Linux also needs patchelf.
#
# Why six libraries and not one: in v0.12.0 jxl_cms is always its own library target, and the
# vendored brotli follows the global BUILD_SHARED_LIBS (its targets have no static-only switch
# libjxl links against). A single merged library would need patching libjxl's CMake, so the
# set mirrors the official Windows release instead:
#   libbrotlicommon, libbrotlidec, libbrotlienc, libjxl_cms, libjxl, libjxl_threads
# highway and skcms are vendored and linked statically into libjxl / libjxl_cms; no system
# brotli/highway/lcms is used. On Linux libstdc++/libgcc are linked statically too.
#
# The copied files are renamed to unversioned names (libjxl.dylib / libjxl.so, ...) and their
# install names / SONAMEs and inter-library references are rewritten to match, with the search
# path set to the library's own directory (@loader_path / $ORIGIN).

set -euo pipefail

VERSION="0.12.0"
TAG="v${VERSION}"
TAG_COMMIT="a7a9c787341cf703dede03c2009fa460cae5e5df"
REPO="https://github.com/libjxl/libjxl.git"
LIBS=(brotlicommon brotlidec brotlienc jxl_cms jxl jxl_threads)   # load order, leaves first

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
WORK_DIR="${WORK_DIR:-${TMPDIR:-/tmp}/woka-jxl-build}"

OS="$(uname -s)"
ARCH="$(uname -m)"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --arch) ARCH="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

case "${OS}" in
  Darwin)
    case "${ARCH}" in
      arm64) OUT_NAME="macos-arm64" ;;
      x86_64) OUT_NAME="macos-x64" ;;
      *) echo "unsupported macOS arch ${ARCH}" >&2; exit 2 ;;
    esac
    EXT="dylib" ;;
  Linux)
    case "${ARCH}" in
      x86_64) OUT_NAME="linux-x64" ;;
      *) echo "unsupported Linux arch ${ARCH} (only linux-x64 is shipped)" >&2; exit 2 ;;
    esac
    EXT="so"
    command -v patchelf >/dev/null || { echo "patchelf is required on Linux" >&2; exit 2; } ;;
  *) echo "unsupported OS ${OS}; use build.ps1 on Windows" >&2; exit 2 ;;
esac

SRC="${WORK_DIR}/libjxl-${VERSION}"
BUILD="${WORK_DIR}/build-${OUT_NAME}"
STAGE="${WORK_DIR}/stage-${OUT_NAME}"
OUT_DIR="${REPO_ROOT}/desktopApp/appResources/${OUT_NAME}"

mkdir -p "${WORK_DIR}"

# --- sources: the tag plus only the submodules the library needs -------------------------
if [[ ! -d "${SRC}/.git" ]]; then
  git clone --depth 1 --branch "${TAG}" "${REPO}" "${SRC}"
  git -C "${SRC}" submodule update --init --depth 1 \
    third_party/brotli third_party/highway third_party/skcms
fi
HEAD="$(git -C "${SRC}" rev-parse HEAD)"
if [[ "${HEAD}" != "${TAG_COMMIT}" ]]; then
  echo "tag ${TAG} resolved to ${HEAD}, expected ${TAG_COMMIT}" >&2
  exit 1
fi

# --- configure + build -------------------------------------------------------------------
CMAKE_ARGS=(
  -S "${SRC}" -B "${BUILD}"
  -DCMAKE_BUILD_TYPE=Release
  -DCMAKE_INSTALL_PREFIX="${STAGE}"
  -DBUILD_SHARED_LIBS=ON
  -DBUILD_TESTING=OFF
  -DJPEGXL_ENABLE_TOOLS=OFF
  -DJPEGXL_ENABLE_DEVTOOLS=OFF
  -DJPEGXL_ENABLE_BENCHMARK=OFF
  -DJPEGXL_ENABLE_EXAMPLES=OFF
  -DJPEGXL_ENABLE_FUZZERS=OFF
  -DJPEGXL_ENABLE_VIEWERS=OFF
  -DJPEGXL_ENABLE_PLUGINS=OFF
  -DJPEGXL_ENABLE_DOXYGEN=OFF
  -DJPEGXL_ENABLE_MANPAGES=OFF
  -DJPEGXL_ENABLE_JNI=OFF
  -DJPEGXL_ENABLE_SJPEG=OFF
  -DJPEGXL_ENABLE_OPENEXR=OFF
  -DJPEGXL_ENABLE_TCMALLOC=OFF
  -DJPEGXL_ENABLE_SKCMS=ON
  -DJPEGXL_BUNDLE_LIBPNG=OFF
  -DJPEGXL_FORCE_SYSTEM_BROTLI=OFF
  -DJPEGXL_FORCE_SYSTEM_HWY=OFF
  -DJPEGXL_FORCE_SYSTEM_LCMS2=OFF
)
if command -v ninja >/dev/null; then
  CMAKE_ARGS+=(-G Ninja)
fi
if [[ "${OS}" == "Darwin" ]]; then
  CMAKE_ARGS+=(
    -DCMAKE_OSX_ARCHITECTURES="${ARCH}"
    -DCMAKE_OSX_DEPLOYMENT_TARGET=11.0
    -DCMAKE_MACOSX_RPATH=ON
    -DCMAKE_INSTALL_NAME_DIR=@rpath
    -DCMAKE_INSTALL_RPATH=@loader_path
  )
else
  CMAKE_ARGS+=(
    '-DCMAKE_INSTALL_RPATH=$ORIGIN'
    "-DCMAKE_SHARED_LINKER_FLAGS=-static-libstdc++ -static-libgcc"
  )
fi

cmake "${CMAKE_ARGS[@]}"
cmake --build "${BUILD}" --parallel
cmake --install "${BUILD}"

# --- copy with unversioned names and fixed-up references ---------------------------------
mkdir -p "${OUT_DIR}"

real_lib() {   # real file behind the lib<name>.<ext> symlink chain
  local link="${STAGE}/lib/lib$1.${EXT}"
  [[ -e "${link}" ]] || link="${STAGE}/lib64/lib$1.${EXT}"
  [[ -e "${link}" ]] || { echo "missing lib$1.${EXT} in ${STAGE}" >&2; exit 1; }
  if [[ "${OS}" == "Darwin" ]]; then
    python3 -c 'import os,sys; print(os.path.realpath(sys.argv[1]))' "${link}"
  else
    readlink -f "${link}"
  fi
}

for lib in "${LIBS[@]}"; do
  dst="${OUT_DIR}/lib${lib}.${EXT}"
  cp -f "$(real_lib "${lib}")" "${dst}"
  chmod 0644 "${dst}"
done

if [[ "${OS}" == "Darwin" ]]; then
  for lib in "${LIBS[@]}"; do
    f="${OUT_DIR}/lib${lib}.dylib"
    install_name_tool -id "@rpath/lib${lib}.dylib" "${f}"
    # Rewrite every @rpath/lib<dep>.<N>.dylib reference to the unversioned sibling.
    while read -r ref; do
      for dep in "${LIBS[@]}"; do
        if [[ "${ref}" =~ ^@rpath/lib${dep}(\.[0-9]+)*\.dylib$ ]]; then
          install_name_tool -change "${ref}" "@rpath/lib${dep}.dylib" "${f}"
        fi
      done
    done < <(otool -L "${f}" | tail -n +2 | awk '{print $1}')
    if ! otool -l "${f}" | grep -A2 LC_RPATH | grep -q '@loader_path'; then
      install_name_tool -add_rpath @loader_path "${f}"
    fi
    codesign --force --sign - "${f}"   # install_name_tool invalidates the signature
  done
  for lib in "${LIBS[@]}"; do otool -L "${OUT_DIR}/lib${lib}.dylib"; done
else
  for lib in "${LIBS[@]}"; do
    f="${OUT_DIR}/lib${lib}.so"
    patchelf --set-soname "lib${lib}.so" "${f}"
    for needed in $(patchelf --print-needed "${f}"); do
      for dep in "${LIBS[@]}"; do
        if [[ "${needed}" =~ ^lib${dep}\.so(\.[0-9]+)*$ && "${needed}" != "lib${dep}.so" ]]; then
          patchelf --replace-needed "${needed}" "lib${dep}.so" "${f}"
        fi
      done
    done
    patchelf --set-rpath '$ORIGIN' "${f}"
  done
  for lib in "${LIBS[@]}"; do
    echo "lib${lib}.so: $(patchelf --print-needed "${OUT_DIR}/lib${lib}.so" | tr '\n' ' ')"
  done
fi

echo "Done -> ${OUT_DIR}"
echo "Load order: ${LIBS[*]}"
