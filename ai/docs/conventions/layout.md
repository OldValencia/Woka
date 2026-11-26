# Woka — Package Layout

Moved out of `CLAUDE.md` so it is not loaded into every session. Open it before adding a file or a
package; keep it current when a directory appears, moves or goes.

Package root `com.woka`. **Feature-first.** `app/` wires, `core/` is shared by everyone,
`feature/<name>/` owns one thing. A file's package is its directory; nothing lives outside these
three roots.

```
com.woka/
  app/        App.kt (+AppDependencies, SettingsProjection) · di/
              navigation/ Router.kt, ScreenHost.kt
    shell/    MainShell, GlobalTopBar, WinControls, SmartBreadcrumbs, FloatingDock,
              DockItem, DockSecondaryItem, DockAccessories, TopBarAccessories
  core/
    data/     MediaItem, LocalMediaRepository, MediaKind, WokaStorage,
              Settings{Repository,Store,Mutations}, WokaSettings, PlaybackMode, TrackPrefs
    system/   SystemMediaControls + AWT media keys                        (jvm)
    ui/       theme/ (+ViewerChrome) · icons/ · components/ (Marquee, IosSwitch,
              SegmentedControl, DropdownItem, DetentSlider, FolderCard*, FolderCoverMosaic,
              GridScrollbarAdapter + GridLineMetrics, RenameDialog)
              dialogs/ AWT FileDialog (jvm) · image/ ImageBitmapCache (jvm)
    utils/    DesktopUtils, VideoThumbnail                                (expect/actual)
  feature/
    home/ · folder/ · library/ · search/ · settings/
    viewer/   NativeMediaViewer                                          (expect/actual)
      shell/  screen, state, effects, content, frame, gestures, keys, layout, toolbar,
              carousel, fullscreen, playlist, FileInfo*, Popover*, MediaThumbnails,
              ViewerBackdrop, TransientOverlay, ToolbarPopover, and the types the shell
              speaks in: ViewerMedia, ViewerContext, ViewerEvents
              plus the sizing model: SizeMode, ScaleMath, ZoomScale, PanBounds
        zoom/   zoom *menu UI* only: SizeControl, SizeIndicatorButton, ZoomMenu*
        slider/ MediaSlider*, MediaSliderTrack/Style, MediaVerticalSlider
      image/  ImageLoader
      video/  controls, Subtitle* UI, PlayerSettings*, PlayerSpeed*, chapters, volume
        model/  Cue, MediaTrack(s), MediaChapter, SubtitleSelection…    (commonMain)
        engine/ VideoPlayerState + the UI-facing command surface        (jvmMain)
          decode/ grabber, decode loop, pacer, presenter, renderScale
          audio/ · subtitle/ · track/ · sync/ (A/V offsets, seek) · probe/ (HDR, chapters)
          join/ decoder lanes, their pool, the hand-off at a kept range's join (ME-232)
      pip/    PipManager, GlobalPipWindow, Pip*
    editor/   Phase 5, image + video behind one shell (`implemented/image-editor/`, `pending/video-editor/`)
      core/   media-agnostic: ParamSpec/ParamSet, ToolOp, EditDocument, EditorHistory,
              EditorSession, ToolManifest, EditorTool/CanvasTool, ToolStage,
              EditorToolRegistry, EditorEngine + its factory                (commonMain)
      shell/  the UI every tool inherits: screen, top bar, rail, inspector, param rows,
              canvas host, shortcuts, EditorTokens
      tools/  one directory per tool — a manifest, a param schema, a pure stage()
      engine/ image/ RasterStage, RasterRecipe, RasterFold, surface, renderer  (jvmMain)
      save/   SaveTarget naming, AtomicWrite, save jobs, cache invalidation   (jvmMain)
      di/     EditorModule — the single place a tool is declared             (jvmMain)
desktopApp/   launcher: main(), window, CLI args
```

`expect` in `commonMain`, `actual` in `jvmMain` — **both halves live in the same package**
(`core/utils/VideoThumbnail.kt` / `VideoThumbnail.jvm.kt`).
