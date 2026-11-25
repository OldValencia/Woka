# Viewer and Editor Redesign — backlog

Blocks are promoted into ticket files when they are next, re-cut against the tree as it then is
(`tickets.md` §1, §4). No file lists here. The feature doc's questions were answered on
2026-11-25; RD-101…RD-202 are tickets, RD-203 is next. Each block is promoted and built
whole — the author's call, 2026-11-25 (`tickets.md` §4a waived).

## Editor

### RD-203 — Styles, Transform and Markup panels
Style tiles with hover preview and the intensity rows; crop shape tiles, orientation, the
straighten dial, rotate and flip; the markup panel with text on the canvas. Markup's compact
bars still use ME-155's stepper with a popover (`FloatControl`), left by RD-202 for this.
**The author, 2026-11-25:** Styles' Undertone and Look must be laid out differently — in the
294 dp inspector their preview strips show only two previews at a time. Re-lay them out here
(the prototype draws a three-column grid of style tiles, `#editor/photo` → Styles).

### RD-204 — Frame sets in the editor
The left frame strip in the editor (as in the viewer) and the All frames / Frame N scope — the
scope line above the inspector's tabs (left out of RD-201).

## Video editor

### RD-301 — Timeline
Transport row, ruler, lanes with thumbnails and waveforms, playhead with scissors, skim badge,
snapping, drop-to-add audio, clip context menu, resizable height — the prototype's
`#editor/video` and `#editor/audio`. Brings Theme's `sound` and `soundBg` (moved here from RD-101).

### RD-302 — Clip and Sound tabs, the scope line
The Clip tab (speed, mute, split, delete, more) and Sound (mute, volume, fades, even loudness, add
music); the scope line moving between Whole video, a clip, a sound and the text. Cover, which left
the tabs in RD-201, returns in the clip's menu (RD-301 or here).

## Dialogs

### RD-401 — Ways out
`Create a copy` (size, SDR, Audio only…), `Keep your changes?`, `Revert to original?`, the help
sheet (`?` in the header); Undo and progress toasts. A separate button replaces `Done ▾` (answer 3);
Create a copy takes Resize's size and HDR's Standard colours for HDR media, the latter hidden by a
Settings switch. **State after RD-201:** `Done ▾`'s caret is gone, so a film's other exports
(save as mp4, render…) are unreachable until this button; Resize and HDR have no tab, and
Settings → Editor still lists them — decide their switches here. Their UI tests
(`EditorRailHdrUiTest`, `ResizeInspectorUiTest`) were deleted with the tabs; this brings the
replacements.

## Viewer

### RD-501 — One transport and the GIF
Brings the Theme colours the viewer first draws (from RD-101): `surfaceRaised`, `accentEditorSoft`,
`canvas`, `glass`, wherever this or RD-502 draws them first.
The shared transport with per-kind slots, the round play button, the fixed time label; GIF
controls and frame step; `,` / `.` for video while paused.

### RD-502 — Carousel badges and Original
Badges on the carousel; `Original` in the pill for an edited file with click, hold and `\`.

## Fullscreen and PiP

### RD-601 — Fullscreen pill and the compact PiP
Size | PiP in fullscreen; in PiP no size control, a compact transport, resize at the film's
proportions and free with Alt. Covers the PiP items of 1.0.9 in `next_steps.md`.

## Size

### RD-701 — Size remembered per kind
Waits on its own plan, discussed when it is reached (answer 5): the kinds, the short-video/film boundary, what is remembered;
then the size menu names the kind it remembers for, in the prototype (PT-104) and in the app.

## Release tail

### RD-801 — Minimum window size
The window cannot be made smaller than 1024 × 700 — the width where the editor's header stops
overlapping (measured in the prototype, 2026-11-25). Today it shrinks to 800 × 600.

### RD-802 — Keep the system awake in fullscreen
While a film plays in fullscreen (not exclusive fullscreen, so the OS does not know), Windows must not
sleep or blank the screen; released on pause, exit or close. Other platforms where the API allows.
From `next_steps.md` 1.0.9; a small task in the same release.
