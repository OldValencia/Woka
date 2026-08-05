# Plan: Feature-First Package Restructure

Move from layer-first (`data/ di/ system/ ui/ utils/`) to feature-first: `app/`, `core/`,
`feature/<name>/`. Not a single line of logic changes — only `package`/`import` statements, physical
file locations, and three splits forced by the package division (Phases 9 and 10).

First of three. Order: **this** → `code-quality-refactor.md` → `viewer-verification-pass.md`.
Everything deliberately deferred here is picked up by the next plan.

---

## Established facts (verified against the code)

1. **The Image Viewer and the Video Viewer are one component, not two.** `MediaViewerScreen` creates
   `videoState: VideoPlayerState?` and threads it through `MediaViewerPlayer` → `MediaViewerContent`
   → `MediaViewerFrame`; the video branches switch on `videoState != null`.
2. **There is currently no image-only code in `ui/viewer/` at all.** `HdrImagePainter` paints HDR
   *video* frames (`MediaViewerFrame.kt:38,44` — chosen by `videoState.currentFrameHdrInfo`). The one
   genuinely image-only line is `Image.makeFromEncoded(item.file.readBytes())` at
   `ThumbnailLoader.kt:45`.
3. Video-specific code is ~60 files: all of `player/`, `Subtitle*`, `Player*`, `Pip*`, the time
   slider, the volume stack.
4. Grid previews go through Coil (`FolderCardPreview.kt`); the viewer goes through `ThumbnailLoader`.
   These are two independent image-loading paths.

Hence the viewer split: **`shell/` (the shared shell) + `image/` + `video/` + `pip/`**, not mirrored
`image/`/`video/` packages. `image/` gets real content from the `ThumbnailLoader` split (Phase 9).

---

## Target structure

```
com.woka/
  app/
    App.kt                      AppDependencies + root composable
    di/                         Koin.kt (common), PlatformModule.kt (jvm)
    navigation/                 Router.kt, Screen
    shell/                      MainShell, GlobalTopBar, WinControls, SmartBreadcrumbs,
                                FloatingDock, DockItem, DockAccessories, TopBarAccessories
  core/
    data/                       MediaItem, LocalMediaRepository, SettingsRepository(+Impl),
                                SettingsStore, SettingsMutations, WokaSettings,
                                PlaybackMode, TrackPrefs
    system/                     SystemMediaControls(+Factory), AwtMediaKeyControls,
                                NoopSystemMediaControls, SystemMediaSession
    ui/
      theme/                    Theme.kt
      icons/                    Icons.kt
      components/               Marquee, IosSwitch, SegmentedControl, DropdownItem,
                                FloatingPanelModifier, GridScrollbarAdapter, RenameDialog,
                                FolderCard, FolderCard{Overlays,Preview,Style,Utils}
      dialogs/                  Dialogs.kt (jvm, AWT FileDialog)
      image/                    ImageBitmapCache (new, Phase 9)
    utils/                      DesktopUtils, VideoThumbnail (expect/actual)
  feature/
    home/                       HomeScreen, HomeViewModel
    folder/                     FolderScreen, FolderViewModel, FolderActions,
                                FolderFileOperations, FolderStateMapper, FolderContent,
                                FolderGrid, FolderTopBar, FolderDockSetup, FolderOverlays
    library/                    LibraryPopup, LibraryFilterMenu, LibraryTopAccessory
    search/                     SearchOverlay, SearchInput, SearchResultsList
    settings/                   SettingsOverlay, SettingsOverlayContent, SettingsPanels,
                                SettingsComponents, SettingsToggleRow, SettingsDangerRow,
                                SettingsMinutesStepper, MediaViewerSettingsPanel,
                                VideoPlayerSettingsPanel
    viewer/
      NativeMediaViewer.kt      expect (common) + actual (jvm) — entry point
      shell/                    screen, state, effects, content, frame, gestures, keys,
                                layout, toolbar, carousel, navigation, fullscreen, playlist,
                                FileInfo*, Popover*, tooltips, HUD overlays,
                                MediaThumbnails, and the sizing model itself —
                                SizeMode, ScaleMath, ZoomScale, PanBounds,
                                MediaViewerZoomActions
        zoom/                   the zoom *menu UI* only: SizeControl, SizeIndicatorButton,
                                ZoomMenu{Input,Popover,PresetRow,Slider}
        slider/                 MediaSlider, MediaSliderHover, MediaSliderTooltip,
                                MediaVerticalSlider
      image/                    ImageLoader (new, Phase 9)
      video/                    controls, subtitle UI, PlayerSettings*, PlayerSpeed*,
                                chapters, audio extension, volume, HdrImagePainter,
                                MediaSliderChapters, VideoReloadOverlay,
                                VideoAlreadyOpenedDialog, MediaViewerSystemControlsEffect
        model/                  (commonMain) Cue, MediaTrack, MediaTracks, MediaChapter,
                                ExternalSubtitle, SubtitleImage, SubtitleSelection,
                                TrackMatching
        engine/                 (jvmMain) the whole current jvm `player/`: decode, audio, seek,
                                tracks, subtitles, HDR probe, chapters, resume,
                                VideoThumbnailExtractor
      pip/                      PipManager, GlobalPipWindow (expect+actual), PipCornerFlash,
                                PipKeyHandler, PipWindowDragModifier
    editor/                     empty, reserved for roadmap Phase 5 (`image/`, `video/`)
```

### Invariants that must not be broken

- **`expect`/`actual` live in the same package.** Pairs: `utils/DesktopUtils`,
  `utils/VideoThumbnail`, `viewer/NativeMediaViewer`, `viewer/pip/GlobalPipWindow`,
  `di/Koin`↔`di/PlatformModule`, `data/SettingsRepository`↔`data/SettingsRepositoryImpl`. Each pair
  moves in a single operation.
- **Directory = package.** A file in `feature/viewer/shell/zoom/` declares
  `package com.woka.feature.viewer.shell.zoom`.
- Generated resources (`woka.shared.generated.resources.Res`) are not touched.
- The anti-FQN rule is mandatory in moved files: every inline `com.woka.*` must become an import
  (Phase 11) — otherwise it simply stops compiling.

---

## Phase mechanics

Every phase ends with a green `./gradlew.bat :shared:compileKotlinJvm -q`. The order is chosen so
each phase moves a dependency leaf and the import fixes land in one pass.

One phase:
1. Move the files per that phase's map.
2. Rewrite the `package` line in every moved file.
3. Rewrite `import com.woka.<old path>` to the new one **across the whole tree**
   (`shared/src/**`, `desktopApp/src/**`), including inline FQNs where present.
4. Build, and wait for the result.

---

## Phase 1: `core/utils`, `core/system`, `core/data`

- `utils/DesktopUtils.kt` (common+jvm) → `core/utils/DesktopUtils.kt`
- `utils/VideoThumbnail.kt` (common) + `VideoThumbnail.jvm.kt` (jvm) → `core/utils/`
- `system/*` (5 jvm files) → `core/system/`
- `data/LocalMediaRepository.kt` (holds `sealed class MediaItem`) → `core/data/`
- `data/{PlaybackMode,SettingsMutations,SettingsRepository,TrackPrefs,WokaSettings}.kt` → `core/data/`
- `data/{SettingsRepositoryImpl,SettingsStore}.kt` (jvm) → `core/data/`

**Files:** 12 files move; imports change almost everywhere (`com.woka.data.*` →
`com.woka.core.data.*` is the single largest replacement in the project).

## Phase 2: `core/ui`

- `ui/theme/Theme.kt` → `core/ui/theme/`
- `ui/icons/Icons.kt` → `core/ui/icons/`
- `ui/components/{Marquee,IosSwitch,SegmentedControl,DropdownItem,FloatingPanelModifier,
  GridScrollbarAdapter,RenameDialog}.kt` → `core/ui/components/`
- `ui/components/{FolderCard,FolderCardOverlays,FolderCardPreview,FolderCardStyle,
  FolderCardUtils}.kt` → `core/ui/components/` (the card is shared: called from both
  `HomeScreen.kt:102` and `FolderGrid.kt:120`)
- `ui/components/dialogs/Dialogs.kt` (jvm) → `core/ui/dialogs/`

**Files:** 15 files.

## Phase 3: `app/`

- `App.kt` → `app/App.kt`
- `di/Koin.kt` (common) + `di/PlatformModule.kt` (jvm) → `app/di/`
- `navigation/Router.kt` → `app/navigation/`
- `ui/components/{MainShell,GlobalTopBar,WinControls,SmartBreadcrumbs,FloatingDock,DockItem,
  DockAccessories,TopBarAccessories}.kt` → `app/shell/`

`desktopApp/src/main/kotlin/com/woka/main.kt` imports `com.woka.App` and the Koin modules — fixed
here.

**Files:** 12 files + `main.kt`.

## Phase 4: `feature/home`, `feature/folder`, `feature/library`, `feature/search`

- `ui/{HomeScreen,HomeViewModel}.kt` → `feature/home/`
- `ui/{FolderScreen,FolderViewModel,FolderActions,FolderFileOperations,FolderStateMapper}.kt` →
  `feature/folder/`
- `ui/components/{FolderContent,FolderGrid,FolderTopBar,FolderDockSetup,FolderOverlays}.kt` →
  `feature/folder/`
- `ui/components/{LibraryPopup,LibraryFilterMenu,LibraryTopAccessory}.kt` → `feature/library/`
- `ui/components/{SearchOverlay,SearchInput,SearchResultsList}.kt` → `feature/search/`

**Files:** 18 files.

## Phase 5: `feature/settings`

- `ui/components/{SettingsOverlay,SettingsOverlayContent,SettingsPanels,SettingsComponents,
  SettingsToggleRow,SettingsDangerRow,SettingsMinutesStepper,MediaViewerSettingsPanel,
  VideoPlayerSettingsPanel}.kt` → `feature/settings/`

After this phase `ui/components/` is empty — delete it.

**Files:** 9 files.

## Phase 6: `feature/viewer/pip`

- `ui/viewer/pip/PipManager.kt` → `feature/viewer/pip/`
- `ui/viewer/GlobalPipWindow.kt` (common expect) + `ui/viewer/GlobalPipWindow.kt` (jvm actual) →
  `feature/viewer/pip/` — moved **together**, otherwise the `actual` loses its `expect`
- `ui/viewer/{PipCornerFlash,PipKeyHandler,PipWindowDragModifier}.kt` → `feature/viewer/pip/`

`App.kt:175` calls `GlobalPipWindow` — fix the import.

**Files:** 6 files.

## Phase 7: `feature/viewer/video/model` and `feature/viewer/video/engine`

- commonMain `ui/viewer/player/{Cue,ExternalSubtitle,MediaChapter,MediaTrack,MediaTracks,
  SubtitleImage,SubtitleSelection,TrackMatching}.kt` → `feature/viewer/video/model/`
- jvmMain `ui/viewer/player/*.kt` (33 files) → `feature/viewer/video/engine/`

No subpackages inside `engine/` at this step — splitting the engine into `decode/ audio/ subtitle/
track/` is deferred to `code-quality-refactor.md` (otherwise this phase stops being verifiable).

**Files:** 41 files.

## Phase 8: `feature/viewer/video` (UI)

- `MediaViewerVideoControls.kt`, `MediaViewerTimeSlider.kt`, `MediaViewerVolumeControl.kt`,
  `MediaViewerVolumeOverlay.kt`, `MediaViewerAudioExtension.kt`, `MediaSliderChapters.kt`
- `PlayerSettings{Control,Menu,Overlay,Playback,Sync}.kt`, `PlayerChapterSection.kt`,
  `PlayerResetRow.kt`, `PlayerSpeed{Notice,Slider,SliderParts}.kt`
- `Subtitle{ActionRow,BitmapLayer,Control,CustomizeControls,CustomizePanel,Overlay,Popover,Style,
  VerticalOverlay}.kt`
- `VideoReloadOverlay.kt`, `VideoAlreadyOpenedDialog.kt`, `MediaViewerSystemControlsEffect.kt`
- `HdrImagePainter.kt` → `feature/viewer/video/` — despite the name it is selected purely by
  `videoState.currentFrameHdrInfo` and has nothing to do with still images today. Whether HDR
  tone-mapping is needed for HDR photos is a review question, not a refactor one (see
  `viewer-verification-pass.md`).

**Files:** 28 files.

## Phase 9: `feature/viewer/image` — splitting `ThumbnailLoader`

`ThumbnailLoader.kt` is 175 lines (mandate: 130–150) and mixes three unrelated things: full-size
still decoding for the viewer, JavaCV poster-frame extraction for video, and a shared in-memory
cache. The `image/` and `video/engine/` packages require it to be cut, so the split is part of the
move, not a separate initiative.

- Create `core/ui/image/ImageBitmapCache.kt` — the `ConcurrentHashMap<String, ImageBitmap>` plus
  `ioDispatcher`.
  *The cache eviction policy is deliberately left unchanged here* — that is behaviour, and it is
  handled in the review plan (§ "unbounded full-size bitmap cache").
- Create `feature/viewer/image/ImageLoader.kt` — still-image decoding (`Image.makeFromEncoded` +
  cache).
- Create `feature/viewer/video/engine/VideoThumbnailExtractor.kt` — `load`, `extract`, `grabFrameAt`,
  `saveAndDecode`, plus `ThumbnailCacheFile.kt` (moves here too).
- Create `feature/viewer/video/engine/VideoThumbnailHeuristics.kt` — `buildTimestampCandidates` and
  `isMonochrome`. A sixth file rather than the five first sketched: the extractor in one piece came
  to ~160 lines, past the mandate. The cut is on a real seam — these two functions are the
  "where to look" and "is this frame worth showing" judgement calls, not a severed tail.
- The `FFmpegLogCallback`/`av_log_set_level` init becomes `quietFfmpegLogs()` in the engine, **called
  from the facade's `init`, not the extractor's.** In the original it sat in `ThumbnailLoader`'s
  object initialiser, so it fired on the first load of *any* item, still or video. Putting it in the
  extractor would delay it to the first video poster-frame decode and leave the CLI
  "open a video file directly" path with FFmpeg logging still loud. Facade-side init reproduces the
  original firing moment exactly, and keeps bytedeco out of `shell`.
- Create `feature/viewer/shell/MediaThumbnails.kt` — a facade `suspend fun loadThumbnail(item:
  MediaItem): ImageBitmap?` dispatching `MediaItem.Video` → extractor, otherwise → `ImageLoader`.
  This is the single entry point called by `NativeMediaViewer.kt:16` and `VideoThumbnail.jvm.kt:9`.
- Delete `ThumbnailLoader.kt`.

The public contract (`loadThumbnail(item)`) is preserved exactly; behaviour does not change.

**Files:** `ThumbnailLoader.kt` (deleted), `ThumbnailCacheFile.kt`, +4 new, `NativeMediaViewer.kt`
(jvm), `core/utils/VideoThumbnail.jvm.kt`.

## Phase 10: `feature/viewer/shell`

Everything left in `ui/viewer/`:

- Screen and lifecycle: `MediaViewerScreen`, `MediaViewerScreenEffects`, `MediaViewerEffects`,
  `MediaViewerState`, `MediaViewerPlayer`, `MediaViewerPlaylist`
- Render and gestures: `MediaViewerContent`, `MediaViewerFrame`, `MediaViewerContentGestures`,
  `MediaViewerTapGestures`, `MediaViewerBackgroundPath`
- Keyboard: `MediaViewerKeyHandler`, `MediaViewerShortcutEffect`, `MediaViewerPlayerModifier`
- Chrome: `MediaViewerLayoutWrapper`, `MediaViewerToolbar`, `MediaViewerCarousel`,
  `MediaViewerNavigationButtons`, `MediaViewerGlobalControls`, `GlobalControlButtons`,
  `MediaViewerTooltip`, `ViewerTooltip`, `MediaOverlays`
- Fullscreen: `MediaViewerFullscreen{Window,Surface,Overlays}`
- File info: `FileInfoDialog`, `FileInfoFormat`, `FileInfoStreamCards`
- Popovers: `PopoverHoverState`, `PopoverListRow`
- Misc: `MediaViewerUtils` (clipboard)
- `shell/zoom/`: `SizeMode`, `SizeControl`, `SizeIndicatorButton`, `ScaleMath`, `ZoomScale`,
  `ZoomMenu{Input,Popover,PresetRow,Slider}`, `PanBounds`, `MediaViewerZoomActions`
- `shell/slider/`: `MediaSlider`, `MediaSliderHover`, `MediaSliderTooltip`, `MediaVerticalSlider`
- `NativeMediaViewer.kt` (common expect + jvm actual) → `feature/viewer/` (one level above shell),
  both halves in one operation

**Files:** ~44 files. After this phase `ui/` is empty in both source sets — delete it.

## Phase 11: mandatory cleanup

These are not "drive-by improvements": without them the project either does not compile, or violates
the mandate in files we have just touched.

- **Inline FQNs — 23 of them across 12 files.** The ones starting with `com.woka.` break physically
  from the move; the rest sit in the very same moved files and are fixed in the same pass:
  - `ui/components/FolderCardPreview.kt` — 7 × `coil3.compose.AsyncImage`
  - `ui/HomeScreen.kt` — 5 (`com.woka.ui.components.LocalDockAccessories`,
    `com.woka.utils.openDirectoryPicker`, `com.woka.AppDependencies`, 3 × `DropdownItem`)
  - `ui/components/SearchOverlay.kt` — 2 × `androidx.compose.ui.graphics.TransformOrigin`
  - one each: `App.kt:159` (`java.io.File`), `LibraryPopup.kt:72` (`java.io.File`),
    `FolderCardUtils.kt`, `FolderCard.kt`, `DockItem.kt`, `HomeViewModel.kt:46`
    (`com.woka.data.PinnedSession`), `di/Koin.kt`, `system/AwtMediaKeyControls.kt`,
    `desktopApp/main.kt`
- **`shared/src/jvmMain/kotlin/com/woka/Scratch.kt`** — a dev script with `fun main()` and a
  hardcoded `D:\Movies\Avatar...` path, compiled into the artifact. Proposed for deletion.
  **Requires an explicit yes from Valik before deleting** — if the file is wanted, it moves to
  `core/system/` as a debug tool, but reading the path from an argument rather than a constant.
- Create `feature/editor/{image,video}/.gitkeep` — a claim on roadmap Phase 5.

**Files:** 12 files with FQNs, `Scratch.kt`, 2 × `.gitkeep`.

## Phase 12: documentation

- `CLAUDE.md` → the "Layout — package root `com.woka`" section: replace the tree with the target one.
- `.claude/AGENTS.md` → the anti-FQN grep example contains `com\.woka\.` — still valid, no edit
  needed.
- `ai/docs/roadmap.md` → note the completed package restructure under Phase 3.

**Files:** `CLAUDE.md`, `ai/docs/roadmap.md`

---

## Verification after every phase

```powershell
./gradlew.bat :shared:compileKotlinJvm -q
```

After Phase 12, in full:

```powershell
./gradlew.bat :shared:build
./gradlew.bat :desktopApp:run
```

Plus a mechanical anti-FQN sweep over the whole tree (never by eye):

```
Grep(pattern: "(androidx\.|java\.|javax\.|kotlinx\.|org\.bytedeco\.|org\.jetbrains\.|com\.woka\.|coil3\.|org\.koin\.)[a-z0-9_]+\.",
     path: "shared/src", output_mode: "content", -n: true)
```

Any hit outside an `import`/`package` block is a violation.

## Not in this plan — handed over to `code-quality-refactor.md`

- Splitting `video/engine/` into subpackages (`decode/`, `audio/`, `subtitle/`, `track/`, `sync/`,
  `probe/`) — done where the engine moves to collaborators (plan 2, Phase 9).
- Splitting files past 150 lines (`GridScrollbarAdapter` 184, `FolderCard` 182, `App` 177,
  `FolderGrid` 169, `FolderContent` 163, `SubtitleCustomizePanel` 155, `VideoPlayerState` 151,
  `FolderCardPreview` 151, `DockItem` 151, `main.kt` 151) — plan 2, Phase 10, after Phases 2–9 have
  already changed the size of most of them. The exception is `ThumbnailLoader`, whose split is forced
  by the package division (Phase 9 here).
- Any behaviour change. If a phase demands changing logic, it stops, the question is raised, and the
  finding is recorded in plan 2's Phase 1 (the intake section) with file and line **at the moment of
  discovery**.
