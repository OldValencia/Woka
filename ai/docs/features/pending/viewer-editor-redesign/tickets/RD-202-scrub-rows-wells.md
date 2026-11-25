# RD-202 — Scrub rows, wells and segments

| | |
|---|---|
| **Order** | third, after RD-201's frame |
| **Depends on** | RD-201 |
| **Feature** | [Viewer and Editor Redesign](../viewer-editor-redesign.md) |

**Goal.** Draw the inspector's levels, sections and tabs in the prototype's form
(`#editor/photo` → Adjust): scrub rows grouped in wells, segmented controls and tabs with a
sliding knob, Auto-enhance above Adjust's rows and Reset adjustments under them.

**Why now.** RD-201 docked the inspector but its rows are still ME-155's steppers with a
popover — built for a card floating over the picture. RD-203's panels stand on these rows.

**One ticket, the whole block** (the author, 2026-11-25): every tool's levels change at once,
because every level is drawn by the same renderer — not Adjust first.

## The spec, measured (`base.css`, `editor.css`)

- **Scrub row** (`.control.scrub`): 42 dp, 12 dp aside, 10 dp gaps; 18 dp glyph (muted;
  `accentText` once moved), 13 sp name, ↺ (22 dp, on hover once moved), the number (44 dp,
  12 sp, muted → text once moved; hover `hoverStrong`, focus `well2` with an `accentLine`
  ring), unit 12 sp faint. The **whole row is the slider**: press sets the value at the
  pointer, drag follows. Foot: fill from default to value (2 dp, 3 dp dragging; accent once
  moved, faint at the default), a 4 × 8 thumb (12 dragging), the default's 1 × 5 tick (8 dp and
  accent while held there), a 2 dp `line` while hovered. Row hover `hover`.
- **Well** (`.well`): `well`, radius 12, a `line` between rows.
- **Segmented** (`.inspector .segmented`): `track`, 3 dp padding, a `knob` indicator that
  slides, selected text semibold; 36 dp tall.
- **Tabs** (`.tools`): the selected tab is the same sliding knob, not a fill — RD-201 drew
  `accentSoft`, which the prototype's `!important` overrides.
- **Auto-enhance** (`.well-button.auto-button`): full width, 40 dp, `well`, accent glyph, 13 sp
  semibold. **Reset adjustments** (`.text-action`): glyph and 12 sp muted, no plate.

## Steps

1. `ParamScrubRow.kt` (new): `ScrubRow(spec, params, writer)` per the spec; tap → `edit(settled)`,
   drag → `edit(settled = false)` then `settle()`; `snapToDetent` holds values within 1.5 % of
   the range at the default; right-click resets. `ParamScrubParts.kt` (new): `ScrubField`
   (settles on focus loss and Enter), `ScrubReset`. `ParamScrubTrack.kt` (new): the foot.
2. `ParamWell.kt` (new): `ScrubWell`, `WellButton`, `TextAction`.
3. `ParamRunGrouping`: levels join levels whatever their range; `isStrip` and its threshold go.
4. `ParamRowRuns`: a run of levels → `ParamStrip`. `ParamRowStrip`: sections (when more than
   one) over a `ScrubWell` of the open section — the chips and the single control go;
   `ParamStripChip.kt` deleted, `ParamStripChooser.kt` keeps `SectionControl`.
5. `FloatParamRow` → a well of one. `FloatControl` (the stepper) stays for compact lines —
   Markup's bars, RD-203.
6. `EditorToolPanel`: `footer(rows)` beside `header(rows)`. `ParamRows` draws header actions
   as `WellButton`s above the rows and footer actions as `TextAction`s under them; Adjust:
   header `Auto` ("Auto-enhance"), footer `ResetAll` ("Reset adjustments").
   `ParamHeaderAction.kt` deleted; `EditorToolSection` draws only the title.
7. `EditorRoomControls`: `RoomSegmented` (knob indicator, no hairline, 36 dp); used by sections
   and choice rows. `EditorTabBar`: a sliding knob indicator.
8. `ParamRows` passed 150 with the footer: the spec dispatcher moves to `ParamRow.kt`.

## Tests

`ui/ScrubRowUiTest` (new): press sets, drag settles once, the default holds, Enter settles a
typed number, right-click resets. `ui/AdjustPanelUiTest` rewritten for the well and the two
actions; `AdjustSectionUiTest` follows the rows. Deleted with what they tested:
`ParamStepperUiTest` (non-compact levels are no longer steppers), `AdjustPanelGestureUiTest`,
`ParamStripChipUiTest` (no chips).

## Done when

- Adjust, Styles' intensity and every other level draw as scrub rows in wells, both themes.
- `Grep "param-chip"` over the tree: no match.
- Every touched `.kt` ≤ 150 lines; anti-FQN clean; `./gradlew.bat :shared:build` green.
