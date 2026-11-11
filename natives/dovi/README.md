# woka_dovi — typed Dolby Vision configuration access

A small C accessor for `AVDOVIDecoderConfigurationRecord`, which the installed Bytedeco FFmpeg
bindings omit. It returns the profile and base-layer compatibility as copied integers. No colour
conversion, frame decoding or RPU processing is implemented here.

FFmpeg is **not modified or rebuilt**. The accessor is compiled against the unmodified public
headers from the official `n8.1.2` source tag. It links no second FFmpeg library and allocates,
retains, frees and modifies no FFmpeg-owned memory.

## Reproduce

| Target | Command | App resource |
|---|---|---|
| Windows x64, Visual Studio C++ tools | `powershell -File natives/dovi/build.ps1` | `windows-x64/woka_dovi.dll` |
| macOS arm64 / x64, CMake + C compiler | `sh natives/dovi/build.sh` | `macos-<arch>/libwoka_dovi.dylib` |
| Linux arm64 / x64, CMake + C compiler | `sh natives/dovi/build.sh` | `linux-<arch>/libwoka_dovi.so` |

macOS supports `CMAKE_OSX_ARCHITECTURES=arm64` or `x86_64`, one resource directory per build.
The packaging workflow builds this bridge on each platform. Windows was built and tested locally
on 2026-11-11; macOS/Linux builds are provided but have not yet been run in this session.

Source: `https://codeload.github.com/FFmpeg/FFmpeg/tar.gz/refs/tags/n8.1.2`.
SHA-256: `9fd092511605bbebafe095ea6d38d9e40f34d12f7386e1258372df8be0576eb7`.
The scripts verify the archive before extracting it. FFmpeg is LGPL-2.1-or-later; its source and
licence are in that archive. The shim's source and build instructions live beside this README.

`dovi_meta.h` includes `pixfmt.h`, whose byte-order aliases need generated `avconfig.h`.
CMake generates only `AV_HAVE_BIGENDIAN` from the target compiler's byte order for that inclusion;
no FFmpeg implementation or build configuration is fabricated. Kotlin's accessor checks the
runtime `libavutil` version against the version compiled from the headers before reading fields.

## C ABI

`int woka_dovi_read(const void *data, uint64_t size, unsigned avutil_version)`

- `-1`: null pointer or length different from the compiled record size.
- `-2`: runtime/header `libavutil` version mismatch; pointer not dereferenced.
- `-3`: unsupported record version or malformed flags.
- Otherwise: `0x10000 | (profile << 8) | compatibility`. This packing belongs to this bridge,
  not FFmpeg's in-memory layout. The C compiler accesses FFmpeg's named fields.

The pointer is borrowed synchronously while the grabber owns its side data. The read does not
escape the grabber's thread. Streams without DV side data do not load the bridge.
