# Viewer and Editor Redesign — backlog

Blocks are promoted into ticket files when they are next, re-cut against the tree as it then is
(`tickets.md` §1, §4). No file lists here. The feature doc's questions were answered on
2026-11-25; RD-101 is next.

## Tokens

### RD-101 — Theme colours and the editor's room palette
`Theme.kt` dark: surfaceHover white 5.5 %, textSecondary #9A9FAA, border white 7 %; a light text tone
#AD6500 for the editor orange; the prototype's extra colours and the room's `--e-*` palette next to
`EditorTokens`, both themes (answer 1). Every later block draws with these. Visible: secondary text,
hovers and hairlines across the app.

## Editor

### RD-201 — The editor's header and inspector frame
One header (Cancel, undo/redo | pill | Original, help, F6, Create a copy…, Done); the rail becomes
inspector tabs with the scope line and `Revert to original…`; the picture keeps its place when the
editor opens and the room dims. Tabs per answer 2: Styles, Adjust, Transform (crop, straighten,
rotate, flip), Markup; video adds Clip and Sound; Resize, HDR and Cover leave the rail.

### RD-202 — Scrub rows, wells and segments
The inspector's controls in the prototype's form: scrub rows with value field, reset and detent,
grouped in wells; segmented controls with the sliding indicator. Applied to Adjust first, so the
change is visible on one tool end to end.

### RD-203 — Styles, Transform and Markup panels
Style tiles with hover preview and the intensity rows; crop shape tiles, orientation, the
straighten dial, rotate and flip; the markup panel with text on the canvas.

### RD-204 — Frame sets in the editor
The left frame strip in the editor (as in the viewer) and the All frames / Frame N scope.

## Video editor

### RD-301 — Timeline
Transport row, ruler, lanes with thumbnails and waveforms, playhead with scissors, skim badge,
snapping, drop-to-add audio, clip context menu, resizable height — the prototype's
`#editor/video` and `#editor/audio`.

### RD-302 — Clip and Sound tabs, the scope line
The Clip tab (speed, mute, split, delete, more) and Sound (mute, volume, fades, even loudness, add
music); the scope line moving between Whole video, a clip, a sound and the text.

## Dialogs

### RD-401 — Ways out
`Create a copy` (size, SDR, Audio only…), `Keep your changes?`, `Revert to original?`, the help
sheet; Undo and progress toasts. A separate button replaces `Done ▾` (answer 3); Create a copy takes
Resize's size and HDR's Standard colours for HDR media, the latter hidden by a Settings switch.

## Viewer

### RD-501 — One transport and the GIF
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
