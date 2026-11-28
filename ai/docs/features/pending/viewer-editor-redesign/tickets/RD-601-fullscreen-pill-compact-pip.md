# RD-601 — Fullscreen pill and the compact PiP

| | |
|---|---|
| **Order** | eleventh, after RD-502; RD-701 (size remembered per kind) follows |
| **Depends on** | RD-501 (the transport and its tokens) |
| **Feature** | [Viewer and Editor Redesign](../viewer-editor-redesign.md) |

**Goal.** Give the fullscreen window the prototype's pill — size | PiP, without the inert carousel
button (`#viewer/fullscreen`). In PiP (`#viewer/pip`), the pill is the PiP button alone, the
transport is compact, and the window resizes from any corner or side at the film's proportions,
freely with Alt.

**Why now.** These are the two PiP items of 1.0.9 in `next_steps.md`. The fullscreen pill carries
an eye button that does nothing and says so in its tooltip. In PiP, the zoom controls take a
third of a 480 dp window, and the transport keeps ±5 s, subtitles and settings at full size.
Resizing goes through Windows' own edges, which know nothing of the film. A 16:9 film dragged
to a square leaves two black bands it did not ask for.

## Decided with the author (2026-11-28)

- **Nothing pins PiP to a screen corner.** `pipBounds` puts it bottom-right only when it is
  entered, as today. The window stays draggable anywhere (`pipWindowDraggable`, untouched), and
  a resize is anchored on **the window's own** opposite corner or side, wherever it stands. The
  prototype's bottom-right anchoring is its stand-in desktop, not a spec.
- **All four corners and all four sides resize PiP.** The opposite corner or side stays where
  it is. The prototype's single top-left handle assumed a window that never moves, and PiP is
  dragged anywhere.
- **The film's proportions always; Alt held frees them.** There is no setting. ("At least make
  it a setting" in `next_steps.md` is answered by Alt.)
- **Windows' own resize is switched off for this window**, and Woka draws its own handles,
  in PiP only. Windows cannot keep a ratio, and two mechanisms on the same edges would fight.
  **The fullscreen window no longer shrinks by its edge.** Going into PiP is the pill's button,
  as in the prototype.
- **No maximum, and no clamp on position** (asked during step 1). A resize may grow the window past
  the work area and onto another monitor; only the minimum is enforced, so the app stays usable.
- **The minimum is 320 × 180 at any ratio, free or locked** (asked during step 1). The compact
  transport needs about 290 px of window width (measured in the prototype), so a shorter-side-180
  rule would clip it at 180 × 180 with Alt or 180 × 320 for a portrait film. Locked, the minimum is
  the smallest size at the film's ratio that covers 320 × 180 (9:16 → 320 × 569).

## Taken from the prototype as drawn (not asked)

- **Fullscreen pill** (`.focus-view`): the size control, a divider, the PiP button. No Info,
  no carousel, no HDR, no Edit. Its place is unchanged.
- **PiP pill** (`.pip-view .size-part` hidden): the PiP button alone, `accentFolders` while
  PiP, 8 dp from the top and the right.
- **Compact transport** (`.pip-view .viewer-playback`): 40 dp high, radius 12, gap 6, 10 dp from
  the window's sides and bottom. It drops ±5 s, subtitles and settings. Play, time, seek, length,
  volume and the exit button stay.
- **Minimum PiP** (`PIP_MIN_WIDTH`, `PIP_MIN_HEIGHT`): 320 × 180 dp, see the decisions above.

## Resizing, specified

| Grabbed | Stays | Changes |
|---|---|---|
| a corner | the opposite corner | both sides; locked: the height follows the width |
| left / right side | the opposite side and the top edge | the width; locked: the height follows downward |
| top / bottom side | the opposite side and the left edge | the height; locked: the width follows rightward |

Zones are 16 dp squares in the corners and 6 dp strips along the sides between them, inside the
window, with the matching AWT resize cursor. They lie under the pill and the transport, so a
control wins where the two overlap. The ratio is the one `pipBounds` already uses
(`PipManager.nativeSize`, 16:9 when unknown). Alt is read on every move, so pressing or releasing
it mid-drag switches at once.

## Steps

1. **Prototype first.** In `woka.html`, replace the one `#pip-resize` with eight
   `.pip-resize` elements carrying `data-edge` (`nw n ne e se s sw w`). In `viewer.css`, place
   them by edge: corners 16 px, sides 6 px, each with its resize cursor. In `fullscreen.js`, one
   pointer handler resizes from the grabbed edge per the table and anchors the window by `left`
   / `top` instead of `right` / `bottom`. Use the film's ratio, and none while `altKey`. Enforce
   only the minimum, 320 × 180. Check with
   `node ai/mockups/tools/shot.mjs` at 1024 × 700, both themes: `#viewer/fullscreen`,
   `#viewer/pip`, and a scripted drag on `se` and on `w`, with and without Alt.
2. **The geometry, pure.** Add `PipResize.kt` (new, `pip/`):
   - `enum class PipEdge { N, NE, E, SE, S, SW, W, NW }`.
   - `resizedBounds(start: Rectangle, edge: PipEdge, dx: Int, dy: Int, ratio: Float?, min: Dimension): Rectangle`
     follows the table. A null `ratio` is free. The result is held at `min` (free: each axis;
     locked: the smallest size at the ratio covering it), keeping the anchored corner or side
     fixed. No maximum.

   Device pixels throughout, as `pipBounds` uses.
3. **The handles.** `BoxScope.PipResizeHandles(window: Frame, ratio: () -> Float?)` (new, `pip/PipResizeHandles.kt`): eight zones per the spec. Each zone:
   - on press, records `MouseInfo` and `window.bounds`, as `pipWindowDraggable` does, since the
     window moves under the pointer;
   - on move, calls `resizedBounds` with `ratio` unless `keyboardModifiers.isAltPressed`, and
     `window.setBounds`;
   - consumes its events, so neither the drag nor the tap below sees them.

   The cursor is `PointerIcon(Cursor(Cursor.*_RESIZE_CURSOR))`. `rememberPipWindowMode` writes
   `WindowState` after a resize, as `applyBounds` does. Lift that write into an internal
   `syncState(window, state)` so a handle's release can call it.
4. **Windows' resize off, the handles on.** `MediaViewerFullscreenWindow` is at 149, so the split
   is its own step first: the black `Box` and its children move to `MediaViewerFullscreenBody`
   (new, `shell/MediaViewerFullscreenBody.kt`). Then `Window(resizable = false)`, and the body
   draws `PipResizeHandles` after `MediaViewerFullscreenSurface` and before the overlays, while
   `pipMode.isPip`. Correct the `isPipSized` / `rememberPipWindowMode` KDoc: the frame's edge is
   no longer a route into PiP. The listener stays for the button and the OS shortcuts.
5. **The pill.** `MediaViewerFullscreenControls` loses `carouselMode` / `onToggleCarousel`. It
   draws `SizeControl`, `PillDivider` and `PipWindowButton` in fullscreen, and `PipWindowButton`
   alone in PiP. `MediaViewerFullscreenTopControls` pads 8 dp in PiP and 16 dp in fullscreen.
   `ToggleCarouselButton` loses `isFullscreen` and its inert branch, which nothing reaches now.
6. **The compact transport.** `TransportTokens` gains `compactHeight = 40.dp`,
   `compactRadius = 12.dp`, `compactGap = 6.dp` and `compactInset = 10.dp`.
   `Modifier.transportBar(colors, compact = false)` picks height and radius. `MediaViewerVideoControls`
   gains `compact: Boolean = false`:
   - the inset is 10 dp all round, against 32 × 8;
   - the gap is 6 dp;
   - `MediaViewerTimeSlider(showSkip = !compact)` is a new parameter that hides both `SkipButton`s;
   - there is no `SubtitleControl` and no `PlayerSettingsControl`.

   `MediaViewerFullscreenOverlays` gains `isPipWindow` and passes it as `compact`. The window
   passes `pipMode.isPip`.
7. **Tags for the tests.** Where missing, add tags to the two `SkipButton`s
   (`viewer-skip-back`, `viewer-skip-forward`), `SubtitleControl` (`viewer-subtitles`),
   `PlayerSettingsControl` (`viewer-settings`), `PipWindowButton` (`viewer-pip`) and
   `SizeControl`'s root (`viewer-size`).

## Files

`ai/mockups/design/{woka.html, woka/viewer.css, woka/fullscreen.js}` (edit);
`feature/viewer/pip/{PipResize, PipResizeHandles}.kt` (new), `feature/viewer/pip/PipWindowMode.kt` (edit);
`feature/viewer/shell/MediaViewerFullscreenBody.kt` (new);
`feature/viewer/shell/{MediaViewerFullscreenWindow, MediaViewerFullscreenOverlays,
MediaViewerFullscreenControls, GlobalControlButtons, MediaViewerGlobalControls}.kt` (edit);
`feature/viewer/video/{TransportStyle, MediaViewerVideoControls, MediaViewerTimeSlider}.kt` (edit),
plus the tag sites of step 7 (`SubtitleControl`, `PlayerSettingsControl`, `zoom/SizeControl`).
Over §4a's eight: the slice the author sees is one window in two modes. Pill, transport and
resize each read `isPip`, and none of them is visible without the others' mode.
`MediaViewerFullscreenWindow` (149) and `GlobalControlButtons` (149) are at the ceiling. Every
file is measured with `wc -l` before it is touched.

## Done when

- `#viewer/fullscreen` and `#viewer/pip` match the corrected prototype in both themes
  (shot.mjs computed styles: pill, transport, handles).
- `unit/PipResizeTest`:
  - each edge keeps its anchor;
  - locked, the result has the film's ratio;
  - free (null), only the dragged axes move;
  - the size stops at 320 × 180, free and locked, landscape and portrait;
  - nothing caps growth (a result larger than any screen is returned as is).
- `ui/FullscreenPillUiTest` (new): in fullscreen `viewer-size` and `viewer-pip` show, and no
  eye/carousel button; in PiP only `viewer-pip`.
- `ui/CompactTransportUiTest` (new): on a film with subtitles, the full bar has
  `viewer-skip-back`, `viewer-subtitles` and `viewer-settings`; `compact` has none of them, and
  has play, seek and volume.
- By hand, on the native image (what ships):
  - every zone of PiP resizes and keeps the ratio;
  - Alt frees it;
  - the window cannot be pulled out of fullscreen by its edge;
  - the pill's button still goes both ways.
- The anti-FQN grep is clean on every edited `.kt`. No `.kt` is over 150 lines.
- `./gradlew.bat :shared:build` is green.
