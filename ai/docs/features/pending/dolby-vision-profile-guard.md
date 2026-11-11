# Plan: Dolby Vision Profile 5 guard

**Date:** 2026-11-11. **Status:** implemented on Windows; automated checks pass; author confirms normal
video playback and the expected P5 notice. Editor/export manual checks and actual macOS/Linux CI
builds remain unconfirmed.
The author switched to Astra after the missing-binding finding. This implements step 2 of the
1.0.8 order, not the later P5 reshaping/colour support. Background: [investigation §3](dolby-vision-and-8k.md#3-profile-5-причина-неправильных-цветов).

## Confirmed constraints

- Both P5 samples carry `AV_PKT_DATA_DOVI_CONF`, but installed FFmpeg 8.1.2-1.5.14 sources have no
  `AVDOVIDecoderConfigurationRecord` class or field-access functions. The probe's byte offsets are
  not an approved production binding. The native record size is explicitly outside FFmpeg's ABI.
- `FreshFrameGrabber` owns picture decoding for playback, posters, hover and encoded exports.
  Header-only reads and packet copies also exist; a decode-only guard must not silently claim that
  copied DV pixels became SDR or stop unrelated audio-only reads.
- The player has a generic open-failure notice, shared with PiP. The editor canvas currently returns
  while no frame exists, so it needs the specific unsupported notice too.
- `VideoPlayerDecoder.kt` and `HoverExcerptReel.kt` are exactly 150 lines; any additions require an
  explicit extraction step. `VideoPlayerState.kt` is 149 lines; no unplanned growth beyond 150.

## Phase 1 — Typed native access gate

1. Build a small C accessor against the official pinned FFmpeg 8.1.2 headers. Read named
   `AVDOVIDecoderConfigurationRecord` fields, check input length against the compiled type, and
   return copied scalar profile/compatibility/flags. Borrow the pointer only during the call;
   never retain or free FFmpeg memory. No second FFmpeg library is linked or loaded.
2. Verify the source hash in the reproducible build script. Bind through the existing JDK FFM /
   bundled-library convention. Reject a mismatched runtime version or malformed record explicitly;
   do not equate missing bridge with missing Dolby metadata.
3. Exercise the accessor on both real P5 samples, P8.1/P8.4, P7/P10 and ordinary SDR/HDR controls.
   Include missing/truncated records and repeated open/close. Do not integrate until these results
   establish profile accuracy and bounded ownership. Record dated evidence here.

**Next files:** `natives/dovi/woka_dovi.c`, `natives/dovi/CMakeLists.txt`, `natives/dovi/build.ps1`,
`natives/dovi/README.md`, a JVM FFM adapter under `feature/viewer/video/engine/probe/`, and a focused
test/probe under `jvmTest`. Build outputs go under `build/` until the gate passes.

## Phase 2 — One complete user-visible guard

The native gate passes on Windows: both P5 samples, P7/6, P8/1, P8/4, P10/1, non-DV controls,
null/short buffers, version mismatch and 12 repeated opens. No tests skipped. macOS/Linux build
scripts and packaging jobs must remain present; their results are not claimed as tested locally.

Integration files, read before editing:

- `engine/probe/DoviSupport.kt` (new): capability policy and internal decoder rejection.
- `engine/decode/FreshFrameGrabber.kt`: check once before image decoding for the current stream;
  reset on stop/release; explicit opt-in for diagnostic probes only.
- `engine/VideoPlaybackFailure.kt` (new) and `engine/VideoPlayerState.kt`: failure value for UI.
- `engine/decode/VideoDecodeFailure.kt` (new) and `engine/decode/VideoPlayerDecoder.kt`: extract
  existing cleanup to respect the 150-line bound, publish a specific failure on Main.
- `video/UnsupportedVideoNotice.kt` (new), `video/PlaybackHealthNotice.kt`,
  `editor/engine/video/VideoEditorCanvas.kt`: reuse `NoticeCard` in viewer/PiP and the editor.
- `viewer/shell/ViewerMedia.kt`: a known failure must construct video media even without a
  poster; otherwise the shell waits for a rejected poster forever and hides the notice.
- `engine/preview/HoverExcerptReel.kt`: extract its grabber-opening helper (150-line file),
  end the flow cleanly on an unsupported format; no exception reaches the UI collector.
- `engine/VideoThumbnailExtractor.kt` and `core/data/WokaStorage.kt`: return no wrong-colour poster
  for P5 and invalidate prior cached pixels using the existing schema migration.
- `editor/engine/video/render/RenderSource.kt`: check before packet scanning/export policy.
- `editor/engine/video/VideoWriteSupport.kt` (new), `VideoEditorOutputs.kt`: header-only check
  before Done writes an unseen edit; Revert remains usable. Regression checks the entire source hash.
- `editor/engine/video/render/FramePhoto.kt`: return the specific decode failure instead of losing
  it as a generic missing frame. `engine/PlaybackResume.kt`: do not change resume on rejection.
- `jvmTest/probe/DolbySampleProbe.kt`: explicit diagnostic opt-in to keep raw metadata research usable.
- Native packaging: `natives/dovi/build.sh`, `natives/dovi/README.md`, `.github/workflows/package.yml`.

These paths share the existing `com.woka.feature.viewer.video` root unless qualified above.
The visible guard spans these readers; separating them would leave wrong-colour output elsewhere.
Regression tests cover header policy, first decode and seek, poster, hover, player failure value,
render rejection without a written file and the existing P8.1 poster regression.

- Check the selected video stream before producing decoded pixels, once per opened stream,
  including reopen/seek. P5 reports `Dolby Vision Profile 5 is not supported yet` and explains
  that correct colours need Dolby Vision processing. Compatible PQ/HLG base layers keep playing.
- Publish a failure value on Main; reuse the existing notice card in viewer, PiP and editor.
  Keep generic damaged-file errors distinct. No uncaught rejection may escape a hover coroutine.
- Suppress wrong-colour posters and previews. Invalidate already cached incorrect P5 posters;
  do not add a header open to every ordinary cache hit.
- Encoded video and frame-photo export must fail honestly before writing a wrong-colour result.
  A bit-preserving packet-copy path is separate from colour conversion; document its policy.
- Preserve track selection, cancellation, ownership and low CPU/RAM. Keep checks off the per-frame
  metadata parsing path. Build and run focused regressions, then the unit/integration/UI suite.

## Done when

- Both P5 files show a specific persistent explanation without publishing false-colour frames.
- No false-colour poster, hover excerpt or encoded export is produced; files remain unchanged on
  rejection. Missing/truncated DV metadata and unsupported bridge versions have explicit outcomes.
- P8.1/P8.4/P7/P10 and ordinary SDR/HDR controls retain their existing pixels and metadata.
- No UI-thread probing, deep buffers, retained pointers or external system-FFmpeg dependency.
- File-size and anti-FQN checks pass; manual author check is recorded separately from tests.

## Results — 2026-11-11

- FFmpeg's code and binaries are not modified or rebuilt. The shim uses official `n8.1.2` public
  headers, archive SHA-256 `9fd092511605bbebafe095ea6d38d9e40f34d12f7386e1258372df8be0576eb7`.
- Windows MSVC Release build passes. `woka_dovi.dll` is 100864 bytes; `dumpbin /dependents`
  reports only `KERNEL32.dll`, no second FFmpeg or external CRT dependency.
- Typed configuration tests read P5/0 from both P5 files, P7/6, P8/1, P8/4 and P10/1 from the
  real controls. The last pair agrees with the saved independent ffprobe inventory (P10 is not /0).
- Native tests reject null/0–8-byte records and a deliberately mismatched runtime version. Twelve
  successive P5 header opens complete and release their grabbers. This is not an hours-long memory test.
- Integration tests: first P5 image and seek image rejected; no P5 WebP or hover excerpt; player
  publishes the specific failure, stops its decoder/audio jobs and has no frame; photo/video export
  rejects before writing an output. Done rejects too, with SHA-256 of the whole input unchanged.
- Render policy for this stage: reject P5 edited-video renders before packet scanning, including
  otherwise copy-eligible renders, because no faithful preview exists. Revert remains available.
  This does not prohibit audio-only decoding or introduce a P5 colour conversion.
- The failure cancels the resume timer and audio tasks; rejected playback must not reset an old
  resume record. The hover rejection is consumed in the flow, not thrown into the UI coroutine.
- Cache schema v7 discards earlier P5 false-colour posters once, through the existing migration;
  this also rebuilds other thumbnail entries once. No new header probe is added to ordinary cache hits.
- `:shared:build :shared:jvmTest --tests 'com.woka.unit.*' --tests 'com.woka.integration.*'
  --tests 'com.woka.ui.*' --console=plain`: **BUILD SUCCESSFUL**, 1675 tests in 354 suites,
  zero failures, errors or skips. Research probes are not part of that run. The earlier regression
  for the real P8.1 poster still passes. Existing SettingsStore temporary-root errors remain in logs.
- The real-player UI regression additionally verifies `rememberViewerMedia(null, player)` produces
  video media on P5 rejection and the specific notice is visible without a poster. This prevents
  the shell's null-media spinner from hiding the error. The final suite passes after this fix.
- Kotlin file-size checks pass (30 changed/new files, max 150 lines); anti-FQN finds no matches
  outside imports/packages. No temporary production diagnostics are introduced.
- macOS/Linux shell syntax passes `bash -n`; both builds are wired into the existing packaging
  workflow. Their compilation, loading and playback have **not** been run on this Windows host;
  no cross-platform runtime pass or installer result is claimed. CI changes are local, not pushed.

## Manual follow-up

Author feedback, 2026-11-11: ordinary videos work; P5 videos show the expected unsupported notice.
This confirms playback and the notice, not the entire editor/export checklist below.

Open both P5 samples in the viewer and editor: the specific persistent explanation should replace
false colours; sound should stop. Hover should not animate false-colour frames. Done, Render video
and Save frame as photo must not write output for P5; Revert may remove existing edits. Check ordinary
SDR/HDR and the P7/P8/P10 controls still play, including the corrected P8.1 posters. On macOS/Linux,
first build the bridge through the packaging workflow or `natives/dovi/build.sh`, then repeat.

## Decision log

| Date | Question | Choice | Reason |
|---|---|---|---|
| 2026-11-11 | Transfer probe offsets to Kotlin? | No; typed C accessor with bounds/version checks first | The installed bindings omit DV configuration and FFmpeg does not guarantee its struct size as ABI. |
| 2026-11-11 | Implement P5 tone mapping here? | No; classify and report unsupported | Correct P5 needs RPU reshaping and an independent colour reference, a later planned stage. |
| 2026-11-11 | Keep macOS/Linux? | Portable CMake shim, native build scripts and packaging on each OS | A Windows DLL is only one platform's build output; no Windows API is used by the accessor. |
| 2026-11-11 | Allow P5 edits without a faithful preview? | Reject Done and edited exports; allow Revert | Do not write an unseen edit or false-colour export into a user's result. |
