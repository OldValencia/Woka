# ME-161 — An adjustments panel that holds fifteen

| | |
|---|---|
| **Order** | 72 — before ME-142, which is what makes it necessary |
| **Depends on** | ME-111 (the tool it reshapes), ME-141 (the scrolling strip it reuses) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Draw a long run of like sliders as **one chooser over one control** — the iOS Photos model
the feature doc names as this editor's UX reference — so the panel holds eight adjustments now and
fifteen later without a scroll and without eating the picture.

**Why now.** It is arithmetic, and it blocks ME-142. From `EditorTokens.Inspector` — `rowHeight` 36 dp,
`rowGap` 2 dp, `padding` 10 dp vertical, a header of ~25 dp:

| rows | card height |
|---|---|
| 6 — today | 271 dp |
| 8 — after ME-142 | 349 dp |
| 15 — after ME-162 | 613 dp |

`main.kt` sets `window.minimumSize = Dimension(800, 580)`. At that height, minus the top bar and the
rail, the canvas region is around 460 dp: **eight rows already leave the photograph about 110 dp**, and
fifteen do not fit at all. The inspector has never scrolled — no `verticalScroll` or `LazyColumn` in
`EditorInspector`, `ParamRows` or `EditorBottomPanel` — so growing the stack is not an option that
merely looks bad, it is one that runs out of window.

## Three shapes were measured, and the obvious one lost

**A named compact bar** was this ticket's first design and it does not survive the arithmetic. ME-155's
`CompactRow` wraps instead of growing, which is right, but it collapses every control to a labelless
button — correct for Markup, where a swatch is a colour and `64px` is a size, and wrong here, where
`+12` names nothing and an adjustment is told apart only by its name. Put the name back in and the
control is ~215 dp wide (the longest, "Noise Reduction", is ~95 dp before the 114 dp stepper frame),
which fits **one per line** in a 392 dp card. Fifteen would be fifteen lines: worse than the stack it
replaces.

**Chips of name and value**, no stepper ends, three per line: ~215 dp for fifteen. It fits, and it
grows with every parameter added.

**A chooser over one control** — a scrolling row of name-and-value chips, the selected one's slider,
field and `Reset` under it. About **115 dp, and it does not grow at all**, which is what makes ME-162's
seven more cost nothing. It is also the shape the feature doc already commits to.

## Derived from the schema, not declared by the tool

There is no new hook on `EditorTool`. A run of **four or more adjacent `FloatParam`s sharing one
range** is a chooser, decided by `joins`/`isStrip` beside the rules that already gather actions into a
group and preview specs into a line. §3.4's claim is that a tool contributes a schema and is given its
layout; a `strips()` hook next to `lines()` would have had Adjust ask for a shape instead.

Four is a measurement: a labelled row is 36 dp and the chooser's own furniture is about three rows'
worth, so below four the strip trades a click for nothing. Checked 2026-09-01 — Adjust is the only
tool this catches: Filter declares two floats, Resize one, and Markup's are claimed by the compact
line it declares.

## Steps

1. **`shell/ParamValuePanel.kt` (edit)** — the slider, the field and `Reset` split out of the popover
   into `ParamValueControls(spec, params, writer)`, which emits no layout of its own so the popover's
   spacing is unchanged and the strip can place the same three inline. A strip that showed fifteen
   adjustments and then opened a popover to set one would put a card over a card.

2. **`shell/ParamRowStrip.kt` (new)** — `ParamStrip`: the selection (`remember`ed on the run's keys, so
   a tool whose rows come and go cannot keep a selection naming a spec that is gone), the chooser row,
   and `ParamValueControls` for the selected spec. The chooser reuses `StripScrollButton` and
   `wheelScrollsStrip` from `ParamRowStripScroll` — a desktop wheel reports on `y` and a
   `horizontalScroll` reads `x`.

3. **`shell/ParamStripChip.kt` (new, step 2's split)** — one chip: name, value, and **the value's
   colour as the "which of these have I touched" affordance** — dim at the detent, bright when moved.
   Without it a chooser showing one control at a time hides every edit but the open one.

4. **`shell/ParamRunGrouping.kt` (new, step 5's split)** and **`shell/ParamRowRuns.kt` (edit)** —
   `joins` gains the like-float clause, `isStrip` the threshold, and `ParamRun` two branches: the strip,
   and — before the wrapping group — a plain stack for a float run *below* the threshold, so Filter's
   two sliders stay two labelled rows.

5. **Two splits, both forced.** `ParamRowRuns.kt` reached 166 lines and `ParamRowStrip.kt` 158, so the
   grouping functions left the first (they compose nothing, which is the seam) and the chip left the
   second, as `ParamRowPreviewOption` sits beside `ParamRowPreviewChoice`.

6. **Two gestures on the chooser, asked for by hand 2026-09-01.** The strip is **dragged with the
   primary button**: `horizontalScroll` alone answers the wheel and nothing else on desktop, where
   `scrollable` reads drags as touch, so a mouse held down and moved across the strip moved nothing.
   `draggable` claims the gesture only past the touch slop, which is what lets a chip's own
   `clickable` still take a press that does not travel — a drag must not select and a click must
   still select. And **the secondary button resets a chip** through the editor's existing
   `resetOnRightClick`, which reaches an adjustment *without opening it* — the thing a chooser needs
   and a stack of rows never did.

   **An earlier reading had the right button doing both** and cannot work: the strip is made of
   chips, so every attempt to drag would have begun by resetting whatever it started on.

7. **The readout has a fixed width**, `Inspector.readoutWidth`, reported by hand the same day. `0`,
   `+12` and `−100` are three widths, so a readout that sized itself made every chip to the right of
   the one being dragged jump on each frame of the drag. The same defect `ParamValueStepper` avoids
   with `VALUE_WIDTH`.

**No tool changed, and no schema.** `AdjustTool` is untouched, which is the §3.4 claim holding.

## Files

`shell/ParamValuePanel.kt` (edit), `shell/ParamRowRuns.kt` (edit), `shell/ParamRunGrouping.kt` (new),
`shell/ParamRowStrip.kt` (new), `shell/ParamStripChip.kt` (new) under
`shared/src/jvmMain/kotlin/com/woka/feature/editor/`;
`ai/docs/features/pending/image-editor/image-editor.md` (edit — a §9 row and the status table).

## Verification

- `jvmTest/ui/AdjustPanelGestureUiTest.kt` (new) — a press that *travels* leaves the selection where
  it was, so a drag never selects; a right-click on a chip puts that adjustment back. The mouse is
  moved onto the node before the press: without `moveTo(center)` the injected press lands where the
  node has no pointer over it and the handler never sees it — measured 2026-09-01, and production
  code was not the cause;
- `jvmTest/support/ParamPanel.kt` (new) — the panel **collecting** the document. `ParamRows` takes a
  document by value, so a scene passing `document.value` once draws a panel nothing can move and
  every write below is silently invisible;
- `jvmTest/ui/AdjustPanelUiTest.kt` (new) — the shape is the subject, so the assertions are about what
  is drawn: six adjustments produce **one** chooser and **one** slider (`param-contrast-slider` must
  not exist while Exposure is selected); clicking a chip moves the control to it; a chip carries its
  own readout, found with `useUnmergedTree` because a clickable chip merges its children's semantics;
- the same file, the threshold from the other side — `FixtureTool` declares one float among specs of
  every other kind and must still draw a labelled row, which is what says this did not quietly reshape
  every tool with a slider;
- `jvmTest/ui/MarkupBarUiTest.kt`, `MarkupSizeRowUiTest.kt`, `MarkupSliderDragUiTest.kt`,
  `ParamRowUiTest.kt`, `PreviewChoiceRowUiTest.kt` — unchanged and still green, which is what says the
  new `joins` clause reshaped nothing that was already grouped;
- `jvmTest/unit/AdjustMatrixTest.kt` — unchanged: this ticket changes no pixel of the picture;
- **by hand at 800×580**, the minimum window: the Adjust panel open, the photograph still the thing you
  are looking at.

## Done when

- Adjust draws a chooser and one control; six adjustments occupy about 115 dp rather than 271;
- every chip names itself and reports its value, and a moved adjustment reads brighter than a resting
  one;
- a run of fewer than four sliders is unchanged;
- Markup's compact bar is the shape ME-155 confirmed — no label appeared on any of its controls;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
