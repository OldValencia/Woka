# ME-263 — Markup that appears, holds and fades

| | |
|---|---|
| **Order** | after ME-215 |
| **Depends on** | ME-215 (clip-scoped layers), ME-284 (the viewer folds), ME-291 (the render) |
| **Feature** | [Picture](../picture.md) §3.3 · mockup `video-editor.html` state 7 |

**Goal.** A shape or caption on a film shows from a chosen moment, for a chosen time, and fades.

**Why now.** The author, 2026-11-02: when, and for how long, markup is on screen.

## Shape
- A sibling `ObjectParam` on `MarkupTool`, keyed by shape id: `TimeWindow(atMs, holdMs, fadeMs)`
  in **timeline** time. No `MarkupShape` variant and no existing wire format changes; a shape with
  no entry is on for the whole film — which is what a watermark is (§950).
- One pure function in `commonMain`, `markupAlpha(window, timelineMs): Float`, called by the
  editor canvas, the viewer and the render so the three cannot disagree (`render.md` §3.3).
- Inspector, through `rows()`, for a film and only with a shape selected: **Appears at** (with a
  "from the play head" action), **Holds**, **Fades**. Right-click resets each.
- Strip: the selected shape's span as a thin accent bar over the clips (mockup state 7).

## Steps
1. `TimeWindow`, `markupAlpha`, unit tests (edges, zero fade, past the end).
2. `MarkupTool`: the windows param, `rows()` for a film, `restyled` / `displayed` for the selection.
3. `RasterStage.Paint` takes an alpha supplier; the video canvas passes `ClipWalk.timelineMs`;
   stills pass none (alpha 1).
4. Viewer: `RecipePainter` gets `EditedClock.toTimeline(position)` per frame.
5. Render: `RecipePicture.draw` gets the output timestamp.
6. Strip span; an integration render of a timed caption — present at t, absent before it.

## Done when
- A caption set to appear at 0:05 for 3 s with a 0.5 s fade does exactly that in the editor, the
  viewer and a rendered file.
- `./gradlew.bat :shared:build` green.
