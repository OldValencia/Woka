# ME-111 — Adjustments tool

| | |
|---|---|
| **Order** | **done 2026-08-22** — confirmed by hand |
| **Depends on** | ME-105 (the fold's `Pixels` stage), ME-110 (the `resolve`/`footnote` hooks it inherits) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Six sliders that compose into one colour matrix, so a photograph can be corrected without
a second render path.

**Why now.** It is the first tool in the `Color` domain, and the fold has been carrying
`RasterStage.Pixels` and `multiply` since ME-007 with nothing that produces one — the multiplication
path is written and unexercised. It is also what ME-112 previews: filters cannot be built or tested
until there is a colour matrix to look at.

## What already exists, and is not rebuilt

- `RasterStage.Pixels(colorMatrix)` and `multiply(accumulated, next)` in `ColorMatrixMath.kt`: the
  fold already collapses any number of colour stages into one `ColorFilter`, and rebuilds it only
  when a parameter changes.
- `ColorMatrix.isIdentity()`: an all-neutral document already yields `colorFilter = null`, so a
  photograph with the panel open but untouched costs the GPU nothing.
- `FloatParam` and `FloatParamRow`: the slider, its detent, its signed readout and its commit
  granularity are the schema's, not this tool's (§3.4). **No Compose code belongs in this ticket.**

## The six, and why not eight

Exposure, Brightness, Contrast, Saturation, Warmth, Tint (§9, 2026-08-18). Highlights and Shadows
are deliberately absent: they need a per-luminance curve, which is a fourth fold primitive against
§3.2's three, and adding one is a decision about the engine rather than about a tool.

**Auto is out of this ticket, and that is a finding rather than a cut.** An automatic correction
reads the picture — a histogram, a black point — and a tool receives parameters and nothing else
(§3.4). `resolve` (ME-110) hands over the *frame*, not the pixels, so Auto needs a hook that does not
exist. It stays in the backlog until something asks for it, with the reason recorded there.

## Built — the one number the ticket did not name

**A `ColorMatrix`'s offsets are in 0..255, not 0..1.** The fifth column is added after the multiply,
in the scale the channels are sampled in — so contrast written against `0.5`, which reads perfectly
well in code, darkens the whole picture as it is "increased". The constant is named `MID = 127.5f`
and the test asserts in the same units, because an assertion in 0..1 would agree with the bug.

Everything else went as specified. The icon is `slash_circle`, unused until now.

**Two bugs the by-hand pass found, neither of them in this tool.** Both were older than it and
needed a colour slider to become visible:

- **The preview applied a `Single` tool's operation twice** — `ops + pending` in the fold. Dragging
  Tint from −100 to +100 looked neutral and snapped to green on release. The fold now asks
  `commitPending`, so the preview and the result are one implementation (§3.1).
- **`ParamWriter` held a captured document.** `DetentSlider` keeps its gesture lambda in a
  `pointerInput`, so a live drag wrote against the document as it was when that gesture was set up,
  and moving a second slider wiped the first. It reads `session.document.value` at the moment of the
  write instead.

## Steps

1. `tools/adjust/AdjustMatrix.kt` (new), pure and Compose-free: one function per adjustment
   returning a `ColorMatrix` — `exposureMatrix(stops: Float)`, `brightnessMatrix(amount: Float)`,
   `contrastMatrix(amount: Float)`, `saturationMatrix(amount: Float)`, `warmthMatrix(amount: Float)`,
   `tintMatrix(amount: Float)`. Each returns the identity at `0f`. Warmth scales red against blue;
   tint scales green against magenta; contrast pivots on mid grey (`0.5`), which is the one that is
   wrong by default if written as a bare multiply.
2. `tools/adjust/AdjustMatrix.kt`: `fun adjustmentMatrix(params: ParamSet): ColorMatrix?` — the six
   folded through `multiply`, and **null when every one of them is neutral**, so `stage()` can return
   null rather than composing an identity the fold would then have to recognise.
3. `tools/adjust/AdjustTool.kt` (new): `object AdjustTool : EditorTool`. `ToolDomain.Color`,
   `order = 0`, `ToolCardinality.Single`, `ToolInteraction.Continuous` — one drag is one undo step,
   decided by the manifest and never by a row. Six `FloatParam`s over `-100f..100f`, `default = 0f`,
   `detent = 0f`, `unit = null`; `stage(params)` returns
   `RasterStage.Pixels(adjustmentMatrix(params) ?: return null)`.
4. `core/ui/icons/Icons.kt`: an icon for it. **Not one another tool already carries** — two identical
   glyphs in the rail is a mistake this project has made twice (2026-08-21, 2026-08-22).
   `slash_circle.svg` and `circle.svg` are unused; pick one and name it.
5. `di/EditorModule.kt`: `single<EditorTool>(named(AdjustTool.manifest.id)) { AdjustTool }` — with its
   own qualifier, because two unqualified declarations of the same type are one definition declared
   twice and the second silently replaces the first (2026-08-22).

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/adjust/AdjustMatrix.kt` (new) ·
`.../tools/adjust/AdjustTool.kt` (new) · `.../di/EditorModule.kt` (edit) ·
`shared/src/commonMain/kotlin/com/woka/core/ui/icons/Icons.kt` (edit)

## Done when

- `jvmTest/unit/AdjustMatrixTest.kt`: every adjustment is the identity at zero; saturation at −100
  produces equal RGB coefficients per row (grey); contrast pivots on mid grey — `0.5` maps to `0.5`
  at any amount, which a bare multiply gets wrong; warmth moves red and blue in opposite directions.
- `jvmTest/unit/AdjustToolTest.kt`: an all-neutral `ParamSet` composes **no stage**; two adjustments
  set at once produce one `Pixels` stage, not two; the fold's `colorFilter` is null for a neutral
  document and non-null for an adjusted one.
- `jvmTest/integration/AdjustReadersTest.kt`: a JPEG with an adjustment written into its recipe comes
  back with different pixels through `ImageLoader` **and** through `StillPreview`, and the same
  document leaves the dimensions untouched — a colour stage must not move the frame.
- By hand: drag a slider and watch the canvas follow at once; `Done`, and the grid tile and the
  viewer agree with it.
- `./gradlew.bat :shared:build` green.
