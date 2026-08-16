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

## Result — 2026-08-16

`./gradlew.bat :shared:build` green. **525 tests, 0 failures, 0 errors** — 517 before, plus eight.

### ME-005's sealed `ToolStage` does not work, and now we know why

ME-005 shipped `ToolStage` as a `sealed interface` and recorded that the claim was unproven until a
`jvmMain` type implemented it. It fails, and the compiler gives **two** independent reasons:

```
Extending sealed classes or interfaces from a different module is prohibited.
A class can only extend a sealed class or interface declared in the same package.
```

`commonMain` and `jvmMain` count as separate modules for sealedness, and
`feature/editor/engine/image` is not `feature/editor/core` either. `ToolStage` is now a plain
interface — the fallback ME-005 wrote down. Nothing else changed: exhaustiveness was only ever wanted
where the fold branches, and that is over `RasterStage`, which is sealed inside its own package.

### `RasterStage.Pixels` carries a `ColorMatrix`, not a `ColorFilter`

Step 1 says `Pixels(colorFilter: ColorFilter)` and step 4 says colour composition is a `ColorMatrix`
multiply "not a chain of `ColorFilter`s". Those cannot both hold: a `ColorFilter` is opaque, so a fold
handed two of them can only chain them. Step 4 carries the reasoning, so it wins — `Pixels` holds the
matrix and the single `ColorFilter` is built once, at the end of the fold, and only when the product
is not the identity.

### Composition order is the load-bearing detail

`concat` and `multiply` in `ColorMatrixMath.kt` both compose **left-applied-first**: the accumulated
transform goes on the right of the product, the new stage on the left. Neither product commutes, so
this direction is the difference between rotating a cropped picture and cropping a rotated one.
`sortedBy` is stable, which is what keeps two operations of the same domain in the order the user
made them — an unstable sort would let two arrows swap places between runs of the same document.

### Two things beyond the ticket

- A test that a **pending** operation folds identically to a committed one. The fold takes
  `document.ops + pending`, and if it did not, every slider would preview something other than what
  committing produces.
- `RasterFoldTest.kt` reached 174 lines, so its fixtures moved to `support/RasterFoldHarness.kt`
  (`StageTool`, `translation`, `scaleRed`, `withFoldRegistry`) rather than the cases being cut in
  half arbitrarily. Test file now 134, harness 67.

`fold` asks the registry for `MediaKind.Image` enablement: this is the image engine's fold, and the
video engine will have its own.
