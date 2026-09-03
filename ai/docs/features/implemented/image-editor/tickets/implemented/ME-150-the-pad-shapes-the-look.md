# ME-150 — The pad shapes the look, not the photograph

| | |
|---|---|
| **Order** | 59 — after ME-141, whose panel this re-means |
| **Depends on** | ME-141 (the pad, the two strips and `styleMatrix`) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Make Styles' pad shape the **chosen look** — `x` its vividness, `y` its brightness, applied
to the look matrix before `Intensity` lerps it toward the identity — and stop it editing the
photograph.

**Why now.** Asked for 2026-08-28, straight after ME-141 was exercised by hand. The pad is meant to
shape the filter, the way iOS Photos does, and today it shapes the picture: `styleMatrix` multiplies
in `exposureMatrix(tone.y)` and `warmthMatrix(tone.x)` whatever look is chosen, including `Original`.

Beyond the ask, those two functions are **imported from `tools/adjust`**, where Exposure and Warmth
are already sliders of their own. Move the pad, open Adjust, and Exposure reads 0 over a visibly
brighter picture — two controls over one effect, neither aware of the other. The pad as built
duplicates a shipped tool.

## The reading that was chosen, and the one that was not

The backlog named two. **B is taken** — the axes become the look's own shape.

**A — the pad is a per-look preset** (each `PhotoFilter` carries a `ParamPoint`, chosen look seeds
the pad) does not answer the ask: under A the pad still edits the photograph, it merely starts
somewhere different per look. The duplication with Adjust survives it, and it needs a rule for when
*not* to re-seed or a second look throws away a tone dialled by hand.

**B settles three things at once.** The Adjust duplication disappears, because these numbers exist
only inside the look and never reach the picture. "Which of the two strips does the pad belong to"
stops needing an answer — it shapes the look, and the undertone keeps `Palette`. And at `Original`
the pad does nothing, because there is no look to shape, which is the same truth the strip already
tells when it hides a strength on `Neutral`.

A stays in the backlog as a possible follow-on: per-look starting points are sensible **once B has
defined what the axes mean**. Doing A first would define presets for axes we would then redefine.

**The pad row stays visible at `Original`, and inert.** Hiding it was tried by hand under ME-141 and
reversed: the panel's width changed the moment a look was picked, and a control that appears is a
control nobody knew was there. Inert-and-shown is the lesser of the two; if it reads as broken on
screen, dimming it is a follow-on, not this ticket.

## Steps

1. **`tools/filter/FilterMatrix.kt` (edit)** — a private shape for the look:

   ```kotlin
   private fun lookShape(tone: ParamPoint): ColorMatrix?
   ```

   Null at the origin, so an untouched pad costs nothing. Otherwise the product of
   `ColorMatrix().apply { setToSaturation(1f + tone.x / 100f) }` and
   `channels(1f, 1f, 1f, offset = tone.y / 100f * BRIGHTNESS)`, with
   `private const val BRIGHTNESS = 64f` beside `MID` — offsets here are in 0..255, so a quarter of
   the range at either end. `x = -100` is fully desaturated and `x = +100` is twice the saturation.
   Reuses the file's own `channels`; it does **not** import anything from `tools/adjust`.

   The numbers are a first pass and are to be judged by eye on a real photograph — that is what the
   by-hand check in *Done when* is for.

2. **`tools/filter/FilterMatrix.kt` (edit)** — `filterMatrix(filter: PhotoFilter, intensity: Float)`
   gains a third parameter, `tone: ParamPoint`. Order is the backlog's:
   `lerp(identity, look × shape, intensity)` — the shape multiplies the **full** look matrix, and the
   intensity lerp is applied to the product. So `Intensity = 0` still returns null with the pad
   anywhere, and a shaped look fades out exactly as an unshaped one does.

3. **`tools/filter/StyleMatrix.kt` (edit)** — the contributions become **two**:
   `undertoneMatrix(...)` and `filterMatrix(params[FilterTool.Filter], params[FilterTool.Intensity],
   params[FilterTool.Tone])`. Delete the `exposureMatrix(tone.y)` and `warmthMatrix(tone.x)` entries
   **and their imports** — the import lines are the mechanical proof the duplication is gone. Its
   KDoc's paragraph about borrowing from `tools.adjust` goes with them.

4. **`tools/filter/FilterTool.kt` (edit)** — the pad's words follow its meaning:
   `label = "Look shape"`, `xLabel = "Vividness"`, `yLabel = "Brightness"`. Update the one-line KDoc
   above `Tone` — it currently says "Tone is exposure and Colour is warmth" — to say the axes shape
   the chosen look and do nothing at `Original`. The key stays `"tone"`: it is a wire key in every
   saved document, and the values it holds are still a `ParamPoint` in the same range.

## Files

`tools/filter/FilterMatrix.kt` (edit), `tools/filter/StyleMatrix.kt` (edit),
`tools/filter/FilterTool.kt` (edit) — all under
`shared/src/jvmMain/kotlin/com/woka/feature/editor/`.

## Verification

- `jvmTest/unit/FilterToolTest.kt` (edit) — the four claims, all on `styleMatrix`:
  - **the pad is inert at `Original`** — `styleMatrix` returns the same result for a document with
    `Tone` at the origin and one with `Tone` at a corner, both on `PhotoFilter.Original`. This is the
    test that fails today, and it is the ticket in one line;
  - `x` at `+100` on a look produces a matrix differing from the same look at the origin, and `-100`
    produces a grey one — asserted through `setToSaturation`'s own result, not by eye;
  - `y` at `-100` lowers the R/G/B offsets of the look's matrix;
  - `Intensity = 0` returns null with the pad at a corner, so the shape cannot outlive the look.
- `jvmTest/ui/PadRowUiTest.kt` (edit) — the row draws the new axis labels.
- By hand, which is the only thing that can judge the constants: pick `Vivid`, move the pad to each
  corner, and confirm the look strengthens and dims rather than the photograph; then open Adjust and
  confirm Exposure and Warmth still read 0.
- `./gradlew.bat :shared:build` green.

## Done when

- moving the pad on `Original` changes nothing on screen and adds no matrix;
- moving the pad on a look changes that look, and Adjust's Exposure and Warmth still read 0;
- `StyleMatrix.kt` imports nothing from `tools.adjust`;
- the pad's three labels name the look's shape, and the `"tone"` wire key is unchanged, so documents
  written under ME-141 still open;
- `./gradlew.bat :shared:build` green.
