# ME-109 — Crop tool

| | |
|---|---|
| **Order** | 32 — **moved back 2026-08-19**: the carriers first, then the tools |
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

## Steps

1. `engine/image/RasterRecipe.kt`: `fun RasterRecipe.scaledBy(factor: Float): RasterRecipe` — pure.
   The matrix becomes `S · M · S⁻¹` and `outputSize` is multiplied and rounded, so a recipe folded in
   source coordinates describes the same picture in a smaller bitmap's coordinates. Paints are
   carried through untouched and **documented as ME-113's**: nothing produces one yet.
2. `engine/image/RecipeStillEdits.kt`: fold against `stillDecoderFor(file).nativeSize(file, frameIndex)`
   and hand the result `scaledBy(decoded.width / native.width)`. A reader handed a native bitmap
   scales by 1.0 and is bit-for-bit what it is today. This replaces the "folded against the bitmap it
   was handed" paragraph, which stops being true here.
3. `tools/crop/CropMath.kt` (new), pure and Compose-free: fit a rect to an aspect inside a frame,
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
5. `tools/crop/CropOverlay.kt` (new): `@Composable override fun Overlay(scope: EditorToolScope)`.
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

`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/crop/CropTool.kt` (new) ·
`.../tools/crop/CropMath.kt` (new) · `.../tools/crop/CropOverlay.kt` (new) ·
`.../shell/EditorToolScopeImpl.kt` (new) · `.../shell/EditorCanvasHost.kt` (edit) ·
`.../shell/EditorScreen.kt` (edit) · `.../engine/image/RasterRecipe.kt` (edit) ·
`.../engine/image/RecipeStillEdits.kt` (edit) · `.../di/EditorModule.kt` (edit)

## Done when

- `jvmTest/unit/CropMathTest.kt`: an aspect fit centres and never leaves the frame; a handle drag
  past the opposite edge stops at the minimum size; a drag with an aspect locked moves both axes; a
  hit test returns the handle under the point and the body between them.
- `jvmTest/unit/RasterRecipeTest.kt`: `scaledBy(0.5f)` halves a crop's origin, size and output; a
  factor of 1.0 returns an equal recipe.
- `jvmTest/unit/RasterFoldTest.kt`: a crop reports **its own** frame, and a crop after a quarter turn
  clips the turned picture rather than the original (application order, §3.4).
- `jvmTest/integration/`: a JPEG cropped, saved and read back through `ImageLoader` **and** through
  `StillPreview` shows the same region — the preview is the reader that decodes small, and it is the
  one step 2 exists for. Assert the aspect ratio of the preview, not its absolute size.
- `jvmTest/ui/`: dragging inside the canvas with Crop active previews without pushing history, and
  pointer-up leaves exactly one undo step.
- By hand: crop a photograph, `Done`, and the grid tile, the carousel thumbnail and the viewer all
  show the cropped frame without leaving the folder.
- `./gradlew.bat :shared:build` green.
