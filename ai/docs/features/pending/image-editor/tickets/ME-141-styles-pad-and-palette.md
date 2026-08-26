# ME-141 — Styles: two carousels, a pad and a palette slider

| | |
|---|---|
| **Order** | 49 — after ME-116 |
| **Depends on** | ME-112 (the looks, the matrices and the preview-choice row it supersedes the panel of), ME-111 (`exposureMatrix`, `warmthMatrix`) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Turn Filters into **Styles**: an undertone family beside the existing looks, a two-axis pad
for Tone and Colour, and a Palette slider — one tool, still one `ColorFilter`.

**Why now.** Requested 2026-08-22 after iOS 27. It is also the ticket that adds the **ninth
`ParamSpec`**, and `ParamRows`'s `when` is exhaustive with no `else`, so the type cannot land without
its renderer — the guard that has held since ME-003 is about to be exercised for the first time since.

**This supersedes ME-112's inspector, not its matrices.** `FilterMatrix` keeps its eight constants and
its two `filterMatrix` overloads untouched; `FilterTool.stage()` keeps folding to one
`RasterStage.Pixels`; the **panel** is rebuilt and the tool is renamed. Nothing about §3.2 changes and
no engine change is needed: every one of the four contributions below is a `ColorMatrix`, and colour
matrices multiply.

## The four questions this block was blocked on, now answered (2026-08-26)

| Question | Answer |
|---|---|
| What do Tone and Colour mean numerically? | **Tone = exposure** (y), **Colour = warmth** (x). Both matrices already exist in `AdjustMatrix.kt` and are imported rather than rewritten. |
| Undertone and look at once, or one or the other? | **One of each.** Two independent parameters whose matrices multiply, exactly as an adjustment and a filter already do in the fold. |
| Are Palette and Intensity the same number? | **No — two numbers.** Palette is the undertone's strength, Intensity stays the look's. |

## The three things that are not obvious

**1. The tool keeps the id `filter`.** The title becomes "Styles" and the summary changes; the
manifest id is the settings key, the op's owner and what is written into every file already edited.
Renaming it would orphan every saved document. The new parameters get new keys and old documents fill
them from the defaults — `ParamSet` already does that, which is why this is a rename and not a
migration.

**2. A pad writes two numbers from one gesture, and `ParamWriter.edit` takes one spec and one
value.** So the pad is **one** spec whose value is a point, not two specs driven together — anything
else would put two operations in flight per pointer event and halve the undo granularity the manifest
promises. `ParamPoint(x, y)` is `@Serializable` and `PadParam` encodes to `JsonValue`, the same
carrier `ObjectParam` uses; unlike `ObjectParam` it **has** a renderer, which is the whole difference
between the two.

**3. "One strip, two sections" becomes two strips that read as one.** A single flat option list cannot
carry two independent selections, and the answer above is that it must. So `PreviewChoiceParam` gains
an optional `section` label drawn above its strip, and the two rows sit adjacent at the inspector's
own row gap. That is the trade the block warned about, made in the direction the second answer forces.

**Watch the panel's height by hand.** Two strips, two sliders and a pad is the tallest inspector in
the app; the pad is 120.dp for that reason. §4.2's inspector has never had to scroll and this is the
tool that finds out whether it must — which is also ME-142's problem, one ticket later.

## Steps

1. `core/ParamSpecPad.kt` (new, `commonMain`): `@Immutable @Serializable data class ParamPoint(val x:
   Float, val y: Float)` and `data class PadParam(key, label, xLabel, yLabel, xRange, yRange, default:
   ParamPoint) : ParamSpec<ParamPoint>`, encoding through `editorJson` to `JsonValue` and clamping
   both axes on the way in and out, as `FloatParam` does. Its own file: `ParamSpec.kt` holds the
   scalars and `ParamSpecChoice.kt` the rest, and this is neither.
2. `core/ParamSpecChoice.kt` (edit): `val section: String? = null` on `PreviewChoiceParam`.
3. `shell/ParamRowPad.kt` (new): a 120.dp rounded square on `Theme.viewer.onScrimHover` with a 1.dp
   `onScrimBorder`, cross-hairs at the centre and a white handle at the value.
   `detectDragGestures` writes `writer.edit(spec, point, settled = false)` per event and
   `writer.settle()` on release — the `DetentSlider` contract, so the tool's `Continuous` interaction
   gives one undo entry per gesture without this file deciding anything. **y is inverted**: up is more
   exposure. Double-click returns to the default, as a slider's double-click returns to its detent.
4. `shell/ParamRows.kt` (edit): `is PadParam -> PadParamRow(spec, params, writer)`. This is the line
   the exhaustive `when` refuses to compile without.
5. `shell/ParamRowPreviewChoice.kt` (edit): draw `spec.section` above the strip when it is non-null,
   in the `InspectorFootnote` type style, and keep the row identical when it is null.
6. `tools/filter/UndertoneMatrix.kt` (new): `enum class PhotoUndertone(val label: String) { Neutral,
   Amber, Gold, RoseGold, Bright, CoolRose }` and `undertoneMatrix(undertone, palette): ColorMatrix?`,
   built and lerped exactly as `FilterMatrix` builds and lerps a look — null for `Neutral`, null at
   zero palette, null when the product is the identity. Starting constants, to be tuned by hand
   against the corpus: Amber `channels(1.10, 1.02, 0.90)`, Gold `channels(1.08, 1.05, 0.86)`,
   RoseGold `channels(1.10, 0.98, 0.96)`, Bright `channels(1.06, 1.06, 1.06, offset = 6f)`, CoolRose
   `channels(1.04, 0.98, 1.06)`. `Neutral` is the default and is the *absence* of an undertone, the
   way `Original` is the absence of a look.
7. `tools/filter/FilterTool.kt` (edit):
   - manifest `title = "Styles"`, `summary = "Undertones, looks and a tone pad"`; **`id` unchanged**;
   - `Undertone = PreviewChoiceParam("undertone", PhotoUndertone.entries, Neutral, ::label, section =
     "Undertone")`, `Palette = FloatParam("palette", "Palette", 0f..100f, default = 100f, detent =
     100f, unit = "%")`, `Filter` gains `section = "Look"`, `Intensity` unchanged, and `Tone =
     PadParam("tone", "Tone & Colour", xLabel = "Colour", yLabel = "Tone", −100f..100f, −100f..100f,
     ParamPoint(0f, 0f))`;
   - `params` in panel order: `Undertone, Palette, Filter, Intensity, Tone`;
   - `stage()` multiplies the four contributions through the fold's own `multiply` and answers null
     when the product is the identity — the rule every tool follows, so opening the panel and closing
     it leaves the document untouched. `exposureMatrix(tone.y)` and `warmthMatrix(tone.x)` are
     imported from `tools.adjust`; they are pure functions of one float and there is no reason for a
     second copy of either.
8. `jvmTest/support/FixtureParams.kt` (edit): declare a `Pad` spec, so `EditorFixtureTest`'s "one of
   every `ParamSpec` type" assertion covers the ninth and the round-trip test proves a `ParamPoint`
   survives a document.
9. `ai/docs/features/pending/image-editor/image-editor.md` (edit): the §3.4 `ParamSpec` table gains
   its ninth row. The table is the schema's public face and a spec missing from it is a spec nobody
   knows to use.

## Files

`shared/src/commonMain/kotlin/com/woka/feature/editor/core/ParamSpecPad.kt` (new) ·
`.../core/ParamSpecChoice.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/ParamRowPad.kt` (new) ·
`.../shell/ParamRows.kt` (edit) · `.../shell/ParamRowPreviewChoice.kt` (edit) ·
`.../tools/filter/UndertoneMatrix.kt` (new) · `.../tools/filter/FilterTool.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/support/FixtureParams.kt` (edit)

## Done when

- `jvmTest/unit/UndertoneMatrixTest.kt` (new): `Neutral` answers null at any palette; every other
  undertone answers a non-identity matrix at 100 and null at 0; the matrix at 50 sits exactly halfway
  between the identity and the matrix at 100, entry by entry — the lerp `FilterMatrixTest` already
  asserts for looks.
- `jvmTest/unit/FilterToolTest.kt` (edit): an undertone **and** a look set together produce one
  stage whose matrix is the product of the two, in that order; the pad at the origin contributes
  nothing; a document with only the pad moved still produces a stage. A document written before this
  ticket — `filter` and `intensity` only — decodes unchanged and folds to the same matrix it did
  before, which is the assertion that the rename cost nobody their edits.
- `jvmTest/unit/EditorFixtureTest.kt`: passes with nine spec kinds rather than eight, without being
  edited beyond its `EVERY_SPEC_KIND` set.
- `jvmTest/ui/PadRowUiTest.kt` (new): a drag across the pad writes one operation per event and
  **one** history entry on release; dragging upward increases the y value, which is the inversion
  step 3 exists for; a double-click returns both axes to the default.
- `jvmTest/ui/PreviewChoiceRowUiTest.kt` (edit): a row with a `section` draws its label and a row
  without one is laid out exactly as before.
- By hand: open a photograph, pick Gold, pull Palette back to 60, add Dramatic at 80, drag the pad
  warm and bright, press Done and reopen — the panel comes back set exactly as it was left. Confirm
  the whole inspector still fits over the canvas on the smallest window the app allows.
- `./gradlew.bat :shared:build` green.
