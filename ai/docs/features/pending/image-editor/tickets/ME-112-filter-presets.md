# ME-112 — Filter presets, and the preview-choice row

| | |
|---|---|
| **Order** | **done 2026-08-22** — tests green, by hand outstanding |
| **Depends on** | ME-111 (a colour matrix to look at), ME-102 (the row it completes) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Eight named looks as colour-matrix constants, chosen from a strip of thumbnails that each
show the picture through that look.

**Why now.** `PreviewChoiceParam` has existed since ME-003 and its renderer is the one row
`ParamRows` still answers with `Unit` — deferred there in ME-102 with the reason written in the code:
built before filters existed it would draw a strip of identical thumbnails and prove nothing. This is
the ticket that has something to preview.

## The one thing that is not obvious

**A thumbnail is a picture, and the shell may not name one** (§3.3). `ParamRows` cannot render a
filtered preview itself without learning what a bitmap is, which is the seam the whole editor is
built to keep.

So the engine draws it, exactly as it draws the canvas: a second, small `@Composable` that takes a
document and a modifier. The row asks for one thumbnail per option, each with a document containing
only that option's operation, and knows nothing about what comes back.

**No extra decode.** `ImageEditorEngine.Surface` already holds its proxy for the session
(`rememberEditorFrame`); the thumbnails draw the same bitmap through eight different colour filters,
which is a filter rebuild each and no pixel work — the property §3.2 was designed for.

## Steps

1. `core/EditorEngine.kt`: add `@Composable fun Thumbnail(document: EditDocument, modifier: Modifier)`,
   defaulted to drawing nothing. Same shape as `Surface`, same reason it is on the engine: a medium
   knows how to draw itself small, and the shell must not learn.
2. `engine/image/ImageEditorEngine.kt`: implement it as `RasterSurface` over the **same** proxy the
   canvas uses, with `recipe(document)` and `ContentScale`-equivalent sizing from the modifier. It
   must not call `EditorProxy.load` again — that is a decode per thumbnail per recomposition.
3. `tools/filter/FilterMatrix.kt` (new), pure: `enum class PhotoFilter` over **Original, Vivid,
   Dramatic, Warm, Cool, Mono, Noir, Silvertone** (§9), each with a `label` and a `ColorMatrix`
   constant; `Original` is the identity. Plus
   `fun filterMatrix(filter: PhotoFilter, intensity: Float): ColorMatrix?` — the constant lerped
   toward the identity by `intensity / 100`, and **null at zero intensity or at `Original`**.
4. `tools/filter/FilterTool.kt` (new): `object FilterTool : EditorTool`. `ToolDomain.Color`,
   `order = 1` (after Adjustments), `Single`, `Continuous`. Params:
   `PreviewChoiceParam("filter", …)` over the eight, and `FloatParam("intensity", 0f..100f,
   default = 100f, detent = 100f, unit = "%")`. `stage(params)` returns
   `RasterStage.Pixels(filterMatrix(...) ?: return null)`.
5. `shell/ParamRows.kt` + `shell/ParamRowPreviewChoice.kt` (new): replace the `Unit` branch with a
   renderer — a horizontal strip of options, each drawing
   `thumbnail(ToolOp(tool.manifest.id, params.with(spec, option)), Modifier.size(…))` above its
   label, the selected one carrying `Theme.colors.accentEditor` as the chip row already does. The
   thumbnail lambda is passed into `ParamRows` by `EditorScreen`, which is what may name the engine.
6. `shell/EditorScreen.kt`: pass `thumbnail = { op, modifier -> engine.Thumbnail(EditDocument(ops = listOf(op)), modifier) }`.
   A document of exactly one operation, so a thumbnail shows *that look*, not the whole edit.
7. `di/EditorModule.kt`: `single<EditorTool>(named(FilterTool.manifest.id)) { FilterTool }` — its own
   qualifier (2026-08-22).
8. `core/ui/icons/Icons.kt`: an icon that no other tool carries. `magic_wand.svg` is unused and is
   what a filter set looks like everywhere else.

**If step 5 pushes `ParamRows.kt` past 150 lines, the new renderer is its own file** — which is why
it is named as one above rather than left to discretion.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/filter/FilterMatrix.kt` (new) ·
`.../tools/filter/FilterTool.kt` (new) · `.../shell/ParamRowPreviewChoice.kt` (new) ·
`.../shell/ParamRows.kt` (edit) · `.../shell/EditorScreen.kt` (edit) ·
`.../engine/image/ImageEditorCanvas.kt` (new — the canvas `Surface` and `Thumbnail` share, extracted to keep `ImageEditorEngine` inside the size mandate) ·
`.../engine/image/ImageEditorEngine.kt` (edit) · `.../di/EditorModule.kt` (edit) ·
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorEngine.kt` (edit) ·
`shared/src/commonMain/kotlin/com/woka/core/ui/icons/Icons.kt` (edit)

## Done when

- `jvmTest/unit/FilterMatrixTest.kt`: `Original` composes nothing at any intensity; an intensity of
  zero composes nothing for every filter; intensity 50 is halfway between the identity and the
  constant, asserted on a coefficient rather than on the whole array; `Mono` and `Noir` produce equal
  RGB coefficients per row.
- `jvmTest/unit/FilterToolTest.kt`: a filter and an adjustment in one document fold to **one**
  `colorFilter`, not two — the claim `multiply` exists for; the fold's stage count is one.
- `jvmTest/ui/PreviewChoiceRowUiTest.kt`: the strip draws one thumbnail per option, each asked for
  with its own operation and nothing else. Clicking one — it writes that option, commits a single
  undo step, and a reopened file shows what it carries rather than the default — is
  `jvmTest/ui/PreviewChoiceWriteUiTest.kt`, split off when one file could not hold both inside
  the size mandate.
- `jvmTest/ui/`: the row asks for exactly **one** thumbnail per option per composition — a counting
  fixture engine, because "no extra decode" is the property that will regress silently.
- By hand: the strip shows the photograph eight ways, choosing one applies it, and the intensity
  slider moves it back toward the original.
- `./gradlew.bat :shared:build` green.
