# ME-161 — An adjustments panel that holds fifteen

| | |
|---|---|
| **Order** | 72 — before ME-142, which is what makes it necessary |
| **Depends on** | ME-155 (the compact bar), ME-111 (the tool it reshapes) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Draw Adjust as one wrapping bar of named value buttons instead of a stack of slider rows, so
the panel holds nine adjustments now and fifteen later without a scroll and without eating the picture.

**Why now.** It is arithmetic, and it blocks ME-142. From `EditorTokens.Inspector` — `rowHeight` 36 dp,
`rowGap` 2 dp, `padding` 10 dp vertical, a header of ~25 dp:

| rows | card height |
|---|---|
| 6 — today | 271 dp |
| 8 — after ME-142 | 349 dp |
| 15 — after ME-162 | 613 dp |

`main.kt` sets `window.minimumSize = Dimension(800, 580)`. At that height, minus the top bar and the
rail, the canvas region is around 460 dp: **eight rows already leave the photograph about 110 dp**, and
fifteen do not fit at all. The inspector has never scrolled — there is no `verticalScroll` or
`LazyColumn` in `EditorInspector`, `ParamRows` or `EditorBottomPanel` — so growing the stack is not an
option that merely looks bad, it is one that runs out of window.

## The existing bar is right, and labelless is what is wrong with it

ME-155 shipped `CompactRow`: a `FlowRow` of controls each collapsed to a button that reports its own
value and opens its own popover, confirmed by hand after five passes. It wraps, so it is exactly the
shape that turns fifteen controls into three or four lines instead of fifteen.

What it cannot do as it stands is say **which** control a button is. Its KDoc puts the reasoning
plainly — "there is nothing left for a label to explain: a swatch is a colour, `64px` is a size, `Aa`
in bold is a weight" — and that is true of every control Markup put in it. It is false of an
adjustment: `+12` identifies nothing, and the name is today a `ViewerTooltip` on hover
(`ParamValueStepper.kt:62`), which is not a way to read a panel.

**The rule is already in the data.** `FloatParam.unit` is `String?`, and `formatFloat` appends it:
Markup's sizes declare `px` and render `64px`; Adjust's six declare no unit and render `+12`. So a
compact control shows its name **when its value carries no unit**, which needs no new hook on
`EditorTool`, no flag on the manifest and no decision at the call site — and it leaves Markup's bar
exactly as it was confirmed.

## Steps

1. **`shell/ParamValueStepper.kt` (edit)** — a `name: String` already arrives and is spent on the
   tooltip. Add `showsName: Boolean = false`; when true the button draws the name before the value in
   `onScrimSecondary`, keeps the value in `onScrim`, and the tooltip stays for the truncated case.

2. **`shell/ParamRowScalars.kt` (edit)** — `FloatControl` passes `showsName = spec.unit == null`. One
   expression, in the one place that already holds both the spec and the formatter. **Not a parameter
   threaded from the tool**: a tool that had to ask for its own labels back is a tool writing UI.

3. **`shell/EditorTokens.kt` (edit)** — the named button is wider than a bare number, so
   `Inspector.barGap` and `CONTROL_GAP` are re-checked against a four-per-line bar, and a
   `compactNameGap` is added if the name and value need air between them. Numbers come from the mockup
   or from the by-hand pass, not from taste.

4. **`tools/adjust/AdjustTool.kt` (edit, 72 lines)** — `override fun lines(rows) = listOf(rows)`: all
   of Adjust's floats become one bar. This is the whole of the tool's part, which is the §3.4 claim
   holding — a tool asked for a layout and wrote none of it.

5. **`shell/ParamValuePanel.kt` (check, likely no edit)** — the popover that opens from a button
   already carries the spec's label above its slider. If it does not, it gains one here: the bar
   identifies a control, and the panel is where the control is actually set.

## Files

`shell/ParamValueStepper.kt` (edit), `shell/ParamRowScalars.kt` (edit), `shell/EditorTokens.kt` (edit),
`tools/adjust/AdjustTool.kt` (edit), `shell/ParamValuePanel.kt` (edit if step 5 finds it bare) under
`shared/src/jvmMain/kotlin/com/woka/feature/editor/`;
`ai/docs/features/pending/image-editor/image-editor.md` (edit — §9 row and the status table).

## Verification

- `jvmTest/ui/AdjustPanelUiTest.kt` (new), the ticket's real test:
  - Adjust draws **one** compact bar and **no** `DetentSlider` row — the panel's shape, asserted rather
    than eyeballed,
  - each of the six buttons shows its own name *and* its value, so a panel of six numbers cannot ship,
  - clicking one opens its popover and the other five do not open theirs;
- `jvmTest/ui/MarkupBarUiTest.kt`, `MarkupSizeRowUiTest.kt`, `MarkupSliderDragUiTest.kt` — **unchanged
  and still green, and this is the assertion that matters most**: Markup's controls carry units, so the
  rule in step 2 must leave every one of them labelless exactly as ME-155 confirmed;
- `jvmTest/ui/ParamRowUiTest.kt` — a `FloatParam` outside a compact bar still draws its slider row with
  its label column, untouched;
- `jvmTest/unit/AdjustMatrixTest.kt` — unchanged: this ticket changes no pixel;
- **by hand at 800×580**, the minimum window: the Adjust panel open, the photograph still the thing you
  are looking at.

## Done when

- Adjust draws as one wrapping bar; six adjustments occupy two lines rather than six rows;
- every button in it names itself without a hover;
- Markup's bar is byte-for-byte the shape ME-155 confirmed — no label appeared on any of its controls;
- the panel needs no scroll at eight adjustments, measured at the 800×580 minimum;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
