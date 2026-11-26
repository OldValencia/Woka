# RD-205 — Straighten

| | |
|---|---|
| **Order** | sixth, after RD-204; RD-301 follows |
| **Depends on** | RD-203 (Transform's panel) |
| **Feature** | [Viewer and Editor Redesign](../viewer-editor-redesign.md) |

**Goal.** Level a tilted picture with the prototype's dial in Transform (`#editor/crop`): any angle
in ±45°, cropped to the largest picture of the same shape that the turned one still fills, kept by
the recipe and drawn the same in the editor, the viewer, the grid, a save and a render.

**Why now.** RD-203 drew Transform without it; the prototype's Transform has the dial between
Orientation and the rotate buttons. Arbitrary angles were out of the image editor's plan
(`implemented/image-editor/image-editor.md`, "Out"), so this is an engine feature first.

**Decided with the author (2026-11-26).**
- **Real pixels only.** The file shrinks to the area the turned picture fills: 6000 × 4000 at 10°
  becomes 4818 × 3212; nothing is scaled up. Transform's size line shows it live.
- **The angle belongs to the original picture.** It folds before every turn and flip, whatever
  order they were made in, so a flip after straightening mirrors a level picture and the dial keeps
  its value (after a flip, dragging it right turns the visible picture the other way). No control
  rewrites another's value.
- **±45°, as the prototype's dial** — the backlog's ±15° was wrong about the prototype.

## How it fits the engine

Every reader draws one matrix (`RasterRecipe.matrix`): `RasterSurface` and `RecipePainter` through
`drawThroughRecipe`, the save, the still reader, GIF bake and video render through
`renderAtNativeScale`. A turn is a matrix like any other, so **no reader changes** — the work is in
the fold.

A tool's `stage()` is never told the frame (§3.4), and a straighten cannot be stated without it: its
centre and the size it keeps are both the frame's. Hence a stage that is **a function of the frame
it lands on**, `RasterStage.Framed`, which the fold resolves against its running size.

The crop needs the same thing. Its rect is stored in pixels of the frame it was drawn in, and that
frame now changes size with the angle. A crop chosen at 5° and kept at 10° must sit where it sat,
so the crop records the frame it was chosen in and is **scaled to the frame it lands on** — by one
function that the overlay and the fold both call, so the rectangle drawn and the rectangle cut are
the same integers.

## Steps

1. **Prototype first** (`ai/mockups/design/woka/app.js`). `updatePicture`: the angle before the
   flips and the turn — `rotate(${state.rotation}deg) scaleX(..) scaleY(..) rotate(${angle}deg)
   scale(${cover})`, so a flip mirrors the levelled picture. `cropDimensions`: divide the base
   4032 × 3024 by the same `cover` factor before taking the crop's share, floored. Check with
   `node ai/mockups/tools/shot.mjs`.
2. **Splits before additions.** `tools/crop/CropOverlay.kt` (158): move `CropAspectEffect` to
   `tools/crop/CropAspectEffect.kt` (`internal`). `engine/image/RasterFold.kt` (148): move
   `applied`, `orderedStages`, `flatten` and `resolve` to `engine/image/RasterFoldOrder.kt`
   (`orderedStages` becomes `internal`). Pure moves.
3. **`RasterStage.Framed`** (`RasterStage.kt`): `data class Framed(val at: (IntSize) -> Geometry)`
   — "a geometry that needs the frame it lands on". `fold`: `is RasterStage.Framed ->` resolve
   `stage.at(outputSize)` and apply it exactly as the `Geometry` branch does (extract that branch
   into a local `apply(geometry)` so the two cannot differ).
4. **`foldsFirst`.** `ToolManifest.foldsFirst: Boolean = false` — "applied before the rest of its
   domain, whatever order the user made things in". `orderedStages` sorts
   `compareBy({ it.second.manifest.domain.ordinal }, { if (it.second.manifest.foldsFirst) 0 else 1 })`,
   still stable, so every existing document folds as it did.
5. **The straightened frame** — `engine/image/StraightenFrame.kt` (new, pure):
   `internal fun straightened(frame: IntSize, degrees: Float): RasterStage.Geometry`. With
   `c = cos|θ|`, `s = sin|θ|`, `k = c + max(W/H, H/W) · s`: output `floor(W/k) × floor(H/k)`, at
   least 1 × 1 (floored, so no corner pixel is sampled outside the picture); matrix "translate
   `(−W/2, −H/2)`, then `rotateZ(degrees)`, then translate `(outW/2, outH/2)`", composed with
   `concat` in that order as `derivedFrame` does. Positive degrees turn clockwise on screen, as the
   prototype's CSS `rotate()`.
6. **The crop's frame.** `tools/crop/CropRect.kt`: `@Serializable data class CropFrame(width, height)`
   with `Unset = CropFrame(0, 0)` and `toIntSize()`. `CropMath.kt`:
   `fun CropRect.scaledTo(from: IntSize, to: IntSize): CropRect` — each edge scaled by its axis and
   rounded, then `clampedTo(to)`; `from` unset or equal to `to` returns `this`. `CropTool`:
   `Frame = ObjectParam("frame", CropFrame.serializer(), CropFrame.Unset)` added to `params`;
   `stage` returns `Framed { frame -> Geometry(translate(−r.left, −r.top), IntSize(r.width, r.height)) }` with
   `r = rect.scaledTo(storedFrame, frame)` — a crop with no stored frame (every document saved
   before this) gets `r = rect`, unclamped, exactly today's stage. `CropState.kt`:
   `cropOpWith(rect, frame)` writes `Frame` beside `Rect`; `cropRectIn(frame)` returns
   `stored.scaledTo(storedFrame, frame)` before the clamp. `CropOverlay` and `CropAspectEffect`
   pass the `frame` they already hold.
7. **`StraightenTool`** — `tools/straighten/StraightenTool.kt` (new). `Angle = FloatParam(key =
   "degrees", label = "Straighten", range = -45f..45f, default = 0f, unit = "°", step = 0.5f,
   dial = true)`; `FloatParam.dial: Boolean = false` added in `ParamSpec.kt` ("drawn as a dial,
   not a scrub row"). Manifest: id `straighten`, title `Straighten`, summary `Level a tilted
   horizon`, icon `Icons.Static.Straighten`, `ToolDomain.Geometry`, `order = 2` (after Crop, so
   the tab reads Shape, Orientation, Straighten, rotate, flip), `joins = "transform"`, `Single`,
   `Continuous`, `foldsFirst = true`. `stage`: null at 0°, else
   `RasterStage.Framed { straightened(it, degrees) }`. Registered in `EditorModule` beside Crop.
   Icon: `level.svg` in `composeResources/drawable`, drawn as the set's other glyphs, and
   `Straighten = Res.drawable.level` in `EditorIcons.kt` — Settings → Editor lists the tool, and it
   takes its own tab when Transform is off, as Crop does.
8. **The dial** — `shell/ParamDial.kt` (new), `shell/ParamDialScale.kt` (new, the drawing) and
   `shell/ParamDialInput.kt` (new, the drag and the keys).
   `ParamRun` sends a run whose first spec is a `FloatParam` with `dial` to `ParamDial` before the
   scrub-row branch. Built on the spec's `ScrubLevel` (`levelsOf`), so writing, settling and reset
   are the scrub rows' own. From `editor.css` `.dial*` and `instruments.js`:
   - the name above it as a choice's name stands; a well (`RoomColors.well`, 12 dp radius, padding
     10/6 dp), hover `RoomColors.hover`, focus a 2 dp inset `accentLine`; tooltip "Drag sideways
     to level the horizon. Double-click to reset.";
   - readout `0.0°`, 13 sp mono medium, `text`, `muted` at zero, 6 dp above the window;
   - a 36 dp window fading out at both ends (opaque 24–76 %); a scale moving under a fixed needle,
     8 dp per degree: 1° ticks 9 dp `faint`, 5° ticks 15 dp `muted`, labels every 15° (10 sp mono
     `muted`) at the foot; the needle 2 × 21 dp `accent`, from the top; the scale glides 320 ms to a
     written value when not dragged; the needle pulses 1 → 1.35 → 1 in height over 240 ms on
     arriving at zero;
   - drag sideways: `value = start − dx / 8 dp`, rounded to 0.1°, |value| < 0.9° snaps to 0 — one
     preview per move, one settle on release, so one history entry per drag; `ew-resize` cursor;
   - ← / → ±0.5°, with Shift ±5°, Home 0 — each a settled write; double-click resets to the detent.
9. **The finer grid.** `CropCanvas` takes `fine: Boolean`: an 8 × 8 grid (`RULE`, 1 px) instead of
   thirds. `CropOverlay` passes `fine = scope.document.pending?.toolId == StraightenTool.manifest.id`
   — while the dial moves, as the prototype's `.straightening` does.

## Files

`ai/mockups/design/woka/app.js` (edit);
`engine/image/RasterStage.kt`, `RasterFold.kt` (edit), `RasterFoldOrder.kt`, `StraightenFrame.kt` (new);
`core/ToolManifest.kt`, `core/ParamSpec.kt` (edit, commonMain);
`tools/crop/CropRect.kt`, `CropMath.kt`, `CropState.kt`, `CropTool.kt`, `CropOverlay.kt`,
`CropCanvas.kt` (edit), `CropAspectEffect.kt` (new);
`tools/straighten/StraightenTool.kt` (new); `di/EditorModule.kt` (edit);
`shell/ParamRowRuns.kt` (edit), `ParamDial.kt`, `ParamDialScale.kt`, `ParamDialInput.kt` (new);
`core/ui/icons/EditorIcons.kt` (edit), `composeResources/drawable/level.svg` (new).
Paths under `com/woka/feature/editor/` unless named. Above §4a's eight files because the angle is
one behaviour from the dial to the saved file (`tickets.md` §4, the author's waiver).

## Tests

- `jvmTest/unit/StraightenFrameTest` (new): 0° is the identity at the same size; 6000 × 4000 at
  10° is 4818 × 3212 and the same at −10°; 4000 × 6000 at 10° is 3212 × 4818; the four output
  corners mapped back through the inverse matrix land inside the source.
- `jvmTest/unit/StraightenFoldTest` (new): a straighten made after a quarter turn folds before it
  (matrix equal to straighten-then-turn); a flip after a straighten leaves `degrees` as written; a
  straighten with the tool switched off is skipped.
- `jvmTest/unit/CropFrameTest` (new): a rect chosen in one frame is scaled into another, and the
  fold's crop size and offset equal `cropRectIn` for that frame; a crop with no stored frame folds
  as before this ticket.
- `jvmTest/unit/EditorTabsTest`: Transform's tab is Crop, Straighten, Transform; with Transform
  off, Straighten has its own tab.
- `jvmTest/ui/StraightenDialUiTest` (new): a drag writes the angle and is one undo step; under 0.9°
  it snaps to 0; ← → Shift Home; double-click resets; the readout.
- `jvmTest/integration/StraightenReadersTest` (new): the 640 × 480 fixture kept at 10° is drawn
  by the viewer (`RecipeStillEdits`, through `renderAtNativeScale` as a save is) at 526 × 394
  with opaque corners, and the grid's preview keeps that shape.
  A film shares the fold and `renderAtNativeScale`; its render is checked by hand, not here
  (`RenderSoundSyncTest`'s native crash, ST-007).

## Done when

- The dial levels the picture live in Transform for a photo, a frame set and a film; the crop
  frame stays on the picture as the angle changes; the size line shows the shrunk size.
- Done keeps it: the viewer, the grid tile, a saved copy and a rendered film show the same
  levelled picture at the size line's size; a flip after straightening leaves it level.
- The prototype draws the flip and the size line as above.
- Every touched `.kt` ≤ 150 lines; anti-FQN clean; `./gradlew.bat :shared:build` green.

## Built — 2026-11-26

- As specified, with these differences: the dial's input is its own file (`ParamDialInput.kt`);
  the drag reads from the press, not from where the touch slop was crossed
  (`detectHorizontalDragGestures` lagged the scale ~2° behind the pointer); a drag that returns
  to its start still settles, so no preview is left in flight.
- `CropAspectEffect` does nothing while another edit is pending: the dial changes the frame on
  every step, which re-ran the effect, and a rect scaled into the new frame can be a pixel off
  its ratio — fitting it would have replaced the dial's pending operation with a crop.
- The dial's dimensions are constants in `ParamDialScale.kt` citing `.dial*`, not
  `EditorTokens` — that file is at 147 lines.
- Tests: `StraightenFrameTest`, `StraightenFoldTest`, `CropFrameTest`, `StraightenDialUiTest`,
  `StraightenReadersTest` (new); `EditorTabsTest`, `CropToolTest` (the crop's stage is `Framed`).
