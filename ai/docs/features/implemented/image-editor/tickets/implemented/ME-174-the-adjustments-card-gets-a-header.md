# ME-174 — The adjustments card gets a header, and its value row turns over

| | |
|---|---|
| **Order** | straight after ME-167, which put Auto in the card as a plain row |
| **Depends on** | ME-167, ME-161, ME-164 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Give a tool's inspector a **header** — actions drawn at the top right of the card
rather than as rows — put Auto and a new Reset All in it, and turn the value row over so
the number sits above the slider that sets it.

**Why now.** Auto shipped as an `ActionParamRow`, which is a full-width button in the
stack of controls: it reads as a thirteenth adjustment rather than as the thing that sets
the other twelve. Asked for by hand the same day (2026-09-02), along with the two changes
that come with it — a Reset All beside it, and a per-parameter Reset that is an icon
where the number is rather than a word at the far end of a row.

## What the card becomes

```
  [ Light | Colour | Detail ]                              ✨   ⇄↺     ← header
  ( Exposure )( Brightness )( Contrast )( Highlights ) …           ← the chooser
                    −40 ⟲                                         ← value, then reset
  ────────────●──────────────────────────                          ← the slider
```

The two header actions sit on the section control's own row, which is empty to the right
of it today.

## Steps
1. `EditorToolPanel` gains `fun header(rows: List<ParamSpec<*>>): List<ParamSpec<*>> = emptyList()` — "these specs of mine are drawn in the card's header, not as rows". The
   **same shape as `folded`**, which already says "this spec rides inside another's
   popover", and for the same reason: only the tool knows that one of its controls is
   about the others. The shell drops whatever is named, exactly as it drops `folded`.
2. `ParamRows` removes the header specs from `visible` alongside `ridden`, and hands them
   to the strip.
3. `ParamRowStrip` draws them at the end of the `SectionControl` row, as icon buttons with
   tooltips. **A tooltip is not decoration here** — an icon-only control has nowhere else
   to say what it does, which is the rule `EditorPeekButton` already follows.
4. `AdjustParams` gains `ResetAll = ActionParam("resetAll", "Reset all")`. It needs no
   maths: `ParamWriter` seeds an action from `tool.defaults()`, so pressing it *is* the
   reset, and `adjustStage` answers null for a neutral set.
5. `AdjustTool.header` returns `listOf(Auto, ResetAll)`; `resolve` keeps answering
   `autoAdjustments` for Auto and null for everything else.
6. **Icons.** Auto takes `magic.wand`, which is in the set today and worn by **Styles** —
   so Styles moves to `paintpalette`. A wand means "make this better on its own", which is
   Auto; Styles offers a look to choose from and is not that. `EditorRail`'s own KDoc says
   two tools sharing a glyph is a mistake it has made twice, and this keeps that true.
   Reset All takes `slider.horizontal.2.arrow.trianglehead.counterclockwise` — sliders
   inside a returning arrow, which is literally what it does.
7. **The value row turns over.** `ParamValueControls` draws the slider first and the field
   second; it becomes field first, slider second, with the field **centred** and the reset
   beside it. The number is what the eye checks and it should not be below the control that
   changed it.
8. The per-parameter `ResetButton` becomes an icon — `arrow.counterclockwise` — with a
   "Reset" tooltip, in place of the word at the row's far end.

## Files
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorToolPanel.kt` (edit)
`shared/src/commonMain/kotlin/com/woka/core/ui/icons/Icons.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/filter/FilterTool.kt` (edit — one line)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/adjust/AdjustParams.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/adjust/AdjustTool.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/ParamRows.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/ParamRowStrip.kt` (edit, 64 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/ParamValuePanel.kt` (edit, 98 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/ParamValueFields.kt` (edit, 125 lines)

Three SVGs land in `composeResources/drawable/`, exported from SF Symbols by the author:
`paintpalette.svg`, `arrow_counterclockwise.svg`,
`slider_horizontal_2_arrow_trianglehead_counterclockwise.svg`.

## Done when
- `jvmTest/unit/`: a tool declaring `header` has those specs absent from what `ParamRows`
  would draw as rows, and present nowhere twice;
- `jvmTest/unit/`: pressing Reset All leaves every adjustment on its detent and
  `AdjustTool.stage` answering null;
- no tool but Adjust declares a header, and Transform's four actions still draw as rows —
  the header is a declaration, never a rule about `ActionParam`;
- confirmed by hand: this is a layout asked for by eye and only an eye can accept it;
- `./gradlew.bat :shared:build` green.
