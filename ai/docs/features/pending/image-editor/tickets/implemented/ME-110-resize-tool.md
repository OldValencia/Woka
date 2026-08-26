# ME-110 — Resize tool

| | |
|---|---|
| **Order** | **built 2026-08-22** — by hand outstanding |
| **Depends on** | ME-105, ME-109 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Make the picture smaller by a chosen amount, as the last thing that happens to it.

**Why now.** It is the smallest remaining tool that is **pure data** — no overlay, no canvas
interaction — and it is the one that finishes the `Output` domain's ordering claim: a resize must be
the last stage before encoding, and the fold already sorts it there by domain rank (§3.4). It also
gives the inspector the footnote row that every later numeric tool inherits.

**Depends on ME-109 for a reason, not for tidiness.** A scaling stage is the other half of the
problem ME-109's steps 1–2 fix: a recipe carrying a scale, folded against a preview that is already
scaled, would scale twice. `scaledBy` has to exist before this lands.

## The shape, decided here

The backlog asked for two `IntParam` (width, height) and an aspect lock. **That does not fit the
architecture, and the ticket changes it to one scale.** `stage(params)` receives parameters and
nothing else (§3.4) — no frame, no source size — so absolute target dimensions cannot become a
matrix: the scale is `target / frame`, and `frame` is a running value only the fold holds. The three
ways out are a stage that names a frame it cannot know, a fourth fold primitive (§3.2 says three), or
a parameter that needs no frame at all.

So: **`FloatParam("scale", 0.05f..1f, default = 1f)`**, aspect always preserved, and
`RasterStage.Geometry(Matrix().apply { scale(s, s) }, outputSize = null)` — the fold derives the new
frame through `derivedFrame`, exactly as a rotation does. A resize expressed as a proportion also
survives a crop changing the frame underneath it, which an absolute pair of numbers would not.

Absolute targets stay reachable where the source size is actually known — the inspector — as presets
that resolve to a scale before they are written.

## Built — two things the ticket left open, decided here

- **The scale is held in percent (`5..100`), not as a `0.05..1` factor.** The inspector's existing
  readout prints value plus unit, so a factor would have shown `0.5%`. A percentage costs the shell
  nothing and `stage()` divides by a hundred; no renderer learns a special case.
- **Steps 3 and 4 collapsed into two hooks on `EditorTool`, both defaulted and both taking the
  frame.** The ticket's rule — *no tool learns the frame* — is kept where it matters, which is
  `stage()`: the fold stays a pure function of parameters. What the ticket had not separated is that
  a *chip* and a *footnote* are UI-time questions, asked when the frame is known:
  - `resolve(spec, params, frame)` — "long edge 1280" becomes a scale **before** it is stored, so
    the document holds a number and nothing downstream needs the picture's size;
  - `footnote(params, frame)` — the `4000 × 3000 → 2000 × 1500` line, which the inspector prints
    and every other tool declines by not overriding.

  The alternative was a `when` over tool ids in the shell, which is the thing §3.4 exists to prevent.

## Steps

1. `tools/resize/ResizeTool.kt` (new): `object ResizeTool : EditorTool`. `ToolDomain.Output`,
   `order = 0`, `ToolCardinality.Single`, `ToolInteraction.Continuous` — the drag previews and the
   release commits, so one drag is one undo step.
   Params: `FloatParam("scale", "Scale", 0.05f..1f, default = 1f, detent = 1f, unit = "%")` plus
   `ChoiceParam("preset", …)` over `Full, ThreeQuarters, Half, Quarter` (four, so `ChoiceParamRow`
   draws a `SegmentedControl`) and `ChoiceParam("longEdge", …)` over `UHD, W2560, W1920, W1280`.
   `stage()` returns null at scale `1f`.
2. `tools/resize/ResizeScale.kt` (new), pure: `scaleForLongEdge(target: Int, frame: IntSize): Float`
   and `percentLabel(scale: Float): String`. Unit-tested, because "2560 on a 4000×3000" is arithmetic
   nobody should read out of a composable.
3. `shell/ParamWriter.kt`: resolving a preset is a **write of another parameter**, which is new. Add
   `fun resolve(spec: ChoiceParam<*>, value: Enum<*>, frame: IntSize)` — or, if that turns out to
   need the tool's identity, keep the resolution in the row and give the writer only the resulting
   `edit(scale, …)`. Decide by writing step 4 first; the rule is that **no tool learns the frame**.
4. `shell/EditorInspector.kt` + `shell/EditorScreen.kt`: an optional footnote line under the rows,
   supplied by the screen — it is the screen that can name `engine.drawnSize(document)` — reading
   `4000 × 3000 → 2000 × 1500`. Off for every tool that passes nothing, which is all of them today.
5. `di/EditorModule.kt`: one line adding `ResizeTool` to the tool list.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/resize/ResizeTool.kt` (new) ·
`.../tools/resize/ResizeScale.kt` (new) · `.../shell/EditorInspector.kt` (edit) ·
`.../shell/EditorScreen.kt` (edit) · `.../shell/ParamWriter.kt` (edit) ·
`.../di/EditorModule.kt` (edit)

## Done when

- `jvmTest/unit/ResizeScaleTest.kt`: a long-edge target on a landscape and on a portrait frame both
  resolve to the same proportion of the **longer** side; a target larger than the source clamps to
  1.0, because this tool never enlarges.
- `jvmTest/unit/RasterFoldTest.kt`: a resize halves the derived frame; a resize **after** a crop
  applies to the cropped frame, not the original — the domain ordering, asserted rather than assumed;
  a scale of 1.0 contributes no stage at all.
- `jvmTest/integration/`: a JPEG resized to 50 %, saved and read back decodes at half the source's
  dimensions on both the `ImageLoader` and the `StillPreview` paths.
- `jvmTest/ui/`: the footnote reads the target dimensions and follows the slider; the panel shows
  the document's scale, not the default, on a reopened file (ME-134's contract, on a tool that has a
  value worth showing).
- By hand: resize, `Done`, reopen — the editor opens on the resized document and the Information
  dialog's dimensions match what the grid draws.
- `./gradlew.bat :shared:build` green.
