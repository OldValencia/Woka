# ME-154 — The overlay lives in the picture's space, not the canvas's

| | |
|---|---|
| **Order** | 56 — a defect fix, ahead of ME-153 |
| **Depends on** | nothing |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Hand a `CanvasTool`'s overlay a matrix in the space it is actually composed in, so what it
draws lands on the picture rather than an offset away from it.

**Why now.** Reported by hand 2026-08-29 with two screenshots: draw a caption, drag the picture, click
the caption — and the selection frame sits well away from the plate. Parked on 2026-08-28 as "not
reproduced" against a still at Fit, which was the wrong call: the screenshot was right and the
reproduction was one gesture away.

## What was wrong

`EditorCanvasHost` composes the overlay **inside** the picture's own box — the one it has already
centred in the canvas and moved by the clamped pan. So the overlay's origin is the picture's top-left,
and every pointer position it receives is picture-relative.

`EditorCanvasGeometry.sourceToCanvas` translates by `offset`, which is exactly that centring plus that
pan, because `panForZoomAbout` and the gesture modifier on the **outer** box are handed canvas-relative
positions and need it there.

Both were true at once, so the overlay applied `offset` a second time:

- a press at picture-local `p` was stored as `(p − offset) / scale` instead of `p / scale`, so the mark
  the fold then drew landed `offset` away from the cursor;
- the overlay's own drawing — handles, the crop frame, ME-148's caption frame — was mapped back with
  the same surplus `offset` and so appeared *at* the cursor, `offset` away from the mark it belonged to.

At Fit on a picture whose aspect nearly matches the canvas, `offset` is close to zero and none of this
shows. **Dragging the picture is what makes it large**, which is why the first report said the panel
looked fine until the picture was moved.

## Steps

1. **`EditorCanvasGeometry.kt` (edit)** — add `sourceToPicture` (the scale and no translation) and its
   computed inverse `pictureToSource`, beside the canvas-relative pair, with the reason each exists on
   each. The file reaches the size mandate, so `panForZoomAbout` moves to `EditorCanvasZoom.kt` as an
   extension — the seam is real: where the picture sits on one side, the zoom's focus arithmetic on the
   other.
2. **`ActiveToolOverlay.kt` (edit)** — `rememberToolScope` composes the preview matrix with
   `geometry.sourceToPicture`. Nothing else changes: `EditorToolScope` still calls its two members
   `sourceToCanvas` and `canvasToSource`, because from a tool's side they are still "where I draw" and
   "what a press means".

## Files

`shell/EditorCanvasGeometry.kt` (edit), `shell/EditorCanvasZoom.kt` (edit),
`shell/ActiveToolOverlay.kt` (edit), under `shared/src/jvmMain/kotlin/com/woka/feature/editor/`.

## Verification

- `jvmTest/unit/EditorCanvasSpacesTest.kt` (new) — `sourceToPicture` maps the source origin to `(0, 0)`
  **whatever the pan**, and the source's far corner to the drawn size; `sourceToCanvas` still carries
  the centring and the pan, asserted against `offset` and with the fixture checked to have a non-zero
  one; picture and source round-trip through each other at a zoom and a pan together;
- `jvmTest/unit/EditorCanvasGeometryTest.kt` — unchanged but for the import `panForZoomAbout` moved to,
  which is what says the canvas-relative half was not disturbed;
- the crop, markup move, markup resize and caption UI suites are the regression net for the overlay's
  own arithmetic and stay green.

## Done when

- a caption's plate, its selection frame and its four handles are one rectangle after the picture has
  been dragged;
- a mark drawn on a panned picture lands under the cursor and stays there when the pan is undone;
- `./gradlew.bat :shared:build` green.

## What this means for files already saved

A mark drawn while the picture was panned was **stored at a shifted source coordinate**, so it is
misplaced in the file that was written, by `offset / scale` source pixels at the moment it was drawn.
Nothing migrates that — the shift is not recoverable from the document — and marks drawn at Fit on a
picture that filled its canvas are unaffected, which is most of them.
