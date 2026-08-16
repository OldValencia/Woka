# Plan: Code Quality Refactor

**Status: DONE.** All phases shipped; `:shared:build` and `:desktopApp:compileKotlin` green, the
anti-FQN sweep clean over the whole tree, no `.kt` over 150 lines, and the manual playback run that
Phase 9 hinged on has been done by Valik and passed. Two items were deliberately **not** built and
say why in place — the command-channel rewrite (Phase 9) and 10.7's `Data.makeFromFileName`. Two
visible choices are left open for a later plan, not forgotten: the blurred backdrop's refresh policy
(10.4) and `VideoReloadOverlay`'s 0.55 scrim (Phase 4).

Second of three. Order: `package-restructure-feature-first.md` → **this one** →
`viewer-verification-pass.md`.

Plan 1 moved files and touched no line of logic; it is **shipped** (`implemented/`). This plan touches
**only** logic: it removes duplication, folds scattered constants into domain types, collapses
parameter lists, introduces abstractions where procedural code currently sits on top of a bag of
fields, cuts runtime cost, and closes the file-size mandate. Paths are given in plan 1's target
structure, which is now the actual structure.

---

## Flag on execution order

This plan rewrites zoom maths, the viewer's event model and the player internals — and tests only
appear in plan 3. Refactoring without a safety net is verified by eye and by the build, which is not
enough for Phases 8–9.

Cheap insurance that does not change the agreed order: **pull Phase 3 of plan 3 (pure-logic tests —
`ZoomScaleTest`, `ScaleMathTest`, `PanBoundsTest`, `PlaylistWalkTest`, `TrackMatchingTest`,
`SubtitleFileParserTest`) forward as Phase 0 of this plan.** Those are 6 test files over code this
plan rewrites; they are written against current behaviour and act as control fingerprints. Plan 3
then simply does not duplicate what is already written.

**Decided (Valik): Phase 0 is not executed.** Verification is by eye, with a manual run after each
phase. Tests stay entirely in plan 3. The knowingly accepted consequence: a regression in Phases 8–9
(event model, player internals) will only surface on the manual run, so both phases are broken into
small steps with a run in between — already built into them.

---

## Phase 0: not executed

See the decision above. The section is kept so phase numbering does not shift relative to references
from the sibling plans.

---

## Phase 1: inbox from plan 1

A receiver section. Things plan 1 had to walk past ("zero unprompted changes") surface during the
move: dead code, dependency cycles exposed by the package change, classes whose home was not
unambiguous. Each such find is recorded here with file and line **at the moment it is found**, not
from memory.

### Found during the move

- **pip ↔ shell cycle (plan 1, Phase 6).** `feature/viewer/pip/GlobalPipWindow.kt:5,10` calls
  `MediaViewerFullscreenWindow` from `viewer/shell`, while `shell/MediaViewerFullscreenWindow.kt:23-25`
  calls `PipCornerFlash`, `handlePipKeyEvent` and `pipWindowDraggable` from `viewer/pip`. It compiles
  (Kotlin permits cyclic package dependencies), but the fullscreen window and the PiP window know
  about each other. Invisible before the move — both lived in one flat `ui/viewer/`.
  Untangle while working on `ViewerMedia` (Phase 8): the shared part is most likely "an always-on-top
  window with a video surface and its own key handler", of which fullscreen and PiP are two modes.

- **shell ↔ video cycle through the slider (plan 1, Phase 8).** `shell/slider/MediaSlider.kt:30-31`
  — the declared shared slider — calls `MediaSliderTrack` and `chapterIndexAt` from
  `video/MediaSliderChapters.kt`, i.e. it knows about video chapters. In the other direction
  `video/MediaViewerTimeSlider.kt` calls `MediaSlider`. The shared slider is not shared: chapters
  must arrive as a parameter (a list of marks), not as a call into the video module. Fixed in Phase 7
  (collapsing `MediaSlider`'s 14 parameters) — break the cycle there.

- **`ZoomMenuSlider` and `PlayerSpeedSlider` are one widget, copied twice (plan 1, Phase 8).**
  Both declare three identical `private val`s: `KNOB_SIZE = 12.dp`, `DETENT_SIZE = 4.dp`,
  `TRACK_HEIGHT = 3.dp` (`ZoomMenuSlider.kt:36-38`, `PlayerSpeedSlider.kt:31-33`), and both call
  `markerLeft` from `video/PlayerSpeedSliderParts.kt:51` — the zoom slider is built on the speed
  slider's geometry, with its own copy of the constants. Collapse into one parameterised detent
  slider in `core/ui/components/` (Phase 5, deduplication): track, knob, detent marks and the
  positioning maths are identical; only the range and the label differ.

Already known to be deferred here by plan 1:

- **Splitting `feature/viewer/video/engine/` into sub-packages.** 36 files in a flat list. Target
  division by responsibility: `decode/` (grabber, frames, pacer, presenter, renderScale),
  `audio/` (decoder, samples, tempo, sound line), `subtitle/` (parsers, charset, bitmap subs, stream
  decode), `track/` (probe, selection, prefs, persist, reset), `sync/`, `probe/`.
- **Files past the 130–150 line mandate** — closed in Phase 11, because Phases 2–10 change the size
  of half of them anyway.

Confirmed as **already closed by plan 1**, not to be repeated here:

- Inline fully-qualified names. A full-tree grep for `androidx.|java.|javax.|kotlinx.|org.bytedeco.|
  org.jetbrains.|com.woka.|coil3.` outside `import`/`package` returns one hit, and it is prose inside
  a doc comment (`core/system/AwtMediaKeyControls.kt:29`). Plan 3's note about "23 inline FQNs" is
  stale.

**Files:** determined as work proceeds.

---

## Phase 2: a media domain type instead of six extension lists

**Problem (verified).** The extension lists are declared six times across four files and **contradict
each other**:

| Place | Images | Video |
|---|---|---|
| `LocalMediaRepository.kt:23-24` | jpg jpeg png webp bmp | mp4 mkv avi mov webm |
| `FolderCardUtils.kt:21-22` (subtitle) | jpg jpeg png webp gif bmp heic | mp4 mkv avi mov webm |
| `FolderCardUtils.kt:35` (count) | jpg jpeg png webp gif heic | mp4 mkv avi mov webm |
| `FolderCardUtils.kt:40` (preview) | jpg jpeg png webp gif heic | mp4 mkv avi mov webm |
| `FolderStateMapper.kt:49-57` (filter) | jpg jpeg png gif webp bmp heic | mp4 mkv avi mov webm |
| `FolderCardPreview.kt:100` | — | mp4 mkv avi mov webm |

Direct consequences, to be reproduced and attached to the plan as bug reports:
- `.gif` and `.heic` are listed in the filters, the counter and the preview, but
  `LocalMediaRepository` **never returns them** — those branches are dead and the files do not open.
- `.bmp` *is* returned by the repository, but the card counter (`:35`) and the preview (`:40`) do
  **not** count it: a folder of nothing but `.bmp` shows "0 items" and an empty cover, yet opens
  non-empty.

**Actions.**
- Create `core/data/MediaKind.kt`: `enum class MediaKind { Image, Video }` + the single extension
  sets + `fun File.mediaKind(): MediaKind?` and `val File.isMedia: Boolean`.
- Reconcile the sets. **Decided (Valik):** images `jpg jpeg png webp bmp gif heic`, video
  `mp4 mkv avi mov webm`. `MediaItem.Image/Video` are built only through `mediaKind()`.
- **HEIC is in the list, but a decoder for it is a separate feature, not this phase.** Skia
  (`Image.makeFromEncoded`) does not decode HEIF; nor does Coil in the grid. So after Phase 2 `.heic`
  starts being *listed* and *counted*, but opens as a placeholder until a decoder exists. This is a
  deliberate intermediate step, not a defect: today `.heic` does not work *and* is inconsistent
  between filters.
  *(`.gif` is a different case — Skia decodes its first frame, so folding it in actually fixes it.)*
  A separate plan in `pending/` will describe:
  - HEIF decoding (candidates: JavaCV/FFmpeg — the HEVC branch is already a dependency; or libheif
    over JNI). Engine choice belongs to that plan;
  - **viewing the other images inside the container.** HEIF is a container, not a single frame: it
    carries collections, bursts, live photos and alternative representations. Valik's requirement is
    to page through them inside one file, not just the primary image. That is a separate viewer
    concept ("pages inside a file") which the model has no room for today: `MediaItem` is one file,
    one frame.
  Until that plan exists, `viewer/image/ImageLoader` returns `null` for `.heic` and the viewer shows
  its usual broken-file placeholder.
- **Added:** give `MediaKind` a `canDecode` flag (false for `heic` only, until the decoder lands) so
  the viewer can say "format not supported yet" instead of showing the generic broken-file
  placeholder. One boolean; it stops the intermediate state from reading as a bug.
- Rewrite all six places on `mediaKind()`. Delete the local lists.
- Check that `SUBTITLE_FILE_EXTENSIONS` (already single, in the engine) stays single — do not
  duplicate it into `core/`.

**Files:** `core/data/MediaKind.kt` (new), `core/data/LocalMediaRepository.kt`,
`core/ui/components/FolderCardUtils.kt`, `core/ui/components/FolderCardPreview.kt`,
`feature/folder/FolderStateMapper.kt`

## Phase 3: one place that knows where data lives on disk

**Problem (verified).** The storage root is assembled by hand in three places:
`SettingsStore.kt:22`, `ThumbnailCacheFile.kt:14`, `FolderCardUtils.kt:43`. The latter two also
**derive the same thumbnail-cache path twice**, independently of each other.

A separate defect in the same code: the cache file name is `videoFile.name.hashCode()` — a hash of
the **name, not the path**. Two files called `clip.mp4` in different folders share one thumbnail: the
second folder shows a frame from the first. `String.hashCode()` also collides and yields negative
names.

**Actions.**
- Create `core/data/WokaStorage.kt`: `root` (`user.home/Documents/OldValencia/Woka`),
  `settingsFile`, `thumbnailsDir`, `thumbnailFile(source: File)`.
- Key the thumbnail off the **absolute path**, not the name. Format: SHA-256 in hex truncated to 16
  characters — deterministic, no negative numbers, no collisions in practice.
- **Added:** fold the source file's `length()` and `lastModified()` into the hashed input. Today a
  file replaced in place under the same name keeps serving the old poster frame forever, and there is
  no invalidation path at all. Same cost, one more failure mode closed.
- One-off migration: the old `name.hashCode()` cache is not renamed, it is **wiped** — `.thumbnails`
  is recreated on first launch of the new version. Thumbnails are cheaper to regenerate than to
  migrate. *Woka is not deployed and has no users — stopgap compatibility is not needed.*
- Move `SettingsStore`, `VideoThumbnailExtractor` and `FolderCardUtils` onto `WokaStorage`.

**Files:** `core/data/WokaStorage.kt` (new), `core/data/SettingsStore.kt`,
`feature/viewer/video/engine/ThumbnailCacheFile.kt` (folded into `WokaStorage`, deleted),
`core/ui/components/FolderCardUtils.kt`

## Phase 4: viewer chrome design tokens

**Problem (verified).** `Color.Black.copy(alpha = …)` — 38 occurrences; `RoundedCornerShape(12.dp)`
— 15. Alpha values have drifted apart (0.5 / 0.6 / 0.7); the viewer chrome's radii and paddings are
literals in every file. Meanwhile `core/ui/theme/Theme.kt` exists and is the declared source of truth
(`FINAL_design_mockup.html`).

Plus 4 points where Material leaks into the design system, against the "Apple HIG, never Material"
mandate: `MediaOverlays.kt:128` (`MaterialTheme.typography.titleSmall`), `MediaSlider.kt:50-51`
(`MaterialTheme.colorScheme.onSurface` as default values), `MediaViewerVolumeOverlay.kt:106`
(`MaterialTheme.typography.labelSmall`).

Clarification: `material3` is imported in ~34 files, but almost everywhere as a source of primitives
(`Text`, `Icon`, `CircularProgressIndicator`). That is not a violation; the violation is exactly the
4 places where **styling** comes from Material.

**Correction on execution: "38 literals → one token" was the wrong reading.** The 38 occurrences are
four unrelated concerns, and the mockup settles none of them — it is 252 lines covering the shell,
with four `rgba(0,0,0,…)` values, all shadows. Breakdown as actually found:

| Count | Concern | Verdict |
|---|---|---|
| 17 | `spotColor`/`ambientColor` on `.shadow(…)`, alphas 0.1–0.5, mostly home/folder/library/search | **not** viewer chrome; an app-wide shadow-tint group, out of this phase's scope |
| 2 | modal scrim behind Search and Settings, both 0.42 | app-level, consistent already; out of scope |
| 11 | viewer HUD / popover / tooltip backgrounds | **this phase** |
| 8 | scrollbar, folder-card play badge, card border, carousel dim, and `subtitleBoxOpacity` (a user setting) | leave alone |

And the alphas did not "drift": 0.6 is every popover (4 sites, unanimous), 0.7 is every tooltip
(2 sites, unanimous), 0.5 is every HUD badge. Three roles, not one value with noise — so three
tokens, each set to the value already in use. Zero visible change.

Same for `RoundedCornerShape(12.dp)`: only 4 of the 15 are viewer chrome; the other 11 are the app
window, dialogs, popups and settings rows, i.e. a general `Theme.radii` question, not this one.

**Done.**
- `core/ui/theme/ViewerChrome.kt` (new): `hudScrim` (0.5), `popoverScrim` (0.6), `tooltipScrim`
  (0.7), `cornerRadius` (12.dp), `tooltipCornerRadius` (6.dp), `popoverWidth` (210.dp),
  `popoverPadding`, `hudPadding`, `tooltipPadding`, `hudLabel`, `overlayLabel`. Theme-independent by
  design: this sits on the frame, not on an app surface. Reached as `Theme.viewer.*`.
- 11 chrome sites moved onto the tokens.
- All 4 `MaterialTheme` reads removed. The two typography ones are written out as explicit
  `TextStyle`s carrying Material's own `titleSmall`/`labelSmall` metrics, so the rendering is
  identical. The two colour ones in `MediaSlider` were **dead defaults** — its single caller
  (`MediaViewerTimeSlider.kt:99-101`) always passes `Theme.colors.accent`/`textSecondary` — so the
  parameters simply lost their defaults.

**Open, deliberately not decided here** (each is a visible choice, and the mockup does not cover the
viewer):
- `VideoReloadOverlay.kt:36` uses 0.55 — the only real outlier. Snap to `hudScrim` (0.5) or keep?
- `MediaViewerCarousel.kt:115` dims unselected thumbnails at 0.4, and `:122` draws its play badge at
  0.5. Different role from HUD/popover/tooltip; left as literals.
- The 17 shadow tints and 11 non-viewer 12.dp radii want their own token groups. Worth a step in a
  later phase, not a silent change here.

**Files:** `core/ui/theme/ViewerChrome.kt` (new), `core/ui/theme/Theme.kt`, `MediaOverlays.kt`,
`MediaViewerVolumeOverlay.kt`, `PlayerSettingsOverlay.kt`, `SubtitleVerticalOverlay.kt`,
`MediaViewerTooltip.kt`, `MediaSliderTooltip.kt`, `MediaViewerFullscreenOverlays.kt`,
`MediaSlider.kt`

## Phase 5: overlay deduplication

**Problem (verified).** Two copied scaffolds.

*Scaffold A — three HUD overlays.* `PlayPauseOverlay` (`MediaOverlays.kt:25`), `SkipOverlay` (`:62`),
`ZoomHudOverlay` (`:103`) share an identical preamble:

```kotlin
if (trigger == 0L) return
var visible by remember(trigger) { mutableStateOf(true) }
LaunchedEffect(trigger) { visible = true; delay(N.milliseconds); visible = false }
AnimatedVisibility(visible = visible, enter = …, exit = …, modifier = Modifier.fillMaxSize()) { … }
```

They differ only in duration (400/400/900 ms), animation and content.

*Scaffold B — three toolbar popovers.* `VolumeVerticalOverlay`, `SubtitleVerticalOverlay` (`:44`) and
`PlayerSettingsOverlay` (`:30`) have a **byte-identical** signature
(`isVisible, bottomPadding, alignment, videoState, onHover`) and a matching body:
`AnimatedVisibility(fadeIn/fadeOut, fillMaxSize)` → `Box(contentAlignment = alignment)` →
`Box(padding(end = X, bottom = bottomPadding).width(210.dp).background(…).padding(12/8)
.onPointerEvent(Enter/Exit))`. The only difference is the `end` padding (60 / 136 / 96 dp) and the
content. The comment at `PlayerSettingsOverlay.kt:24-26` admits it outright: "matching the
architecture of the Volume (§2.1) and Subtitle (§2.2) overlays".

**Done.**
- `feature/viewer/shell/TransientOverlay.kt` (new, 56): `TransientOverlay(trigger, durationMs, enter,
  exit, alignment, content)` — scaffold A in one place; the three overlays are now content plus
  parameters. `trigger` is deliberately still a `Long`: Phase 6 swaps it for a monotonic event here,
  in one file, instead of three. `MediaOverlays.kt` 133 → 111.
- `feature/viewer/shell/ToolbarPopover.kt` (new, 81): the scaffold plus `PopoverAnchor`, holding the
  three `end` offsets (Subtitles 136, Volume 96, Settings 60) beside a comment naming the toolbar
  icon order they follow. `width` is nullable — the volume popover sizes to content because the
  audio-track list docks onto its side; the other two take the 210.dp token from Phase 4.
  `MediaViewerVolumeOverlay` 115 → 103, `SubtitleVerticalOverlay` 114 → 89, `PlayerSettingsOverlay`
  64 → 34.
- The early `return`s (`videoState == null`, `hasAnySubtitle != true`) stayed **in the callers** —
  domain conditions, not styling. Checked in all three.
- `core/ui/components/DetentSlider.kt` (new, 145) replaces both slider copies: `ZoomMenuSlider`
  113 → 45, `PlayerSpeedSlider` 116 → 53. `markerLeft` moved in as a private function, so
  `shell/zoom` no longer imports from `video/` and the triplicated `KNOB_SIZE` / `DETENT_SIZE` /
  `TRACK_HEIGHT` exist once. Everything is a 0f..1f fraction: the widget knows nothing about
  percentages or multipliers, and each caller keeps its own mapping in both directions.

**Two differences between the sliders were preserved, not unified** — they are behaviour, and
flattening them would have been a silent change:
- The zoom track draws a filled portion, the speed track does not. Expressed as
  `DetentSliderColors.fill: Color?`, null meaning no fill.
- Zoom commits every drag position (re-applying a zoom is free); speed holds the value locally and
  commits on release, because each committed change re-seeks the audio grabber and rebuilds the
  atempo graph. Expressed as separate `onTap` / `onDragStart` / `onDrag` / `onDragEnd` lambdas rather
  than one nullable "commit policy" flag. Zoom passes no `onDragStart`, matching its present
  behaviour of not jumping until the drag actually moves.

**Files:** `TransientOverlay.kt`, `ToolbarPopover.kt`, `core/ui/components/DetentSlider.kt` (new),
`MediaOverlays.kt`, `MediaViewerVolumeOverlay.kt`, `SubtitleVerticalOverlay.kt`,
`PlayerSettingsOverlay.kt`, `shell/zoom/ZoomMenuSlider.kt`, `video/PlayerSpeedSlider*.kt`

## Phase 6: real events instead of timestamp triggers

**Problem (verified).** `MediaViewerState` holds four `Long` fields used as one-shot events:
`playPauseTrigger`, `skipTrigger`, `zoomHudTrigger`, `volumeTrigger`, plus `volumeTriggerTimer: Int`
as a home-made "currently showing" flag. Each is raised by assigning `System.currentTimeMillis()`
(`MediaViewerScreen.kt:101`, `MediaViewerPlayer.kt:57-58`, others), and the consumer catches the
change through `remember(trigger)`.

The mechanism's defect: `System.currentTimeMillis()` has a coarse resolution on Windows. Two events
inside one tick produce the **same value**, `remember(trigger)` does not restart, and the second HUD
never shows. Reproduced by a fast double press of skip, or two zoom clicks in a row.

**Done.**
- `feature/viewer/shell/ViewerEvents.kt` (new, 58): `class ViewerEvent` with a monotonic
  `seq: Int` (private setter) and `raise()`. A counter that only goes up removes the clock-resolution
  failure outright rather than narrowing it. The `pendingHud: Pair<ViewerHud, Int>?` alternative was
  dropped: the four HUDs are independent and can legitimately overlap, so one shared slot would have
  invented a mutual exclusion the UI does not have.
- Same file: `rememberEventFlag(event, durationMs)` — true for `durationMs` after each raise. It is
  the timer both `TransientOverlay` and the volume popover needed, so `volumeTriggerTimer` is gone
  along with the `LaunchedEffect` that drove it from `MediaViewerScreenEffects` — a different file
  from its only reader.
- `MediaViewerState` loses exactly 5 observable fields and gains 4 plain `val`s
  (`playPauseEvent`, `skipEvent`, `zoomHudEvent`, `volumeEvent`). 55 → 50 lines.
- `TransientOverlay` takes a `ViewerEvent` and drops its `if (trigger == 0L) return` guard: seq 0
  simply yields `visible = false`.
- `MediaViewerFullscreenWindow` had a **fourth** copy of the pattern in a local
  (`var fsZoomHud by remember { mutableStateOf(0L) }`), not visible from `MediaViewerState`. Now a
  remembered `ViewerEvent`.
- Trap avoided, worth recording: `isVisible = hover == Volume || rememberEventFlag(...)` puts a
  composable call behind `||` short-circuit, which Compose cannot key. Read into a `val` first.

**Still open, same defect class, out of this phase's scope:** `PipManager.kt:75` passes
`nonce = System.currentTimeMillis()` into `Screen.Folder` so `replaceTop` sees a distinct route. Two
PiP restores inside one clock tick would produce an equal `Screen` and no navigation. It is a
user-driven action so the window is tiny, but it is the same reasoning that failed here.

**Files:** `ViewerEvents.kt` (new), `TransientOverlay.kt`, `MediaOverlays.kt`, `MediaViewerState.kt`,
`MediaViewerScreen.kt`, `MediaViewerScreenEffects.kt`, `MediaViewerPlayer.kt`,
`MediaViewerPlayerModifier.kt`, `MediaViewerShortcutEffect.kt`, `MediaViewerZoomActions.kt`,
`MediaViewerFullscreenWindow.kt`

## Phase 7: collapsing parameter lists

**Problem (verified).** 18 functions take ≥ 10 parameters:

| Params | Function |
|---|---|
| 20 | `MediaViewerToolbar` |
| 18 | `SettingsOverlay` |
| 17 | `MediaViewerPlayer`, `MediaViewerContent` |
| 16 | `FolderContent` |
| 14 | `MediaSlider`, `MainShell`, `FolderGrid` |
| 13 | `handleMediaViewerKeyEvent`, `MediaViewerLayoutWrapper` |
| 12 | `Modifier.mediaViewerKeyHandler`, `Modifier.mediaViewerGestures`, `MediaViewerTopBarSetup` |
| 11 | `MediaViewerNormalControls`, `MediaViewerFullscreenControls`, `FolderOverlays` |
| 10 | `MediaViewerSettingsPanel`, `FolderDockSetup` |

Worst case is `SettingsOverlay`: 18 parameters, 16 of which are eight "value + callback" pairs where
the value is read from `WokaSettings` and the callback pokes the identically named setter on
`SettingsRepository`. `App.kt:133-152` writes all 18 arguments out by hand — 20 lines out of 179 in a
file that is itself past the mandate.

**Done, in four steps with a build between each.** 18 functions ≥ 10 parameters → **12**; worst case
20 → 15.

*Step 1 — Compose stability, then `SettingsOverlay`.* `@Immutable` on `WokaSettings`,
`PinnedSession`, `TrackRef`, `TrackPrefs`; `@Stable` on the `SettingsRepository` interface. Without
this the collapse below would be a straight downgrade: an unannotated `data class` holding `List`/
`Map` fields is inferred **unstable**, so every composable taking it becomes non-skippable. The
annotation states a fact — every setter goes through `.copy()`.
`SettingsOverlay` 18 → 4, `SettingsOverlayContent` 20 → 5, `MediaViewerSettingsPanel` 10 → 2,
`AppearanceSettingsPanel` 2 → 2, `LibrarySettingsPanel` 4 → 2.

> **Plan was wrong here, recorded rather than quietly fixed:** it predicted `App.kt` "drops under the
> mandate with no split at all". The `SettingsOverlay` call site was 21 lines and became 6, so
> `App.kt` went 179 → **165** — still over 150. It stays a Phase 11 candidate.

*Step 2 — `MediaSlider` 14 → 7, and the cycle.* `SliderSegment(start, end, label)` expressed as
fractions, plus `MediaSliderTrack` and `segmentIndexAt`, moved into `shell/slider/`;
`video/MediaSliderChapters.kt` deleted. `MediaViewerTimeSlider` maps chapters → segments in a new
`video/ChapterSegments.kt`. **The shell → video import is gone**, so the shared slider is finally
shared. The seven styling parameters became one `@Immutable MediaSliderStyle` with a
`defaultMediaSliderStyle()` factory reading `Theme`.

*Step 3 — the folder feature.* `FolderContent` 16 → 9, `FolderOverlays` 11 → 3, `FolderGrid` 14 → 11,
`FolderDockSetup` 10 → 8.
- `FolderActions.kt` **is not** what the plan assumed — it is a filesystem-operations object
  (delete/move/copy/rename), not a callback bundle. Reusing it would have been wrong.
- Instead `FolderOverlayState`: a `@Stable` holder for the four dialog/menu values, replacing four
  `var`s in `FolderScreen` plus four `onShow…Change` callbacks threaded down and back. Same shape as
  `MediaViewerState` and `PopoverHoverState`. `showBgContextMenu` and its offset get a private setter
  and one `openBgContextMenu(at)`, because setting them apart is what let the menu open at a stale
  position.
- `FolderZoom(layout, zoomIn, zoomOut)` with a derived `columnSize`: the dock accessory needed
  `layout`, the grid needed `columnSize`, and both needed the two callbacks — one setting passed as
  three parameters, with the `layout → columnSize` mapping living apart from both consumers.

*Step 4 — the viewer trio.* `MediaViewerToolbar` 20 → 15, `MediaViewerPlayer` 17 → 14,
`MediaViewerContent` 17 → 13, `MediaViewerFrame` 8 → 6. New `shell/ViewerContext.kt` holds
`ViewerActions` (onEdit/onCopy/onInfo — passed unchanged through four levels to the context menu),
`ViewerTransform` (sizeMode + pan), `BackdropTransform` (resolved scale + pan, for the toolbar's
blurred backdrop) and `ViewerChromeState` (carouselMode + isFullscreen, with a derived
`isCarouselEnabled`).
- The toolbar's three hover callbacks collapsed into the **existing** `PopoverHoverState`, which the
  caller was already holding and unwrapping into three lambdas.
- `ViewerGeometry` as the plan sketched it was **not** built: it mixed `containerSize`/`nativeSize`,
  which flow *up* out of the content layer, with `sizeMode`/offsets, which flow *down* into it. One
  struct for both directions would have been a bag.
- `ViewerTransform` and `BackdropTransform` are deliberately two types, not one with four fields:
  content needs the size mode, the toolbar needs the resolved scale, and neither should carry a field
  it must ignore.

**Left alone**, because the long list is genuinely independent inputs: `MainShell` (14), the key
handlers and gesture modifiers (12–13), `MediaViewerLayoutWrapper` (13),
`MediaViewer{Normal,Fullscreen}Controls` (11), `FolderGrid` (11 after step 3).

**Files:** `core/data/{WokaSettings,TrackPrefs,SettingsRepository}.kt`,
`feature/settings/{SettingsOverlay,SettingsOverlayContent,SettingsPanels,MediaViewerSettingsPanel}.kt`,
`app/App.kt`, `feature/viewer/shell/slider/{MediaSlider,MediaSliderTrack,MediaSliderStyle}.kt` (last
two new), `feature/viewer/video/{MediaViewerTimeSlider,ChapterSegments}.kt` (second new),
`video/MediaSliderChapters.kt` (deleted),
`feature/folder/{FolderScreen,FolderContent,FolderGrid,FolderOverlays,FolderDockSetup,
FolderOverlayState,FolderZoom}.kt` (last two new),
`feature/viewer/shell/{ViewerContext,MediaViewerToolbar,MediaViewerPlayer,MediaViewerContent,
MediaViewerFrame,MediaViewerScreen,MediaViewerFullscreenSurface}.kt` (first new)

## Phase 8: an abstraction for "what the viewer is showing"

**Problem (verified).** `videoState: VideoPlayerState?` is threaded as a nullable parameter through
the whole shell — `MediaViewerScreen` → `MediaViewerPlayer` → `MediaViewerContent` →
`MediaViewerFrame`, plus gestures, keys, toolbar, three popovers, the fullscreen branch. 29 files and
201 references. Every consumer answers "is this even a video?" again, via `?.` and `if (… != null)`.
The type "an image in the viewer" does not exist at all — it is `videoState == null`.

This is the root of why "Image Viewer" and "Video Viewer" are inseparable: media in the viewer has no
type, only a nullable field.

**Done.** 201 references → **162**, 29 files → 26. No CompositionLocal for the player: an implicit
dependency on an object with a lifecycle and threads debugs worse than a long explicit parameter.

`feature/viewer/shell/ViewerMedia.kt` (new, 86):

```kotlin
@Stable
sealed interface ViewerMedia {
    val poster: ImageBitmap      // drawn until a frame decodes; keys the file-change animation
    val nativeSize: IntSize      // what "Actual size" means
    @Immutable data class Still(override val poster: ImageBitmap) : ViewerMedia
    @Stable class Video(val player: VideoPlayerState, override val poster: ImageBitmap) : ViewerMedia
}
```

- **The current frame is not a constructor value**, per the correction recorded before the phase ran.
  `ViewerMedia.Video.frame` is a getter over `player.currentFrame`, so the read stays at the leaf that
  draws it instead of minting a new object per frame through the whole tree.
- `nativeSize` absorbs the fork that was sitting in the middle of `MediaViewerContent`. The fallback
  chain is unchanged and load-bearing: **track dimensions → current frame → poster**. A video's
  poster is a ≤400px thumbnail, so reporting it as native size would make "Actual" wrong until the
  tracks are probed. That the getter therefore still reads `currentFrame` is pre-existing, not new —
  and is a Phase 10 item, not a Phase 8 one.
- `MediaViewerContent` 13 → 12 params, `MediaViewerPlayer` 14 → 14 (swap, not shrink),
  `MediaViewerToolbar` 15 → 14, `MediaViewerFrame` keeps 7 but takes `media` + `targetPoster`.
- `SubtitleVerticalOverlay` and `PlayerSettingsOverlay` now take a **non-null** `VideoPlayerState` and
  are rendered on the type match in the caller. Their `videoState == null` early returns are gone.
  `hasAnySubtitle` stayed inside: "this video has no subtitles" is a property of the video, not of
  what kind of media the viewer holds.
- `MediaViewerFullscreenSurface` builds its own `ViewerMedia` around the PiP player and the 1×1
  placeholder it already had.

**Deviations from the sketch, each for a stated reason:**
- Video widgets take `VideoPlayerState`, **not** `ViewerMedia.Video`. The plan's goal was that the
  caller decides on a type match instead of the widget early-returning; a non-null player achieves
  exactly that, and handing a widget a `ViewerMedia.Video` only for it to read `.player` adds a hop
  with no gain.
- **The conversion stops at the rendering tree.** Effects and handlers that operate on the player —
  `MediaViewerPlaybackEffects`, `MediaViewerSystemControlsEffect`, the key handler, the gesture
  modifiers, `MediaViewerVideoControls` and its children — keep `VideoPlayerState?`. They are about
  the player, not about what the viewer is showing, and `ViewerMedia` would be a wrapper they
  immediately unwrap. This is what accounts for the 162 remaining references.

**Found, not fixed — a behaviour decision for Valik.** The video control row is rendered on
`currentItem is MediaItem.Video` (the *item* type) while its player may still be null, which is why
everything under it is nullable. Switching that condition to `media is ViewerMedia.Video` would make
the whole chain non-null, but the row would then appear a moment later instead of appearing inert —
a visible change, so it was left alone.

**Files:** `ViewerMedia.kt` (new), `MediaViewerScreen.kt`, `MediaViewerPlayer.kt`,
`MediaViewerContent.kt`, `MediaViewerFrame.kt`, `MediaViewerToolbar.kt`,
`MediaViewerFullscreenSurface.kt`, `MediaViewerFullscreenOverlays.kt`, `PlayerSettingsOverlay.kt`,
`SubtitleVerticalOverlay.kt`

## Phase 9: OOP in the player engine

**Problem (verified).** `VideoPlayerState` is 152 lines of fields, but all behaviour lives in
**45 extension functions** (`fun VideoPlayerState.…`) across 36 files. The class encapsulates
nothing: every internal is `internal` so that neighbouring files can mutate it.

Worse is the cross-thread protocol. **18 `@Volatile` fields** serve as a home-made message bus
between three coroutines (video decode, audio decode, presenter): `seekTargetMs`, `seekGeneration`,
`videoReadyGen`, `tracksProbed`, `rebasePresenterClock`, `speedGeneration`, `audioDelayGeneration`,
`pendingAudioReopenIndex`, `pendingVideoReopenIndex`, and the plain `isSeeking` /
`isPlayingBeforeSeek`. The comments in `VideoPlayerState.kt:92-131` are, in effect, documentation of
a protocol that does not exist in the types. Every rule ("audio waits for `tracksProbed`", "the
presenter rebases its clock on `rebasePresenterClock`") rests on the discipline of reading comments.

**Step 1 — sub-package split: DONE.** Pure move, no logic touched; `:shared:build` green, anti-FQN
clean, every `package` line agrees with its directory (checked mechanically over the whole tree).

| Package | Files | What |
|---|---|---|
| `engine/` | 6 | `VideoPlayerState` + the public transport surface (`VideoPlayerControls`, `VideoPlayerSpeed`), `PlaybackResume`, and the two thumbnail files |
| `decode/` | 6 | grabber, decode loop, pacer, presenter, renderScale, frame conversion |
| `audio/` | 4 | decoder, samples, tempo, sound line |
| `subtitle/` | 9 | parsers, charset, bitmap subs, stream decode, sidecar scan |
| `track/` | 5 | probe application, prefs, persist, reset, video-track swap |
| `sync/` | 2 | A/V + subtitle offsets, seek positioning |
| `probe/` | 3 | HDR info, chapters, media tracks |

Deviations from the sketched six, each because forcing the file would have been worse than leaving
it: `VideoPlayerControls` and `VideoPlayerSpeed` are the UI-facing command surface and belong beside
the state, not inside a machinery package. `PlaybackResume` is user-facing behaviour, not a track
concern. `VideoThumbnailExtractor`/`VideoThumbnailHeuristics` are poster frames for the grid and are
not part of playback at all — **they should leave `engine/` entirely**, but that is a different move
and is recorded here rather than done silently.

Imports were derived from a declaration scan, not hand-guessed; the compiler then caught the four
symbols the scan missed (a `const val`, and extension functions used with a dot). 17 imports the
scan added but nothing used were pruned, again with the compiler as the check.

**Raised, not acted on (§ file-size mandate):** `decode/VideoPlayerDecoder.kt` 144 → 159 and
`engine/VideoPlayerState.kt` 152 → 158, both purely from the added import blocks. Splitting them is
exactly what the collaborator extraction below does, so they are left for it rather than cut twice.

**Step 2 — the protocol: DONE. The collaborator extraction and the command channel: NOT done, and
that is a decision, not an omission.** The plan's own escape clause — *"if the volatile protocol
carries non-obvious semantics a channel does not reproduce one-for-one, stop and raise it"* — is the
case here. Reading every read and write of the 18 fields first is what settled it.

*What shipped.* `engine/PlaybackGeneration.kt` (new, 101) owns the six fields that formed the seek /
speed / audio-offset handshake: `targetMs`, `seek`, `videoReady`, `speed`, `audioDelay` and the
presenter rebase. Same `@Volatile` storage, same polling sites, same order of reads — the mechanism is
untouched on purpose. What changed is that the halves of a handshake are no longer separate
assignments a caller can get wrong, and every rule that lived in a comment on `VideoPlayerState` is
now attached to the operation that performs it. `tracksProbed` became a `CompletableDeferred<Unit>` —
it always was a one-shot latch, and the audio loop's `while (!tracksProbed) delay(10ms)` is now an
`await()`.

> **Defect found and fixed, not merely moved.** `VideoPlayerState.seek` did
> `seekGeneration++` *then* `seekTargetMs = safeProgress` — signal before data. Both decode loops
> trigger on the generation and then read the target, so a loop that observed the bump between the
> two stores seeked to the **previous** target, and, having already advanced its own `videoSeekGen`,
> never corrected itself: picture at the old position, `currentPositionMs` at the new one, until the
> next seek. `handleEndOfStream` had the order right and said so in a comment; the two disagreed.
> `requestSeek(toMs)` now writes the target first, always, for every caller.

> **Second fix:** `isSeeking` was a plain `Boolean` written on the UI thread and read from all three
> decode coroutines. Now `@Volatile`. Nothing observed misbehaving, but the JMM permits a loop to
> hoist that read and never see the change.

*Why the command channel was not built.* Four semantics that `Channel<Command>` does not reproduce:
- **Coalescing is the feature, not an accident.** Three slider scrubs inside one decode iteration must
  produce **one** seek to the newest target. A counter does that for free; a channel delivers three
  commands and performs three seeks — the exact scrubbing regression this phase must not cause. To
  keep it you re-implement the counter on top of the channel.
- `pendingVideoReopenIndex` / `pendingAudioReopenIndex` are **last-write-wins slots**, not queues.
  Two fast track switches must open the last track only.
- `videoReady == seek` is a **relational gate between two counters** that re-arms on every seek, not a
  one-shot. `CompletableDeferred` is wrong by construction; a `StateFlow<Int>` would be the same
  comparison with `.value` appended.
- The loops **poll a priority ladder** (reopen → seek → offset → speed → park → lead/lag), they never
  block on a mailbox. A channel receive is either blocking (wrong shape) or `tryReceive` (what the
  volatile read already is).

*Why the six collaborator classes were not extracted.* A `VideoDecodeLoop(state)` that still writes
`state.generation` encapsulates nothing — it is a rename with real regression risk on the one code
path in the app that cannot be verified by building. The value in this phase was always the protocol,
and the protocol now has a type. If the classes are wanted later they are a much smaller step on top
of this, because the shared mutable surface between them is now one object with a named API rather
than eighteen fields.

*Nothing tuned was undone:* `FramePacer`'s reused scratch buffer, the video channel's capacity of 8,
and the skip-conversion-when-late policy are untouched.

**Manual playback run: done, passed (Valik).** This was the one phase whose correctness a green build
does not establish — playback, hard scrubbing, audio and video track switches, speed off 1×, the A/V
offset in both directions, and a Repeat File wrap. No regression observed.

**Raised and acted on (§ file-size mandate):** `VideoPlayerState` 158 → **134** by moving the fields
out; `decode/VideoPlayerDecoder.kt` 159 → **128** by splitting its one-time probe phase into
`decode/VideoPlayerDecodeStart.kt` (49). With those two, **no `.kt` in the tree is over 150.**

**Files:** `engine/PlaybackGeneration.kt` (new), `engine/{VideoPlayerState,VideoPlayerControls,
VideoPlayerSpeed}.kt`, `decode/{VideoPlayerDecoder,VideoPlayerDecodeStart,VideoPlayerPresenter}.kt`
(second new), `audio/VideoPlayerAudioDecoder.kt`, `sync/VideoPlayerSync.kt`,
`track/VideoPlayerTrackReset.kt`

## Phase 10: runtime cost

New phase. Everything above improves the shape of the code; this one is about what the app actually
spends. Placed after Phases 8–9 so recomposition scoping is fixed once, on the final shape, rather
than twice. All findings below are read in the code, not guessed; each says how to confirm it.

**Run ahead of Phase 9's step 2, deliberately.** Step 2 is the one part of the plan whose correctness
can only be established by a manual playback run between each collaborator; Phase 10 is establishable
by the build and by reading. Nothing in 10.1–10.8 touches the engine's internals, so the reordering
costs nothing and leaves step 2 for a session with a keyboard in front of it. `:shared:build` green,
anti-FQN clean over every touched file.

**10.1 — The whole app tree recomposes on every settings write.**
`App.kt:46` collects the entire `settings` StateFlow, and `WokaSettings` is one object: any setter —
volume, a zoom step with `rememberSizeState` on, a resume position — produces a new instance and
recomposes `App` → `MainShell` → `AnimatedContent` → the current screen. `PlaybackResume.kt:17,27`
writes the play head **every 5 seconds during playback** when resume is enabled, so it is a periodic
full-tree recompose, not a one-off.
*Done.* New `app/SettingsProjection.kt` (28): `SettingsRepository.collectField { … }`, a `map` +
`distinctUntilChanged` + `collectAsState` seeded from the StateFlow's current value so nothing renders
against a placeholder. `App` now collects `theme` and `linkedFolders` and never touches the record.
`SettingsOverlay` **collects the settings itself** and lost its `settings` parameter (4 → 3): it was
the last reason `App` had to hold the whole object, and pushing the read into the consumer is what
actually removes the invalidation rather than moving it. `@Immutable` on `WokaSettings` was already
done in Phase 7.
*Confirm:* toggle resume on, play a video, watch the recomposition counts.

**10.2 — `buildFolderState` runs on the UI thread and does per-item disk I/O.**
`FolderViewModel.kt:31` uses `CoroutineScope(Dispatchers.Main)` and `state` is
`combine(...).stateIn(scope, …)`, so `buildFolderState` executes on Main. Inside it,
`FolderStateMapper.kt:37-38` calls `lastModified()` per item inside a comparator (O(n log n) stat
syscalls) and `:44` calls `isHidden` per item — plus four intermediate list copies. And because
`settingsRepository.settings` is one of the combined flows, this re-runs on **every settings change**,
including the volume slider and the 5-second resume write. On a folder of a few thousand files that
is thousands of stat calls on the UI thread every time the volume moves.
*Done.* All three fixes, as written.
- `FolderSettings` (six fields: layout, sort, showTitles, showFolders, showHiddenFiles, mediaFilter)
  plus `WokaSettings.folderSettings(path)`, both in `FolderStateMapper.kt`. The ViewModel maps and
  `distinctUntilChanged()`s before the `combine`, so `path` also drops out of `buildFolderState`'s
  signature — the projection is the only thing that needed it.
- `.flowOn(Dispatchers.Default)` between the `combine` and the `stateIn`; `stateIn` still shares on
  the Main-dispatched scope, which is where collectors want the value.
- `MediaItem` gained `lastModified` and `isHidden`, captured at construction — which happens on
  `Dispatchers.IO` inside `LocalMediaRepository` at all four construction sites in the tree. They are
  declared per subclass rather than in the sealed base: a base-class initialiser runs before the
  subclass assigns `override val file`, so the obvious placement would have dereferenced null. Kept
  out of the constructors so `data class` equality still compares on the file alone.
*Confirm:* open a folder with several thousand files, drag the volume slider, watch frame times.

**10.3 — Blocking file I/O inside composition.**
`FolderCard.kt:53-57` calls `File(path)`, `file.isDirectory` (a stat) and — through
`getFolderCardSubtitle` — `file.length()` (another stat) in the composable body. `isDir` is not
remembered, so both re-run on every recomposition of every card: hover, selection and highlight all
trigger it. `FolderCardUtils.kt:30-51` additionally does a full `listFiles()` per folder card with no
memoisation across navigations.
*Done.* `File(path)` and `isDirectory` are `remember(path)`ed in `FolderCard`; the subtitle already
was. `fetchFolderStateData` gained a `ConcurrentHashMap` keyed on the absolute path.
**The validation stamp is a pair, not just the directory's `lastModified`** — a video preview appears
only once its poster frame has been written, and writing a poster does not touch the folder being
previewed. So `WokaStorage.thumbnailsDir.lastModified()` is part of the stamp; without it a card
would keep its pre-thumbnail cover until the folder's own contents changed, which is a visible
regression the plan did not anticipate. Cost is one extra stat per call, against a full `listFiles()`
saved.
*Note:* the cache is unbounded — one small entry (an Int and ≤ 3 `File`s) per folder ever shown. Left
that way on purpose; an eviction policy here would be guessing at a number.
*Confirm:* a Home screen of linked folders on a slow or network drive.

**10.4 — The toolbar recomposes at video frame rate, and re-blurs the frame each time.**
`MediaViewerToolbar.kt:70` reads `videoState?.currentFrame` in the composable's own scope. Two
consequences. First, the whole toolbar body recomposes on every presented frame — including the two
linear scans over `mediaItems` at `:46-47`, the video controls and the carousel. Second, that
`Image` draws a full-screen copy of the current frame with a saturation `ColorFilter` and
`BlurEffect(50f/scale)` — a full-surface gaussian blur re-rasterised ~60 times a second on top of
the main frame draw, whether or not the chrome is visible (the clip path at
`MediaViewerBackgroundPath.kt:79` only restricts where it lands, it does not skip the work).
*Done, the invisible half.* `shell/ViewerBackdrop.kt` (new, 41) takes the `Image`, so the `frame` read
lands in a leaf and the toolbar's own scope no longer moves at frame rate; the saturation
`ColorFilter` is `remember`ed instead of rebuilt per pass. `currentItem`/`currentIndex` are
`remember(currentId, mediaItems)`ed. `MediaViewerToolbar` 127 → 122 — the extraction was to its own
file rather than a private function below, because keeping it inline put the file at 151.

**Still open, and still Valik's call:** the blur policy. `BlurEffect(50f/scale)` over a full-screen
copy still re-rasterises on every presented frame; freezing it on a poster or refreshing at 2–4 Hz is
visible — the backdrop currently animates with the video — so it is not decided here.
*Confirm:* 4K video with the chrome visible vs hidden; frame times.

**10.5 — The video control row recomposes at frame rate.**
`MediaViewerVideoControls.kt:79` reads `currentPositionMs` in the `Row`'s content scope, so the whole
row — play/pause icon, `painterResource` lookups, subtitle control, volume control, settings control,
the fullscreen button — recomposes at the presenter's rate. Only the time slider needs the position.
*Done, and taken one level further than the plan asked.* The position is a `() -> Long` all the way
down: `MediaViewerVideoControls` passes it without invoking it, `MediaViewerTimeSlider` passes it
without invoking it (otherwise the two skip buttons and their `painterResource` lookups would still
recompose per frame), and it is finally called in two places only — a private `TimeLabel`, and
`MediaSlider`, whose `value` parameter changed from `Float` to `() -> Float` for exactly this reason.
`MediaSlider` has a single caller, so the signature change is contained.
*Behaviour preserved deliberately:* the elapsed label shows the **player's** position, not the drag
target, while scrubbing — the tooltip is what shows where the release lands. Routing the drag value
into the label would have been a silent visible change.

**10.6 — `MediaViewerFrame` allocates a painter per frame.**
`MediaViewerFrame.kt:45-49` does `remember(frameToDraw) { BitmapPainter(frameToDraw) }`; since
`frameToDraw` is a new bitmap every frame, this allocates a painter (and re-runs the `ContextMenuArea`
item lambda) per frame. Small next to the blur, but free to fix once the frame read is at the leaf.
*Done, partly.* The `ContextMenuItem` list is now `remember(actions)`ed, so it is built once per
action set instead of three items per frame.
**The painter allocation is left, and that is a judgement call, not an oversight.** Removing it means
`MediaViewerFrame` must stop reading `currentFrame` during composition at all — i.e. a `Painter`
subclass that reads the state inside `DrawScope`, with `HdrImagePainter` following suit. That is a
rewrite of the one composable that draws every pixel the user sees, to save one small object per
frame next to a 33 MB frame buffer. It wants its own step and its own run, not a ride on this one.

**10.7 — `ImageLoader` doubles peak memory on large stills.**
`ImageLoader.kt:15` does `Image.makeFromEncoded(file.readBytes())` — the entire encoded file is
materialised as a heap `ByteArray` alongside the decoded raster. For a 50 MB image that is 50 MB of
avoidable transient heap. `org.jetbrains.skia.Data.makeFromFileName` avoids the copy.

> **Plan was wrong; not done.** Checked against the sources of the skiko actually resolved here
> (**0.144.6**): `org.jetbrains.skia.Image`'s companion offers exactly one encoded-bytes factory,
> `makeFromEncoded(bytes: ByteArray)`. There is no `Data` overload and no
> `makeDeferredFromEncodedData`, and `Data.bytes` copies straight back onto the heap — so routing
> through `Data.makeFromFileName` would add an allocation, not remove one. The `readBytes()` copy is
> unavoidable on this version. Recorded as a comment at the call site so the next reader does not
> re-derive it. Revisit only if skiko is upgraded.

*Note:* the unbounded `ImageBitmapCache` (no eviction, full-resolution rasters retained for the
process lifetime) is plan 3's F1 and stays there — the eviction policy is Valik's decision and needs
a measurement, not a refactor.

**10.8 — Allocation in comparators.**
`LocalMediaRepository.kt:48-49` and `FolderStateMapper.kt:35-36` sort with `lowercase()` inside the
selector, allocating a String per comparison. `String.CASE_INSENSITIVE_ORDER` does the same job with
none. `FolderStateMapper.kt:63`'s separate "folders first" pass is a second full sort and copy of the
list; fold it into one comparator.
*Also:* `FolderScreen.kt:87` builds `state.items.filter { … }` fresh on every recomposition and hands
the new list to `NativeMediaViewer`, changing identity each time. `remember(state.items)` it.

*Done.* `CASE_INSENSITIVE_ORDER` in both places. The "folders first" pass folded into a single
`itemComparator(sort)` built on a shared `foldersFirst` prefix — the pair of sorts was a full extra
sort **and** a full extra list copy, and the result is identical because the old second sort was
stable. `FolderScreen`'s viewer list is `remember(state.items)`ed.

**Ordering inside the phase:** 10.2 and 10.3 first (they affect ordinary browsing and are low risk),
then 10.5 / 10.6 / 10.8, then 10.1, and 10.4 last because it needs a decision. Each item gets its own
step and its own manual run.

**Raised, not acted on (§ file-size mandate).** Both were already over before this phase and are
Phase 11 candidates; nothing here was shaped around the counter:
`core/ui/components/FolderCard.kt` 183 → **186** (two lines of comment on the `remember`s) and
`app/App.kt` 165 → **166**. `App.kt` would have grown by ~10 had `collectField` stayed inline, which
is why it is its own file.

**Files:** `feature/folder/{FolderViewModel,FolderStateMapper,FolderScreen}.kt`,
`core/ui/components/{FolderCard,FolderCardUtils}.kt`, `core/data/LocalMediaRepository.kt`,
`app/{App,SettingsProjection}.kt` (second new), `feature/settings/SettingsOverlay.kt`,
`feature/viewer/shell/{MediaViewerToolbar,ViewerBackdrop,MediaViewerFrame}.kt` (second new),
`feature/viewer/shell/slider/MediaSlider.kt`,
`feature/viewer/video/{MediaViewerVideoControls,MediaViewerTimeSlider}.kt`,
`feature/viewer/image/ImageLoader.kt`

## Phase 11: the file-size mandate

Closed last: Phases 2–10 change the size of most candidates by themselves (`App.kt` drops under the
limit in Phase 7, the three popovers in Phase 5, `MediaViewerState` in Phase 6).

Re-measure with `wc -l` after Phase 10 and split what is left. Candidates as of today:
`GridScrollbarAdapter` 184, `FolderCard` 183, `App` 179, `FolderGrid` 166, `FolderContent` 159,
`SubtitleCustomizePanel` 155, `VideoPlayerState` 152, `FolderCardPreview` 152, `DockItem` 152,
`PlayerSettingsSync` 150, `MediaSlider` 150, `MediaViewerSettingsPanel` 150.

`FolderCardPreview.kt` (152) separately: seven `AsyncImage` calls across three layout branches differ
only by modifier. Collapse to one `PreviewTile(file, modifier)` and three layouts — the file drops
under the limit with no artificial split.

Every cut follows a semantic boundary, with an explicit criterion stated in the step. Mechanically
"chopping off the tail" and compressing lines to satisfy the counter are forbidden by the mandate.

**Done.** Re-measured after Phase 10: `FolderContent` (159), `PlayerSettingsSync`, `MediaSlider` and
`MediaViewerSettingsPanel` (150 each) had already fallen under by themselves, exactly as predicted;
`FolderCard` and `App` had instead crept *up*. Ten files were over. **Two remain, both Phase 9 step
2's own**, left rather than cut twice: `decode/VideoPlayerDecoder.kt` 159 and
`engine/VideoPlayerState.kt` 158. Every other `.kt` in the tree (230 files) is at or under 150.

| Was | Cut | Now | The boundary |
|---|---|---|---|
| `FolderCard` 186 | `FolderCardData.kt`, `FolderCardBody.kt` | 108 | what a card is *made of* vs what it *looks like* vs how it *reacts* |
| `GridScrollbarAdapter` 184 | `KnownLayoutGridScrollbarAdapter.kt`, `GridLineMetrics.kt` | 42 | factory · scrollbar contract · row arithmetic |
| `App` 166 | `navigation/ScreenHost.kt` | 122 | `App` wires shell/overlays/theme; *which* screen shows is navigation's |
| `FolderGrid` 164 | `FolderItemContextMenu.kt` | 143 | what you can do *to* an item vs how the grid lays items out |
| `SubtitleCustomizePanel` 155 | `SubtitleCustomizeParts.kt` | 120 | the settings offered vs the chrome around them (matches `PlayerSpeedSliderParts.kt`) |
| `FolderCardPreview` 153 | `FolderCoverMosaic.kt` | 108 | folder cover vs file preview — the branches share only their backing surface |
| `main.kt` 153 | `TitleBarDrag.kt` | 100 | app startup vs window behaviour |
| `DockItem` 152 | `DockSecondaryItem.kt` | 96 | two public composables sharing nothing but the dock |

Three things worth recording rather than burying:
- `FolderCardStyle` gained an `ambientColor` field. The card's shadow derived its ambient tint inline
  from `isSelected` and the animated `glowColor` — the one derived colour the style object did not
  own. Folding it in is what let `FolderCardBody` take 5 parameters instead of 7; the value is
  identical.
- `FolderCardData` + `rememberFolderCardData(path)` also finishes 10.3: every stat a card makes is now
  behind one `remember(path)` in one place, rather than scattered through the composable body.
- `ScreenHost`'s transition was rewritten from two near-identical branches into one with a `sign`.
  Verified equal case by case (deeper: in from `+width`, out to `-width`; back: mirrored; same path:
  no animation), not by eye over the diff.

**Files:** `core/ui/components/{FolderCard,FolderCardData,FolderCardBody,FolderCardStyle,
FolderCardPreview,FolderCoverMosaic,GridScrollbarAdapter,KnownLayoutGridScrollbarAdapter,
GridLineMetrics}.kt` (5 new), `app/App.kt`, `app/navigation/ScreenHost.kt` (new),
`app/shell/{DockItem,DockSecondaryItem}.kt` (second new),
`feature/folder/{FolderGrid,FolderItemContextMenu}.kt` (second new),
`feature/viewer/video/{SubtitleCustomizePanel,SubtitleCustomizeParts}.kt` (second new),
`desktopApp/{main,TitleBarDrag}.kt` (second new)

## Phase 12: reconciliation

- `./gradlew.bat :shared:build` — green. ✅ (also `:desktopApp:compileKotlin`)
- `./gradlew.bat :desktopApp:run` — manual run over the "Playback", "Audio", "Subtitles" and "PiP"
  sections of `viewer-verification-pass.md`. The full checklist stays with plan 3; here only the
  absence of regressions in what was touched is checked. ✅ **Passed (Valik).**
- Mechanical anti-FQN run over the whole tree. ✅ — 230 `.kt` files, one hit, and it is the prose
  inside `core/system/AwtMediaKeyControls.kt:29` already cleared in Phase 1.
- `wc -l` over every `.kt` — no file over 150. ✅ **All 232 files.**
- Update `CLAUDE.md` if Phase 9 changed the stack description (the player engine goes from "JavaCV
  decode, audio, seek, tracks" to a set of collaborators). ✅ `engine/` is written out as its seven
  sub-packages and the files added by Phases 2–11 are in the map. No collaborator classes were
  created (see Phase 9), so there is nothing further to describe.

**All five checks pass. Plan closed** — move to `implemented/`, and `viewer-verification-pass.md` is
next in the chain.

Two behaviour questions raised during the work stay open on purpose, and belong to whoever picks them
up rather than to this plan: 10.4's blurred-backdrop refresh policy, and Phase 4's
`VideoReloadOverlay` 0.55 scrim (plus the 17 app-wide shadow tints and 11 non-viewer 12.dp radii that
want token groups of their own).

---

## Boundaries

- **Behaviour does not change.** The only exceptions are four confirmed defects fixed as a
  side-effect: the extension-list disagreement (Phase 2), the thumbnail cache key collision
  (Phase 3), events collapsing inside one clock tick (Phase 6), and the stale-thumbnail-after-replace
  case (Phase 3). Each is recorded in the report as its own line.
- Phase 10 changes *when and how often* work happens, not what is displayed — with one exception,
  10.4's blurred backdrop, which is flagged as a decision rather than assumed.
- **No new features.** Anything that amounts to a feature goes to `pending/` as its own file.
- Plan 3's findings (F1–F6: bitmap cache eviction, double load, EXIF, HDR for stills, the `finally`
  masking) are **not moved here** — they need a run and scope decisions, and stay in plan 3.
- No phase leaves the tree unbuildable. Phases 8, 9 and 10 are broken into internal steps.
