#!/usr/bin/env sh
# Builds libwoka_jxr.{dylib,so} (Release) and copies it to desktopApp/appResources/<os>-<arch>/.
# The pinned jxrlib archive is downloaded and SHA-256-checked by CMake (see CMakeLists.txt).
# Usage: ./build.sh [build-dir]        Needs: cmake >= 3.20, a C compiler (clang / gcc).
# macOS: set CMAKE_OSX_ARCHITECTURES=x86_64 to cross-build macos-x64 on Apple Silicon.
set -eu

here=$(cd "$(dirname "$0")" && pwd)
build=${1:-"${TMPDIR:-/tmp}/woka_jxr_build"}

case "$(uname -s)" in
  Darwin) os=macos; lib=libwoka_jxr.dylib ;;
  Linux)  os=linux; lib=libwoka_jxr.so ;;
  *) echo "unsupported OS: $(uname -s) (use build.ps1 on Windows)" >&2; exit 1 ;;
esac

arch=${CMAKE_OSX_ARCHITECTURES:-$(uname -m)}
case "$arch" in
  arm64|aarch64) arch=arm64 ;;
  x86_64|amd64)  arch=x64 ;;
  *) echo "unsupported arch: $arch" >&2; exit 1 ;;
esac

extra=""
if [ "$os" = macos ]; then
  osx_arch=$([ "$arch" = arm64 ] && echo arm64 || echo x86_64)
  extra="-DCMAKE_OSX_ARCHITECTURES=$osx_arch -DCMAKE_OSX_DEPLOYMENT_TARGET=11.0"
fi

# shellcheck disable=SC2086
cmake -S "$here" -B "$build" -DCMAKE_BUILD_TYPE=Release $extra
cmake --build "$build" --config Release

dest="$here/../../desktopApp/appResources/$os-$arch"
mkdir -p "$dest"
cp "$build/$lib" "$dest/"
echo "copied $lib -> $dest"

# Show the dynamic dependencies so a non-system one is noticed.
if [ "$os" = macos ]; then otool -L "$dest/$lib"; else ldd "$dest/$lib" || true; fi
