# ME-165 — A stage that is a shader

| | |
|---|---|
| **Order** | after ME-168; before ME-162, the first tool to use it |
| **Depends on** | ME-007 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Add `RasterStage.Shader` as a fourth thing a stage can be — a Skia `RuntimeEffect` and its
uniforms — folded into the recipe and drawn identically everywhere a recipe is drawn.

**Why now.** `RasterStage` has three cases and its own KDoc says *"three and no more, because the fold
has to collapse them"*. Every adjustment still on the list is per-luminance or per-neighbourhood, and
a `ColorMatrix` is a 4×5 **affine** map: a tone curve is not a linear map of the channels, and a
convolution reads the pixels around the one being written. `AdjustTool`'s KDoc already names this as
the blocker and `AdjustMatrixTone.kt` says the real Vibrance *"waits for ME-162's shader stage"*. No
tool ships here — the engine change alone, proved against a fixture, which is how ME-147 shipped
`ToggleSetParam`.

## Steps
1. Amend `image-editor.md` **before a line of Kotlin**: §3.2's three-primitive block gains a fourth
   line, and §9 gets a dated row superseding the 2026-08-16 "three shapes" decision with the reason.
   `RasterStage.kt`'s KDoc is rewritten in the same change — it currently argues the opposite.
2. Add to `RasterStage.kt`:
   `data class Shader(val effect: RuntimeEffect, val uniforms: Map<String, FloatArray>, val childName: String = "content") : RasterStage`.
   The child is the picture as already drawn, so a shader composes over whatever `Pixels` produced.
3. In `RasterRecipe.kt`, add `val shaders: List<RasterStage.Shader>` — a **list**, not one value: two
   convolutions cannot be multiplied into one the way two colour matrices can, so they stay ordered
   and run in sequence. Extend `scaledBy(factor)` to pass `factor` into each shader as a reserved
   `uResolutionScale` uniform, because a radius stated in source pixels must shrink with the bitmap
   the reader path folds against.
4. In `RasterFold.kt`, add the fourth `when` branch appending to a `shaders` accumulator inside the
   existing domain-sorted walk. Nothing else in the fold changes.
5. In `RasterSurface.kt`, apply the shader list after `drawImage(colorFilter = …)` and **before**
   `recipe.paints` — markup is drawn on top of a sharpened picture, not sharpened with it. Keep the
   no-bitmap-per-frame property: build each `RuntimeShader` once per recipe in a `remember`, never per
   draw.
6. In `save/NativeRender.kt`, apply the same list in the same position against the real
   `org.jetbrains.skia.Canvas`. The two draw routines must stay the same sequence literally — that is
   what makes preview and save agree, and it is the property the tests below check.
7. Add `FixtureShaderTool` beside the existing `FixtureTool` in the harness: one `FloatParam` driving
   a trivial `RuntimeEffect` (multiply by a uniform) whose output is predictable per pixel.

## Files
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/RasterStage.kt` (edit, 48 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/RasterRecipe.kt` (edit, 73 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/RasterFold.kt` (edit, 126 lines — at the mandate, budget a split)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/RasterSurface.kt` (edit, 78 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/save/NativeRender.kt` (edit, 57 lines)
`shared/src/jvmTest/.../FixtureShaderTool.kt` (new)
`ai/docs/features/pending/image-editor/image-editor.md` (edit — §3.2, §9)

## Done when
- `jvmTest/unit/` asserts the fold carries two shader stages in domain order, and that `scaledBy`
  writes the scale uniform;
- `jvmTest/integration/` asserts a `FixtureShaderTool` op produces **the same pixel values** through
  `RasterSurface` and through `renderAtNativeScale` at scale 1.0 — a test that checks one path does
  not prove the agreement, and the agreement is the deliverable;
- `RasterFold.kt` and `RasterRecipe.kt` each stay under 150 lines, or the split is its own step;
- `./gradlew.bat :shared:build` green.

## Note
`ToolStage` is a bare interface and deliberately not sealed — ME-005 tried sealed and ME-007 reversed
it, because a `jvmMain` type cannot extend a `commonMain` sealed one. `RasterStage` is sealed within
its own file, so the fourth case is a local change and the video engine's stage type is untouched.
