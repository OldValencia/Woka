# RD-301 — Timeline

| | |
|---|---|
| **Order** | seventh, after RD-205; RD-302 (Clip and Sound tabs) follows |
| **Depends on** | RD-201 (the room, the inspector), RD-204 (the film's scope crumbs) |
| **Feature** | [Viewer and Editor Redesign](../viewer-editor-redesign.md) |

**Goal.** Replace the film's floating 640 dp band with the prototype's docked timeline
(`#editor/video`): the transport, the ruler, a clip lane with stills and each clip's wave, a
Markup lane, the playhead with scissors, skimming, the context menu and a resizable height.

**Why now.** The band is the last part of the editor still drawn in the old style: a scrim card
over the picture, with tabs under the clips and a bar that floats over them. The panel is fixed
at 640 dp, so a long film cannot get more room on screen.

**Decided with the author (2026-11-26).**
- **No music in 1.0.9.** The Audio button, the empty "Add music" row, dropping a file and
  `Detach sound` are ME-244 / ME-242 (1.1.0); the engine mixes no outside sound. The prototype
  keeps them only in `#editor/audio`, labelled 1.1.0. Theme's own `sound`/`soundBg` are only
  read by the music clip, so they come with ME-244. The timeline reads `Theme.room.sound`/`soundBg`,
  which arrived in RD-201.
- **A Markup lane.** It has one bar per shape and stacks overlapping bars into rows. A shape
  with no window draws across the whole film. Dragging the bar's body or an edge gives it a
  window, the same write `Appears at` makes. Bars snap to 0, the playhead, clip joins and the
  film's end, with a guide line; Alt turns snapping off. `MarkedSpan` is removed.
- **Skim without a decode.** Hovering shows a line, plus a badge with the time and the nearest
  filmstrip still. The canvas does not change. A seek on every hover would cost a heavy HEVC's
  pre-roll (~3.5 s measured) in CPU and RAM. The prototype's badge gets the picture.
- **`m:ss:ff`**, frame-accurate (ME-219), in the prototype's place and type; the ❙◀ ▶❙ buttons
  go, and Alt+←/→ still step a frame. Prototype corrected.
- **Clicks as in the prototype.** A click on a clip selects it, as one step (scope `Clip N`),
  without moving the playhead; the ruler seeks. From Transform or Markup the click opens Clip.
  A click on a clip's wave opens Sound. The clip tabs (2026-11-04) are removed: the body no
  longer seeks, which was the problem they solved.
- **Handles in every tab** on the selected clip, as well as the film's two outer ends. Scissors
  are always shown on the playhead. The trimmed source draws dimmed, but only while an outer end
  is dragged. Prototype gets the dimmed ends.
- **The floating clip bar becomes a context menu**, the prototype's plus Duplicate and Join.
  ❄ moves from the playhead into the menu as `Hold this frame for 2 s`. Speed waits for the Clip
  tab (RD-302), so until RD-302 it cannot be reached. Cover returns as `Use frame as cover`.
- **Right-click opens the menu.** Resetting the cut, previously the right-click (2026-11-02),
  becomes `Undo all cuts` in the empty-place menu.
- **The wave is always shown and the height is remembered** (left to judgement by the author).
  The speaker toggle (ME-241) is removed: peaks are measured either way, and an always-visible
  wave makes a clip's sound, mute or silence visible. The height is saved in `settings.json`,
  because it is set once per screen.
- **Not in the prototype; kept on judgement:** the edited dot on a clip with its own look
  (ME-215, RD-204's `EditedDot`); zoom past 4× (the app's tightest is 5 s across the lane), with
  the factor between − and + while zoomed and a click on it fitting the film (the author's
  2026-11-04 "show that a zoom happened"); `ZoomOverview` as the lane's scrollbar.

## The spec, measured (`editor.css`, `.timeline` and on, room overrides)

- **Panel:** `room.panel`, 1 dp `room.line` on top; height from settings, 220 by default,
  clamped to 200 … window − 380. Resizer: an 8 dp band straddling the top edge; grip 34 × 3,
  radius 3, `faint` at 60 %, on hover 56 wide in `accent`.
- **Transport** (padding 10/14/6): play — 32 dp round, `well2`, hover `knob`, glyph 15 `text`;
  time 6 dp after it, mono 500 12 sp `text`, ` / total` in `faint`. Right: Split — 32 high,
  padding 0 × 10, radius 8, scissors 16 + "Split" 12 sp `muted`, hover `text` on `hoverStrong`;
  a divider 18 high in `line`; − and + 30 × 30, radius 8, 15 sp `muted`.
- **Tracks:** a 44 dp label column (icon 16, `faint`, on `panel`), lanes to 16 dp from the
  right, 6 dp between rows. Ruler row 27: mono 10 sp `faint`, a tick row 4 dp high in `line`.
  Lane: min 46, `lane`, radius 8.
- **Clip** (video row 64): radius 8, `well`; hover a 1 dp inner white 20 %; selected a 2 dp ring
  in `accent`. Stills over the top 46, opacity .92. Name top 5 / left 6, padding 1 × 5, radius
  4, black 55 %, white mono 500 10 sp — `2.4 s`, `2.4 s · 2×`, `2.0 s · still`. Wave strip: the
  bottom 18 dp on `soundBg`, the wave in `sound`, inset 2 × 4; muted at 35 %; a freeze draws a
  1 dp line at half height. Handles 10 wide, `accent`, a 2 × 16 grip in `onAccent` at 75 %;
  opacity 0, hover .65, selected 1.
- **Markup bar:** 30 high, `well2`, radius 8, label 12 sp `text` (a text shape's text, else its
  kind); fades masked over 14 dp at each end. Selected, hover, handles as a clip's.
- **Playhead:** 2 dp `accent` through ruler and lanes, a 10 dp dot 5 from the top. Scissors
  26 × 22, radius 7, top 2, centred on the line, `accent` (hover `accentHover`), glyph 15
  `onAccent`, shadow 0 2 8 black 33 %; while playing or with nowhere to cut: opacity 0, scale
  .6, 140 ms.
- **Skim** line 1 dp `text` at 35 %. **Snap guide** 1 dp `text`, glow 8 white 45 %.
- **Skim badge:** centred, 12 above the canvas's bottom; `tip`, radius 7, padding 3 × 9; a
  96 × 54 still (radius 5) over the time, mono 11 sp `tipText`. **Drag badge:** `tip`, radius
  6, padding 3 × 8, mono 11 sp, 14 above the pointer.
- **Menu:** min width 216, padding 5, 1 dp `line`, radius 12, `menu`, shadow 0 20 50 black 45 %;
  items min 30, padding 4 × 10, radius 7, 13 sp `text`, hover `hoverStrong`, danger `danger`,
  shortcut mono 11 sp `faint`; separators 1 dp `line`, margin 4 × 6.

## Steps

**Prototype first**
1. `woka/`: the time as `m:ss:ff`; music (Audio button, ghost row, drop, `Detach sound`) behind
   a flag on only in `#editor/audio`, its scene labelled `Video + music · 1.1.0`; the skim badge
   with its still; Duplicate and Join in the clip menu, `Undo all cuts` in the empty-place
   menu; the trimmed source dimmed during an outer-end drag; the text lane's tip `Markup`.
   Check headless (`shot.mjs`).

**Settings and the dock**
2. `WokaSettings.editorTimelineHeight: Int = 220`; `SettingsRepository.setEditorTimelineHeight(dp)`
   and its `SettingsRepositoryImpl` override, written as `setEditorToolEnabled` is.
3. `EditorScreen`: the left column becomes a `Column` — a `Box(weight 1f)` with the canvas
   layer, the frame band and `EditorBottomPanel`, then the engine's `Timeline`. The timeline
   scope moves from `EditorBottomPanel` into `rememberTimelineScope(...)` (new,
   `EditorTimelineDock.kt`), and `EditorBottomPanel` keeps the notices only.
   `EditorTokens.Band` keeps `bottomInset`; its other members are deleted once nothing reads them.
4. `EditorTimelineScope`: drop `editing` and `markedSpan`. Add `openTool(id: String)` (the
   screen's `tabs.choose` for the tab holding that tool) and `selectShape(id: String)` (writes
   `ToolSelection.id`), both default no-ops. `EditorTimelineScopeImpl` implements them.
   `CanvasTool.spanOf`, `MarkupTool.spanOf` and `windowOf` are deleted when nothing else reads them.

**The panel**
5. `VideoEditorTimeline.kt` (replaces `VideoEditorBand.kt`): the panel from the spec, keeping
   `WalkClips` and `MixClipSound`. The resizer previews the height locally and writes the
   setting on release. Tag `editor-timeline`.
6. `TimelineTransport.kt` (replaces `VideoEditorBandParts.kt`): `Transport` (32 dp round),
   `Readout` (`timecode` kept, the prototype's type), Split (`splitAtPlayHead`), − / + calling
   `wheelAtPlayHead(∓1f)`, and the factor between them while zoomed
   (click: `fit()`, replaces `ZoomPill`).
7. `TimelineRuler.kt`: labels at the smallest 1/2/5 × 10ⁿ s step leaving ≥ 80 dp between them,
   four ticks per step, on `StripGeometry` so zoom and scroll move them. A press and drag seeks
   (the strip's `scrub`, moved here).
8. `ClipStrip` → the video row: the lane grid, `ClipTile` restyled to the spec with `ClipWave`
   in its bottom strip (mute and the film's mix through `gainAt`, as `SoundRow` passes).
   `stripGestures` loses `editing`: a press on a handle (both outer ends, both edges of the
   selected clip) trims; a click on the body picks it (`edits.pick(id, key)`, no seek) and,
   with Transform or Markup open, `openTool("trim")`; a click on its wave picks it and
   `openTool("sound")`; a drag past the slop reorders as now. `trimmedEnds` lays out the lead and
   tail only while `drag` is an outer `StripDrag.Edge` (`EdgeCarry` keeps the edge under the
   pointer). `ZoomOverview` goes to the lane's foot, in `line` and `faint`.

**Playhead and skim**
9. `PlayHead` restyled to the spec; `PlayHeadActions.kt` keeps one action, the scissors,
   enabled when the player is paused and `clips.split(at, "") != clips`. ❄ is removed.
10. `TimelineSkim.kt` (new): hover over the tracks, not while playing or dragging, sets
    `skimMs`; the line draws over the lanes; a `Popup` centred over the canvas column, 12 dp
    above the timeline's top edge, shows `FilmstripCells.stillAt(source ms of clips.atTimeline(skimMs))`
    and `timecode(skimMs)`. Cleared on exit.

**Markup lane**
11. `MarkupLane.kt` (new): the document's last `markup` op, the same op the canvas edits.
    One bar per shape over `MarkupWindowsParam[id] ?: TimeWindow(0, total)`, packed into rows
    by `laneRows(windows)` (pure, first row it fits). A press on the body or an edge drags
    `atMs`/`holdMs` through `edits.preview(ToolOp("markup", params.with(MarkupWindowsParam, …)))`,
    then `commit` (one step; none if nothing moved). The drag badge shows the length (edge) or
    the start (body). A click calls `selectShape(id)` and `openTool("markup")`; the clip scope
    is unchanged. Row icon: Markup's own.
12. `TimelineSnap.kt` (new, pure): `snap(ms, points, tolerancePx, pxPerMs): Long?` over 0, the
    playhead, every join and the end; tolerance 8 dp; Alt held, none. The guide draws at the hit.

**Menu and keys**
13. `MenuColors` (new, `core/ui/components`) with `MenuColors.app` (today's values);
    `MenuSurface` and `DropdownItem` take it (defaulted); `RoomControls.menu` gives the room's.
    `TimelineMenu.kt` (new), opened by a right-click via `ContextMenuPopup`, picks first:
    - **clip:** Split at playhead `S` · Copy look · Paste look (`PasteKind.Look`) · Paste sound
      settings (`PasteKind.Sound`) · Move earlier · Move later (`move(id, index ∓ 1)`) ·
      Duplicate · Join · Hold this frame for 2 s (`freezeAtPlayHead`, off on a freeze) · Use frame
      as cover (`CoverTool.atPlayHead(Use, …)` previewed and committed) · Delete clip `Del`;
    - **Markup bar:** Remove `Del` (the shape out of `Shapes` and its window);
    - **empty place:** Split at playhead `S` · Hold this frame for 2 s · Use frame as cover ·
      Undo all cuts (`resetCut`, off with no cut).
    `resetOnRightClick` leaves the strip. `TrimTool.footnote`: "Right-click the timeline for
    more" in place of the reset hint.
14. `EditorEngine.timelineKey(key: Key): Boolean = false`; `VideoEditorEngine` answers through
    `VideoPlayerHolder.strip` (the timeline's `StripState`, set while composed): `S` splits,
    Home / End put the playhead at 0 / the end, Delete / Backspace delete the selected clip.
    `EditorKeys`: Delete goes to the canvas first (`canvasKeys?.onDelete?.invoke() == true`), else
    to `timelineKey`; S, Home and End go to `timelineKey` after `yields`.

**Removed**
15. `ClipBar.kt`, `ClipSpeedChip.kt`, `ClipSpeedSlider.kt`, `ClipTabs.kt` (with
    `DeselectPulse`), `SoundRow.kt`, `SoundRowToggle.kt`, `MarkedSpan.kt`, `SelectedClipBar`,
    `pickTab`; `ClipBadges` keeps the edited dot as RD-204's `EditedDot`, the label moves into
    the clip's name and the freeze's orange wash goes.

**Tests**
16. `ui/VideoTimelineUiTest` (replaces `VideoBandUiTest`): the duration as `m:ss:ff`; play
    toggles the player; the resizer's drag writes `editorTimelineHeight`; + and − zoom, the
    factor fits. `ClipStripUiTest`: scissors split once; a click selects without seeking; a
    right-click opens the clip menu and `Delete clip` closes the hole; `Undo all cuts` takes the
    film whole. `ClipStripFreezeUiTest`: the menu's Hold inserts a hold, selection kept.
    `TrimEdgeUiTest`: handles with Styles open. `ClipLookUiTest`: copy and paste by the menu.
    `SoundRowUiTest` → `ClipWaveUiTest`: every clip has its wave, a click on it opens Sound.
    New `ui/MarkupLaneUiTest`: an untimed shape spans the film; a body drag moves its window
    as one step; an edge snaps to the playhead and not with Alt; a click opens Markup with the
    shape selected. New `ui/TimelineSkimUiTest`: hover shows the line and the badge with the
    time, leaving hides both, none while playing. New `ui/TimelineTabSwitchUiTest`: a clip
    click from Transform opens Clip. Unit `unit/MarkupLaneRowsTest`, `unit/TimelineSnapTest`.

## Files

New: `EditorTimelineDock.kt`, `VideoEditorTimeline.kt`, `TimelineTransport.kt`,
`TimelineRuler.kt`, `TimelineSkim.kt`, `MarkupLane.kt`, `TimelineSnap.kt`, `TimelineMenu.kt`,
`core/ui/components/MenuColors.kt`, the new tests. Edited: `woka/` (step 1), `WokaSettings`,
`SettingsRepository`, `SettingsRepositoryImpl`, `EditorScreen`, `EditorBottomPanel`,
`EditorTokens`, `EditorTimelineScope`, `EditorTimelineScopeImpl`, `CanvasTool`, `MarkupTool`,
`MarkupTiming`, `EditorEngine`, `VideoEditorEngine`, `VideoPlayerHolder`, `EditorKeys`,
`ClipStrip`, `ClipTile`, `ClipWave`, `ClipBadges`, `ClipStripParts`, `PlayHeadActions`,
`StripGestures`, `StripGeometry`, `StripZoomIndicator`, `TrimHandles`, `TrimTool`,
`ContextMenuPopup`, `DropdownItem`, `RoomControls`, the tests (step 16). Deleted: step 15,
`VideoEditorBand.kt`, `VideoEditorBandParts.kt`, `VideoBandUiTest`, `SoundRowUiTest`.

The author waived the size limit for a block (2026-11-25). Each touched `.kt` still stays ≤ 150
lines; where a file passes that, the split is its own step in the build.

## Done when

- A film in the editor shows the docked timeline under the picture, beside the inspector, in
  both themes. Its height survives a restart.
- Cut, trim, reorder, hold, duplicate, join, delete, copy/paste look and cover all work from the
  timeline whichever tab is open. Speed is the one exception, until RD-302.
- Markup shapes appear as bars, and moving one moves the shape's time on the canvas and in the
  viewer.
- `Grep "VideoEditorBand|ClipBar|ClipTabs|SoundRowToggle|MarkedSpan|markedSpan|spanOf"` over
  `shared/src`: no match.
- Every touched `.kt` ≤ 150 lines; anti-FQN clean; `./gradlew.bat :shared:build` green.

## Result — 2026-11-26

Built; `./gradlew.bat :shared:build` green (1804 tests); checked by hand by the author. The
prototype was changed first and checked headless (`shot.mjs`). Where the build differs from the
steps:

- **Ctrl + wheel zooms** (the prototype's tooltips). A plain wheel scrolls the lanes vertically,
  which a tall Markup lane needs; before, a plain wheel zoomed the strip (ME-218).
- **The scope is read at event time.** `EditorTimelineScopeImpl` is rebuilt every composition, so
  the lanes' pointer handlers are keyed on nothing or on `StripState`, and read the scope through
  `rememberUpdatedState`. Keyed on the scope, a Markup drag would restart at its first preview.
- **Files beyond the list:** `TimelineReadout.kt`, `TimelineResizer.kt`, `TimelineTracks.kt`,
  `TimelineSkimBadge.kt`, `TimelineMenuItems.kt`, `TimelineKeys.kt`, `TimelineTokens.kt`,
  `ClipStills.kt` (out of `ClipTile`), `MarkupBars.kt`, `MarkupBarTile.kt`, `MarkupLaneParts.kt`.
  The tab rule is the pure `tabFor` in `EditorTimelineDock.kt`. `EditorTimelineScope` also gives
  `selectedShape`, and `openTool` takes the domains it may open from.
- **The menu card** keeps the app's geometry (radius 10, padding 6) in the room's colours
  (`MenuColors`); the prototype has 12 and 5.
- **The selected clip's ring** is drawn inside the tile, so the lane does not clip it. Markup
  bars have no hover ring; a selected bar shows its handles.
- **Tests:** `support/TimelineHarness.kt` (`onTimeline`) is shared by the UI tests.
  `VideoTimelineUiTest`, `ClipStripUiTest`, `ClipStripFreezeUiTest`, `TrimEdgeUiTest`,
  `ClipLookUiTest`, `ClipWaveUiTest`, `MarkupLaneUiTest` and `TimelineSkimUiTest` cover the
  timeline in the UI. `MarkupLaneRowsTest`, `TimelineSnapTest`, `TimelineTabSwitchTest` and
  `StripGeometryTest` cover it without a window.
- **Not covered by tests:** the zoom factor appearing in the UI, because the 3 s corpus film is
  shorter than the tightest view (the arithmetic is `StripZoomTest`'s); Alt during a Markup drag,
  covered by a unit test only.
