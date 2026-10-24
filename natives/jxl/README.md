# libjxl (JPEG XL decoder) natives

Woka calls libjxl's C API (`jxl/decode.h`, `jxl/thread_parallel_runner.h`) through the Java FFM
API. The libraries ship inside the app via Compose Desktop `appResources`
(`desktopApp/appResources/<os>-<arch>/`); users install nothing.

- **Version:** libjxl **v0.12.0** (tag commit `a7a9c787341cf703dede03c2009fa460cae5e5df`).
- **Licences:** libjxl BSD-3-Clause; brotli MIT; highway Apache-2.0 / BSD-3 (static, inside
  `jxl`/`jxl_cms`); skcms BSD-3 (static, inside `jxl_cms`). Texts in `licenses/`.

## What ships

| OS | Files | Source |
|---|---|---|
| windows-x64 | `brotlicommon.dll` `brotlidec.dll` `brotlienc.dll` `jxl_cms.dll` `jxl.dll` `jxl_threads.dll` | official release `jxl-x64-windows.zip`, SHA-256 `4ee3f72134a2a49774c357f2bffecf30d752d702147896c493578789de15a97c` |
| macos-arm64, macos-x64 | `libbrotlicommon.dylib` `libbrotlidec.dylib` `libbrotlienc.dylib` `libjxl_cms.dylib` `libjxl.dylib` `libjxl_threads.dylib` | `build.sh` (from source) — **not built yet** |
| linux-x64 | same names with `.so` | `build.sh` (from source) — **not built yet** |

`jxl.dll` imports `brotlienc`, `brotlidec`, `jxl_cms`; both brotli libs import `brotlicommon`;
`jxl_threads` imports nothing of these. Windows DLLs also import `MSVCP140.dll` and
`VCRUNTIME140.dll` (not in the zip): Woka's bundled JDK runtime ships both in `runtime/bin`, and
`awt.dll` already loads `MSVCP140` into the process. The UCRT (`api-ms-win-crt-*`) is part of
Windows 10+.

## Load order

`System.load` leaves first, same on every OS (`System.mapLibraryName` gives the file name):

```
brotlicommon -> brotlidec -> brotlienc -> jxl_cms -> jxl -> jxl_threads
```

On Windows, if `MSVCP140.dll` ever fails to resolve, load
`${java.home}/bin/msvcp140.dll` first.

## Rebuilding

- Windows: `pwsh -File natives/jxl/build.ps1` — downloads the pinned zip, checks SHA-256,
  copies the six DLLs. Verified: a fresh download matched the hash and produced byte-identical
  DLLs.
- macOS / Linux: `natives/jxl/build.sh [--arch arm64|x86_64]` on the target OS — clones the tag,
  builds with CMake (vendored brotli/highway/skcms, no tools/JNI/sjpeg/OpenEXR), renames to
  unversioned names and rewrites install names / SONAMEs to `@loader_path` / `$ORIGIN`.
  **Untested** — written from the v0.12.0 CMake files, never run.

## Test samples

The five samples live in the test corpus as `shared/src/jvmTest/resources/media/jxl-*.jxl`;
`JxlStillDecoderTest` asserts the values below.

Encoded with `cjxl` v0.12.0 from the official `jxl-x64-windows.zip` (build `4128790`).
Expected values come from decoding each file with that release's `djxl` to 8-bit PNG/APNG and
reading the raw pixel bytes (not through ImageMagick, whose APNG reader alters colours).
All files are sRGB, 8 bits per sample, orientation 1, 64x48.

| File | Size | Kind | Frames | Alpha | (0,0) | (63,0) | (0,47) | (63,47) |
|---|---|---|---|---|---|---|---|---|
| `lossy.jxl` | 292 B | VarDCT `-d 1` | 1 | no | 2,0,253,255 | 254,0,14,255 | 22,254,255,255 | 252,255,12,255 |
| `lossless.jxl` | 119 B | Modular `-d 0` | 1 | no | 0,0,255,255 | 255,0,0,255 | 0,255,255,255 | 255,255,0,255 |
| `alpha.jxl` | 228 B | Modular `-d 0`, straight alpha | 1 | yes | 0,0,255,255 | 255,0,0,159 | 0,255,255,159 | 255,255,0,64 |
| `animated.jxl` frame 0 | 80 B | Modular `-d 0`, 100 ms/frame, loops forever | 3 | no | 255,0,0,255 | same | same | same |
| `animated.jxl` frame 1 | | | | | 0,255,0,255 | same | same | same |
| `animated.jxl` frame 2 | | | | | 0,0,255,255 | same | same | same |
| `jpeg-recompressed.jxl` | 560 B | lossless JPEG transcode (container + `jbrd`) | 1 | no | 0,1,73,255 | 252,1,78,255 | 2,254,77,255 | 255,254,80,255 |

Values are R,G,B,A. Alpha is 255 where the image has none.

Notes for assertions:

- `lossless.jxl` and `alpha.jxl` reproduce the source PNGs exactly; `animated.jxl` frames are
  solid colours and exact.
- `lossy.jxl` and `jpeg-recompressed.jxl` are what libjxl's decoder produces for 8-bit output;
  allow ±2 per channel if the request differs from djxl's (other output type, colour profile,
  dithering). For reference, ImageMagick's libjpeg decode of `source/src.jpg` gives
  0,2,73 / 253,1,78 / 3,254,77 / 255,255,79 — not the same as libjxl's.
- `animated.jxl`: `JxlBasicInfo.have_animation` = 1, ticks per second 100/1, each
  `JxlFrameHeader.duration` = 10 ticks (100 ms), `num_loops` = 0.
- `JxlSignatureCheck`: 2 (`JXL_SIG_CODESTREAM`) for all except `jpeg-recompressed.jxl`, which is
  3 (`JXL_SIG_CONTAINER`).

### Regenerating

Sources were made with ImageMagick 7.1.2 and encoded as follows:

```sh
magick -size 64x48 xc:black -channel R -fx 'i/63' -channel G -fx 'j/47' -channel B -fx '1-i/63' +channel -depth 8 PNG24:rgb.png
magick rgb.png \( -size 64x48 xc:black -fx '1-0.75*(i/63+j/47)/2' \) -alpha off -compose CopyOpacity -composite -depth 8 PNG32:rgba.png
magick -size 64x48 xc:red f0.png; magick -size 64x48 xc:lime f1.png; magick -size 64x48 xc:blue f2.png
magick -delay 10 -dispose none f0.png f1.png f2.png -loop 0 anim.gif
magick -size 64x48 xc:black -channel R -fx 'i/63' -channel G -fx 'j/47' -channel B -fx '0.3' +channel -quality 90 -sampling-factor 4:2:0 src.jpg

cjxl rgb.png  lossy.jxl    -d 1
cjxl rgb.png  lossless.jxl -d 0
cjxl rgba.png alpha.jxl    -d 0
cjxl anim.gif animated.jxl -d 0
cjxl src.jpg  jpeg-recompressed.jxl
```

