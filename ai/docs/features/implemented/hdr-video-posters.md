# Plan: HDR video posters

**Date:** 2026-11-11. **Status:** complete; automated checks pass; manually verified by the author on 2026-11-11.
Source: `next_steps.md` 1.0.8; measured cause: [Dolby Vision and 8K §4](../pending/dolby-vision-and-8k.md#4-p81-почему-неподвижный-постер-темнее).

## Scope

Map a video's static poster to sRGB before caching, using the player's transfer, gamut,
declared peak and HDR10+ scene peak. Keep the existing frame selection, rotation, 400 px bound,
clip-look fold after the cache, and hover animation. P5 colour support is separate.
The existing global HDR mode applies before caching; both cache layers must distinguish modes.
No decoder pool, playback loop, queue or full-frame retention changes.

## Steps

1. Split bitmap preparation and storage out of the 150-line `VideoThumbnailExtractor.kt` into
   `VideoPosterBitmap.kt`. Keep selection and seeking in the extractor. Carry a selected frame's
   colour metadata alongside its image, captured before another grab can replace side data.
   Keep a rejected first candidate as an owned <=400 px fallback: `Java2DFrameConverter` reuses
   its full-size image, so retaining that image would mismatch pixels and metadata after a new grab.
   Read `FreshFrameGrabber.lastPicture` as the player does, including cloned seek-frame cases.
2. In the new helper, rotate as before, scale to at most 400 px and map with `HdrImagePainter`
   using `HdrView.mode.applyTo`. Map only the small bitmap, once per cache miss; SDR Auto/Off
   retains the existing pixel path. Close temporary Skia images, surfaces and bitmaps deterministically.
   Do not use the still-photo `toneMapped` helper: it measures pixels instead of using video metadata.
3. Store and retrieve the memory entry with `ImageBitmapCache.key(path, 0)` so forced modes cannot
   overwrite Auto. Keep file content in the existing WebP cache and use atomic replacement.
4. Bump the existing `WokaStorage` thumbnail schema marker to v6. The established schema migration
   discards old thumbnails once, including photographs; no new per-entry migration system.
5. Add integration regression tests for PQ/HLG mapping, SDR unchanged, mode-separated memory/disk
   caches, mapped disk readback without a second pass, and v5 invalidation. Compare the same frame
   against the actual viewer painter; verify the fixture's tags before pixel assertions.

**Files:**
- `shared/src/jvmMain/kotlin/com/woka/feature/viewer/video/engine/VideoThumbnailExtractor.kt` (edit)
- `shared/src/jvmMain/kotlin/com/woka/feature/viewer/video/engine/VideoPosterBitmap.kt` (new)
- `shared/src/commonMain/kotlin/com/woka/core/data/WokaStorage.kt` (edit)
- `shared/src/jvmTest/kotlin/com/woka/integration/VideoPosterHdrTest.kt` (new)
- `shared/src/jvmTest/kotlin/com/woka/integration/RealVideoPosterHdrTest.kt` (new, optional real P8.1 sample)
- `shared/src/jvmTest/kotlin/com/woka/support/VideoPosterFixture.kt` (new if fixture setup needs a split)
- `shared/src/jvmTest/kotlin/com/woka/unit/StorageKeyTest.kt` (v6 marker assertion)
- `shared/src/jvmTest/kotlin/com/woka/integration/ThumbnailSweepTest.kt` (v6 marker assertion)

Eight Kotlin files are needed for this complete slice: production extraction and storage, a fixture
and deterministic regression, real-sample parity, and the two existing schema-marker checks.

## Verification

- Compile, run focused poster/cache/edited-poster/HDR regression tests, then `:shared:build`.
- Run anti-FQN checks and `wc -l` on every changed Kotlin file.
- Check real P8.1 poster against the same decoded frame through the viewer painter; preserve measured
  peak 2.44. Save evidence next to this plan, not in a new testing tree.
- Manual author check: grid and carousel, selected cover and clip edits, PQ/HLG/SDR, HDR mode switch,
  hover without double mapping, relaunch/disk cache. Card dimming is a separate intentional UI effect.
- Report measured results and any unperformed visual/performance checks explicitly.

## Results — 2026-11-11

- `:shared:compileKotlinJvm -q`: passed.
- Focused cache/pipeline/sweep/storage-key/edited-reader/render-HDR/poster tests: 42 tests,
  zero failures or skips. The final run includes seven passing poster tests, including the real-file
  regression and the converter-buffer fallback case.
- Real P8.1: 400×225 frames at 5, 10 and 20 s use white point 2.44. Maximum RGB difference between
  the production poster preparation and an independent decode through the viewer painter is **0**
  at every position, before lossy WebP encoding. XML output: `shared/build/test-results/jvmTest/`.
- The focused run containing settings-backed editor tests prints `SettingsStore` shutdown-hook
  `FileNotFoundException` messages for disposed temporary roots. Its assertions still pass. No
  settings code is changed; this is not presented as an entirely warning-free test process.
- Final sequential build: `./gradlew.bat :shared:build :shared:jvmTest --tests 'com.woka.unit.*'
  --tests 'com.woka.integration.*' --tests 'com.woka.ui.*' --console=plain`: **BUILD SUCCESSFUL**,
  1660 tests in 349 suites, zero failures, errors or skips. Research probes are excluded from this
  run. The optional real P8.1 test runs, not skips, on this machine.
- An earlier unfiltered build was cancelled after it included long-running research probes. Its
  cancellation overlapped a replacement run and caused a missing Gradle binary-results file; that
  replacement failed. Both ended before the successful sequential run; no failure is hidden as a pass.
- Final `wc -l`: eight changed Kotlin files are 57–139 lines; anti-FQN check finds no matches outside
  imports/packages. No temporary diagnostic code is added.
- The author reports the manual check passed on 2026-11-11 and authorises the commit. Long-session
  CPU/native-memory measurements are not claimed by that confirmation.

## Decision log

| Date | Question | Choice | Reason |
|---|---|---|---|
| 2026-11-11 | HDR mode for posters? | Follow existing global mode and cache suffix | Avoid a new policy inconsistent with existing still previews and video playback. |
| 2026-11-11 | Mapping location? | Small cached poster, before clip edits | Reuses the viewer painter without adding work to every UI draw or mapping markup. |
| 2026-11-11 | Old posters? | Existing v6 schema migration | Uses the documented cache mechanism; rejected a second per-entry migration system. |
