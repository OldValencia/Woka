# ME-155 — Two style drawers instead of four swatch rows

| | |
|---|---|
| **Order** | 55 — **before ME-149**; the panel is used every session and ME-149 is not yet reachable |
| **Depends on** | ME-153 (`EditorTool.lines`, `PairedRowFrame`), ME-141 (`InspectorAccessory`) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Collapse Markup's colour rows into **one swatch each**, and put everything that styles them
into a narrow popover that slides out beside the card.

**Why now.** Asked for 2026-08-29, after ME-153 shortened the panel by one row and no more. Measured
the same day: `Color` and `Fill` are 288 dp each — two rows of nine circles — and they are what the
card's width and half its height are actually spent on. Nothing pairs beside them because nothing
fits; ME-153 records the arithmetic.

## The shape asked for

Three rows, and two of them open a drawer:

```
Shape        [↗] [▢] [○] [∿] [Aa]
Text style   ●                     →   Color · Style · Text size
Fill style   ⃠                     →   Fill · Corner
```

- pressing a drawer's swatch opens a **narrow** popover to the right of the card, carrying that
  group's own rows in full — sliders as sliders, chips as chips;
- the row itself shows the one thing worth seeing closed: the colour currently set;
- **`Corner` stays a slider** and `Text size` stays a slider — the drawer is where they get the room
  ME-153 could not find for them.

**Not a second colour picker.** `ColorPickerPopover` is what a *swatch* opens; this is what a *group*
opens, and the two nest: the drawer holds a `Color` row, and that row's rainbow circle still opens the
picker.

## What it is built from

**`InspectorAccessory` already does this**, and is the reason this is cheap: `EditorBottomPanel` owns
one, `LocalInspectorAccessory` publishes it, and Styles' pad puts a panel beside the card through it
(ME-141). What is new is that it is **narrow** — Styles' is wide because a pad is square, and this one
holds rows.

`ACCESSORY_ALLOWANCE` is a single 160 dp constant taken off the card's ceiling whenever an accessory is
open; a second width means it stops being one number, and that is the one shell change to plan for.

## Steps, in the order they compile

1. **The declaration.** A tool says which of its specs live in a drawer and what that drawer is called
   — the same shape `lines(rows)` already has, and for the same reason: Markup's rows come and go with
   the pen, so the grouping must be a function of the visible rows and not a static map.
2. **The row.** One `ParamRowFrame` per drawer: the label, a `ColorSwatch` showing the group's colour
   spec, and the press that opens the accessory. Reuses `ColorSwatch` and `swatchFill` whole — a
   drawer closed is exactly a swatch.
3. **The drawer.** A card of `EditorTokens.Inspector`'s own furniture rendering `ParamRows` for the
   group's specs, published through `LocalInspectorAccessory`. One open at a time.
4. **`markupRows`/`markupLines` collapse** to `Shape`, `Text style`, `Fill style` — and the visibility
   rules ME-146 and ME-151 wrote move *inside* the drawers, where `Corner` still needs a plate to
   round.

## Verification

- `jvmTest/ui/MarkupDrawerUiTest.kt` (new) — the closed panel draws three rows and no slider; pressing
  `Text style` shows `param-color`, `param-faces` and `param-textSize`; pressing `Fill style` closes
  the first and shows `param-fill`; a value set inside a drawer reaches the document;
- `jvmTest/ui/MarkupRowVisibilityUiTest.kt` — `Corner` still appears only once a plate is chosen, now
  inside the fill drawer;
- `PadRowUiTest` green unchanged, which is what says the shared accessory still serves Styles.

## Done when

- Markup's card is three rows and no wider than the dock;
- each drawer opens beside the card, narrow, and closes when the other opens;
- every control the panel had is still reachable and still writes what it wrote;
- `./gradlew.bat :shared:build` green.
