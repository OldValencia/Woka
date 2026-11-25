# App Prototype — backlog

Blocks are promoted into ticket files when they are next (`tickets.md`); their edges are not the
ticket's. No file lists here.

## Viewer

### PT-104 — Size remembered per kind
The size popover names the kind its mode is remembered for. Drawn when the 1.0.9 per-kind size
plan has fixed the kinds and the boundary; until then this block waits on that plan.

### PT-105 — Dialogs section
File info as the app draws it (stream cards, track tags), rename, create a copy, keep changes,
revert — each a scene under Dialogs, opened over the viewer or editor it belongs to.

## Library

### PT-201 — Sidebar, toolbar, grid
Ported from the code: sidebar tree with counts, Pinned, Link folder…; toolbar with folder name,
View (Show, Size S·M·L, Sort), Pin, search box, Info, Settings; grid with folder mosaic and stack
cards and media cards with watched progress; empty folder. Scenes: a photo folder, a series folder,
an empty folder. The viewer's sidebar becomes this sidebar.

### PT-202 — Search, Info panel, Settings, Quick Look, selection
Search results inside the window, the Info panel (Ctrl+I), the Settings overlay with its panels,
Quick Look on Space, the selection bar with several items selected.

## Status

### PT-301 — Visual truth switches to `woka.html`
After PT-201: `CLAUDE.md`, `mandates.md`, `feature-docs.md` §6, `plans.md` §1 and
`code-style/composable.md` point at `woka.html`; §6 and `plans.md` §1 say a new surface is a scene in
`woka.html` rather than a mockup file of its own. Whether `FINAL_design_mockup.html` is deleted or
kept as history is the author's call at that point. Shipped docs in `implemented/` keep their
references — they describe what was true when they were written.
