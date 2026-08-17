# ME-105 — Transform tool

| | |
|---|---|
| **Order** | 19 |
| **Depends on** | ME-102, ME-103 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Rotate and flip — the first real tool, and the test of whether a tool can be data.

**Why now.** Everything under it is built: the schema draws panels, the canvas draws recipes, the save
writes files. Until a real tool runs through all three, none of it has been proved by anything but a
fixture.

## The tool itself is small. Two shell gaps are not.

Its block promised "if this needs anything the shell does not already give it, the shell is wrong", and
it needs two things. Both were found by reading the contract against what a rotation actually requires,
before writing a line of it.

### 1. `stage()` cannot know the frame it acts on

`fun stage(params: ParamSet): ToolStage?` receives **only the parameters**. But
`RasterStage.Geometry(matrix, outputSize)` must declare the size *after* it applies, and a quarter turn
swaps the axes — so the tool would have to know the picture's dimensions, which nothing hands it. Worse,
a *second* rotation needs the size after the first: a running value the fold holds in a local and never
shares.

**Not fixed by widening the contract.** `stage(params, size)` would put the same arithmetic in every
geometry tool and give the video editor a parameter a trim has no use for.

Fixed in the fold instead: `RasterStage.Geometry.outputSize` becomes **nullable**, and null means
"derive it". `RasterFold` maps the current frame's corners through the stage's matrix, takes the
bounding box, **translates so it starts at the origin**, and adopts its size. A rotation therefore
declares nothing but `Matrix().rotateZ(90f)` — no size, no compensating translation — and the fold
normalises it. Crop keeps declaring its size explicitly, because a crop *chooses* a frame rather than
deriving one, and that stays the distinction between the two.

### 2. An action op inherits the previous action's flags

`ParamWriter.edit` builds the new op from `paramsOf(document, tool)`, which for a `Repeating` tool is
**the last committed op**. So pressing `Rotate left` and then `Flip H` produces an operation carrying
*both* flags true, and the picture rotates twice.

An `ActionParam` records "this happened", never "the state is now" — that is what its KDoc already says
and what `Repeating` means. So an op built for an `ActionParam` starts from `tool.defaults()`, not from
the previous op. One branch in `ParamWriter`, and the reason belongs in the code.

## Steps

1. `engine/image/RasterStage.kt`: `Geometry(val matrix: Matrix, val outputSize: IntSize? = null)`.
   Document the null as "derived", with the crop/rotate distinction above.
2. `engine/image/RasterFold.kt`: thread the running frame through `orderedStages` so each stage is
   folded against the frame as it stands, and for a null `outputSize` compute the bounding box of the
   current frame mapped through the matrix, prepend the normalising translation, and take its size.
   Extracted as `internal fun derivedFrame(frame: IntSize, matrix: Matrix): Pair<Matrix, IntSize>` —
   pure, and unit tested, because it is the one piece of arithmetic here that can be wrong by 90°.
3. `shell/ParamWriter.kt`: when `spec is ActionParam`, base the op on `tool.defaults()`.
4. `tools/transform/TransformTool.kt` (jvmMain): `object TransformTool : EditorTool` — manifest
   (`Geometry`, `order = 0`, `Repeating`, `Instant`, `Icons.Static.Editor` until a rotate icon exists),
   four `ActionParam`s — `RotateLeft`, `RotateRight`, `FlipHorizontal`, `FlipVertical` — and a `stage`
   returning `RasterStage.Geometry(matrixFor(params))` with no size at all.
5. `tools/transform/TransformMatrix.kt`: `internal fun matrixFor(params: ParamSet): Matrix?` — the four
   matrices, and null when no flag is set, which is the "no-op configuration" the fold already skips.
6. `di/EditorModule.kt`: `single<EditorTool> { TransformTool }`. **One line**, and if it needs a second
   the architecture claim in §3.4 is wrong.

## Files

`jvmMain feature/editor/tools/transform/TransformTool.kt`, `tools/transform/TransformMatrix.kt` (new),
`engine/image/RasterStage.kt`, `engine/image/RasterFold.kt`, `shell/ParamWriter.kt`,
`di/EditorModule.kt` (edit), `jvmTest unit/TransformMatrixTest.kt`,
`jvmTest unit/DerivedFrameTest.kt`, `jvmTest integration/TransformSaveTest.kt` (new)

## Verification

`unit/TransformMatrixTest.kt` — each of the four actions produces the matrix that sends the picture's
corners where they belong; no flag set produces null; two flags set is not reachable through
`ParamWriter` and is asserted to prefer the first declared, so the behaviour is defined rather than
accidental.

`unit/DerivedFrameTest.kt` — a quarter turn of 640×480 derives 480×640; two quarter turns derive
640×480 again; a flip derives 640×480 unchanged; the normalising translation puts the top-left corner
exactly at the origin in all four cases.

`integration/TransformSaveTest.kt`, the end-to-end claim this ticket exists to make — a rotate committed
through `ParamWriter`, folded, drawn and **saved**, produces a file whose dimensions are swapped and
whose corner pixels moved the way a rotation moves them. Plus: `Rotate left` then `Flip H` produces two
ops, each carrying exactly one flag.

## Done when

- `EditorModule` gained exactly one line;
- `TransformTool` contains no Compose import and no `@Composable`;
- rotating four times returns the picture to its original dimensions;
- `./gradlew.bat :shared:build` green, with the tests above passing.
