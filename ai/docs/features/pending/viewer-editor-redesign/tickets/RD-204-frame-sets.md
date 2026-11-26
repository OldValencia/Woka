# RD-204 — Frame sets: the strip and the scope line

| | |
|---|---|
| **Order** | fifth, after RD-203; RD-205 (Straighten) and RD-301 follow |
| **Depends on** | RD-201 (the inspector the scope line stands in) |
| **Feature** | [Viewer and Editor Redesign](../viewer-editor-redesign.md) |

**Goal.** Draw the frame strip as the prototype's card in the viewer and the editor
(`#viewer/frames`, `#editor/frames`), and move the editor's scope from the pill under the header
to a scope line above the inspector's tabs — `All frames | Frame N` for a frame set, `Whole video ›
Clip N` for a film.

**Why now.** RD-201 left the scope where ME-156 put it, centred over the picture, and the strip
drawn as the viewer's loose column; the editor's frame is otherwise the prototype's.

**Decided with the author (2026-11-26).**
- **A click on a frame only walks to it**, as ME-158 does; the scope changes only on the switch.
  The prototype switched to `Frame N` on click — a hidden action; the prototype was corrected.
- **The consequence line only at a loss**: under the switch only while `All frames` would replace
  other frames' own edits (ME-126); `Applies to all N frames` / `Applies to frame N only` go.
  Prototype corrected (`.scope-note`).
- **Both strips now**, one component in two colour sets, so the strip does not change on entering
  the editor. `glass` and `surfaceRaised` join `Theme` here, not in RD-501.
- **The film's scope line now** — the prototype's crumbs, `Whole video` clickable back to the whole
  film. RD-302 keeps the Clip and Sound tabs and the sound/text crumbs.
- **A GIF in the editor has no scope** — played, edited whole (ME-138). Its strip and transport
  in the editor were settled after this ticket (2026-11-26) and go to RD-501; the prototype draws
  them (`#editor/gif`).

## The spec, measured (`shell.css`, `editor.css`)

- **Strip card:** left 12, top 16 below the bar; padding 6, gap 6, radius 12, 1 dp border; viewer
  `glass` + `border`, editor `--e-panel` + `--e-line`. Max height: the space to the transport in
  the viewer (today's `bottomOverlayPadding`), 16 above the bottom in the editor. No backdrop blur
  (glass is 84–86 % opaque).
- **Tile:** 56 × 42, radius 7, `surfaceRaised` while loading, image cropped; no dimming, no size
  change; selected — 2 dp inner border, `accent` in the viewer, `--e-accent` in the editor.
  Edited dot 7 dp at 4/4, `accentEditor` with a 1.5 dp black 40 % ring.
- **Editor canvas:** start padding 104 with a strip (34 + the card's 70).
- **Scope line:** padding 12/14/0, min height 40, above the tab well. Frame set — the room
  segmented control full width; the loss note 11 sp muted, 7 dp above. Film — crumbs 13 sp,
  height 26, padding 3 × 7, radius 7: `Whole video` muted (hover text on `hoverStrong`), a 12 dp
  chevron in `faint`, the clip semibold text plus its length `2.4 s` in 12 sp muted mono.

## Steps

**Tokens and the strip**
1. `Theme.kt`: `glass` and `surfaceRaised` in `ThemeColors`, dark `rgba(22,26,34,.84)` /
   `#1E232D`, light `rgba(255,255,255,.86)` / `#F2F2F7` (`tokens.css`).
2. `core/ui/components/frames/FrameStripColors.kt` (new): `FrameStripColors(card, line, tile,
   selected)`, `FrameStripDefaults.viewer` (glass, border, surfaceRaised, accent).
3. `FrameStrip`: the card per the spec around the `LazyColumn` (top-aligned, `heightIn(max)` from
   the caller's constraints), `colors` parameter; `FRAME_STRIP_WIDTH` = 82 dp (12 + 70), the
   card's footprint from the edge.
4. `FrameThumbnail`: 56 × 42, radius 7, the tile colour, the 2 dp selected border; dimming and
   the 48/56 sizes removed. `EditedDot` (`EditedFrames.kt`): 7 dp, inset 4, the ring.
5. `MediaViewerPlayerFrames`: `TopStart`, start 12, top 16 — the viewer's area starts under the bar
   (`MainShell`'s column), as the editor's does; the old 72 cleared nothing still there.

**Editor strip**
6. `EditorRoomControls`: `RoomControls.frameStrip` (panel, line, `surfaceRaised`, room accent).
   `EditorFrameStrip` passes it; placed `TopStart`, padding 12/16/16. `canvasInsetFor`: 70 dp.

**Scope line**
7. `EditorEngine.clipNumberOf` → `clipScopeOf(document, id): ClipScope?` (`ClipScope(number,
   lengthMs)`, `EditorFrames.kt`); `VideoEditorClips` fills it from `TrimClip.lengthMs`.
   `EditorFrameControls.selectedClip: Int?` → `clip: ClipScope?`.
8. `EditorScopeLine.kt` (new, replaces `EditorFrameScopeStrip.kt`): the frame set's
   `RoomSegmented("All frames", "Frame N")` and the loss note (`lossNoteOf(frames, scope,
   frameIndex)`: null in Single Frame or with nothing replaced, the open frame left out, as
   `consequenceOf` counted); the film's crumbs — `Whole video` alone as the current crumb with no
   clip, else a button (`editor-scope-whole`, `session.pickClip(null, NO_CLIP)`) › the clip. Tag
   `editor-frame-scope` kept on the line.
9. `EditorInspectorPanel` takes `scope: (@Composable () -> Unit)?` and draws it above
   `EditorTabBar`. `EditorScreen` passes the line for `perFrame`, and for any film (one clip:
   `Whole video` alone). `EditorFrameBand` draws the strip only; deleted if that leaves it a
   one-liner (the call moves into `EditorScreen`).

**Tests**
10. `EditorFrameScopeUiTest`: the switch in the panel; the loss note shown only with other
    frames overridden in All frames; Single Frame and no-loss show none. `EditorFrameStripUiTest`,
    `FrameStripUiTest`, `FrameStripMarkUiTest`: the new sizes and the selected border, no dimming.
    New `ui/EditorFilmScopeUiTest`: a cut film shows `Whole video › Clip 2 · …s` with clip 2
    selected; `Whole video` clears the selection; an uncut film shows `Whole video` alone.

## Files

New: `FrameStripColors.kt`, `EditorScopeLine.kt`, `ui/EditorFilmScopeUiTest.kt`. Edited:
`Theme.kt` (+ its colour sets), `FrameStrip`, `FrameThumbnail`, `EditedFrames`,
`MediaViewerPlayerFrames`, `EditorRoomControls`, `EditorFrameStrip`, `EditorFrameBand`,
`EditorFrameControls`, `EditorFrames`, `EditorEngine`, `VideoEditorClips`, `EditorInspectorPanel`,
`EditorScreen`, the UI tests (step 10). Deleted: `EditorFrameScopeStrip.kt`.

## Done when

- A GIF or burst in the viewer and the editor shows the card strip top-left; entering the editor
  does not move it; both themes.
- The editor's scope sits above the tabs; nothing is drawn over the picture's top edge.
- `Grep "EditorFrameScopeStrip|clipNumberOf"` over `shared/src`: no match.
- Every touched `.kt` ≤ 150 lines; anti-FQN clean; `./gradlew.bat :shared:build` green.

## Result — 2026-11-26

Built as specified, both themes. Where the build differs from the steps:

- The crumbs are their own file, `FilmScopeCrumbs.kt` (tags `editor-scope-whole`,
  `editor-scope-clip`), beside `EditorScopeLine.kt`; the loss note is tagged `editor-scope-note`.
- `FRAME_STRIP_CARD` (70 dp) is the card's width; `FRAME_STRIP_WIDTH` is 12 + that, the viewer's
  Prev-arrow inset. The editor canvas gives up the card's width (`canvasInsetFor`).
- `EditorFrameBand` stays as the strip's placement — the strip only.
- Tests: `EditorFrameScopeUiTest` (8: in the panel, note only at a loss, the open frame not
  counted, Frame N takes the note away), `FrameStripMarkUiTest` (tiles 56 × 42 selected or not),
  new `EditorFilmScopeUiTest` (3). `EditorFrameStripUiTest`, `FrameStripUiTest` unchanged and green.
- Prototype checked headless (`shot.mjs`): the loss note in `#editor/frames`, a frame click keeps
  the scope, `#editor/gif` has no strip or scope. Not checked by eye in the app.
