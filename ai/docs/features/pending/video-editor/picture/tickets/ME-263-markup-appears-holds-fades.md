# ME-263 — Markup that appears, holds and fades

| | |
|---|---|
| **Order** | after ME-215 |
| **Depends on** | ME-215 (clip-scoped layers), ME-284 (the viewer folds), ME-291 (the render) |
| **Feature** | [Picture](../picture.md) §3.3 · mockup `video-editor.html` state 7 |

**Goal.** A shape or caption on a film shows from a chosen moment, for a chosen time, and fades.

**Why now.** The author, 2026-11-02: when, and for how long, markup is on screen.

## Shape
- A sibling `ObjectParam` on `MarkupTool`, keyed by shape id: `TimeWindow(atMs, holdMs,
  fadeInMs, fadeOutMs)` in **timeline** time — the four numbers `audio.md` §3.3a shares with a sound
  entry, built here for both (the author, 2026-11-10: four in the type **and** four rows in the
  panel, a fade in as well as a fade out). `holdMs` is the whole time on screen; both fades fall
  inside it. No `MarkupShape` variant and no existing wire format changes; a shape with
  no entry is on for the whole film — which is what a watermark is (§950).
- One pure function in `commonMain`, `TimeWindow.envelopeAt(timelineMs): Float`, called by the
  editor canvas, the viewer and the render so the three cannot disagree (`render.md` §3.3).
- Inspector, through `rows()`, for a film and only with a shape selected: **Appears at** (with a
  "from the play head" action), **Holds for**, **Fade in**, **Fade out**. Right-click resets each.
- Strip: the selected shape's span as a thin accent bar over the clips (mockup state 7).

## Steps
1. `TimeWindow`, `envelopeAt`, unit tests (edges, zero fades, fades longer than the hold, past the end).
2. `MarkupTool`: the windows param, `rows()` for a film, `restyled` / `displayed` for the selection.
3. `RasterStage.Paint` takes an alpha supplier; the video canvas passes `ClipWalk.timelineMs`;
   stills pass none (alpha 1).
4. Viewer: `RecipePainter` gets `EditedClock.toTimeline(position)` per frame.
5. Render: `RecipePicture.draw` gets the output timestamp.
6. Strip span; an integration render of a timed caption — present at t, absent before it.

## Built — 2026-11-10
- `core/TimeWindow.kt` — the four numbers and `envelopeAt`. `markup/MarkupTiming.kt` — the
  `windows` param, the four rows and **Appear at play head**, the write into the selected shape's
  window and the read back. `markup/MarkupStage.kt` — `stage()` moved out of `MarkupTool` (size
  mandate), each shape drawn at its envelope, a part-faded one through a layer.
- `CanvasTool` gained `rows` / `restyled` taking the selection and the timeline length, and
  `spanOf`; `EditorEngine.timelineMs`, `PictureFacts.timelineMs`, `EditorTimelineScope.markedSpan`.
- `RasterStage.Paint` takes the timeline millisecond, carried on `RasterRecipe.atMs`: the video
  canvas sets it from the walk, the viewer from `EditedClock.toTimeline`, the render from the
  output timestamp — and redraws a freeze's slots when the recipe has markup, so a fade runs
  over a freeze as on the canvas. A still leaves it null and draws every shape whole.
- Strip: `MarkedSpan`, a 3 dp accent bar over the selected shape's span.
- Tests: `TimeWindowTest`, `MarkupTimingTest`, `MarkupTimingRenderTest` (present at t, absent
  before and after, part way through the fade, read frame by frame).

**Not timed, by choice:** the grid poster and the hover excerpts draw every shape whole — neither
knows where on the timeline its frame stands. A deleted shape's window stays in the map, keyed
by an id nothing reuses.

## Done when
- A caption set to appear at 0:05 for 3 s with a 0.5 s fade out does exactly that in the editor, the
  viewer and a rendered file.
- `./gradlew.bat :shared:build` green.
