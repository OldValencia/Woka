# woka_dovi — typed Dolby Vision access and P5 colour conversion

A small C accessor for `AVDOVIDecoderConfigurationRecord`, which the installed Bytedeco FFmpeg
bindings omit. It returns the profile and base-layer compatibility as copied integers.
The additional DV5-001 accessor copies FFmpeg's already parsed per-frame mapping/colour metadata.
DV5-102 adds P5 reshaping and colour reconstruction to BT.2020 PQ RGBA8. Frame decoding
and bitstream RPU parsing remain FFmpeg's responsibility.

FFmpeg is **not modified or rebuilt**. The accessor is compiled against the unmodified public
headers from the official `n8.1.2` source tag. It links no second FFmpeg library and allocates,
frees and modifies no FFmpeg-owned memory. A prepared colour context borrows input planes
only until all row workers have joined and `woka_p5_finish` has cleared them.

## Reproduce

| Target | Command | App resource |
|---|---|---|
| Windows x64, Visual Studio C++ tools | `powershell -File natives/dovi/build.ps1` | `windows-x64/woka_dovi.dll` |
| macOS arm64 / x64, CMake + C compiler | `sh natives/dovi/build.sh` | `macos-<arch>/libwoka_dovi.dylib` |
| Linux arm64 / x64, CMake + C compiler | `sh natives/dovi/build.sh` | `linux-<arch>/libwoka_dovi.so` |

macOS supports `CMAKE_OSX_ARCHITECTURES=arm64` or `x86_64`, one resource directory per build.
The packaging workflow builds this bridge on each platform. Windows was built and tested locally
on 2026-11-18; macOS/Linux builds are provided but have not yet been run in this session.

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

## Per-frame accessor (DV5-001)

`woka_dovi_metadata(data, byte_size, avutil_version, output, output_count)` copies a checked
record into 704 caller-owned doubles; layout and status codes are in `woka_dovi_metadata.h`.
Named FFmpeg fields are read only after version, region, alignment and overlap checks.
Malformed counts, orders and rational denominators are rejected. Failure leaves output untouched.
Integer signal codes and capability flags remain explicit; successful parsing does not certify
that a renderer supports those flags. No pointers escape; stack use is bounded, no heap allocation.

`tests/metadata_test.c` constructs typed C fixtures and checks boundary failures, polynomial/MMR
copying and missing/duplicate L1. CTest runs before the build scripts copy the library to resources.
Windows: 305 checks passed on 2026-11-12. macOS/Linux runtime remains untested.

Research binding/probe: `DoviMetadataAccess` / `DoviP5ReferenceProbe`, opt-in `woka.probe.dv5=true`
and `woka.probe.dv5.library=<absolute path to library>`. Research binaries remain under build;
only `woka_dovi.dll` is packaged.

## Production colour conversion (DV5-102)

`woka_p5.h` defines create/prepare/rows/finish/destroy and their errors. Version validation precedes
frame access. Prepare reads the current frame's checked metadata, validates dimensions, format,
planes, strides and supported signal flags, and invalidates any previous preparation on failure.
Only 10-bit full-range YUV420 P5 with supported residual/resampling flags is accepted. All six
defined chroma locations, polynomial curves and MMR orders 1–3 have a scalar path. MSVC x64
uses AVX2 for left-sited polynomial frames with widths divisible by eight after CPUID/OS checks.
The baseline DLL contains no unconditional AVX2 instructions and no OpenMP dependency.

On CPU fallback the Kotlin owner joins a shared pool of eight row workers before advancing/releasing FFmpeg,
including interruption and worker failure. Output is reusable caller-owned RGBA8 BT.2020 PQ;
the existing Woka HDR stage performs display mapping. Unsupported/missing metadata fails explicitly.
`tests/p5_api_test.c` covers strided output, partial rows, untouched padding, rejected ranges,
unsupported signals and invalidation after failed prepare; GPU bounds and strided output bring
the Windows total to 1,704 checks (2026-11-18). See DV5-104 for performance and acceptance.

## Windows GPU colour/resize (DV5-104)

MSVC x64 additionally builds a C++17 D3D11 compute backend, linking the system `d3d11` and
`d3dcompiler` libraries. The HLSL source is embedded in the DLL; no external shader file is
needed at runtime. The optional synchronous `woka_p5_gpu` entry point runs only after a valid
prepare, with no concurrent row workers; 1 means the caller must use the CPU path.

GPU capability requires feature level 11, the existing AVX2 polynomial/LEFT layout and a
resize ratio no smaller than 1/8 in either dimension. Full-resolution RGBA8 BT.2020 PQ is
reconstructed first; an antialiased triangle filter then resizes that RGB image if requested.
Float LUT layout is asserted against the CPU tables; IEEE strictness preserves colour math.
No HDR display mapping happens here. Unsupported layouts, other platforms, missing devices,
or device failures fall back to CPU; malformed/missing RPU still fails explicitly.

Each converter owns its device and reusable upload/UAV/readback buffers, releases them on
close, and synchronously copies finished pixels into the caller's frame. There is no borrowing
across grab/seek/close. Kotlin also accepts older bridges without this optional symbol.

RTX 5080 verification: six full frames byte-identical to CPU; independent triangle resize
oracle within one byte, and existing seek/colour lifecycle tests passed. Legacy swscale and
the float triangle can differ by up to four byte levels after resize in the sampled frames.
Production decode+colour+resize+bitmap measured 81.60 FPS full4K and 122.62 FPS output1080p
with eight decoder threads. Real player/audio/presenter reached ~59.9 FPS through three
resize/seek phases, without Compose drawing. The author confirmed manual playback of the
tested file on 2026-11-19. Shared/desktop build and 1,689 regression tests passed. Other
platforms and long-duration memory checks remain separate; details and reproducible probes
are in `DV5-104-playback-performance.md`.

`reference.ps1 -Ffmpeg <BtbN ffmpeg.exe> -Output <directory>` produces independent BT.2020 PQ
frames, raw decode controls, logs and a manifest. The downloaded tool is research-only, not packaged.
Provenance and results: `ai/docs/features/pending/dolby-vision-p5/tickets/DV5-001-reference.md`.

## Colour research (DV5-101, not packaged)

`reference.ps1 -Premap -Output <new directory>` uses lossless P010 packing before GPU upload
and the MAIN/OUTPUT hook to capture PQ before display mapping. Low-aligned 10-bit UNORM
uploads amplified texture-filter precision loss on the reference GPU; see the DV5-101 ticket.
Keep the original reference outputs separately. Run from the repository root (shader path).

`woka_p5_compare raw metadata.csv reference width height step chromaXShift [lut|simd]`
prints PQ/linear errors and comparison budgets; `simd` requires step=1, width divisible by 8,
and left chroma. `--self-test` covers polynomial/MMR, PQ, SIMD and row boundaries. The scalar
double/pow implementation remains the comparison oracle for both optimized implementations.
`--check-p010 raw p010 width height` verifies code preservation and chroma interleaving.

`woka_p5_benchmark raw metadata.csv width height iterations threads [simd]` measures the
conversion kernel, including per-frame preparation, excluding decoder/bitmap/UI costs.
SIMD is shared with production, with runtime AVX2/FMA/OSXSAVE detection; OpenMP is linked
only to research targets. Other platforms retain the scalar implementation.

`woka_p5_probe.dll` plus `DoviP5ThroughputProbe` measures software decoding, native conversion
and bounded bitmap ownership. The research DLL is never copied into app resources; it currently
accepts only left-sited 10-bit P5 polynomial frames. `DoviProductionThroughputProbe` measures
the bundled production path, decoder thread counts and requested output sizes separately.
