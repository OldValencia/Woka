#!/bin/sh
set -eu

here=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
build_dir=${1:-"$here/../../build/dovi-native"}
version=8.1.2
expected=9fd092511605bbebafe095ea6d38d9e40f34d12f7386e1258372df8be0576eb7
mkdir -p "$build_dir"
build_dir=$(CDPATH= cd -- "$build_dir" && pwd)
archive="$build_dir/ffmpeg-n$version.tar.gz"
if [ ! -f "$archive" ]; then
    curl --fail --location --retry 2 \
        "https://codeload.github.com/FFmpeg/FFmpeg/tar.gz/refs/tags/n$version" -o "$archive"
fi
if command -v sha256sum >/dev/null 2>&1; then
    actual=$(sha256sum "$archive" | cut -d ' ' -f 1)
else
    actual=$(shasum -a 256 "$archive" | cut -d ' ' -f 1)
fi
if [ "$actual" != "$expected" ]; then
    echo 'FFmpeg source hash mismatch; nothing is built' >&2
    exit 1
fi
source_dir="$build_dir/FFmpeg-n$version"
if [ ! -f "$source_dir/libavutil/dovi_meta.h" ]; then
    (cd "$build_dir" && cmake -E tar xzf "$archive")
fi
machine=$(uname -m)
case "$machine" in
    arm64|aarch64) arch=arm64 ;;
    x86_64|amd64) arch=x64 ;;
    *) echo "Unsupported architecture: $machine" >&2; exit 1 ;;
esac
case "$(uname -s)" in
    Darwin)
        os=macos
        suffix=dylib
        target=${CMAKE_OSX_ARCHITECTURES:-$machine}
        case "$target" in
            arm64) arch=arm64 ;;
            x86_64) arch=x64 ;;
            *) echo 'Build one macOS architecture per resources directory' >&2; exit 1 ;;
        esac
        cmake -S "$here" -B "$build_dir/compile" -DCMAKE_BUILD_TYPE=Release \
            -DFFMPEG_SOURCE_DIR="$source_dir" -DCMAKE_OSX_ARCHITECTURES="$target"
        ;;
    Linux)
        os=linux
        suffix=so
        cmake -S "$here" -B "$build_dir/compile" -DCMAKE_BUILD_TYPE=Release \
            -DFFMPEG_SOURCE_DIR="$source_dir"
        ;;
    *) echo 'Use build.ps1 for Windows' >&2; exit 1 ;;
esac
cmake --build "$build_dir/compile" --config Release
destination="$here/../../desktopApp/appResources/$os-$arch"
mkdir -p "$destination"
cp "$build_dir/compile/libwoka_dovi.$suffix" "$destination/"
echo "Built libwoka_dovi.$suffix -> $destination"
