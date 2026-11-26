# Viewer and Editor Redesign — backlog

Blocks are promoted into ticket files when they are next, re-cut against the tree as it then is
(`tickets.md` §1, §4). No file lists here. The feature doc's questions were answered on
2026-11-25; RD-101…RD-301 are tickets, RD-302 is the next block. Each block is promoted and built
whole — the author's call, 2026-11-25 (`tickets.md` §4a waived).

## Video editor

### RD-302 — Clip and Sound tabs, the scope line
The Clip tab (speed, mute, split, delete, more) and Sound (mute, volume, fades, even loudness);
the scope line moving between Whole video, a clip and a Markup shape. **State after RD-301:**
speed has no control (the clip bar's chip went with the bar), so the Clip tab brings it back.
Cover returned in RD-301's clip menu. Music (`add music`, its sound crumb) is ME-244, 1.1.0.

## Dialogs

### RD-401 — Ways out
`Create a copy` (size, SDR, Audio only…), `Keep your changes?`, `Revert to original?`, the help
sheet (`?` in the header); Undo and progress toasts. A separate button replaces `Done ▾` (answer 3);
Create a copy takes Resize's size and HDR's Standard colours for HDR media, the latter hidden by a
Settings switch. **State after RD-201:** `Done ▾`'s caret is gone, so a film's other exports
(save as mp4, render…) are unreachable until this button; Resize and HDR have no tab, and
Settings → Editor still lists them — decide their switches here. Their UI tests
(`EditorRailHdrUiTest`, `ResizeInspectorUiTest`) were deleted with the tabs; this brings the
replacements. **State after RD-301:** the prototype's timeline menus offer `Save frame as
photo…`; the app's do not — a new export, it comes here with the other ways out.

## Viewer

### RD-501 — One transport and the GIF
Brings the Theme colours the viewer first draws (from RD-101): `surfaceRaised`, `accentEditorSoft`,
`canvas`, `glass`, wherever this or RD-502 draws them first.
The shared transport with per-kind slots, the round play button, the fixed time label; GIF
controls and frame step; `,` / `.` for video while paused.
**A GIF in the editor too** (the author, 2026-11-26; prototype `#editor/gif`): the same transport
under the picture on the room's panel (`.gif-media`), and the frame strip in the room's colours —
**no scope line**, the edit stays whole (ME-138); a click on a frame or a step pauses there and
changes nothing else. Today the app's editor has neither: the GIF plays, a tap or Space pauses.

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
