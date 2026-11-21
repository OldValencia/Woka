# EK-001: accelerated frame preparation proof

**Approved scope:** user's fix attempt following the proposed GPU-decode/scaling experiment.
**Files next:** `shared/src/jvmTest/kotlin/com/woka/probe/EightKAcceleratedProbe.kt`,
`ThreadedRgbaProbe.kt`, `EightKAcceleratedParityProbe.kt`; existing `HardwareContextGrabber.kt`
is reused without production integration.

1. Enumerate bundled filters and exercise GPU scaling on the actual RTX, preserving 10-bit input.
   Save logs under `build/8k-gpu`; source files read-only. No system FFmpeg or replacement binaries.
2. If the direct GPU-scaling gate fails, test bounded threaded libswscale after native HEVC GPU
   download. Reuse the native-decoder probe; reflection remains confined to diagnostics.
   Match existing bilinear conversion and its colour policy; do not slip a colour change into a
   performance fix. Borrow decoded frames only until the next grab; own/free output buffers/scaler.
3. Compare 1/2/4 conversion threads, 1440×810 and 2560×1440 on both real samples; warm up, then
   measure >=300 frames including bitmap creation/copy. Record CPU and host/device memory;
   a per-stage elapsed timer is not total CPU consumption.
4. Compare pixels, timestamps, colour tags and side-data payloads against software at matching
   frames. Exercise seek/reopen/repeated cleanup and retain the existing P5 rejection.
5. Only a passing path can be specified for integration. Do not claim a working player from CLI
   throughput, or use reflection into private JavaCV fields in production.

## Initial findings — 2026-11-11

- `scale_cuda` and `scale_npp` absent. `scale_d3d11` is present but fails on the RTX at output
  texture allocation (80070057), before a frame is rendered. No FFmpeg patch attempted.
- CLI native HEVC+CUDA → hwdownload(P010) → scale(bilinear, 4 threads) → RGBA 2560×1440:
  600 HDR frames in 6.361 s (~94 fps), CPU user 11.953 s + system 0.828 s; max RSS 3365440 KiB.
  Bitmap/UI excluded. High RSS is a reason to check synchronous bounded ownership before adoption.
- Logs: `build/8k-gpu/d3d11-hdr-cli.log`, `cuda-cpu-scale-hdr.log`.

## Synchronous proof — 2026-11-12

Four conversion threads, 60 warmup + 300 measured frames including bitmap creation/copy:

| Input | RGBA | fps | p95 preparation ms | Process CPU | Working set MiB |
|---|---|---|---|---|---|
| SDR 10-bit | 1440×810 | 79.09 | 13.22 | 12.00% | 1438 |
| SDR 10-bit | 2560×1440 | 69.28 | 15.34 | 12.16% | 1469 |
| HDR10 | 1440×810 | 78.85 | 13.29 | 12.91% | 1460 |
| HDR10 | 2560×1440 | 68.87 | 15.49 | 11.55% | 1468 |

One/two threads are slower. Device-wide VRAM is 5080–5112 MiB, not attributable to Woka alone.
This is preparation throughput, not real player cadence or a release/leak measurement.
Reports: `shared/build/dv-investigation/accelerated/{throughput,parity}.txt`.

All 24 comparisons match timestamps, RGB bytes and colour tags/side-data hashes: both 8K files
at both output sizes, plus P8.1 and hybrid HDR10+/DV at 1440×810, at 1/5/20/back-to-2 seconds.
The two 8K samples have no frame side data; controls verify non-empty mastering/content-light,
HDR10+, RPU and decoded DV payloads. Build and filtered probe pass.
Repeated release, fallback and seek contracts are verified on the public-API adapter in EK-002;
the reflection-based diagnostic grabber is not a reusable production lifecycle implementation.
