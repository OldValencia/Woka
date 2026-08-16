# ME-007 — Raster stage and the fold

| | |
|---|---|
| **Order** | 8 |
| **Depends on** | ME-005 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Ops → one matrix, one colour filter, an ordered paint list. Pure, testable, no canvas.

**Why now.** It is the only part of rendering with real logic in it; separating it from the composable
now is what makes it unit-testable at all.

## Steps

1. `RasterStage.kt` (jvmMain, `feature/editor/engine/image/`): `sealed interface RasterStage :
   ToolStage` with `Geometry(matrix: Matrix, outputSize: IntSize)`, `Pixels(colorFilter: ColorFilter)`
   and `Paint(draw: DrawScope.() -> Unit)`.
2. `RasterRecipe.kt`: `data class RasterRecipe(val matrix: Matrix, val outputSize: IntSize, val
   colorFilter: ColorFilter?, val paints: List<RasterStage.Paint>, val skipped: List<ToolOp>)`.
3. `RasterFold.kt`: `fun fold(document: EditDocument, registry: EditorToolRegistry, sourceSize:
   IntSize): RasterRecipe`. Sorts ops by `manifest.domain.ordinal` **stably**, so two ops of the same
   domain keep user order; concatenates geometry matrices; multiplies colour matrices; collects
   paints; and collects ops whose tool is missing or disabled into `skipped` instead of throwing.
4. Colour composition is a `ColorMatrix` multiply, not a chain of `ColorFilter`s — chaining allocates
   per frame and cannot be flattened. Keep the multiply in `ColorMatrixMath.kt` if `RasterFold.kt`
   approaches the line limit.
5. The identity case returns `colorFilter = null`, so a document with no colour ops costs nothing at
   draw time.

## Files

`jvmMain feature/editor/engine/image/RasterStage.kt`, `image/RasterRecipe.kt`, `image/RasterFold.kt`
(new)

## Done when

`jvmTest/unit/RasterFoldTest.kt` covers:

- an op added last but ranked first applies first;
- two ops of the same domain keep insertion order;
- two colour ops multiply into one filter, and all-neutral yields `null`;
- an op from an unregistered tool lands in `skipped` and does not affect the matrix;
- `outputSize` follows the width/height swap on an odd quarter turn.
