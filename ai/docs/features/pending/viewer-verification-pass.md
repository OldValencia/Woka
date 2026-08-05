# Plan: Verification Pass — Test Pyramid (Viewer + Core)

A full re-check of the viewer and the shared core, built as a **testing pyramid**: a wide base of
unit tests, a smaller layer of integration tests, a thin layer of UI automation on top, and a minimum
of manual cases at the apex. Manual cases live in their own directory of documents, not as a list
inside this plan.

Third of three. Order: `package-restructure-feature-first.md` → `code-quality-refactor.md` →
**this one**. The first two are done; every path below has been checked against the current tree.

**Progress:** Phase 0 done · Phase 1 done · Phase 2 next.

---

## The pyramid

| Layer | Location | Size | What belongs here | What does **not** |
|-------|----------|------|-------------------|-------------------|
| **4. Manual** | `ai/docs/testing/manual/` | ~24 cases | Only what a machine cannot check: visual correctness, how smooth it feels, OS integration, separate native windows | Anything an assertion can cover |
| **3. UI automation** | `shared/src/jvmTest/.../ui/` | ~5 files, ~25 checks | Compose UI tests: a user scenario through real composables — click, key, wheel → state or tree change | Verifying formulas and parsers through the UI |
| **2. Integration** | `shared/src/jvmTest/.../integration/` | ~11 files, ~55 checks | Real filesystem in a temp directory, real FFmpeg/JavaCV, real ViewModels and repositories | The Compose runtime; pure computation |
| **1. Unit** | `shared/src/jvmTest/.../unit/` | ~14 files, ~95 checks | Pure functions: zoom maths, parsers, mappers, serialization. No filesystem, no FFmpeg, no Compose | Anything that opens a file or a process |

One rule decides placement: **a test moves up a layer only when it physically cannot be written on
the current one.** A zoom defect is caught by a `ZoomScale` unit test, not by a manual "spin the
wheel"; exactly one manual zoom case survives — "the image is not mushy at Actual".

The 130–150 line file mandate applies to test files too. Where a set of cases will not fit, the plan
states the split up front.

---

## Found by reading the code (backlog for the fix pass, Phase 7)

| # | Finding | Where | Status |
|---|---------|-------|--------|
| F1 | Full-size bitmaps are cached in a `ConcurrentHashMap` **with no eviction** — every image opened stays in RAM at native resolution for the lifetime of the process. The missing policy is commented in the code as a deferred decision | `core/ui/image/ImageBitmapCache.kt:17` | confirmed by reading, needs a measurement |
| F2 | The bitmap is loaded twice on every file change: `state.imageBitmap = loadThumbnail(activeItem)` in two consecutive `LaunchedEffect`s | `feature/viewer/shell/MediaViewerScreenEffects.kt:46` and `:83` | confirmed, unambiguous |
| F3 | EXIF orientation is not applied to still images — `Image.makeFromEncoded` ignores it, so camera photos open sideways | `feature/viewer/image/ImageLoader.kt` | confirm with a fixture |
| F4 | HDR tone mapping only runs for video frames; HDR photos (HEIF/AVIF/PNG with PQ) never reach it | `feature/viewer/shell/MediaViewerFrame.kt` + `video/HdrImagePainter` | scope decision |
| F5 | `grabber.stop()`/`release()` sit unguarded in `finally`: if `start()` threw, `stop()` throws on top and masks the original cause | `feature/viewer/video/engine/VideoThumbnailExtractor.kt` | unambiguous |
| F6 | The only tests in the project are two `assertEquals(3, 1 + 2)` placeholders | `SharedCommonTest.kt`, `SharedLogicDesktopTest.kt` | unambiguous |
| F7 | `WokaStorage.root` was a `val` pinned to `user.home`, resolved at object init. Any test touching settings or the thumbnail cache would have written into the user's **real** directory | `core/data/WokaStorage.kt:16` | blocked Phase 3 — **fixed in Phase 0** |

Inline FQNs, disagreeing extension lists, the thumbnail cache key collision and the timestamp
triggers were closed by plans 1–2 and are not repeated here.

---

## Phase 0: Infrastructure — **done**

- `gradle/libs.versions.toml`: added `kotlinx-coroutinesTest` (version.ref `kotlinx-coroutines`) and
  `compose-uiTestJUnit4` (`org.jetbrains.compose.ui:ui-test-junit4`, version.ref
  `composeMultiplatform`).
- `shared/build.gradle.kts`: declared `val jvmTest by getting` with `libs.kotlin.test`, `libs.junit`,
  `libs.kotlinx.coroutinesTest`, `libs.kotlinx.coroutinesSwing`, `libs.compose.uiTestJUnit4` and
  `compose.desktop.currentOs`. `jvmTest` previously inherited from `commonTest` alone, which covers
  pure logic but not `runTest`, not ViewModels bound to `Dispatchers.Main`, and not UI tests.
- Removed both placeholders (F6): `commonTest/.../SharedCommonTest.kt`,
  `jvmTest/.../SharedLogicDesktopTest.kt`. `commonTest` is now empty — the project has no shared code
  that is not JVM.
- **Production change (F7), approved:** `WokaStorage.root` became a getter —
  `System.getProperty("woka.storageRoot") ?: user.home/Documents/OldValencia/Woka`. Production
  behaviour is unchanged. `thumbnailsDir` stopped being a `by lazy` — it would have pinned the cache
  to whichever root was seen first — and got a once-per-directory preparation under `@Synchronized`.
- `jvmTest/.../support/TempStorageRoot.kt` — creates a temp directory, sets the property,
  **restores the previous value** rather than clearing it, and deletes the directory. Plus the scoped
  form `withTempStorageRoot { }`.
- `jvmTest/.../support/MainDispatcherRule.kt` — `Dispatchers.setMain`/`resetMain` around a
  `StandardTestDispatcher` for ViewModel tests.
- **There will be no `SettingsRepository` fake.** The interface carries 43 methods; a hand-written
  fake is ~100 lines of one-liners duplicating the coercions in `SettingsRepositoryImpl` — a second
  copy of the logic that will drift. Tests use the **real** `SettingsRepositoryImpl` on top of
  `withTempStorageRoot` instead. A consequence accepted deliberately: any test needing a repository
  lives on the integration layer, and the unit layer stays entirely free of it.

**Files:** `gradle/libs.versions.toml`, `shared/build.gradle.kts`, `core/data/WokaStorage.kt`,
`support/TempStorageRoot.kt`, `support/MainDispatcherRule.kt`,
deleted `SharedCommonTest.kt`, `SharedLogicDesktopTest.kt`

## Phase 1: Media corpus — **done**

Fixtures are **committed** to `shared/src/jvmTest/resources/media/` (3.3 MB, 20 files) and reached
through `fixtures/SampleMedia.kt` by name, never by absolute path. Nothing is generated at test time.

The reason: ffmpeg cannot synthesise what is needed. It will not produce bitmap subtitles at all —
asked to go `text → bitmap` it answers "Subtitle encoding currently only possible from text to text
or bitmap to bitmap", verified. The rest `lavfi` would only approximate: eight audio tracks of which
seven share one language and differ only by `title`; sidecars named the way real releases name them;
genuine PQ/BT.2020 metadata. The corpus was assembled from existing files, shrunk to a size a
repository can carry, and renamed — no real title, release group or studio remains, in the file names
or in the stream metadata. Provenance and the exact commands are in `resources/media/README.md`.

Everything is committed, so a missing file means a broken checkout: `SampleMedia.file` fails loudly,
with no `Assume` skips.

| File | What is inside | What it is for |
|------|----------------|----------------|
| `WokaSample.S01E01.…mkv` | h264 320×180; **8 audio tracks — 7 `rus` distinguishable only by `title`, and 1 `eng`**; 3 subtitle tracks, one with no language | `TrackMatching` on its hard case: language alone cannot disambiguate |
| `…S01E01.srt` / `.ru.srt` / `.rus.srt` | UTF-8 under the exact stem; UTF-8 with a language suffix; **CP1251** | sidecar scan, `SubtitleFileCharset` |
| `WokaSample.S01E02.…mkv` | h264, one `eng` audio track | a second stem, so E01's sidecars must not attach to it |
| `…S01E02.en.srt`, `.ass` | UTF-8 with a suffix; ASS with three styles, `{\i1}`/`{\b1}`, `\N` | scan, parsing and ASS rendering |
| `…S01E03.sup`, `…S01E04.idx`/`.sub` | raw HDMV PGS; a VobSub pair | external bitmap subtitles |
| `WokaSample.BitmapSubs.mkv` | h264 1920×1080, 10:20; embedded `hdmv_pgs_subtitle` **and** `dvd_subtitle` | `BitmapSubtitleSource`, `BitmapSubtitleIndex` |
| `WokaSample.DualVideo.mkv` | **two video streams** — `ffv1` `yuv420p10le` PQ/BT.2020 and h264 8-bit BT.709; audio `aac`(eng) + `ac3`(rus, titled); two `subrip` | video track switching, `readHdrInfo` on real metadata |
| `chapters.mkv` | 3 chapters: 0–0.8, 0.8–2.0, 2.0–3.0 s | `MediaChapterProbe`, ticks on the slider |
| `noaudio.mkv` | video with no audio stream | volume and media keys on the boundary case |
| `broken.mkv` | the first 2 KB of a valid container | error paths |
| `still.png/.jpg/.webp` | 640×480 | `ImageLoader` |
| `exif_rot90.jpg` | `still.jpg` with EXIF `Orientation=6` spliced in | **F3**: a correct loader returns 480×640 |
| `tools/*.py` | generators for the `.sup` and the `.idx`/`.sub` pair | how the bitmap subtitles were produced |

The EXIF tag was written by hand — ffmpeg does not write `Orientation`: a 36-byte APP1 segment right
after the SOI marker. The source is deliberately landscape; on a square image the check would be
vacuous.

`SampleMediaTest` guards the corpus itself: every declared file must exist and be non-empty, and the
two stems must differ. Otherwise a broken checkout would surface as a failing player.

**Files:** `fixtures/SampleMedia.kt`, `integration/SampleMediaTest.kt`,
`resources/media/` (20 files + `README.md` + `tools/`)

## Phase 2: Unit tests — the base of the pyramid

Directory `jvmTest/kotlin/com/woka/unit/`. Not one touch of the filesystem, of a process, or of the
Compose runtime; `IntSize`/`Offset` are used as value types. `internal` is visible from `jvmTest`
(friend compilation).

| File | Target | Key checks |
|------|--------|-----------|
| `ZoomScaleTest` | `feature/viewer/shell/ZoomScale.kt` | `fitPercent`/`fillPercent` with the container wider and narrower than the media; `presetPercent` for every `ScalePreset`; `zoomBounds` on degenerate sizes (`IntSize.Zero`, 1×1, 16000×16000); `clampPercent`, `stepUp`/`stepDown` at the limits; `snapToPreset` — hit and miss; `sizeModeFor`; `sliderFraction` is monotonic and within `0f..1f` |
| `ScaleMathTest` | `shell/ScaleMath.kt` | `computeGraphicsScale` for `Preset(Fit/Fill/Actual)` and `Percent`; `contentOverflows` exactly on the boundary |
| `PanBoundsTest` | `shell/PanBounds.kt` | `panLimits` is 0 when the content does not overflow; `clampOffset` never lets the frame past the edge; `rubberBandOffset` is monotonic, sign-symmetric and decaying |
| `SizeModeTest` | `shell/SizeMode.kt` | `startSizeMode` with `defaultUseZoom` on and off; `percentIn` for `Preset` and `Percent`. `persistSizeState` needs a repository and moves to `SettingsPersistenceTest` |
| `PlaylistWalkTest` | `shell/MediaViewerPlaylist.kt` | `nextPath`: `null` when `repeat != Folder && !shuffle`; `null` with fewer than 2 videos; images stay out of the walk; `Repeat.Folder` wraps to the first; shuffle without folder repeat stops at the end; **`orderFor` is stable across calls** |
| `TrackMatchingTest` | `video/model/TrackMatching.kt` | `matchRef`: exact match; language-only match; `ref = null`; empty list; two candidates sharing a language |
| `SubtitleParserTest` | `video/engine/subtitle/SubtitleFileParser.kt`, `SubtitleParser.kt` | SRT: multi-line cue, overlapping timings, empty block, `\r\n`, zero duration. ASS: `Dialogue:` with `{\i1}`, escaped `\N`, commas inside the text. **Split into `SrtParserTest` + `AssParserTest` if one file exceeds 150 lines** |
| `HdrInfoTest` | `video/engine/probe/HdrInfo.kt` | (bt709, bt709) → no tone mapping; (smpte2084, bt2020) → tone mapping + wide; HLG/`arib-std-b67`; unknown values do not throw |
| `WokaSettingsTest` | `core/data/WokaSettings.kt` | JSON round trip; defaults; an unknown field in the file is ignored; malformed JSON does not escape |
| `TrackPrefsTest` | `core/data/TrackPrefs.kt`, `PlaybackMode.kt` | `TrackRef` serialises and deserialises; `resumeAppliesTo` at the boundaries (very short video, watched almost to the end) |
| `MediaKindTest` | `core/data/MediaKind.kt` | Classification by extension; case does not matter; unknown extension; file with no extension |
| `FolderStateMapperTest` | `feature/folder/FolderStateMapper.kt` | Every `SortType`; stable order on equal keys; empty list; selection survives a re-sort |
| `FileInfoFormatTest` | `feature/viewer/shell/FileInfoFormat.kt` | Byte formatting (0, KB, MB, GB), duration (under a minute, hours), dates |
| `VideoThumbnailHeuristicsTest` | `video/engine/VideoThumbnailHeuristics.kt` | Poster frame selection: very short video, zero duration, rejecting a monochrome frame |
| `StorageKeyTest` | `core/data/WokaStorage.kt` | `thumbnailFile` is deterministic; differs for same-named files in different directories; safe for non-ASCII paths; changes with mtime or size. Works on `File` objects through `TempStorageRoot`, reading no more than `length()`/`lastModified()` |

**Files:** 14 files in `jvmTest/kotlin/com/woka/unit/` (+1 if the parser test is split)

## Phase 3: Integration tests

Directory `jvmTest/kotlin/com/woka/integration/`. Real filesystem under `TempStorageRoot`, real
FFmpeg/JavaCV against the Phase 1 corpus, real ViewModels and repositories. No Compose runtime.

| File | What it integrates | Key checks |
|------|--------------------|-----------|
| `SettingsPersistenceTest` | `SettingsStore` + `SettingsRepositoryImpl` + disk | Write → read back from disk; mutations from `SettingsMutations` reach the file; setter coercions (`defaultZoomPercent` 10..1600, `mediaVolume` 0..1, `resumeMinDurationMinutes` 1..240); a malformed `settings.json` yields defaults and the app survives; per-folder settings do not overwrite each other; `persistSizeState` writes the expected preset/percent |
| `LocalMediaRepositoryTest` | Directory scanning | Picks up `.jpg/.png/.mp4` and ignores the rest; subdirectories; empty directory; unreadable directory yields an empty list, not an exception |
| `FolderViewModelTest` | VM + repository + settings + `MainDispatcherRule` | Listing loads from a temp directory; `setSort` reaches `state`; `toggleSelectMode` clears the selection; `selectRange` from `lastSelectedPath`; `selectAll`; `refresh` after a file appears on disk |
| `HomeViewModelTest` | VM + settings | Linked folders are read and written; removing a directory that is gone |
| `MediaProbeTest` | `MediaTrackProbe`, `MediaChapterProbe`, `HdrInfo` from a file | `S01E01`: exactly 8 audio and 3 subtitle tracks, `language`/`title`/`codec` are read, the track with no language does not break parsing; `DualVideo`: two video streams, audio in different codecs; `noaudio.mkv`: audio list empty; `chapters.mkv`: 3 chapters, boundaries increase and land on 0.8/2.0/3.0; `S01E02`: no chapters — empty list, not an exception; `DualVideo` v:0 → tone mapping on, v:1 → off |
| `VideoPlayerEngineTest` | `VideoPlayerState` + grabber | Opening and closing `S01E02` without leaking a grabber; a double `release()` is safe; a missing file gives a clear error rather than an `NPE`; `broken.mkv` errors instead of hanging |
| `VideoSeekTest` | The seek subsystem | Seek to 0, to the middle, past the duration, backwards; a frame arrives after each and its timestamp lands within tolerance of the request; seek while paused |
| `SubtitleIntegrationTest` | Scan, encodings, stream decode, bitmap | Next to `S01E01.mkv` the scan finds `.srt`, `.ru.srt`, `.rus.srt`, and they do **not** attach to `S01E02.mkv`; extension case does not matter; `.rus.srt` in CP1251 reads without mojibake (`SubtitleFileCharset`); `S01E01` — embedded text cues are non-empty with increasing timings; `BitmapSubs.mkv` — `BitmapSubtitleSource` returns a non-empty rectangle for both PGS and VobSub, `BitmapSubtitleIndex` releases a cue after its `end`. **Split into `SubtitleFileTest` + `SubtitleStreamTest` if it exceeds 150 lines** |
| `ImageLoaderTest` | `ImageLoader` + Skia | `still.png/.jpg/.webp` → 640×480; a broken file returns `null` rather than crashing; **`exif_rot90.jpg` returns 480×640** (the F3 test — red until the fix, which is expected and recorded, not "fixed" by weakening the test) |
| `ThumbnailPipelineTest` | `VideoThumbnailExtractor` + `WokaStorage` + `ImageBitmapCache` | The `S01E02` poster is not monochrome, is ≤ 400 px on its longer side, and the cache file is created; a second call reads the cache instead of decoding; the F1 test — N successive loads of large images do not grow retained references without bound (written after the eviction policy is decided, Phase 7) |

**Files:** 11 files in `jvmTest/kotlin/com/woka/integration/`

## Phase 4: UI automation — Compose

Directory `jvmTest/kotlin/com/woka/ui/`. `runComposeUiTest` from `compose.uiTest`, real composables,
driven through `performClick`/`performKeyInput`/`performMouseInput`. What is checked is a
**scenario**, not a formula: formulas are already covered by Phase 2.

- **Production change, needs its own approval:** the tree currently has **zero** `testTag`. UI tests
  need anchors. The plan adds `Modifier.testTag` narrowly and only where no textual or semantic
  anchor exists: the folder grid (container + cell), the viewer frame, the viewer toolbar (size
  button, `X`), the carousel, the settings overlay. The exact tag list is fixed before the edit;
  nothing but `testTag` is touched in those files.

| File | Scenario |
|------|----------|
| `FolderGridUiTest` | Grid over a temp folder of fixtures: N cells render; clicking a cell in select mode toggles the selection; Ctrl+A selects all; leaving select mode clears the selection |
| `ViewerNavigationUiTest` | Opening the viewer on a given file; ←/→ change file and do not wrap at the ends; clicking Prev/Next; `Esc` calls `onClose` and does not leak the event to the global shortcuts |
| `ViewerZoomUiTest` | The wheel changes the percentage; a double click toggles Fit ↔ Actual; dragging moves the frame when it overflows and does nothing when it does not; resizing the container under an active zoom does not let the frame out of bounds |
| `ViewerChromeUiTest` | The chrome auto-hides on its timer and returns on mouse movement; hovering a popover keeps it visible; the info dialog opens and shows the dimensions |
| `SettingsOverlayUiTest` | Switching the theme in the overlay reaches `SettingsRepository`; toggles write their value; the overlay closes |

Constraints recorded up front: `App()` pulls in `AppDependencies`, a Koin singleton with cached
`by inject()` delegates. The UI tests work at the screen and viewer level, and Koin is started
**once per test class**. If `runComposeUiTest` will not come up headless on Windows, the layer is
marked `@Ignore` with the reason and its coverage moves to manual cases; dropping the layer silently
is not an option.

**Files:** 5 files in `jvmTest/kotlin/com/woka/ui/`, plus narrow `testTag` additions in production UI

## Phase 5: Manual test cases — a directory of their own

A new directory, `ai/docs/testing/manual/`. Only what automation cannot take goes here: visual
correctness, how smooth it feels, OS integration, separate native windows, long runs. Anything an
assertion can check is not written into this directory.

Structure:

```
ai/docs/testing/manual/
  README.md            case format, the id scheme, how to record a run
  TC-IMG-*.md          images: what the eye sees
  TC-VID-*.md          video: what is heard and noticed
  TC-SUB-*.md          subtitles: rendering and positioning
  TC-PIP-*.md          PiP: a separate native window
  TC-SYS-*.md          OS integration
  TC-PERF-*.md         long runs and large files
  results/             run logs: YYYY-MM-DD-<build>.md
```

The format of a case is fixed: `ID`, `Area`, `Preconditions` (which files, which settings), `Steps`
(numbered, imperative), `Expected`, `Why this is not automated`. That last field is mandatory: it is
what stops the list from growing back into "click through everything".

The cases:

**TC-IMG** — PNG/WebP transparency on the light and dark themes; no mush at Actual on a large image;
zoom around the cursor judged by eye; a photo with EXIF rotation (F3); an HDR photo — what it looks
like today (input for the F4 decision); the placeholder on a broken file; behaviour when the file is
deleted from the folder while the viewer is open; a file with no read permission.

**TC-VID** — the first frame arrives without a black flash and the audio starts in sync; the audio
pitch does not drift with speed changes (`VideoPlayerAudioTempo`); how smooth a drag-seek on the
slider is; chapter ticks on the slider and the tooltip carrying the chapter name; switching the audio
track on `S01E01` without tearing the picture; `VideoReloadOverlay` — when it appears and when it
goes; all four EOF modes on a mixed folder.

**TC-SUB** — bitmap subtitles: positioning and scale under zoom and in fullscreen; ASS tags (italic,
colour, positioning) per `implemented/subtitle-ass-full-rendering.md`; customisation (size, colour,
background, vertical shift, offset ±); subtitles are not covered by the carousel or the controls.

**TC-PIP** — entering PiP and coming back, with scale and offset carried both ways; dragging the
window and its corners (`PipCornerFlash`); keys inside PiP; the `VideoAlreadyOpenedDialog` conflict,
both branches; closing the main window while PiP is active.

**TC-SYS** — `Woka.exe "path\to.jpg"` opens the viewer, and `X` leads to the parent's Folder View
with the offer to pin the folder; media keys with `systemMediaControls` on and off; the system media
session (title, artwork, state) per `implemented/system-media-controls.md`; the native folder picker;
the fullscreen window — enter, exit, `Esc`, scale preserved; copying to the clipboard pastes into
Explorer.

**TC-PERF** — playback for ≥ 10 minutes: no A/V drift, no memory growth; an image ≥ 8000 px does not
freeze the UI; fast paging with the arrow key held does not pile up loads; the retained-memory
measurement for F1.

**Files:** `ai/docs/testing/manual/README.md` + 6 case files + `results/.gitkeep`

## Phase 6: The run

- `./gradlew.bat :shared:build` — layers 1–3 green, apart from anything explicitly `@Ignore`d with a
  stated reason.
- `./gradlew.bat :desktopApp:run` and a pass over the Phase 5 manual cases. The result is a file in
  `manual/results/`: each case either "ok" or a defect with steps to reproduce.
- Everything found is merged into one list together with F1–F7.

## Phase 7: The fix pass

Order: crashes and leaks → functional defects → the file size mandate.

- **F7** (storage root) — already done in Phase 0 as a precondition; here only the check that the
  production path is unchanged.
- **F2** (double load) — drop one of the two `LaunchedEffect`s; make sure the `showConflictDialog`
  branch still reloads the bitmap after the PiP conflict is confirmed.
- **F5** (`finally` throwing over an exception) — wrap `stop()`/`release()` in `runCatching`.
- **F3** (EXIF) — apply `Orientation` when decoding a still image; `ImageLoaderTest` goes green. The
  requirement is already written into `pending/media-editor-plan.md` ("EXIF-orientation-aware load"),
  so this fix also closes a precondition of the editor.
- **F1** (cache) — **Valik decides.** Options: an LRU capped by entry count; an LRU capped by total
  pixels; separate caps for grid posters and full-size viewer frames. The plan records the options,
  it does not choose. Once decided, the corresponding part of `ThumbnailPipelineTest` is written.
- **F4** (HDR for photos) — a scope decision: either extend tone mapping to still images, or state
  explicitly in `implemented/hdr-tonemapping-plan.md` that HDR is video-only.
- Files pushed past 130–150 lines **during the fix pass** are split here, along a meaningful
  boundary. Files the review did not touch are only listed and **not split without separate
  approval**.
- Anything found in Phase 6 comes with steps to reproduce; what cannot be confirmed or reproduced is
  logged rather than fixed.

**Files:** determined by the results of Phases 2–6.

## Phase 8: Closing

- `:shared:build` again — green.
- The manual cases re-run over whatever changed, a new file in `manual/results/`.
- `ai/docs/roadmap.md`: Phase 4 (The Viewer) marked as verified; a pointer added to
  `ai/docs/testing/manual/` as a permanent artefact rather than a one-off checklist.
- Requirements that surface along the way go into a new file in `pending/`, not as additions here.

---

## Definition of done

- The pyramid holds by check count: unit > integration > UI automation > manual. Target is roughly
  95 / 55 / 25 / 24.
- `:shared:build` is green; every `@Ignore` carries its reason in the message.
- `ai/docs/testing/manual/` is filled in, every case has its "why this is not automated" field, and a
  run is recorded under `results/`.
- Every finding marked unambiguous is closed; F1 and F4 are either closed by Valik's decision or
  explicitly deferred with a reason.
