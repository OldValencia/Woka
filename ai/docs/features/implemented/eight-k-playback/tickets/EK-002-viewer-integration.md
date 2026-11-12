# EK-002 — Accelerated 8K playback in the viewer

**Goal:** play the measured 8K HEVC samples through the existing viewer with bounded native buffers.
**Depends on:** EK-001 pixel/metadata and preparation results. Authorisation: user's fix attempt and
instruction to continue on 2026-11-12. Completed and manually accepted by the author on 2026-11-12;
the author authorises committing this stage.

## Steps

1. Add an owned native HEVC/CUDA codec and packet/frame reader using FFmpeg's public API. Borrow
   JavaCV's public format context for demuxing; never replace or reflect into its private decoder.
   Retain its unopened-to-packets software decoder for fallback and header/track access.
2. Add a reusable download/RGBA converter with four conversion threads and existing bilinear colour
   coefficients. One download frame, one RGBA frame, one decoded frame; clear side data before copy.
   Reject unexpected input geometry/format so it falls back instead of reusing incompatible buffers.
3. Add a FreshFrameGrabber subclass used only by the primary video grabber. Initially eligible:
   Windows, MP4-family HEVC 7680×4320, plain RGBA output, no rotation, at most 3840×2160 output.
   Implementation also requires Main10 and no audio codec opened on the primary grabber.
   P5 guard runs before opening the hardware decoder. RAW/filter, posters, exports and lanes retain
   existing paths. Unsupported hardware/OS stays software. No new FFmpeg binaries.
4. Own seek/flush/drain and timestamps while accelerated. Seek lands a borrowed pending frame;
   next grab returns it once. Preserve container origin and selected stream. Check cancellation
   between packets/seek frames; close on cancellation. On a hardware error, drop owned resources
   and seek software to the requested/next position. Log the reason once per grabber, no retry loop.
5. Make `FreshFrameGrabber` and its last-picture setter available to this subclass without changing
   default behaviour. Native output remains borrowed until next grab; existing retention copies it.
6. Test production adapter parity/seek/end/restart/failure and converter metadata/resource contracts;
   repeat the live viewer probe (including resize/seek/close), record cadence and memory. Run normal
   unit/integration/UI regressions before requesting manual verification.

## Files

- `shared/src/jvmMain/kotlin/com/woka/feature/viewer/video/engine/decode/`: new
  `CudaVideoCodec.kt`, `CudaVideoDecoder.kt`, `ThreadedVideoConverter.kt`,
  `AcceleratedFrameGrabber.kt`, `VideoAccelerationPolicy.kt`;
  edit `FreshFrameGrabber.kt`, `VideoPlayerGrabber.kt`.
- `shared/src/jvmTest/kotlin/com/woka/integration/`: new converter/adapter regression tests.
- `shared/src/jvmTest/kotlin/com/woka/probe/`: new production-adapter probe; existing
  `EightKPlaybackProbe.kt` for actual viewer measurements.

This exceeds eight files because native ownership boundaries and the 150-line limit must coexist
with an end-to-end viewer result. It is one behavioural slice; no intermediate state is called fixed.

## Done when

Production uses no private JavaCV reflection; source files remain untouched; runtime hardware
failure has a tested software fallback. Both supplied samples show sustained improvement in the
real viewer, with reported limits. Build, anti-FQN, line counts and regressions pass.

## Implementation and verification — 2026-11-12

- `CudaVideoCodec` owns the native HEVC codec, device reference and format callback. It is closed
  before the callback is freed. `CudaVideoDecoder` owns a packet and decoded frame, borrows the
  public demuxer, receives before sending, drains EOF, flushes on seek and checks cancellation.
  API contract: [FFmpeg send/receive](https://ffmpeg.org/doxygen/8.0/group__lavc__encdec.html).
- `ThreadedVideoConverter` reuses host/output buffers and legacy bilinear swscale with four threads.
  Frame metadata is cleared and copied each time; changing geometry/format is rejected. Borrowed
  output enters the existing bitmap copy and retention path, with no queue/pacing policy changes.
- `AcceleratedFrameGrabber` owns these resources only on eligible input. Hardware errors retire
  that decoder and seek the existing software path to the next/requested time. Repeated `start`
  keeps JavaCV's existing rejection; stop/restart/release release the hardware owners.
- New regression files: `ThreadedVideoConverterTest.kt`, `AcceleratedFrameGrabberTest.kt`.
  New probes: `EightKAdapterProbe.kt`, `CudaMetadataProbe.kt`. Existing live probe accepts an output
  directory property to preserve the baseline; metrics add a working-set snapshot.

**Adapter probe:** both files at 1440×810 and 2560×1440; matching timestamp and full pixel CRC on
40 frames after 0/5/20/back-to-2 seconds, then the final nine frames after 29.8 seconds and repeated
EOF. Three stop/start cycles per case match. Working set with simultaneous software reference and
GPU decoder is ~1.6 GiB, returning to ~165–167 MiB after release. This isolated result excludes UI.
A deliberately unsupported data-grab request exercises the runtime software-handoff branch; its
next timestamp/pixels match software. A physical GPU/driver failure is **not** simulated by that test.

**Native metadata/cancellation probe:** P8.1 and hybrid HDR10+/DV, 1/5/20/back-to-2 seconds. All eight
timestamps and non-empty side-data payload hashes match software. Cancellation is observed before
the next packet. Device-wide VRAM falls after close; other applications affect that total, so it is
not an isolated Woka allocation measurement. Native GPU frame contents are never exposed to Skia.

**Live viewer probe:** 12 measured phases, two rounds per file, 5 s settling + 8 s observation.
Small 1440×900 surface → 3440×1440 screen → small; Fit gives RGBA 1440×810 / 2560×1440.

| Input | Small drawn fps | Screen drawn fps | CPU across phases |
|---|---|---|---|
| SDR 10-bit | 59.41–60.00 | 59.99 | 8.19–12.50% |
| HDR10 | 59.33–59.99 | 59.39–60.00 | 10.70–12.67% |

Inter-draw p95 is 18.09–30.91 ms; max 32.99 ms. Working-set samples 1696–3099 MiB. No claim of
lower overall RAM, physical scanout timing, weak-hardware performance, or a long-term leak test.
Existing frame-drop thresholds, presenter pacing, frame retention, export and lanes are unchanged.

**Checks:** `:shared:build` and all 1677 unit/integration/UI tests pass, 356 suites, zero skipped.
Anti-FQN is clean; every changed/new Kotlin file is at most 131 lines. The first validation run
caught two incorrect test assumptions (a 1 s seek beyond a 1 s fixture, repeated start treated as
no-op); both fixtures/contracts were corrected, then the entire adapter validation passed.

Reports: `shared/build/dv-investigation/accelerated/{adapter,native-metadata,parity,throughput}.txt`,
`shared/build/dv-investigation/accelerated-live/{playback.txt,playback.jfr}`.
Logs: `build/8k-gpu/{adapter-validation,accelerated-live,regression}.log`.

Reproduce ordinary regressions:
`./gradlew.bat :shared:build :shared:jvmTest --tests 'com.woka.unit.*' --tests 'com.woka.integration.*' --tests 'com.woka.ui.*'`.
For real-file probes set `woka.probe.8k=true` on the Gradle Test task and filter to the named probe.
Live comparison also uses 1536 MiB max heap, 256 MiB max young generation and the desktop JIT flags;
`woka.probe.8k.output=build/dv-investigation/accelerated-live` preserves the previous live report.

**Manual acceptance — 2026-11-12:** after being asked to check both samples in `:desktopApp:run`
(resize/fullscreen, seek, pause/resume and switching to an ordinary film), the author reports:
«Всё работает, проверил руками» and authorises the commit. The stage is complete.
macOS/Linux and actual weak/low-memory hardware have not been run; those limitations remain.
