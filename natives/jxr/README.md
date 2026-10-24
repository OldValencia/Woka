# woka_jxr — JPEG XR decoder

One shared library (`woka_jxr.dll` / `libwoka_jxr.dylib` / `libwoka_jxr.so`) that wraps Microsoft's
jxrlib behind a flat C API for Java FFM. jxrlib is compiled in statically; on Windows the CRT is
linked statically too (`/MT`), so the DLL imports only `KERNEL32.dll`.

## Source

- jxrlib: <https://github.com/4creators/jxrlib> (mirror of Microsoft's jxrlib 1.1),
  commit `f7521879862b9085318e814c6157490dd9dbbdb4`,
  archive `https://github.com/4creators/jxrlib/archive/<commit>.tar.gz`,
  SHA-256 `8ab20a21f6c3f8cfdedd2511b03a2009e52cce7fa6550202c355063d0d83213e`.
  CMake downloads and hash-checks it (`FetchContent`); `-DJXRLIB_SOURCE_DIR=<dir>` uses a local copy.
- Licence: jxrlib is BSD-2-Clause, © Microsoft Corp. Its copyright notice and licence text must ship
  with the app (the source tarball's `LICENSE`).

## Build

| Platform | Command | Output |
|---|---|---|
| Windows x64 (VS 2022+/18 with C++ tools) | `./build.ps1 [-BuildDir <dir>]` | `desktopApp/appResources/windows-x64/woka_jxr.dll` |
| macOS arm64 / x64 | `./build.sh [build-dir]` (`CMAKE_OSX_ARCHITECTURES=x86_64` for x64) | `appResources/macos-<arch>/libwoka_jxr.dylib` |
| Linux x64 | `./build.sh [build-dir]` | `appResources/linux-x64/libwoka_jxr.so` |

Windows uses jxrlib's native configuration (`WIN32`, as in its VC14 projects); macOS/Linux use its
portable `__ANSI__` configuration (as in its Makefile). **Only the Windows build has been run.**
The macOS/Linux path is untested.

## API (`woka_jxr.h`)

```c
int  woka_jxr_decode(const uint8_t* data, size_t size, int* out_width, int* out_height, uint8_t** out_rgba);
int  woka_jxr_info  (const uint8_t* data, size_t size, int* out_width, int* out_height); // header only
void woka_jxr_free  (uint8_t* p);
```

`0` = success. `1` bad arguments, `2` not JPEG XR / corrupt / truncated, `3` unsupported pixel
format, `4` out of memory, `5` more than 2^28 pixels. On success `*out_rgba` is `malloc`'d,
`width * height * 4` bytes, top-down rows without padding, **non-premultiplied RGBA8, sRGB**.
Release it with `woka_jxr_free`. The caller's bytes are only read, never kept. Width and height
take the container's orientation tag into account. jxrlib was not written to resist hostile input.
The shim rejects files whose image or alpha payload runs past `size`, but nothing more.

## Pixel-format handling

The decoder runs in the file's native pixel format (`Copy` without jxrlib's format converter), and
the shim converts to RGBA8:

| File format | Conversion |
|---|---|
| 8bppGray, 24bppRGB/BGR, 32bppRGB/BGR, 32bppRGBA/BGRA | copied / reordered; alpha kept |
| 32bppPRGBA/PBGRA | un-premultiplied (`c / a`) in the encoded domain |
| 16bppGray, 48bppRGB, 64bppRGBA, 64bppPRGBA (16-bit integer) | treated as sRGB-encoded, as WIC does: `round(v * 255 / 65535)` |
| Fixed point (16-bit: v/8192, 32-bit: v/2^24), Half, Float: Gray, RGB, RGBA, PRGBA | treated as **linear scRGB**: clamp to [0,1] (NaN → 0), apply the sRGB OETF (`12.92·L` for L ≤ 0.0031308, else `1.055·L^(1/2.4) − 0.055`), round to 8 bits through a 65536-entry table. Values above 1.0 clip, with no tone mapping. Alpha is clamped and scaled linearly. PRGBAFloat is divided by alpha before the OETF. Covers 64bppRGBAHalf / 64bppRGBHalf / 128bppRGBAFloat (HDR screenshots from Windows Game Bar / Xbox). |
| 16bppRGB555/565, 32bppRGB101010 | expanded to 8 bits. Field order follows WIC (Bgr555/Bgr565/Bgr101010: B in the low bits), swapped when the codestream's RED_BLUE_SWAPPED bit is set |
| BlackWhite | 1 → 255, 0 → 0 |
| 32bppRGBE | Radiance `m · 2^(e−136)` as linear, then the OETF as above |
| YUV420/422/444-tagged, 32bppCMYK | jxrlib converts them to 24bppRGB itself (CMYK: naive, no ICC). Untested |
| 64bppCMYK, CMYK+alpha, n-channel | rejected with `3` |

Embedded ICC profiles are ignored.

## Samples

Fifteen 64×48 samples were made with Windows' own WIC JPEG XR encoder and checked against WIC's own
decode (converted to Bgra32): every pixel within 1 LSB. Five of them — 8-bit, alpha, lossy, 16-bit
gray, half-float HDR — are kept as `shared/src/jvmTest/resources/media/jxr-*.jxr`, each with WIC's
decode beside it in `reference/`. `hdr_*` hold linear 0.2140 grey (left third), a linear ramp
(middle) and over-range R = 2.0 (right third); `hdr_rgbahalf` has alpha 0.5 in its bottom half. The
grey comes out 127 here and 128 from WIC: the exact sRGB value of 0.2140 is 127.48.
