# Plan: Verification Pass — Test Pyramid (Viewer + Core)

A full re-check of the viewer and the shared core, built as a **testing pyramid**: a wide base of
unit tests, a smaller layer of integration tests, a thin layer of UI automation on top, and a minimum
of manual cases at the apex. Manual cases live in their own directory of documents, not as a list
inside this plan.

Third of three. Order: `package-restructure-feature-first.md` → `code-quality-refactor.md` →
**this one**. The first two are done; every path below has been checked against the current tree.

**Progress:** Phases 0–3 done · Phase 5 done · **Phase 4 next, and unblocked** — the feasibility gate
is green (`ui/ComposeHarnessTest.kt`), the first product file is written
(`ui/SettingsToggleUiTest.kt`), and the `testTag` production change is approved (2026-08-06).

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
| F8 | `sizeSchemaVersion` defaults to 0 and is **only ever** set to 1 by `migrateSizeSchema` on load. A `settings.json` written by a fresh install therefore carries version 0 and is migrated on the next launch as if it came from the old schema — silently wiping the size defaults chosen in that first session. One line: a state with no file behind it should start at the current version | `core/data/SettingsStore.kt:53` + `WokaSettings.kt:59` | found in Phase 3, unambiguous, `@Ignore`d test written |
| F9 | `SettingsStore.start` subscribes to its save channel inside a coroutine launched on `Dispatchers.IO`, and the channel has no replay — so a `requestSave()` issued in the moment after the constructor returns reaches a flow with **no collector** and is dropped. Four Phase 3 tests hung on it before `awaitWriterReady` existed. Benign in production (the next change writes, and a shutdown hook flushes on exit), so it delays rather than loses | `core/data/SettingsStore.kt:64` | found in Phase 3, low severity, worked around in the harness |

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

## Phase 2: Unit tests — the base of the pyramid — **done**

Directory `jvmTest/kotlin/com/woka/unit/`. Not one touch of the filesystem, of a process, or of the
Compose runtime; `IntSize`/`Offset` are used as value types. `internal` is visible from `jvmTest`
(friend compilation) — confirmed, including `internal` declared in `commonMain`.

**16 files, 172 checks, green.** Three deviations from the table below, each recorded where it bit:

- The subtitle test **was** split, as the table allowed: `SrtParserTest` (12) + `AssParserTest` (14).
- **Date sorting moved out of `FolderStateMapperTest`.** `MediaItem.lastModified` is stat'd in the
  constructor, so two items with different timestamps need two real files — the sort physically
  cannot be exercised on this layer. `NameAsc`/`NameDesc`/`Custom`, folders-first, the hidden and
  media filters, tie stability and the per-folder settings projection are covered here; the date
  order moves to `FolderViewModelTest` in Phase 3. Noted in the file's header comment.
- **`StorageKeyTest` writes files**, as the table already anticipated: the key is `absolutePath |
  length() | lastModified()`, so it needs something on disk to carry the last two. Everything it
  writes is under `withTempStorageRoot`.

Two things checked rather than assumed, both clean, so **no new findings**: `formatFps` uses
`(fps * 1000).toLong()`, and `(long)(29.97 * 1000)` is 29970 on this JVM, not 29969 — the broadcast
rates are now pinned in the test. `MediaItem` construction on a path that does not exist stats
nothing that throws.

One fixture bug caught and fixed while writing, not a defect: `clampOffset` on a pinned axis returns
`-0.0f`, which is the same point on screen but not `assertEquals`-equal to `0f`.

`FileInfoFormat` runs its fractional output through `String.format`, i.e. the default locale, so
`3.30 MB` is `3,30 MB` in half of Europe. That is correct for a UI, and the assertions accept either
mark instead of pinning the suite to one machine.

| File | Target | Key checks |
|------|--------|-----------|
| `ZoomScaleTest` | `feature/viewer/shell/ZoomScale.kt` | `fitPercent`/`fillPercent` with the container wider and narrower than the media; `presetPercent` for every `ScalePreset`; `zoomBounds` on degenerate sizes (`IntSize.Zero`, 1×1, 16000×16000); `clampPercent`, `stepUp`/`stepDown` at the limits; `snapToPreset` — hit and miss; `sizeModeFor`; `sliderFraction` is monotonic and within `0f..1f` |
| `ScaleMathTest` | `shell/ScaleMath.kt` | `computeGraphicsScale` for `Preset(Fit/Fill/Actual)` and `Percent`; `contentOverflows` exactly on the boundary |
| `PanBoundsTest` | `shell/PanBounds.kt` | `panLimits` is 0 when the content does not overflow; `clampOffset` never lets the frame past the edge; `rubberBandOffset` is monotonic, sign-symmetric and decaying |
| `SizeModeTest` | `shell/SizeMode.kt` | `startSizeMode` with `defaultUseZoom` on and off, and a stored percentage of 0 or negative; `percentIn` for `Preset` and `Percent`. `persistSizeState` needs a repository and moves to `SettingsPersistenceTest` |
| `PlaylistWalkTest` | `shell/MediaViewerPlaylist.kt` | `nextPath`: `null` when `repeat != Folder && !shuffle`; `null` with fewer than 2 videos; images stay out of the walk; `Repeat.Folder` wraps to the first; shuffle without folder repeat stops at the end; **`orderFor` is stable across calls** |
| `TrackMatchingTest` | `video/model/TrackMatching.kt` | `matchRef`: exact match; language-only match; `ref = null`; empty list; two candidates sharing a language |
| `SrtParserTest` + `AssParserTest` | `video/engine/subtitle/SubtitleFileParser.kt`, `SubtitleParser.kt` | SRT: multi-line cue, overlapping timings, empty block, `\r\n`, zero duration, a short fraction as tenths, `<i>` stripped but `<<SIGN>>` left alone. ASS: `Dialogue:` with `{\i1}`, `{\an8\pos()}`, escaped `\N` and `\h`, commas inside the text, `Comment:` skipped, a malformed timestamp dropping one line and not the file |
| `HdrInfoTest` | `video/engine/probe/HdrInfo.kt` | (bt709, bt709) → no tone mapping; (smpte2084, bt2020) → tone mapping + wide; HLG/`arib-std-b67`; unknown values do not throw |
| `WokaSettingsTest` | `core/data/WokaSettings.kt` | JSON round trip; defaults; an unknown field in the file is ignored; malformed JSON does not escape |
| `TrackPrefsTest` | `core/data/TrackPrefs.kt`, `PlaybackMode.kt` | `TrackRef` serialises and deserialises; `resumeAppliesTo` at the boundaries (very short video, watched almost to the end) |
| `MediaKindTest` | `core/data/MediaKind.kt` | Classification by extension; case does not matter; unknown extension; file with no extension |
| `FolderStateMapperTest` | `feature/folder/FolderStateMapper.kt` | `NameAsc`/`NameDesc`/`Custom`; folders first under every `SortType`; stable order on equal keys; empty list; the Photos/Videos filters; selection survives a re-sort; `folderSettings` per-folder overrides. **Date order needs real mtimes → `FolderViewModelTest`, Phase 3** |
| `FileInfoFormatTest` | `feature/viewer/shell/FileInfoFormat.kt` | Byte formatting (0, KB, MB, GB), duration (under a minute, hours), dates |
| `VideoThumbnailHeuristicsTest` | `video/engine/VideoThumbnailHeuristics.kt` | Poster frame selection: very short video, zero duration, rejecting a monochrome frame |
| `StorageKeyTest` | `core/data/WokaStorage.kt` | `thumbnailFile` is deterministic; differs for same-named files in different directories; safe for non-ASCII paths; changes with mtime or size. Works on `File` objects through `TempStorageRoot`, reading no more than `length()`/`lastModified()` |

**Files:** 16 in `jvmTest/kotlin/com/woka/unit/` — `ZoomScaleTest` (14), `ScaleMathTest` (10),
`PanBoundsTest` (12), `SizeModeTest` (7), `PlaylistWalkTest` (11), `TrackMatchingTest` (11),
`SrtParserTest` (12), `AssParserTest` (14), `HdrInfoTest` (7), `WokaSettingsTest` (8),
`TrackPrefsTest` (13), `MediaKindTest` (10), `FolderStateMapperTest` (11), `FileInfoFormatTest` (12),
`VideoThumbnailHeuristicsTest` (11), `StorageKeyTest` (9). Every file is under the 150-line mandate.

## Phase 3: Integration tests — **done**

Directory `jvmTest/kotlin/com/woka/integration/`. Real filesystem under `TempStorageRoot`, real
FFmpeg/JavaCV against the Phase 1 corpus, real ViewModels and repositories. No Compose runtime.

**20 files, 152 checks, green** (3 `@Ignore`d: F8, F3, F1). Unit + integration together: **324
checks, 0 failures.**

What the layer needed beyond the table below:

- **A build change, not a production one.** `shared/build.gradle.kts` now sets `woka.storageRoot` on
  the `Test` task, pointing at `build/test-storage`. Every `SettingsStore` registers a JVM shutdown
  hook that writes `settings.json`, and those hooks fire long after a per-test `withTempStorageRoot`
  has restored the property — without a default for the whole test JVM they would have written into
  the real `Documents/OldValencia/Woka`. F7 fixed the *reachability*; this makes the safe root the
  default rather than something each test has to remember. Verified after a full run: the real
  directory is untouched, `build/test-storage/settings.json` holds what the hooks wrote.
- **`support/` grew a harness layer** so the split files below share one definition each:
  `Await` (`waitFor`/`awaitTrue`/`awaitValue`), `HotFlow` (`withCollector` — both ViewModels publish
  `WhileSubscribed`, so with no collector `state.value` never leaves its initial value and every
  assertion would pass for the wrong reason), `SettingsWriter` (`awaitWriterReady`, `onSettingsDisk`,
  `awaitSettingsWrite`), `MediaProbes` (`onGrabber`, `onSubtitleStreams`, `firstCueStart`),
  `PlayerHarness` (`onPlayer`, `onOpenPlayer`), `FolderHarness`, `MediaCopies`, `KoinOnce`.
- **Koin is started once for the whole test JVM and never stopped** (`startKoinOnce`). The decode
  loop reads `AppDependencies.settingsRepository` for playback resume, and `AppDependencies` caches
  its `by inject()` delegates for the life of the process — restarting the container between tests
  would leave them pointing at a dead one.
- **The virtual clock does not reach any of this.** `SettingsStore` debounces on `Dispatchers.IO`,
  the listing runs on IO, the sort on Default, and frames arrive on the decoder's own threads. The
  ViewModel tests install an `UnconfinedTestDispatcher` as `Dispatchers.Main` — enough for the scope
  to exist — and then **poll**, because everything underneath is real.

**A fixture was added, and the corpus README says why.** `textsubs.mkv` (23 KB): every container in
Phase 1 was cut to three or four seconds, which sliced out every subtitle event. `S01E01` and
`DualVideo` still *declare* their `subrip` tracks and the probe lists them — the failure message read
`declares [#9 subrip, #10 subrip, #11 subrip], parsed {}` — so the embedded-text path had no fixture
at all. Phase 1's "ffmpeg cannot synthesise this" reasoning covers bitmap subtitles, not text ones,
so it is muxed rather than worked around. The declared-but-empty case is now itself a test.

Two claims from the table below **could not be asserted on this corpus** and are recorded rather than
faked:

- *"the S01E02 poster is not monochrome"* — every fixture is a few seconds of low-motion footage that
  `isMonochrome` rejects, including the 1080p one, so the extractor rightly falls back to its first
  candidate. The pipeline is asserted end to end (size, the file written, the bytes read back);
  poster *quality* moves to a manual case, TC-VID.
- *"`broken.mkv` errors"* for the thumbnail path — its first 2 KB still hold a decodable frame, so it
  is an error path for playback but not for the extractor. A file of pure garbage covers that; the
  truncated container gets its own test saying the extractor is right to use what it finds.

Two further notes, both checked rather than assumed: `kotlinx.serialization` has `encodeDefaults`
off, so a `false` reaches `settings.json` by being **absent** — asserting its presence would assert
the wrong thing. And `ImageLoader.load` does not guard `Image.makeFromEncoded`; it throws on a
corrupt file, and `MediaThumbnails` is where that becomes a placeholder. Both are tested as they are.

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

**Files:** 20 in `jvmTest/kotlin/com/woka/integration/` — the 150-line mandate forced seven splits,
each along a real boundary: `SettingsPersistenceTest` (9) + `SettingsRecordsTest` (9) +
`SizeStatePersistenceTest` (3) · `SubtitleStreamTest` (6) + `BitmapSubtitleTest` (8) ·
`SubtitleFileTest` (10) + `SubtitleCharsetTest` (9) · `MediaProbeTest` (10) +
`MediaChapterProbeTest` (5) · `FolderViewModelTest` (6) + `FolderSelectionTest` (7) ·
`VideoSeekTest` (8) + `VideoTransportTest` (8) · `ThumbnailPipelineTest` (7) +
`ThumbnailCacheTest` (5). Plus `SampleMediaTest` (3), `LocalMediaRepositoryTest` (11),
`HomeViewModelTest` (9), `ImageLoaderTest` (10), `VideoPlayerEngineTest` (9). Longest file: 146.

## Phase 4: UI automation — Compose

Directory `jvmTest/kotlin/com/woka/ui/`. `runComposeUiTest` from `compose.uiTest`, real composables,
driven through `performClick`/`performKeyInput`/`performMouseInput`. What is checked is a
**scenario**, not a formula: formulas are already covered by Phase 2.

- **Production change — approved by Valik, 2026-08-06.** The tree has **zero** `testTag`. The plan
  adds `Modifier.testTag` narrowly: the folder grid (container + cell), the viewer frame, the viewer
  toolbar (size button, `X`), the carousel, the settings overlay. Nothing but `testTag` is touched
  in those files.

  What the approval rests on: `testTag` writes one string into the semantics tree and does nothing
  else. It takes no part in measurement, layout, drawing or input, so **not one pixel changes** —
  it is `data-testid` for Compose, the thing an e2e runner hooks onto. The two honest costs: the
  string ships in the release binary (nobody strips it) and each tagged element gains one semantics
  modifier node. Both are immeasurable against composition, but neither is literally zero.

  **The rule still binds: a tag only where no textual or semantic anchor already exists.** The list
  above is a draft of candidates, not a licence — `SettingsToggleUiTest` already drives a real
  settings row with no tag at all, because the row has both. Check each candidate against a real
  anchor first, and tag only what fails that check. Approval removes the blocker; it does not turn
  the draft into the answer.

  **Do not land tags ahead of the tests that use them.** A tagged tree with nothing exercising it is
  a production edit no one can justify from the diff — write each `*UiTest` and the tags it needs in
  the same pass.

- **Anchor check, first candidate — the folder grid.** Done, so `FolderGridUiTest` starts from a
  verdict rather than a survey:

  | Candidate | Anchor today | Verdict |
  |-----------|--------------|---------|
  | grid container, `FolderGrid.kt:93` | a bare `LazyVerticalGrid` — no text, no click action, no content description | **tag needed** |
  | grid cell, `FolderCard(path, onClick, showTitle = true, …)` | renders `data.name` as the title beneath the cover, and carries `onClick` | **no tag** — `onNodeWithText(fileName)` plus `hasClickAction()` |

  The cell's anchor is **conditional**: the title is drawn only when `showTitle` is on, which the
  grid feeds from the `showTitles` setting. `FolderGridUiTest` therefore drives `FolderCard`
  directly with `showTitle = true`, the same way `SettingsToggleUiTest` drives a single row — that
  keeps the anchor a property of the test's own setup instead of a setting it hopes is enabled. If a
  later scenario has to go through the real grid with titles off, that cell earns a tag then, and
  only then.

  `FolderScreen.kt:55` also renders a literal `"Loading..."`, which is a ready-made anchor for the
  loading state — no tag, and no need to reach into `isLoading`.

  **The list is already smaller than it looked.** `SettingsToggleUiTest` (4 checks) is written and
  green **without a single tag**: `SettingsToggleRow` renders its title and subtitle as text, and
  `IosSwitch` carries a click action, so `onNodeWithText` plus `hasClickAction()` cover the row
  completely. Every toggle-shaped control in Settings is reachable the same way — check each
  candidate against a real anchor before tagging it, rather than tagging the list as drafted.

- **The entry point is `support/UiHarness.kt`,** so no scenario re-derives the obvious:
  `onUiWithSettings { repository -> … }` for anything that has to prove a write landed, `onUi { … }`
  for pure UI. Both go through the v2 runner. Two shapes worth knowing before writing the next file:
  the v2 `runComposeUiTest` takes a `CoroutineContext` **first**, so a block is called rather than
  passed through; and the receiver type is still `androidx.compose.ui.test.ComposeUiTest` — there is
  no v2 `ComposeUiTest`, only the runner moved. A test class using the harness needs
  `@OptIn(ExperimentalTestApi::class)` because that receiver is experimental.

- **Two things the first product test established, so Phase 4 need not rediscover them:**
  `SettingsOverlay(isOpen, onClose, repo)` takes its repository as a parameter and does not reach
  into Koin, so a screen can be driven with a real `SettingsRepositoryImpl` on a throwaway root —
  no container needed for this one. And `Theme.colors` resolves with **no theme provider wrapped
  around the composable**: production components render bare in `setContent`, so the tests need no
  scaffolding composable of their own.

| File | Scenario |
|------|----------|
| `FolderGridUiTest` | Grid over a temp folder of fixtures: N cells render; clicking a cell in select mode toggles the selection; Ctrl+A selects all; leaving select mode clears the selection |
| `ViewerNavigationUiTest` | Opening the viewer on a given file; ←/→ change file and do not wrap at the ends; clicking Prev/Next; `Esc` calls `onClose` and does not leak the event to the global shortcuts |
| `ViewerZoomUiTest` | The wheel changes the percentage; a double click toggles Fit ↔ Actual; dragging moves the frame when it overflows and does nothing when it does not; resizing the container under an active zoom does not let the frame out of bounds |
| `ViewerChromeUiTest` | The chrome auto-hides on its timer and returns on mouse movement; hovering a popover keeps it visible; the info dialog opens and shows the dimensions |
| `SettingsOverlayUiTest` | Switching the theme in the overlay reaches `SettingsRepository`; toggles write their value; the overlay closes |

Constraints recorded up front: `App()` pulls in `AppDependencies`, a Koin singleton with cached
`by inject()` delegates — Phase 3 established that the container must be started **once for the test
JVM and never stopped** (`support/KoinOnce.kt`), not per class, or those delegates end up pointing at
a dead container. The UI tests work at the screen and viewer level.

**The feasibility question is answered: the layer is viable, and does not need `@Ignore`.**
`ui/ComposeHarnessTest.kt` is a gate, not a product test — six checks on composables it owns, no
production code touched. All six pass headless on Windows:

| Mechanism | Verdict |
|-----------|---------|
| `runComposeUiTest` + `setContent` composes | works |
| a node is laid out and `assertIsDisplayed` | works |
| `Modifier.testTag` is a usable anchor | works |
| `performClick` → state → recomposition | works |
| `performKeyInput` to a node holding focus via `FocusRequester` | works |
| `performMouseInput { click() }` | works |

So the fallback in this section is not taken. The gate file stays in the tree: if a toolchain bump
breaks the layer, it says which mechanism went rather than leaving five product tests failing for an
unclear reason.

Two things for the next session to inherit rather than rediscover:

- **Use `androidx.compose.ui.test.v2.runComposeUiTest`.** The v1 function of the same name is
  deprecated; v2 drives the composition on a `StandardTestDispatcher`, so coroutines queue as they
  do in production rather than running eagerly. All six checks pass unchanged under it — no extra
  `waitForIdle` was needed — but the product scenarios must be written against v2 so they do not
  depend on eager execution the app never has.
- `click()` inside `performMouseInput` needs an explicit `androidx.compose.ui.test.click` import.

The remaining unknown for Phase 4 is **not** the runtime; it is the `testTag` production change
above, which still needs its own approval before anything is written.

**Files:** 5 in `jvmTest/kotlin/com/woka/ui/`, plus whatever `testTag` additions survive the
anchor check above. Two exist already, 10 checks, green: `ComposeHarnessTest` (6) as the gate, and
`SettingsToggleUiTest` (4) as the first product scenario — the "toggles write their value" half of
`SettingsOverlayUiTest`, done without touching production. Remaining: the theme switch and the
overlay close for that file, plus `FolderGridUiTest`, `ViewerNavigationUiTest`, `ViewerZoomUiTest`,
`ViewerChromeUiTest`.

## Phase 5: Manual test cases — a directory of their own — **done**

**`ai/docs/testing/manual/` is written: README + 6 case files + `results/.gitkeep`, 38 cases.**
Every case carries its mandatory *Why this is not automated* field, and each one names the automated
test it defers to rather than duplicating it — TC-VID-04 points at `ThumbnailPipelineTest`, TC-SUB-06
at `SubtitleCharsetTest`, TC-IMG-02 at `ScaleMathTest`, and so on. Four cases are tied to open
findings: TC-IMG-04 (F3), TC-IMG-05 (produces the input for the F4 scope decision), TC-PERF-04
(produces the measurement for the F1 policy decision), TC-VID-04 (the claim Phase 3 could not assert
on this corpus).

**One number to settle in Phase 6.** The estimate below was ~24; the real list is 38, and the
Definition of Done requires `UI automation > manual`. Phase 4 is 5 files plus the 6-check gate, so
the two layers will land close together. Either Phase 4 comes in above 38 or some of these cases
fold together — decide it once Phase 4 is written, and do not pad one layer to satisfy the
inequality.

The section below is the original plan for this phase, kept for the reasoning.


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
- **F8** (size defaults wiped on the first restart) — a state with no file behind it should start at
  the current schema version, so `migrateSizeSchema` only ever sees a file that really is old. Drop
  the `@Ignore` on `SettingsPersistenceTest`'s last case; it then passes.
- **F9** (first save dropped) — optional, and lower priority than the rest: give the save channel
  `replay = 1`, or await the collector in `start()`. Nothing is lost today because the shutdown hook
  flushes; if it is left as is, say so in `SettingsStore` and delete `awaitWriterReady`'s workaround
  comment rather than leaving the harness implying a bug that was decided against.
- **F1** (cache) — **Valik decides.** Options: an LRU capped by entry count; an LRU capped by total
  pixels; separate caps for grid posters and full-size viewer frames. The plan records the options,
  it does not choose. Once decided, the corresponding part of `ThumbnailCacheTest` is written.
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

- The pyramid holds by check count: unit > integration > UI automation > manual. Target was roughly
  95 / 55 / 25 / 24; the layers landed at **172 / 152**, so the shape holds with room to spare.
- `:shared:build` is green; every `@Ignore` carries its reason in the message.
- `ai/docs/testing/manual/` is filled in, every case has its "why this is not automated" field, and a
  run is recorded under `results/`.
- Every finding marked unambiguous is closed; F1 and F4 are either closed by Valik's decision or
  explicitly deferred with a reason. F8 and F9 were found in Phase 3 and join the Phase 7 list —
  F8 has a red-until-fixed test already written and `@Ignore`d, F9 is worked around in the harness.
