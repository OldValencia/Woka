# ME-155 — A compact control bar: every value behind its own button

| | |
|---|---|
| **Order** | 55 — **before ME-149**; the panel is used every session |
| **Depends on** | ME-151 (`ToggleSetParam`), ME-146 (`Fill`/`Corner`), ME-114 (`ColorPickerPopover`) |
| **Feature** | [Image Editor](../image-editor.md) |
| **Supersedes** | the two-drawer shape this ticket held until 2026-08-29 |

**Goal.** Markup's inspector becomes **two labelless lines of buttons**, and every value in the editor
— a colour, a level, a set of faces — is reached the same way: a compact control that *shows* what it
holds and opens its own popover to change it.

## How this shape was arrived at (2026-08-29)

The ticket previously asked for two drawers (`Text style`, `Fill style`) opened beside the card. That
was worked through and dropped, and the reasoning is worth keeping because it is what the shape below
answers:

- **`Color` is not a text property.** It paints all five shapes, so a drawer holding it would either
  move between levels with the pen, or make the arrow's panel worse for no gain.
- **A closed drawer must draw what it holds.** One swatch honestly reports a colour and says nothing
  about a weight or a size. `Fill` passes that test; a group called "Text style" does not.
- **Paired lines were tried and refused** (by hand, 2026-08-29): *buttons beside a slider does not
  work*. That is what kills ME-153's `PairedRowFrame`, and with it the last way to shorten the panel
  by merging two rows into one.

With merging gone, the only lever left is to shorten the **controls** rather than the rows. A slider
that is 300 dp wide becomes a 120 dp stepper that opens a slider; a nine-swatch strip becomes one
swatch that opens the strip. Then a whole tool's controls fit on one line, and the line needs no label
column at all — which is the panel that was asked for.

## The shape

```
 Markup ────────────────────────────────
   ↗    ▢    ○    ∿   [Aa]                  ← the pen, chips, no label
   [●▾]  − │ 64px ▾ │ +   [Aa▾]  [∅▾]       ← its style, no label
 ────────────────────────────────────────

 stroke pens drop the last two:
   [●▾]  − │ 6px ▾ │ +
```

Every control on the second line is the same idea: **a button that reports its value and opens a
popover to change it.**

| button | reports | opens |
|---|---|---|
| `[●▾]` | the pen's colour | the swatch palette, and the hand-picker behind it |
| `− │ 64px ▾ │ +` | the number, and steps it | a slider, a typed field and `Reset` |
| `[Aa▾]` | **the caption itself** — the glyphs drawn in the faces and colour currently set | the four face chips |
| `[∅▾]` | the plate's colour, or the "none" slash | the plate's swatches **and its `Corner` slider** |

**Right-click any of them resets that value to its default.** One gesture, every control, no exception
— which is what makes it worth learning once.

## The same control everywhere else

The stepper is not Markup's. **Every `FloatParam` row in the editor becomes it**, keeping its label
column where it has one:

```
 Adjust ─────────────────────────────────
   Exposure     − │  0  ▾ │ +
   Brightness   − │ +12 ▾ │ +
   Contrast     − │ −4  ▾ │ +
 ─────────────────────────────────────────
```

The design is `img.png`, supplied by hand: label, then a 1 dp rounded frame holding `−`, the value,
and `+`. The value is the button; the popover it opens carries the slider that used to be the row.

**The inline slider is therefore gone from the panel.** That is a real cost and it is accepted
knowingly: dragging brightness now takes a press first. What it buys is a panel of one shape — six
rows of the same 120 dp control instead of six 300 dp tracks — and one place where a value can be
typed, nudged, dragged and reset.

## Steps, in the order they compile

1. **`core/EditorTool.kt`** — `lines()` is **re-pointed, not removed**: it returns the visible rows a
   tool wants drawn as *one compact line*, defaulted to `emptyList()`. Whatever it does not claim
   falls to today's adjacency rules untouched. A second hook beside it, `folded(spec, rows)`, names
   the specs that ride inside another spec's popover rather than getting a control of their own —
   `Corner` inside `Fill`, and nothing else today.
2. **`shell/ParamReset.kt` (new)** — the right-click gesture and the one generic "write this spec's
   default" helper, so a `ParamSpec<*>` is captured in exactly one place.
3. **`shell/ParamValueStepper.kt` (new)** — `− │ value ▾ │ +`. The frame from `img.png`; the ends step
   and clamp, the middle opens.
4. **`shell/ParamValuePopover.kt` (new)** — the drop-up card and its `PopupPositionProvider`: a
   `DetentSlider`, a `BasicTextField` with the spec's unit, and `Reset`. It opens **upward**, because
   the panel it belongs to sits on the dock.
5. **`shell/ParamRowScalars.kt`** — `FloatParamRow` becomes label + stepper; `FloatControl` loses its
   `RowScope` and becomes the stepper alone, for the compact line to use.
6. **`shell/ParamColorCompact.kt` (new)** — the collapsed swatch button, and the popover behind it:
   the palette, the rainbow that switches the *same* popover to the hand-picker, and whatever
   `folded()` named. **One popover, two faces — never a popup over a popup.**
7. **`shell/ParamFacesCompact.kt` (new)** — `[Aa▾]`, drawn as live text in the current faces and ink,
   opening the four chips.
8. **`shell/ParamRowCompact.kt` (new)** — the line itself: a `when` over the spec types that have a
   compact form, laid out in a `Row` with no label column.
9. **`shell/ParamRowRuns.kt` / `ParamRows.kt`** — the `Pair` branch becomes the `Compact` branch;
   specs named by `folded()` are dropped from the line, because their popover already holds them.
10. **`shell/ParamRowFrame.kt`** — `PairedRowFrame` deleted.
11. **`tools/markup/MarkupRows.kt` / `MarkupTool.kt`** — `markupLines` returns the two compact lines;
    `markupFolded` puts `Corner` inside `Fill`; `wideInspector` goes back to false, because two lines
    of buttons do not need the dock's width.

## Verification

- `jvmTest/ui/ParamStepperUiTest.kt` (new) — `+`/`−` step and clamp; the middle opens a popover whose
  slider writes; the field accepts a typed number; `Reset` returns the detent; **right-click on the
  frame resets without opening anything**;
- `jvmTest/ui/MarkupBarUiTest.kt` (new) — the stroke pens draw two buttons and the text pen four;
  pressing `[∅▾]` shows `param-fill-swatch-0` **and** `param-corner`; right-click on it clears the
  plate;
- `jvmTest/ui/MarkupSliderDragUiTest.kt` — the drag moves inside the popover; it is still the test
  that says a knob follows the pointer, on the same two specs;
- `MarkupRowVisibilityUiTest`, `MarkupRestyleUiTest`, `MarkupFacesUiTest`, `MarkupSizeRowUiTest`,
  `ResizeInspectorUiTest`, `ParamRowUiTest` — updated to open a control before reaching into it, and
  green;
- `PadRowUiTest` green **unchanged**, which is what says `PadParam` was not dragged into this.

## Done when

- Markup is two lines and fits the card's 240 dp minimum;
- every `FloatParam` in the editor is a stepper, and its slider lives one press away;
- right-click resets a colour, a level, a face set and a plate;
- `PairedRowFrame` and its branch are gone, with no caller left behind;
- `./gradlew.bat :shared:build` green.

## What changed as this was built (2026-08-29)

**The icon collision was settled first.** `Aa` was the text *pen*'s glyph and is now the faces
button's sample, so the pen took SF Symbols' `character.textbox` — supplied by hand — and
`Icons.Static.ShapeText` points at it. Two adjacent controls drawing the same glyph is one control as
far as the eye is concerned.

**Three things were wrong on screen and none of them was the bar.** Reported by hand from the first
run, and each was a layout rule the collapse merely exposed:

- **Every card in the editor was 420 dp**, whatever it held. `InspectorHeader` was `fillMaxWidth()`
  inside a `widthIn(min, max)` column, so it filled the *ceiling* and the card measured the maximum
  forever — which is why "a card sizes to its content" was true of the code and false in the window.
  With the controls collapsed, a 420 dp card around 210 dp of buttons is most of the panel.
  `FloatParamRow`'s `weight(1f)` was the same fault one level in.
- **A popover filled the window.** A `Popup` is laid out against the window rather than against its
  anchor, so `widthIn(min = …)` around children that fill their width gave a 1100 px card with a
  slider across the whole editor. Each caller states a width now.
- **`[●▾]` and `[∅▾]` were the same button twice.** Ink and a plate are different things and were
  drawn as identical circles; `ColorParam.swatchShape` is the tool's own word for which it is, and a
  plate is drawn as a plate.

**The chevron came off the stepper.** It was there to announce the popover and read as a stray glyph
beside the digits — `0 ∨` — in a field the mockup draws as a plain number. The hover fill and the
hand cursor carry it, which is what the mockup already relied on.

**Two tag collisions, both found by the tests.** `ToggleSetControl` tagged its chips with the key the
compact button already carries, so every reach for `param-faces` failed on the ambiguity; the tag
comes from the caller now. And the stepper's readout lives inside a clickable node, which merges its
descendants — `param-scale-readout` exists only in the unmerged tree, which is how `ResizeInspectorUiTest`
asks for it.

**Two files were split at the size mandate**, both at seams that already existed: `MarkupType.kt`
takes the size and the faces out of `MarkupPen.kt` (a stroke's controls on one side, type's on the
other), and `ParamColorPalette.kt` takes the card out of `ParamColorCompact.kt` (the button that
reports a colour, and the card that changes it).

## The second pass: the panel slides out, it does not land (2026-08-29)

**A popover over the bar is the same complaint from the other side.** Anchored above its button, the
card covered the button — and the whole point of the collapse was that a control reports its value
where you left it. Every compact panel goes through `LocalInspectorAccessory` now, the seam ME-141
built for Styles' pad: **a sibling of the inspector, never a layer over it.** `Popup` is gone from the
editor's parameter panels entirely, and with it the anchoring, the dismissal and the nested-focus
problem that made the hand-picker have to grow the palette's own card instead of raising a second one.

**One slot means one open at a time, by construction.** `InspectorAccessory` gained an `owner`, and
every control asks it rather than keeping a boolean. Two controls with their own flags could both
believe they were open — the last one composed would take the card while the other's button went on
looking pressed. The pad moved onto the same slot, so it cannot collide with a colour either.

**The panel is drawn with the card, not beside it** — `GooeyPair` and `GooeyEffect`, against the
`gooey examples` the request named. The filter is exactly what those use and nothing more: blur the
plates, then push the blurred alpha through a steep ramp, so two shapes whose halos overlap come back
joined by a neck and two that do not come back as themselves. Three things make it work here:

- **only the plates are filtered.** The cards paint nothing of their own while the pair is open —
  `EditorInspector` fades its plate out over the same spring — and the controls draw on top, sharp.
  A filter over the panel itself would blur the swatches and the type;
- **Skia, not `BlurEffect`.** Compose can blur and cannot put a colour matrix after the blur, which is
  the half that does the work. `skiko` is already drawing every frame of this app;
- **the card does not move.** A centred `Row` would shift the inspector left by half the panel and
  take the pressed button out from under the pointer; a mirror spacer holds it still.

**And the panel was too tight and the card too loose.** `CONTROL_GAP` is 10 rather than the chip
row's 6 — chips are one control and read as a group, these are four that each open something — and
`Inspector.minWidth` is 200 rather than 240, which was leaving an empty column down the right of a
card whose content had just been collapsed to buttons. `Inspector.barGap` puts 5 dp above and below
a compact line: `rowGap`'s 2 is right for a labelled row that already carries its own height, and a
bar of 26 dp buttons has none to spare, so Markup's two lines came out stacked against each other.

## The third pass: the layout was a frame behind (2026-08-29)

**The pair lurched left, then right, and only then slid.** It was a `Row` whose left spacer mirrored
the panel's width — and that width came from `onPlaced`, which reports a frame after the fact. So the
panel took its full width the moment it was composed, the card was pushed left by half of it, and the
mirror caught up on the *next* frame and shoved the card back. Two involuntary movements before the
animation had started.

`PairLayout` is a `Layout` instead, and the fix is that a measured width is available **in the frame
that uses it**: the panel is measured, the pair's width is `card + (panel + gap) × progress`, and one
number decides both where the panel sits and how far the card glides. The card moves once, smoothly,
and does not come back.

The panel now travels from **entirely inside the card's footprint** to a gap beyond its right edge,
which is also what makes the goo read: at rest the two plates are one blob shaped like the card, and
the neck is what the blur leaves behind as they separate. Its content fades up over the last half of
the travel, and — deliberately — through `alpha` alone: a `graphicsLayer` translation is a draw-time
transform that placement never hears about, so the blob would have stayed behind while the card moved.

## The fourth pass: the panel described the pen, not the selection (2026-08-29)

**`PAIR_GAP` is 18.** At 10 the blur reached across it and the pair never came apart — one blob with
a waist, which is not what "beside the card" looks like. The neck belongs to the *travel*, where the
panel is still under the card; once it has arrived there is air.

**A control read the pen while it wrote to the selection**, reported by hand: a yellow arrow
reselected after the pen had been set to blue still showed blue, and a caption's `Bold` cleared while
nothing was selected came back unlit when the caption was reselected. `CanvasTool.restyled` had no
read-side twin, so half the loop was missing.

`CanvasTool.displayed(params, selectedId)` is that twin, and `ParamRows` renders through it while
`ParamWriter` goes on starting from the document. The two are different questions: a control reports
the shape under the cursor, and — because **every control sends an absolute value** — a stale reading
is a wrong write waiting to happen. Clicking the lit `Bold` chip of a caption whose face had been
cleared would have turned it *on*.

Markup overrides exactly the specs `restyleSelected` can write and no others. `Kind` is deliberately
not among them: it is the chooser the rows are keyed on, so following the selection would make the
panel change shape under a click on the picture — the thing that rule exists to prevent.

**The first test written for this did not bite.** It asserted the document, which was right the whole
time; the defect was in what the panel *said*. Verified by disabling the fix and watching the suite,
then rewritten against `param-width-readout` — the one reading that is a string on screen.

## The fifth pass: the panels are blocks, and the plate says what it is (2026-08-29)

**Both panels lay out in two lines.** A palette one line long was a card wider than the bar it hung
off, and it stranded a single circle on a second line when it did not fit — four to a line is a
block. The faces panel is two to a line for the same reason: weight and slant, then the two lines
type can wear. **`Fill` lost white** to make its own palette divide by four: it is the one palette
that also carries "none", so it was nine circles where the ink's is eight. White is still a plate,
reached through the picker rather than through a swatch.

**And it shipped wrong the first time, and was reported as done.** The widths were worked out
against a 20 dp card padding when `EditorTokens.Inspector.padding` is 14 *a side* — 28 — so the
palette held three and the chips held one, in a column. `MarkupPanelLinesUiTest` exists because of
that: it opens each panel and asserts that the first *n* controls share a top and the next one does
not, which is the only claim about a `FlowRow` that a reading of the arithmetic cannot substitute
for. Verified against the wrong constant before the right one: it fails there and passes here.

`Corner` restacked with it. On one line inside a 140 dp card it was a label, a track and a number
competing for about 120 dp, of which the track got thirty; the name and the reading share a line now
and the track has the whole width below them.

**"It is not clear what you are pressing."** The plate's button was a slashed square and a chevron,
which says nothing, in a bar that has no labels to say it for it. Two answers, and both are the rule
the rest of the bar already follows:

- **the button draws what it configures** — an `A` sits on the plate, in an ink measured against the
  plate's own luminance so it survives black, yellow and the "none" slash alike;
- **and every control in the bar answers a hover.** `Color`, `Fill`, `Style` and each level name
  themselves in a tooltip. A labelless bar is only legible if the names are one hover away, and
  `ViewerTooltip` is what the chip row already uses for exactly this.
