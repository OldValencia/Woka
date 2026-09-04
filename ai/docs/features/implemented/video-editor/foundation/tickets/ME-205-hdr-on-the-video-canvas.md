# ME-205 — HDR on the video canvas

| | |
|---|---|
| **Order** | fourth in the feature, after ME-207. Last of the foundation, and the only one that touches the still draw path |
| **Depends on** | ME-201 |
| **Feature** | [Foundation](../foundation.md) · umbrella [Video Editor](../../../../pending/video-editor/video-editor.md) |

**Goal.** Tone map an HDR video frame **inside** the recipe, before the colour work, so the editor
draws HDR video correctly and every adjustment is applied to ordinary sRGB light.

**Why now.** ME-201 draws the player's frame through `RasterSurface`, which does not go through
`HdrImagePainter` — so an HDR file opens blown out. It is the last thing in the foundation that is
simply *wrong* rather than absent, and it is also what makes the top bar's `HDR` control reachable at
all (ME-301).

---

## The finding that shapes this ticket

**Prepending to `RasterRecipe.shaders` does not work, and the design doc's word "prepend" was written
before this was read.** `drawThroughShaders` builds the chain in this order:

```
image shader  →  recipe.colorFilter  →  shaders[0]  →  shaders[1]  →  …
```

The colour filter is attached to the image shader **first, always** — and the colour filter *is*
Adjust and Filters, collapsed into one `ColorMatrix`. A tone map placed anywhere in `shaders`
therefore runs **after** the grade, which means every adjustment would be applied to a PQ- or
HLG-coded signal and then tone mapped. That is not a subtle error: a brightness slider on an HDR clip
would do something unrelated to brightness.

So the recipe needs a slot **before** the colour filter, and the two draw paths — the screen and the
save — must both get it from one place, because `drawRecipeImage` is deliberately shared so that a
saved file cannot disagree with the preview it was approved from (ME-165).

---

## Steps

1. Add `val source: RasterStage.Shader? = null` to `RasterRecipe`, ahead of `colorFilter` in the
   declaration so the field order reads in draw order. KDoc it as **"the signal the picture arrives
   in, converted before anything grades it"** — null for every still, because ME-177 tone maps a
   still at the decode and what reaches the fold is already sRGB. Defaulted, so no still path and no
   test changes.
2. **Split `RasterImageDraw.kt` first — it is 142 lines** (checked 2026-09-03) and step 3 pushes it
   over the mandate. Move `imageShaderFor` and `pack` into a new `RasterShaderChain.kt`. The seam is
   already there: what moves is *how a chain's pieces are built*, what stays is *which draw is
   chosen*.
3. In `drawRecipeImage`, the fast path's condition becomes
   `if (recipe.shaders.isEmpty() && recipe.source == null)` — the plain `drawImage` call takes a
   `ColorFilter` and cannot take a shader, so a recipe with a source stage must go through the chain
   even when it has no other effect. In `drawThroughShaders`, apply `recipe.source` to the image
   shader **before** the colour filter is attached, adding it to `built` so it is closed in reverse
   with the rest: an unclosed runtime-effect shader pins the whole frame, which is the incident that
   comment in the file is about.
4. `RasterRecipe.scaledBy` needs no change and the test must say so: `copy()` carries `source`
   through, and a tone map reads one pixel so it declares no `resolutionScale` — the field
   `scaledBy` rewrites for convolutions. Assert it rather than trust it.
5. Move `HDR_EFFECT` out of `HdrImagePainter.kt`, where it is a private top-level `by lazy`, into
   `HdrShader.kt` beside the SkSL as an `internal val`, and move `transferCode()` with it as an
   `internal fun` on `TransferKind`. Both readers then share one compiled `RuntimeEffect` — that lazy
   exists because the shader was being parsed and compiled 24 times a second on the UI thread, and a
   second private copy in the editor would give half of that back.
6. Add `VideoHdrStage.kt` in `feature/editor/engine/video/`: `hdrStageFor(info: HdrInfo):
   RasterStage.Shader?`, null when `!info.needsColorManagement`, otherwise
   `RasterStage.Shader(HDR_EFFECT, uniforms = listOf(1f, 1f, transferCode, wideGamut,
   ASSUMED_WHITE_POINT), resolutionScale = null)`.
   **`scale` is `(1f, 1f)` here, and this is the one line to get wrong.** In `HdrImagePainter` that
   uniform is `intrinsicSize / drawSize`, because that painter's image shader carries no local matrix
   and the effect is handed a *device* coordinate. In the recipe chain `imageShaderFor` has already
   installed the source→bitmap matrix and every stage evaluates its child in **source** coordinates,
   so any other value draws a corner of the frame blown up across the picture.
   `ASSUMED_WHITE_POINT` and not a measured peak: a frame that will be gone in 40 ms cannot be
   measured, and a running peak would make the curve move while the picture plays. A stable
   per-file peak is ME-301's probe, not this ticket.
7. `VideoEditorCanvas` builds the recipe as
   `remember(document, streamHdrInfo) { engine.recipe(document).copy(source = hdrStageFor(...)) }`.
   **`VideoPlayerState.streamHdrInfo`, not `currentFrameHdrInfo`** — the stream's value is written
   once per opened video track and is documented as stable *so the UI can read it without recomposing
   at the frame rate*, which is exactly what a memoised recipe needs. `MediaViewerFrame` reads the
   per-frame field because it mints a painter per frame anyway; this must not.
8. Tests, `jvmTest/kotlin/com/woka/unit/RasterSourceStageTest.kt`: a recipe with a `source` stage
   draws through the chain and its effect is applied **before** the colour filter — provable by a
   two-colour source and a filter whose result differs by order, which is how `RasterRecipeTest`
   already asserts about geometry; a recipe with `source == null` produces the same pixels as today;
   `scaledBy` leaves `source` untouched; `hdrStageFor` answers null for SDR and non-null for PQ.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/RasterRecipe.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/RasterImageDraw.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/RasterShaderChain.kt` (new) ·
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/video/HdrShader.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/video/HdrImagePainter.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/video/VideoHdrStage.kt` (new) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/video/VideoEditorEngine.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/unit/RasterSourceStageTest.kt` (new)

## Done — 2026-09-03

Every step as planned, and the finding above held: the slot had to be its own field, and the test
that proves it needed **two operations that do not commute**. Which is where the one wrong turn was —
the first draft used a `ColorMatrix` *offset* against a shader that scales, and both orders came back
128. `ColorMatrix`'s translation column is in 0…255 while a shader works in 0…1, so the filter was
adding a four-hundredth of what the test meant. It is a **scale** filter and an offsetting shader
now, which is unambiguous in either convention, and the reasoning is in the file so the next person
does not spend it again.

**Step 7 landed in the engine, not the canvas.** The recipe is remembered in
`VideoEditorEngine.Surface` and `Thumbnail` — that is where `recipe(document)` is memoised — so the
stream's colour joins the `remember` keys there and both surfaces get it from one expression. The
canvas draws what it is handed and never learns the picture was HDR.

**`RasterShaderChain` took `imageShaderFor` and `pack` with it**, and both had to become `internal`:
the draw is the only caller today, but a chain built in two files cannot keep them private to one.

**Nothing about a still changed**, and that is asserted rather than assumed: `source` is null for
every photograph, so the fast `drawImage` path is still the one they take.

**Confirmed on screen by the author, 2026-09-03** — *"всё работает"*. That closes the ticket and the
`foundation/` folder with it.

## Done when

- An HDR file from the corpus — `WokaSample.DualVideo.mkv` carries real PQ content, which is what
  ME-172 found — opens in the editor looking like it does in the viewer, not blown out.
- Brightness on that file moves brightness: the adjustment is applied after the tone map, and the
  proof is the ordering test rather than an eye.
- An SDR video is byte-identical to ME-201's output: `hdrStageFor` answers null and the fast
  `drawImage` path is still taken.
- **No still changes.** `EditorSaveTest`, `FrameRecipeTest`, `HdrBakeTest`, `RasterFoldTest` and
  `RasterRecipeTest` pass untouched, and the grid's folded covers are unchanged.
- `HDR_SHADER` is compiled once per process, with one `internal val` and no second `by lazy`.
- Every file touched is inside 130–150 lines, measured with `wc -l`.
- The anti-FQN grep over every edited file is clean.
- `./gradlew.bat :shared:build` green.
