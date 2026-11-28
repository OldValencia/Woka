# RD-502 — Carousel badges and Original

| | |
|---|---|
| **Order** | tenth, after RD-501; RD-601 (fullscreen pill and the compact PiP) follows |
| **Depends on** | RD-201 (the editor's `Original`), RD-204 (`EditedDot`), RD-501 (the transport above the carousel) |
| **Feature** | [Viewer and Editor Redesign](../viewer-editor-redesign.md) |

**Goal.** Restyle the viewer's carousel as `#viewer/photo` draws it — a well, dimmed neighbours,
a ringed current item — with a badge per kind (film length, animation format, frame count,
music) and the edited dot. Put `Original` in the viewer's pill for an edited file, and make it
and the editor's `Original` one control: click, hold, `\`, the `Original` label, Esc.

**Why now.** The carousel tells nothing about a file but a play circle on a video: an animation,
a burst, a track and an edited photo look like any other still, and a film's length is seen only
after opening it. An edit kept in a file can be compared with the original only by entering the
editor and holding a button; the viewer has no way at all.

## Decided with the author (2026-11-28)

- **A film's `Original` in the viewer** is the same moment without the look — Adjust, Styles,
  Transform, Markup — as the editor's `Original` already is. Cuts, speed and sound stay; the film
  keeps playing on the result's timeline. No seek, no switch to the uncut source.
- **The editor's `Original` becomes the prototype's** (`initComparison`: one control in two
  places): a click toggles, a press held past 200 ms shows the original until release and then
  returns to what was shown before the press, `\` held does the same, the `Original` label stands
  over the picture while it shows, Esc ends it first. Today it is hold-only (RD-201 kept it so).
- **An animation's badge reads its format**: the file's extension in capitals — `GIF`, `WEBP`.
- **A film's badge reads the result's length**: what the viewer plays after cuts and speed
  (`EditedClock.totalMs`), the trailing label's number. An unedited film reads its own length.

## Taken from the prototype as drawn (not asked)

- **Badges** (`woka.js` `carouselBadge`, `viewer.css` `.media-chip`, `.media-edited`): one chip
  bottom-left — track: the music glyph alone; film: play glyph + length; animation: format; a
  frame set of more than one picture that is not an animation: its count; a single still: none.
  The edited dot top-right on any file that carries an edit. The centre play circle goes.
- **Length format** is the transport's `formatTime(ms)`: `00:32`, `12:05`, `1:02:03` — the
  prototype's `shortTime` for anything under an hour.
- **`Original` in the viewer** shows only for a file that carries an edit, in the group that
  retracts while editing, before the carousel toggle (`.viewer-actions`: Original, carousel, |,
  Edit). Pressed, its glyph and word take `accent`. It is not in the fullscreen pill
  (`.focus-view .viewer-actions` hidden), so fullscreen and PiP end a comparison.
- **A comparison ends** when the shown file changes and when the editor opens or closes
  (`setScene`, `setViewer` reset `compareOriginal`); Esc ends it before doing anything else.
- **Tooltip, both places:** "Show the original. Click again to return, or press and hold.
  Shortcut: hold \\".

## Colours

The prototype's two new fills map onto tokens the app has (§6), and the prototype follows:
`.media-chip` `--black-53p3` → `Theme.viewer.hudScrim` (black 50 %, `--black-50`);
`--original-label-fill` `#151515c9` → `Theme.viewer.tooltipScrim` (black 70 %). Text on both is
`Theme.viewer.onScrim`. The dot is `EditedDot` as drawn on frame thumbnails.

**Not drawn, not added:** `accentEditorSoft` — its readers are the File information dialog's
`.info-edited` (that dialog is drawn as the app has it, feature doc §"The spec") and the
prototype's `#compare` pressed, which RD-201 already draws in the room. `canvas` is never drawn.
Both leave the backlog with this ticket.

## The spec, measured (`viewer.css`)

| Part | Value |
|---|---|
| Well | radius 12, `colors.background` at 48 %, 1 dp `colors.border`, shadow `0 4 24` black 4.7 %, padding 0 × 12, 32 dp from the sides |
| Row | 80 dp high, items centred, gap 8 |
| Item | 56 dp, radius 8, opacity 0.6, 1 on hover; current 72 dp, opacity 1, 2 dp `accent` ring inside; size and opacity animate 180 ms |
| Chip | left 4, bottom 4, 16 dp high, padding 0 × 4, radius 5, gap 2, `hudScrim`, `onScrim` 9 sp semibold, glyph 8 dp |
| Viewer `Original` | `EditButton`'s geometry (24 dp, padding 0 × 8, glyph 12 dp + 4, 13 sp medium), `Icons.Static.PeekOriginal`; `textPrimary`, pressed `accent` |
| Label | top-centre of the picture's area, 20 dp down, padding 5 × 12, radius 6, `tooltipScrim`, `onScrim` 13 sp; fades in 120 ms |

## Steps

1. **Prototype first.** `.media-chip` background `var(--black-50)`; `--original-label-fill`
   `#000000b3` in both themes. In `app.js`'s keydown, `\` (not repeated, no field focused, an
   edited file in the viewer or any file in the editor) remembers `compareOriginal` and calls
   `toggleComparison(true)`; a keyup listener restores it. Check with
   `node ai/mockups/tools/shot.mjs` at 1024 × 700, both themes, `#viewer/photo`, `#viewer/palm`
   (edited), `#editor/photo`.
2. **One compare state.** `OriginalCompare` (new, `core/ui/components/OriginalCompare.kt`,
   commonMain), `@Stable`: `active` (read-only state), `toggle()`, `end()`, `holdKey(down)` —
   down remembers `active` and shows, up restores; repeats while held are ignored.
   `Modifier.originalPress(compare)`: on press start a 200 ms timer; if it fires, remember and
   show; on release after it, restore; a release before it is a click, `toggle()`. A cancel counts
   as a release (`tryAwaitRelease`, as `EditorPeekButton` does today). `OriginalLabel(visible,
   modifier)` draws the label per the table. Unit-tested without Compose where it can be.
3. **The editor takes it.** `PeekState` is removed; `EditorPeekButton` takes `OriginalCompare`
   and `originalPress`, keeps its look and `editor-peek-original`, and the new tooltip.
   `OriginalCompare` is built in `EditorHost` beside `EditorPanelState` and passed to
   `EditorScreen` (as `panel` is, for the keys). `EditorHost` is at 149: the split is its own step
   before this one. `EditorKeyEffect` gains `original: OriginalCompare`: `Key.Backslash` KeyDown
   and KeyUp go to `holdKey`, ahead of the KeyDown-only return; Esc ends an active comparison
   before `onBack` and `onCancel`. `EditorCanvasLayer` draws `OriginalLabel` over the canvas.
   `EditorHelpSheet`'s photo line: "Click Original, or hold it or \\, to see the photo before your
   edits. Done keeps…".
4. **Is this file edited.** `rememberCarriesEdit(item: MediaItem?): Boolean` (new,
   `viewer/shell/CarriesEdit.kt`): an image asks `AppliedStillEdits.carries` in a
   `LaunchedEffect` keyed on the path and `rememberReloadKey`; a film is
   `AppliedVideoEdits.editOf(file) != null`, remembered on `AppliedVideoEdits.generation`; a track
   is false. `FileInfoMeta.NameLine` keeps its own code (not this ticket's).
5. **The original picture.** `ImageBitmapCache.originalKey(path, frameIndex)` =
   `"$path#original$frameIndex"` + `HdrView.cacheSuffix`, so `removeAllFor` still drops it.
   `ImageLoader.loadOriginal(file, frameIndex)`: `displayable(decode)` with no
   `AppliedStillEdits.apply`, cached under that key — the same LRU budget, so memory does not grow.
   `ViewerThumbnails` gains `suspend fun original(item, frameIndex): ImageBitmap? = full(item,
   frameIndex)`, which leaves the test fakes as they are; `RealViewerThumbnails` answers through
   `MediaThumbnails.loadOriginal`, under its `decoded` policy. `MediaThumbnails` is at 142: if
   the function takes it past 150, it goes to a new `MediaOriginals.kt` and `decoded` becomes
   `internal`.
6. **The viewer compares.**
   - `MediaViewerState` gains `val original = OriginalCompare()` and `canShowOriginal = false`.
   - `MediaViewerPlayer` publishes `canShowOriginal = rememberCarriesEdit(activeItem) &&
     !isFullscreen` and calls `original.end()` when it turns false. The file-change effect
     (`MediaViewerScreenEffects`) and `MediaViewerHandlers`' `onEdit` call `end()`.
   - `StillLoadEffect` is keyed on `state.original.active` too, and asks `thumbnails.original`
     instead of `full` while it is active (no preview step: the edited picture stays until the
     original lands). Photos, frame sets and animations all come through here.
   - A film: `LocalViewerOriginal` (new, in `MediaViewerFrame.kt`), provided by
     `MediaViewerPlayer` around `MediaViewerContent`; `MediaViewerFrame` skips `edit.painter` while
     it is true and draws `rememberFramePainter`. `PlayEditedRanges` and `PlayEditedSound` are
     untouched. The fullscreen window provides nothing, so it never compares.
   - `MediaViewerPlayerOverlays` draws `OriginalLabel` while `original.active`.
7. **The viewer's button and keys.** `ViewerOriginalButton(compare)` (new,
   `viewer/shell/ViewerOriginalButton.kt`; `GlobalControlButtons` is at 149), tag
   `viewer-original`. `MediaViewerNormalControls` gains `original: OriginalCompare?` (null hides
   it) and draws it first in the retracting group, before `ToggleCarouselButton`, with its
   `PillGap`. `MediaViewerEffects` passes `state.original.takeIf { state.canShowOriginal }` through
   a `rememberUpdatedState`. `handleMediaViewerKeyEvent` gains `original: OriginalCompare? = null`:
   `Key.Backslash` down/up go to `holdKey`; Esc with an active comparison ends it and does not
   close. Both callers — `MediaViewerShortcutEffect` and `viewerKeyedModifier` — pass the same
   `takeIf`. The `\` key types `\` on the Russian layout as well, so `Key.Backslash` needs no
   `LayoutFreeKeys` entry; the UI test sends `Key.Backslash`.
8. **Badge facts.** `CarouselChip` (new, `viewer/shell/CarouselChip.kt`): `Music`,
   `Film(lengthMs)`, `Animation(format)`, `Frames(count)`, or null. `suspend fun chipFor(item)` on
   `ImageBitmapCache.ioDispatcher`: a track → `Music`; a film → the source length, then
   `AppliedVideoEdits.editOf(file)?.clock?.totalMs` when its ranges are not empty; an image →
   `MediaThumbnails.frameSet(item)`, `Animation(extension.uppercase())` when `isAnimated`,
   `Frames(viewable.size)` when more than one. The source length is `containerLengthMs(file)` (new,
   `video/engine/probe/ContainerLength.kt`): `onFormatContext(file)` without stream info,
   `format.duration()` in ms, falling back to `measuredDurationMs` where `lengthMs` does (no
   declared length, or `dhav`). Answers are kept in a map keyed on path and `lastModified` (a few
   bytes each; the lazy row disposes off-screen items), the film's edit applied at read.
9. **The carousel.** `MediaViewerCarousel` takes the table's geometry: the well around the
   `LazyRow`, items with animated size, opacity and the ring, the 40 % black veil and the centre
   play circle removed. Per item, `chipFor` in a `LaunchedEffect` keyed on the path, the reload key
   and `AppliedVideoEdits.generation`; `MediaChip(chip)` (in `CarouselChip.kt`, tag
   `carousel-chip`) bottom-left and `EditedDot` top-right when `rememberCarriesEdit(item)`. The item
   body moves to `CarouselItem.kt` so the carousel stays under 150. `MotionBadge`'s KDoc stops
   citing the carousel's play badge, which no longer exists.

## Files

`ai/mockups/design/woka/{viewer.css, tokens.css, app.js}` (edit);
`core/ui/components/OriginalCompare.kt` (new, commonMain);
`core/ui/image/ImageBitmapCache.kt`, `core/ui/components/frames/MotionBadge.kt` (edit);
`feature/editor/shell/{EditorPeekButton, EditorHost, EditorScreen, EditorKeys, EditorCanvasLayer,
EditorTopBarSetup, EditorHelpSheet}.kt` (edit) + the `EditorHost` split;
`feature/viewer/shell/{CarriesEdit, ViewerOriginalButton, CarouselChip, CarouselItem}.kt` (new);
`feature/viewer/shell/{MediaViewerState, MediaViewerPlayer, MediaViewerScreenEffects,
MediaViewerHandlers, MediaViewerStillLoad, MediaViewerFrame, MediaViewerPlayerOverlays,
MediaViewerGlobalControls, MediaViewerEffects, MediaViewerKeyHandler, MediaViewerShortcutEffect,
MediaViewerCarousel, ViewerThumbnails, MediaThumbnails}.kt`, `feature/viewer/NativeMediaViewer.kt`,
`feature/viewer/image/ImageLoader.kt` (edit);
`feature/viewer/video/engine/probe/ContainerLength.kt` (new).
Over §4a's eight because the slice the author sees is the carousel and one control in two places;
splitting either half ships a button that differs between viewer and editor. Every file is
measured with `wc -l` before it is touched; `EditorHost` (149), `GlobalControlButtons` (149) and
`MediaViewerContent` (149, not touched) are at the ceiling.

## As built (2026-11-28)

Where the build left the steps, and why:
- **`OriginalCompare.hold(down)`**, not `holdKey`: the button's hold past 200 ms and `\` are
  one hold, so both call it. `holdOnKey(event)` turns `\`'s down/up into it, and
  `endIfActive()` is Esc's half, so both key paths stay one line. The label went to its own
  `OriginalLabel.kt` (with `ORIGINAL_TOOLTIP`, shared by both buttons) at the size mandate.
- **No `ImageBitmapCache.originalKey`**: that file is at 147. The key is private to
  `ImageLoader.kt` (`path#original<frame>`), still under `removeAllFor`'s `path#` prefix.
- **`ViewerOriginalEffect`** (in `CarriesEdit.kt`) publishes `canShowOriginal` and ends a
  comparison on a file change, in place of the file-change effect; it and the
  `LocalViewerOriginal` provider sit in `MediaViewerScreen` (132), not `MediaViewerPlayer`
  (146). **No `!isFullscreen`**: fullscreen is PiP's own window and `enterFullscreen` closes
  this viewer, so there is nothing to end. `state.offeredOriginal()` is the one
  `takeIf { canShowOriginal }` the pill and both key paths read; the focused path reads it
  per key (`original: () -> OriginalCompare?`).
- **The editor's keys** moved to `EditorHostKeyEffect` (`EditorHostKeys.kt`), the
  `EditorHost` split; `OriginalCompare` is built there beside `EditorPanelState`. `\` is
  handled after the typing gate and before the KeyDown-only return, so a caption still
  takes a typed `\`.
- **`containerLengthMs` makes a second, stream-info pass** when the header states no
  length: measured 2026-11-28, `blank.bik` reads 0 from its header and 40 ms after
  `avformat_find_stream_info`, which is what a grabber reports.
- **The prototype's help line** reads "Click Original, or hold it or \\, …", as the app's.
- **Not covered by a test:** a cut film's chip reading the result's length. `chipFor` takes
  `EditedClock.totalMs` from `AppliedVideoEdits.editOf`; a test would have to install a
  fake `VideoEdits` globally, which no other test does and which would leak into the run.
  `ContainerLengthTest` and `CarouselChipTest` are integration tests (they open files with
  FFmpeg), not unit tests as the steps named the first.

## Done when

- `#viewer/photo`, `#viewer/palm` and `#editor/photo` match the corrected prototype in both themes
  (shot.mjs computed styles: well, item, chip, label).
- `unit/OriginalCompareTest`: click toggles; a hold shows and its release restores; a hold over an
  active comparison leaves it active; `holdKey` ignores repeats; `end()`.
- `unit/ContainerLengthTest` on a film fixture: the length matches `lengthMs` from a grabber.
- `ui/CarouselBadgesUiTest` (new): a film's chip reads `formatTime` of its length; a GIF reads
  `GIF`; a burst its count; a track carries the music glyph; a single still has no chip; an edited
  still and only it has `frame-edited`; a film with a cut reads the result's length.
- `ui/ViewerOriginalUiTest` (new): `viewer-original` is absent on an unedited file and present on
  an edited one; a click shows the original (the drawn bitmap is the source's size under a crop
  recipe) and the label, a second click returns; `\` held shows, released returns; Esc ends the
  comparison and the viewer stays open; paging to the next file ends it.
- `ui/EditorOriginalUiTest` (new): a click on `editor-peek-original` toggles the original and the
  label; a hold returns on release; Esc ends it and the editor stays open; `\` held shows.
- The anti-FQN grep is clean on every edited `.kt`. No `.kt` is over 150 lines.
- `./gradlew.bat :shared:build` is green.
