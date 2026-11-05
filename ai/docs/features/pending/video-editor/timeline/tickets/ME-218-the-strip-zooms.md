# ME-218 — The strip zooms

| | |
|---|---|
| **Order** | after ME-217; promoted and built 2026-11-04 |
| **Depends on** | ME-210 (the strip), ME-217 (the freeze it was needed for) |
| **Feature** | [Timeline](../timeline.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** A short clip or a freeze on a long film can be seen, selected and dragged.
**Why now.** Reported by the author 2026-11-04: on an 81-minute film a 2 s freeze is under a
pixel wide; its edges sat on its neighbours' and it could not be caught at all.

## Shape — the author's choices, 2026-11-04

- **Zoom, not a minimum clip width** (offered and declined): the wheel over the strip zooms
  around the pointer; over the rest of the band, around the play head. Down to 5 s per strip.
- **The zoom is shown beside the strip**: a pill `⌕ 8×` (a click fits the whole film again), and
  an overview hairline over the strip with the window on screen lit.
- **The overview is a scrollbar**: a press centres the window there, a drag moves it. Its grip
  is 10 dp, taller than its 3 dp line. Sideways wheel (or Shift) pans too, and the window
  follows the play head while it plays.

## Built

`StripZoom` (zoom, scroll, fit, the overview's moves, the play-head anchor) held by the band;
`StripZoomInput` (the two wheels, the follow); `StripZoomIndicator` (pill, overview).
`StripGeometry` takes `zoom` and `scrollPx` and answers on-screen x, so gestures and the play
head need not know. **Only the visible part of a tile is composed** (`ClipSpan.visibleIn`) and
its stills are placed on the whole clip and drawn shifted — a zoomed clip can be ~10⁶ px, past
what a layout measures. The clip bar is centred over the visible part.

## Second round — 2026-11-04, by hand

- **The strip jumped, and a drag showed nothing until release.** The zoom was a multiple of
  the fit, so a hold growing under a drag rescaled every clip; and an edge drag held its old
  layout. Now `StripZoom.scale` is absolute (px per ms, null = fit), and an edge drag re-flows
  live at the press's scale, scrolled so the edge stays under the pointer (`EdgeCarry`), the
  borrowed scroll handed back on release.
- **Selecting moved the play head.** Trim draws a tab under each clip (`ClipTabs`): the tab
  selects, the body only seeks, a body drag reorders.
- **A freeze is clearer**: orange over its frame, outlined, its hold on an orange label.
- **Thirty seconds was an arbitrary limit**; a hold now goes to ten minutes.

## Verified

`unit/StripZoomTest` — mapping at 4×, the pointer and play-head anchors, the 5 s limit, a
freeze growing past 300 px, the overview's press and drag, the visible part.
`./gradlew.bat :shared:build` green. **Not yet by hand.** Known: zoomed past a filmstrip
cell (~16 s on an 81-minute film), neighbouring slots repeat one still.
