# Woka — Claude Code Entry Point

Compose Multiplatform **Desktop** (JVM only) media viewer/editor. Apple HIG, minimalist.
Working mandates — workflow, verification, the rules that gate a change: @./ai/docs/conventions/mandates.md

## Hard rules

**Git: only on an explicit instruction.** No commit/add/push/branch/checkout/reset/stash/rm/mv/tag on
your own initiative. An approved plan that *mentions* a commit is not permission to commit — it
describes what the user will do. Finish the work, say what's ready and what a commit would contain,
stop. Read-only `status`/`log`/`diff` only when the task needs it. Unsure → ask.

**Don't guess.** Unknown cause, ambiguous requirement, or unclear whether an action is wanted → add
logging, ask, or both. By the second unconfirmed hypothesis stop theorising: instrument the suspects,
hand over the log lines, state which reading means what. Mark diagnostics `TEMPORARY (§ref)`; remove
them once the cause is known and grep to prove it. Check your own test fixtures before blaming code.

## `ai/` — design source of truth

Read the relevant doc before planning or implementing; never invent behaviour that contradicts it.

- `docs/general_idea.md` — vision, "Linked Folders" paradigm, use cases
- `docs/roadmap.md` — phases, CLI routing (`Woka.exe [path]`)
- `docs/conventions/` — everything about how to work here: `mandates.md` (gates) ·
  `code-style/README.md` (**router** — open it, then the one file for what you are writing) ·
  `feature-docs.md` · `plans.md` · `tickets.md` · `commits.md` (message style, and the rule that
  there is no attribution trailer). `.claude/` holds only harness config and three-line skill stubs
  pointing back here.
- `docs/features/pending/` — approved but unshipped work; **work comes from here**
- `docs/features/implemented/` — shipped plans (+ sibling `assets/`)
- `mockups/design/FINAL_design_mockup.html` — visual truth · `mockups/logos/` — branding

Small feature → `pending/<name>.md` per `docs/conventions/plans.md`. Feature outliving one sitting →
a **folder**: `pending/<feature>/<feature>.md` — the same feature file, named after its folder — plus
`tickets/` (one file per specified ticket + `backlog.md`, no file lists). See
`docs/conventions/feature-docs.md` and `docs/conventions/tickets.md`. Fully shipped → **move** to
`implemented/`. Phase state change → update `roadmap.md`.

## Layout — package root `com.woka`

**Feature-first.** `app/` wires, `core/` is shared by everyone, `feature/<name>/` owns one thing.
A file's package is its directory; nothing lives outside these three roots.

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
      pip/    PipManager, GlobalPipWindow, Pip*
    editor/   Phase 5, image + video behind one shell (`ai/docs/features/pending/media-editor/`)
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

## Stack

Compose Desktop + Skia · MVVM: ViewModels expose `StateFlow`, no `androidx.compose.*` inside them ·
Koin DI, never instantiate inline · JavaCV/FFmpeg for video/audio/probing · `kotlinx.serialization`
JSON at `user.home/Documents/OldValencia/Woka/settings.json` — no DB, no cloud · JVM desktop only.

## Commands

```powershell
./gradlew.bat :shared:compileKotlinJvm -q   # fast check after edits
./gradlew.bat :shared:build                 # + tests
./gradlew.bat :desktopApp:run               # hotRun --auto for hot reload
```

Run via the PowerShell tool with `run_in_background: true`, then **wait** for the result. A failed
build is reported as a failed build. Measure file length with `wc -l` — PowerShell `Measure-Object
-Line` under-reports.
