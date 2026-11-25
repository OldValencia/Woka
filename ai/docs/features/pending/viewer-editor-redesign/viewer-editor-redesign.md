# Viewer and Editor Redesign — 1.0.9

## Goal and status

Build in the app what `ai/mockups/design/woka.html` draws for the viewer and both editors. The
1.0.9 task in `next_steps.md` is the editors' redesign — the current editor did not justify itself
in UX terms — with the viewer brought in line so the two stop diverging. The prototype is the spec
(`feature-docs.md` §6); a disagreement found while building changes the prototype first.

**Status, 2026-11-25:** planned, nothing built. The open questions below are the author's to answer
were answered on 2026-11-25 (below); the first ticket, RD-101, is promoted from the backlog against
the tree as it is (`tickets.md` §1). The prototype itself is finished —
[`app-prototype`](../app-prototype/app-prototype.md), PT-001…PT-301.

| Part | Status |
|---|---|
| Open questions answered | done 2026-11-25 |
| [RD-101 — Theme colours](tickets/RD-101-theme-colours.md) | **done** 2026-11-25 |
| [RD-201 — Editor frame](tickets/RD-201-editor-frame.md) | **done** 2026-11-25 |
| RD-202…RD-802 | [backlog](tickets/backlog.md); next: promote RD-202 |

## The spec, scene by scene

| Surface | Scenes in `woka.html` |
|---|---|
| Photo editor | `#editor/photo`, `#editor/crop`, `#editor/frames`, `#editor/gif` |
| Video editor | `#editor/video`, `#editor/audio` |
| Editor dialogs | `#dialogs/copy`, `#dialogs/keep`, `#dialogs/revert` |
| Viewer | `#viewer/photo`, `#viewer/palm`, `#viewer/frames`, `#viewer/gif`, `#viewer/video`, `#viewer/track` |
| Fullscreen, PiP | `#viewer/fullscreen`, `#viewer/pip` |

The library and the viewer's own dialogs are drawn as the app already has them and are not part of
this redesign.

## What changes, by surface

Named by the app's seams as read on 2026-11-25; a ticket re-reads them before it names a file.

**Editor shell** (`EditorScreen`, `EditorRail`, `EditorTopBarControls`, `DoneButton`/`DoneMenu`):
one header — Cancel and undo/redo on the left; the viewer's pill (size, HDR, Info) in the middle;
Original, help, hide-panel (F6), `Create a copy…` and `Done` (orange, dark text) on the right. The
rail becomes tabs at the top of a right-hand inspector, with a scope line above them for video
("Whole video › Clip 2") and for frame sets ("All frames | Frame N"), and `Revert to original…` at
its foot. The picture keeps its place when the editor opens; the panels arrive around it. The room
dims to the editor's palette (`--e-*` in the prototype).

**Inspector controls** (`EditorInspector*`, the `Param*` rows): the scrub row — icon, label, value
field, reset, a detent at the default that the slider sticks to — grouped in wells; segmented
controls with a sliding indicator; style tiles that preview on hover; Auto-enhance; crop shape tiles,
orientation, the straighten dial, rotate and flip; the markup panel (types, text, font, colour,
size, opacity, background, fade for video); the sound panel.

**Video timeline** (the editor's band and bottom panel): transport row (play, time, Split, Audio,
zoom ±), ruler, clip lane with thumbnails, waveforms and trim handles, audio and text lanes,
playhead with scissors, skim preview and badge, snapping with a guide, drop-to-add audio, the clip
context menu, a resizable height.

**Editor dialogs**: `Create a copy` (size presets, Standard colours (SDR), Audio only…), `Keep your
changes?` (Keep editing / Discard / Keep changes), `Revert to original?`, the help sheet; toasts
with Undo and a progress toast with Cancel.

**Viewer** (`MediaViewerToolbar`, `MediaViewerVideoControls`, the frame strip, `MediaViewerCarousel`):
one transport for video, GIF and audio with per-kind right-hand slots, a 32 px round play button and
a fixed-width time label; a GIF gets play/pause, time ↔ frame number, frame step, loop and speed;
`,` and `.` step a frame (GIF always, video paused). Carousel badges (duration, GIF, frame count,
music, edited dot). `Original` in the pill for an edited file — click, hold, `\`.

**Fullscreen and PiP** (`MediaViewerFullscreenControls`, `PipWindowMode`): the pill is size | PiP
without the inert carousel button; PiP drops the size control, keeps a compact transport (no ±5 s,
subtitles or settings), resizes from its free corner at the film's proportions and freely with Alt
— the 1.0.9 PiP items in `next_steps.md`.

**Tokens** (`Theme.kt`, `EditorTokens`): the six colours where the prototype differs from
`Theme.kt` (PT-001's gap list) and the editor's room palette, settled before any surface is built.

## Answered questions (2026-11-25)

1. **Colours** — the author left it to judgement. `Theme.kt` takes the prototype's dark
   `surfaceHover` (white 5.5 %), `textSecondary` (#9A9FAA) and `border` (white 7 %): Theme's own
   dark `textSecondary` (#515764) is darker than its `textTertiary` (#838893), an inverted
   hierarchy, and its 2 % hover and 3.9 % hairline barely show. The prototype takes Theme's `danger`
   (#FF5C5C / #E5484D, near Apple's system red; the prototype's pastel came from an abandoned first
   palette). `accentEditor` stays #FF9F0A as a fill in both themes; light gets a text tone, #AD6500,
   because #FF9F0A text on white is 2.1 : 1. The prototype's extra colours (`surfaceRaised`,
   `accentEditorSoft`, `canvas`, `glass`, `sound`, the room's `--e-*`) join Theme in the ticket that
   first draws them — the room `--e-*` in RD-201 (`core/ui/theme/EditorRoom.kt`), `sound` in RD-301,
   the rest with the viewer (re-cut when RD-101 was written, `tickets.md` §4).
2. **Tabs.** The tab is **Transform**, not Crop — crop is one action, Transform the family: crop,
   straighten, rotate, flip. Resize goes to `Create a copy`'s size; **HDR goes to saving** —
   `Create a copy` offers Standard colours (SDR) for an HDR file, photo or video — and Settings →
   Editor keeps a switch that turns the HDR option off for anyone who does not need it. Cover goes to
   the clip menu. Prototype updated: the tab reads Transform; the SDR switch shows for HDR media only.
3. **A separate `Create a copy…` button**, no `Done ▾` menu — a drop-down menu is bad UX.
4. **Order in 1.0.9:** the editors, then the viewer (it ships in 1.0.9), then the zoom/size block.
   The keep-awake fix for fullscreen ships in the same release.
5. **Per-kind size:** discussed when it is reached.
6. **Primary button in dialogs:** by context — the editor's orange with dark text in the editor
   (as `Done`), the app's accent over the viewer and the library. The prototype already draws it so;
   the `#FFB34C` noted in PT-001 is never drawn (checked 2026-11-25) and its tokens are removed.

**Also settled:** narrow windows are capped. The editor's header stops overlapping at 1024 px (the
pill with HDR met `Original` at 960 px, measured 2026-11-25), so the window's minimum becomes
1024 × 700 (RD-801).

## Decision log

| Date | Question | Choice | Why |
|---|---|---|---|
| 2026-11-25 | What the redesign builds from | `woka.html`, scene by scene | The author: the prototype is the target design; it replaced `FINAL_design_mockup.html` (PT-301) |
| 2026-11-25 | One viewer per media kind | No — one shell, per-kind slots | `app-prototype.md`; the code already models it (`ViewerMedia`, `FrameStripModel`) |
| 2026-11-25 | Frame strip position | Left, in viewer and editor | `app-prototype.md`; the picture must not move on entering the editor |
| 2026-11-25 | Play button | One 32 px round knob in viewer and editor | PT-002, accepted by the author |
| 2026-11-25 | How the tickets are cut | One surface per ticket, end to end, after the open questions | `tickets.md` §4; the questions decide what several surfaces contain |
| 2026-11-25 | Colours that differ between the prototype and `Theme.kt` | Theme takes the prototype's dark hover, secondary text and border; the prototype takes Theme's danger; editor orange gets a light text tone | Readability: Theme's dark secondary/tertiary are inverted, its hover and hairline barely show; the prototype's danger was a leftover |
| 2026-11-25 | Name of the crop tab | Transform | The author: crop is one action, Transform is the family |
| 2026-11-25 | Where HDR goes | Saving (`Create a copy`), with a Settings switch to hide it | The author |
| 2026-11-25 | `Done ▾` menu | Replaced by a separate `Create a copy…` button | The author: drop-down menus are bad UX |
| 2026-11-25 | Order in 1.0.9 | Editors, viewer, then zoom/size; keep-awake in the same release | The author |
| 2026-11-25 | Dialog primary colour | Editor orange in the editor, app accent elsewhere | Matches `Done` in the editor and the app's accent elsewhere; already so in the prototype |
| 2026-11-25 | Minimum window size | 1024 × 700 | The author allows a cap; 1024 px is where the editor header stops overlapping |
| 2026-11-25 | Where the prototype's extra colours land | In the ticket that first draws them (RD-201, RD-301), not RD-101 | `tickets.md` §4: in RD-101 nothing would read them |
| 2026-11-25 | Resize, HDR, Cover until their new homes | Leave the tabs in RD-201, unreachable until RD-401 / RD-301 | The author: 1.0.9 ships only whole, no stopgaps |
| 2026-11-25 | Crop and Transform | One Transform tab in RD-201: crop frame, Crop's rows, then rotate/flip | The author |
| 2026-11-25 | Ticket size | A backlog block is one ticket, built whole | The author: «не разбивай тикет на меньшие тикеты» |
| 2026-11-25 | Editor orange in the light prototype | `--accent-editor` back to #FF9F0A as the fill; new `--accent-editor-text` #AD6500 for text and focus | Answer 1; the prototype had #AD6500 on its fills too (edited dots) |
