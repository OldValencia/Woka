# ME-109 — Crop tool

| | |
|---|---|
| **Order** | **done 2026-08-22** — confirmed by hand after four passes; the carriers first, then the tools |
| **Depends on** | ME-105, ME-133 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Choose a rectangle on the canvas and have every reader in the app draw the picture inside
it.

**Why now.** It is the second real tool and the **first one that addresses pixels**, which is what
makes it more than a tool: rotate and flip are scale-free, so `RecipeStillEdits` has been folding
recipes against whatever bitmap a reader handed it and getting away with it. A crop rect is in source
pixels. Fold it against a 400 px grid cover and the crop lands somewhere else entirely.
`RecipeStillEdits` says so in its own doc comment and names this ticket. **Steps 1–2 are that fix**,
and ME-110 inherits it.

It is also the first `CanvasTool`: `CanvasTool`, `EditorToolScope` and `EditorCanvasHost`'s `overlay`
slot were all built for it (ME-101) and nothing has ever dispatched into them.

## Built — what the tree said that the ticket could not

Three things came out of writing it, and each is a decision the ticket had guessed at:

- **`stage()` cannot answer "covers the whole frame".** It receives parameters and never
  the frame (§3.4) — the same limit that stops a rotation reporting its own output size. So the
  untouched state lives in the rect instead: `CropRect.Unset` has no area and folds to nothing, and
  the overlay writes a real rect only once a handle has moved.
- **`manifest.order` is the rail's, not the fold's.** The ticket said "order = 1 (after Transform)"
  as though it decided application order; the fold ranks by *domain* and keeps the order the user
  worked in. Turning after a crop turns the cropped picture and cropping after a turn cuts the
  turned one — both asserted, because "after Transform" would have been a false comfort.
- **An operation must be written from `tool.defaults()`.** A partial `ParamSet` is one the
  inspector completes on its next composition, and that write is an undo step nobody asked for.

**One trap, recorded because it cost a diagnostic pass.** `EditorHistory.startingAt` reconstructs
steps from the document's operations (ME-134), so a session opened on a file that already carries a
crop has `canUndo` **true before anything is done**. A UI test that asserts "this gesture changed
nothing" has to assert on the document, not on Undo.

## Found by hand, 2026-08-21 — and every one of them was mine

The first run of the tool was, in the user's words, unusable. Four faults, three of them defects:

- **Resizing did nothing.** `pointerInput` keeps the lambda it was handed until its *keys* change,
  and `EditorToolScopeImpl` is rebuilt on every document change — so every event of a drag measured
  from the rect as it stood when the gesture began. The rectangle twitched around its original size
  instead of resizing. The scope is now read through `rememberUpdatedState`, and the rect, the ratio
  and the matrices are taken live on each event. **This is the classic Compose capture bug and a UI
  test passed straight through it**, because a single short drag from a corner still produces one
  plausible-looking crop.
- **Every aspect ratio was inert.** Choosing one stored the value and reshaped nothing until the
  next handle drag — a chip that lights up and does nothing is a broken control. `CropRect.fittedTo`
  now reshapes on the spot: shorter axis wins (never reaches for picture already excluded), centre
  kept, clamped to the frame.
- **A bare wheel zoomed the canvas.** Older than this ticket, but one rule per app: the viewer zooms
  on `Ctrl`/`Cmd + Scroll` and now so does the editor.
- **Two tools, one placeholder glyph.** Crop was given `Icons.Static.Editor`, the same square-and-
  pencil Transform uses, so the rail was two identical squares. Crop has its own glyph, and the rail
  now draws **icon plus name** — the tooltip is left carrying only what a button cannot say for
  itself, which is why it is unavailable.

## Found by hand again, 2026-08-22 — the tool was unusable

A second by-hand pass, and it was worse than the first. Five faults, and the two that mattered
most were not in the crop maths at all:

- **Transform vanished from the rail.** Two unqualified `single<EditorTool>` declarations in Koin
  are one definition declared twice: the second replaces the first and `getAll` returns one tool.
  Adding Crop deleted Transform. Each tool now carries its own `named(...)` qualifier.
- **The crop measured itself.** The canvas measures its box with `engine.drawnSize(document)`, which
  moves the moment a crop lands — and that moved size was also the frame the rectangle was clamped
  into. Frame shrinks → rect clamped → frame shrinks. By hand: the rectangle collapsed on every
  click and the picture would not drag near its own edges. Fixed by a rule rather than a patch:
  `CanvasTool.previewsThroughOverlay` — while such a tool is open the canvas draws
  `document.without(toolId)`, because the overlay is already showing that result. False by default,
  which is right for markup: a stroke has to be on the canvas as it is drawn.
- **The overlay ate every drag.** `detectDragGestures` takes them all and the overlay covers the
  whole picture, so the canvas could never be panned again. Now `awaitEachGesture`: a press that
  lands on no handle is **left unconsumed** and the host pans. `Ctrl + drag` pans even over the
  rectangle, since a rect at the full frame leaves nowhere else to grab.
- **The chips printed constant names** — `SixteenNine`, `FourThree`. `ChoiceParam` gained `labelOf`,
  and the crop passes `CropAspect::label`, so they read `16:9` and `4:3`.
- **`Flip ratio` did nothing, and could not.** An `ActionParam` is built from `tool.defaults()` by
  contract (so a press never carries the previous op's flags), which means pressing it would have
  discarded the very rect it was meant to turn. Replaced by a `BoolParam` **Portrait**, which is
  built from the current parameters and says what it does.

**A test that does not fail is not a test.** The first regression test written for the second fault
watched the stored rect and passed with the bug still in place — a click that writes nothing leaves
the document alone either way. `CropFrameUiTest` now asserts on the document the *engine is asked to
draw*, and was confirmed to fail with `previewsThroughOverlay` set back to false.

## Third by-hand pass, 2026-08-22 — and the one that needed measuring

- **Switching `1:1` ⇄ `Original` walked the rectangle down to the minimum.** `fittedTo` took the
  shorter side each time, which loses a little on every press. It now holds the **area**: 200×200 at
  4:3 is 231×173 and back at 1:1 is 200×200 again. Twenty round trips are asserted.
- **The corners were nearly unhittable**, worst at the bottom where the rectangle meets the
  inspector. `handleAt` gained a separate, wider `cornerTolerance` (40 screen px against 18), and the
  handle marks are now drawn *inside* the frame instead of straddling its edge.
- **After a flip the rectangle flew off the picture.** Measured rather than guessed, with a probe
  over `fold`: with a flip in the document the canvas draws source `(100, 80)` at `(540, 80)`, while
  the overlay placed the stored rect through the canvas geometry alone — which knows nothing of the
  recipe. The rectangle therefore sat on the mirror image of the region it keeps. `EditorEngine`
  gained `previewMatrix(document)` — how a source coordinate lands on what was drawn, defaulted to
  identity, naming no raster type — and the tool scope composes it with the canvas geometry.

**And that fix exposed a second one.** With a mirrored matrix the screen-to-source distance comes out
*negative*, so the grab tolerance was being clamped to a single source pixel: on a flipped picture no
handle could be grabbed at all. `sourcePixels` takes the absolute distance. The regression test
found it — it failed with "nothing was resized", the printed rect showed the whole frame, and that
was `Body` answering because no edge was ever within reach.

## Fourth by-hand pass, 2026-08-22 — one cause, three symptoms

Reported as three: a crop that "rolled back" when the tool was switched, an editor showing a picture
*wider* than the crop it had applied (all of it, once zoomed out), and a rotation afterwards turning
some other region entirely.

**Measured first.** A probe over the real engine and the real file reported `sourceSize 640×480`,
the stored rect read back intact, `drawnSize 200×150`, both tools enabled — so the fold, the recipe
and the file were all correct, and the fault was in the drawing.

**`RasterSurface` did not clip.** Its box is `recipe.outputSize`; the picture drawn inside it is the
*source*, which a crop makes larger than the box — and a Compose `Canvas` paints outside its bounds
happily. Every operation before the crop produced an output at least as large as what it drew, so
nothing had ever exceeded the box and the omission had never shown. `Modifier.clipToBounds()`.

The save path was never affected: `renderAtNativeScale` draws into a Skia surface that *is* the
output's size, which is why the written file and every reader showed the crop correctly while the
editor did not.

`RasterSurfaceClipUiTest` captures the canvas and asserts the corner outside the frame stays the
background colour; with the clip removed it fails with that corner painted through.

## Steps

1. `engine/image/RasterRecipe.kt`: `fun RasterRecipe.scaledBy(factor: Float): RasterRecipe` — pure.
   The matrix becomes `S · M · S⁻¹` and `outputSize` is multiplied and rounded, so a recipe folded in
   source coordinates describes the same picture in a smaller bitmap's coordinates. Paints are
   carried through untouched and **documented as ME-113's**: nothing produces one yet.
2. `engine/image/RecipeStillEdits.kt`: fold against `stillDecoderFor(file).nativeSize(file, frameIndex)`
   and hand the result `scaledBy(decoded.width / native.width)`. A reader handed a native bitmap
   scales by 1.0 and is bit-for-bit what it is today. This replaces the "folded against the bitmap it
   was handed" paragraph, which stops being true here.
3. `tools/crop/` — split across five files under the size mandate rather than the ticket's one:
   `CropRect` (the stored rect, and the wire format), `CropAspect` (six ratios, `Original` a
   function of the frame), `CropHandle` (eight handles as *which edges they move*, plus the hit
   test), `CropState` (reading a crop out of a document and writing one back) and
   `CropMath.kt` (new), pure and Compose-free: fit a rect to an aspect inside a frame,
   clamp a rect to the frame, apply a handle drag (eight handles, a minimum of 32 source px per
   side), and hit-test a source point to a handle or the body. Every one a function of plain values —
   this is the file the overlay's arithmetic lives in so the overlay is only gestures.
4. `tools/crop/CropTool.kt` (new): `object CropTool : CanvasTool`. `ToolDomain.Geometry`, `order = 1`
   (after Transform), `ToolCardinality.Single` — a second crop replaces the first rather than
   compounding — `ToolInteraction.Canvas`.
   Params: `ObjectParam<CropRect>("rect", …)` holding `left/top/width/height` in **source** pixels,
   `ChoiceParam("aspect", …)` over `Free, Original, Square, FourThree, ThreeTwo, SixteenNine` (six,
   so `ChoiceParamRow` draws its chip row on its own), and `ActionParam("flip", "Flip ratio")`.
   `stage(params)` returns `RasterStage.Geometry(Matrix().apply { translate(-left, -top) },
   IntSize(width, height))` — the one stage that names its own frame (`DerivedFrame`'s doc says why),
   and **null** for a rect covering the whole frame, so an untouched crop composes nothing.
5. `tools/crop/CropOverlay.kt` + `CropCanvas.kt` (new): `@Composable override fun Overlay(scope: EditorToolScope)`.
   Reads the rect from `scope.document`, draws it through `scope.sourceToCanvas` — dimming outside,
   a thirds grid inside, eight handles — turns pointer positions into source coordinates with
   `scope.canvasToSource`, calls `scope.preview` per drag event and `scope.commit` on pointer-up
   (§3.1: one undo step per gesture, not per frame). Hover over a handle sets the resize cursor.
6. `shell/EditorScreen.kt` + `shell/EditorCanvasHost.kt`: pass the active tool's overlay into the
   host's existing `overlay` slot when that tool is a `CanvasTool`, and let the host's own drag
   handler run only when the overlay did not consume (§4.3). The host needs an `EditorToolScope`
   implementation built from `EditorCanvasGeometry` + the session — one internal class, in
   `shell/EditorToolScopeImpl.kt` (new), because `EditorCanvasHost` is at 148 lines.
7. `di/EditorModule.kt`: one line adding `CropTool` to the tool list.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/crop/` (new): `CropTool.kt`,
`CropRect.kt`, `CropAspect.kt`, `CropHandle.kt`, `CropMath.kt`, `CropState.kt`,
`CropOverlay.kt`, `CropCanvas.kt` ·
`.../shell/EditorToolScopeImpl.kt` (new) · `.../shell/EditorCanvasHost.kt` (edit) ·
`.../shell/EditorScreen.kt` (edit) · `.../engine/image/RasterRecipe.kt` (edit) ·
`.../engine/image/RecipeStillEdits.kt` (edit) · `.../di/EditorModule.kt` (edit)

## Done when

- `jvmTest/unit/CropFitTest.kt`: choosing a ratio reshapes what is already there — square takes the
  shorter side and keeps the centre, a ratio never reaches outside the frame, a rect already at the
  ratio is untouched, `Original` follows the picture, `Free` declares no ratio, and a flip turns the
  ratio over.
- `jvmTest/unit/CropMathTest.kt`: an aspect fit centres and never leaves the frame; a handle drag
  past the opposite edge stops at the minimum size; a drag with an aspect locked moves both axes; a
  hit test returns the handle under the point and the body between them.
- `jvmTest/unit/RasterRecipeTest.kt`: `scaledBy(0.5f)` halves a crop's origin, size and output; a
  factor of 1.0 returns **the same instance**, which is what guarantees a native reader is
  bit-for-bit what it was; a nonsense factor changes nothing; a frame scaled below a pixel keeps one.
- `jvmTest/unit/CropToolTest.kt` (rather than growing `RasterFoldTest`, which is at the size
  mandate): an unchosen rect composes nothing; a chosen one names its own frame and moves its corner
  to the origin; the fold reports the crop's frame; a crop after a quarter turn clips the turned
  picture **and** a turn after a crop turns the cropped frame; a second crop replaces the first.
- `jvmTest/integration/CropReadersTest.kt`: a JPEG cropped, written and read back through
  `ImageLoader` **and** through `StillPreview` shows the same region — the preview is the reader
  that decodes small, and it is the one step 2 exists for. The preview is asserted by aspect ratio,
  not absolute size.
- `jvmTest/ui/CropOverlayUiTest.kt`: a drag from a corner handle leaves exactly one undo step —
  proved by *undoing it*, since `Single` cardinality would keep the operation count at one however
  many commits there were — and a drag in the discarded area changes nothing.
- By hand: crop a photograph, `Done`, and the grid tile, the carousel thumbnail and the viewer all
  show the cropped frame without leaving the folder.
- `./gradlew.bat :shared:build` green.
