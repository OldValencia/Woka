# Viewer and Editor Redesign — backlog

Blocks are promoted into ticket files when they are next, re-cut against the tree as it then is
(`tickets.md` §1, §4). No file lists here. The feature doc's questions were answered on
2026-11-25; RD-101…RD-601 are tickets, RD-701 is the next block. Each block is promoted and built
whole — the author's call, 2026-11-25 (`tickets.md` §4a waived).

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
