# ME-162 — Four adjustments the matrix cannot express

| | |
|---|---|
| **Order** | after ME-165, which is the stage it stands on |
| **Depends on** | ME-165, ME-161, ME-142 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Ship Highlights, Shadows, Sharpness and Noise Reduction as shader stages, taking the
adjustments panel from eight to twelve.

**Why now.** Split from ME-142 on 2026-09-01, when the matrix half was promoted and the rest was found
arithmetically impossible on a `ColorMatrix`. Highlights and Shadows are tone curves — per-luminance,
so not a linear map of the channels. Sharpness and Noise Reduction are convolutions — per-neighbourhood,
so they read pixels an affine map never sees. ME-165 gives them the stage; ME-161 measured the panel
for fifteen and ME-164 gave it sections, so twelve costs no panel work.

**Scope decided 2026-09-02: four, not seven.** Definition, Vignette, Brilliance and the per-pixel
Vibrance stay in `backlog.md`. The same shader stage serves them whenever they are wanted, so nothing
here has to be redesigned to add them later.

## Steps
1. Add a third section constant to `AdjustTool.kt` — `DETAIL = "Detail"` — beside `LIGHT` and
   `COLOUR`. `AdjustTool`'s KDoc already anticipates it by that name, and `ParamRowStrip`'s
   `SectionControl` grows a third segment with no UI change: `sections` is derived from
   `specs.mapNotNull { it.section }.distinct()`.
2. Add four `FloatParam`s over the existing `SPAN = -100f..100f`: `Highlights` and `Shadows` with
   `section = LIGHT`, `Sharpness` and `NoiseReduction` with `section = DETAIL`. Give each the `icon`
   its chip needs, per ME-144.
3. New `AdjustToneShader.kt`: **one** `RuntimeEffect` applying both curves in a single pass — a
   shoulder-and-toe curve on luminance, `Highlights` pulling the shoulder down, `Shadows` lifting the
   toe. Preserve chroma by scaling RGB against the luminance ratio rather than curving each channel
   separately, which is what makes a lifted shadow grey instead of muddy. One shader, not two,
   because the two curves compose on the same luminance value.
4. New `AdjustDetailShader.kt`: an unsharp mask for `Sharpness`, and a small-radius bilateral filter
   for `NoiseReduction` — a plain gaussian is a blur, not a denoise, and would read on screen as
   mush. Both radii are stated in **source** pixels and multiplied by ME-165's `uResolutionScale`
   uniform, so the proxy, the save and a 400 px grid tile agree about how hard the effect is.
5. `AdjustTool.stage()` returns one `ToolStage?` today and must now return a colour matrix **and** up
   to two shaders. Add `RasterStage.Composite(val stages: List<RasterStage>)` to `RasterStage.kt` and
   let `RasterFold` flatten it — one branch in the existing `when`, every other tool untouched. Do
   **not** widen the `EditorTool` contract to return a list: `stage()` is shared with the video engine
   and its stage is not a raster one.
6. Order inside the composite: `Pixels`, then tone, then detail. Sharpening after a contrast change is
   what the user is looking at while dragging the slider.
7. Each new adjustment contributes no stage at its detent, and `adjustmentMatrix`'s existing
   "identity → null" rule extends to "no matrix and no shader → null", so an untouched Adjust tool
   still costs the canvas nothing.

## Files
`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/adjust/AdjustTool.kt` (edit, 123 lines — twelve params will pass the mandate; the split is its own step)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/adjust/AdjustToneShader.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/adjust/AdjustDetailShader.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/RasterStage.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/RasterFold.kt` (edit)

## Done when
- twelve chips across three sections, the section control reading Light / Colour / Detail;
- `jvmTest/unit/` asserts each of the four is a no-op at its detent, and that the composite flattens
  in the stated order;
- `jvmTest/integration/` asserts a sharpened save and its preview agree at scale 1.0, and that a
  400 px tile of the same recipe is sharpened proportionally rather than four times too hard;
- confirmed by hand — a convolution is a look, and no assertion decides whether it is the right one;
- `./gradlew.bat :shared:build` green.
