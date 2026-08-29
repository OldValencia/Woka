# ME-153 — Two controls on one line, and a wide Markup card

| | |
|---|---|
| **Order** | 55 — after ME-152; both came from the same pass by hand |
| **Depends on** | ME-151 (the `Style` row), ME-146 (the `Corner` row), ME-141 (`wideInspector`) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Let a tool declare that two of its specs share a line, and let Markup's card grow to the
dock's width — so six stacked rows become four.

**Why now.** Reported by hand 2026-08-29: "the Markup window comes out very big; with the background on
it is far too much." It is six full-width rows — `Shape`, `Color`, `Fill`, `Corner`, `Text size`,
`Style` — and the last two of those grew this week.

## The decisions

**Not one swatch behind a popover.** That was offered and refused the same day: collapsing `Color` and
`Fill` to a single swatch each hides two rows' worth of choice behind a click and **does not narrow the
card**, because the sliders below still set its width.

**Pairs, buttons then slider** (author, 2026-08-29). Two lines carry two controls each, in that order:

| line | left | right |
|---|---|---|
| `Fill` | the swatch strip | `Corner`, **still a slider** — explicitly not a stepper |
| `Text size` | the `Style` chips | the size slider |

**Each control keeps a visible name.** `ParamRowFrame` gives a line one label column, and a line with
two controls needs a second label inside it, or the panel says "Fill" over a slider that sets a corner
radius. That inner label is what this ticket adds to the row furniture.

**And the card grows, like Styles'** (author, 2026-08-29). `wideInspector` exists since ME-141 and its
doc says it is for a tool "whose content is a line that can use any width" — which is what Markup
becomes here. The reason it was refused for Markup before was swatches floating in empty space; paired
lines are the content that earns it.

## Steps

1. **`core/EditorTool.kt` (edit)** — a tool may declare which of its specs share a line:

   ```kotlin
   fun lines(rows: List<ParamSpec<*>>): List<List<ParamSpec<*>>> = rows.map(::listOf)
   ```

   Defaulted to one spec per line, which is every tool but Markup. **A function of the visible rows,
   not a static list**, because Markup's rows already come and go with the pen (`markupRows`) — a
   static pairing would leave a widowed slider on a line whose left half is hidden.

2. **`shell/ParamRowRuns.kt` (edit)** — `groupRuns` consults the tool's `lines` **before** its own
   adjacency rules, and a declared line of two goes to a new branch of `ParamRun`. The existing rules
   are untouched: consecutive actions still share a `FlowRow`, a pad still joins a preview strip.
   `ParamRows` passes the tool through, which it already holds.

3. **`shell/ParamRowFrame.kt` (edit)** — `ParamRowFrame` gains a `secondary: String?` slot, or a
   sibling `PairedRowFrame(left, right) { … }`. **Read it before choosing**: the file is 58 lines and
   owns the one label column every row lines up against, so whichever shape is taken must not give a
   paired line a different label width from a plain one.

4. **`tools/markup/MarkupRows.kt` (edit)** — `markupRows` gains the pairing, expressed against the rows
   it has just decided to show: `Fill` with `Corner` when both are visible, `TextSize` with `Faces`
   when both are. With no plate chosen `Corner` is hidden and `Fill` stands alone, which is the case
   step 1's signature exists for.

5. **`tools/markup/MarkupTool.kt` (edit)** — `override val wideInspector = true`, and `lines` delegating
   to `markupRows`' companion the way `rows` already delegates.

## Files

`core/EditorTool.kt` (edit) under `shared/src/commonMain/kotlin/com/woka/feature/editor/`;
`shell/ParamRowRuns.kt` (edit), `shell/ParamRows.kt` (edit), `shell/ParamRowFrame.kt` (edit),
`tools/markup/MarkupRows.kt` (edit), `tools/markup/MarkupTool.kt` (edit) under
`shared/src/jvmMain/kotlin/com/woka/feature/editor/`.

## Verification

- `jvmTest/unit/ParamRowLinesTest.kt` (new) — `groupRuns` honours a declared pair; a declared pair whose
  second spec is not among the visible rows leaves the first standing alone; a tool declaring nothing
  gets exactly the runs it gets today, asserted against `FixtureTool`;
- `jvmTest/ui/MarkupRowVisibilityUiTest.kt` — with a plate chosen, `param-fill` and `param-corner` are
  both displayed and their rows have the **same top**, which is the assertion that says "one line";
  with no plate, `param-corner` is still absent;
- `jvmTest/ui/ParamRowUiTest.kt` — unchanged and green, which is what says the default path did not
  move;
- by hand: the card is no wider than the dock and no taller than four lines with a plate chosen.

## Done when

- Markup shows four lines instead of six, with `Corner` beside `Fill` and the size slider beside the
  four `Style` chips;
- every control on a paired line still has a name on screen;
- `Corner` is a slider;
- the card may grow to the dock's width and no further;
- no other tool's panel changes;
- `./gradlew.bat :shared:build` green.

## What changed as this was built (2026-08-29)

**Only one pair shipped, and the arithmetic is why.** `Fill` and `Corner` were tried together and the
screenshot showed four swatches simply gone: the dock is ~576 dp, the plate's row already spends 104 on
its label and 288 on nine swatches, and of the 156 left the readout takes 48 — so the slider was offered
~55 dp and the row gave way at the swatches instead. `Faces` costs 122, leaving ~200, and pairs with
`TextSize` comfortably. Six rows became five, not four. **ME-155 is the answer to the size question**;
this ticket is the mechanism it is built on.

**Two defects were found by hand and fixed here.**

- The left half of a pair was `Modifier.weight(1f)`, so it was the half that compressed — and a swatch
  row neither wraps nor scrolls, it lays its swatches out at zero width and loses them silently. Buttons
  now take their natural size and only the slider is weighted.
- **`wideInspector` was a ceiling, not a licence.** It set the card's max to `railWidth`, so a *narrow*
  dock shrank the card: with one tool in the rail the card measured 212 dp, every swatch laid out at
  zero, and ten UI tests failed reporting that clicking a control did nothing. It is `maxOf(railWidth,
  maxWidth)` now — it may grow to the dock, never below the card's own maximum.
