# RD-203 — Styles, Transform and Markup panels

| | |
|---|---|
| **Order** | fourth, after RD-202's rows |
| **Depends on** | RD-202 |
| **Feature** | [Viewer and Editor Redesign](../viewer-editor-redesign.md) |

**Goal.** Draw the three photo tools' panels in the prototype's form with the app's own content
(the author, 2026-11-25: "как в приложении, но в форме прототипа"), the prototype first.

**Why now.** RD-202 gave every level a scrub row; what was left were ME-141's preview strip (two
previews visible in the 294 dp inspector), ME-155's compact Markup bars and popovers, and a
Transform tab drawn as chips and text buttons.

**Decided with the author (2026-11-25).** Straighten is its own block, RD-205. Transform opens
the crop frame on the whole picture so Done right after crops nothing. Styles' groups are one row
of four tiles each with ← / →. Styles keeps the app's content: Undertone and Look, the two
strengths and the look's shape (Vividness, Brightness) as scrub rows.

## Prototype (`woka/`)

- Crop opens at the whole picture (`app.js`, `experience.js`), not 8 % in.
- Styles: Undertone and Look rows of four tiles with arrows (`stylePager`), the well Palette,
  Intensity, Vividness, Brightness; both groups drive the picture.
- Transform: four well buttons, Rotate left / right, Flip horizontal / vertical.
- Markup: the pen as icons; "Click the picture to add text", and a click on the picture places
  it; Colour swatches; Text size or Width; Style B / I / U / S; Background swatches and Corner
  once a plate is chosen; Timing on a film. Font, opacity, the Add text button and the panel's
  text field are gone — the app has none of them.

## App

- **Styles** (`StylePager.kt`, `StyleTile.kt`): a row per preview spec, its section name, ← four
  tiles →, a hover previewing on the canvas (unsettled, cancelled on leaving), a click settling.
  `FilterTool.rows` shows Palette and Intensity; the pad is two scrub rows over the same stored
  point (`ScrubLevel`, `PadParam.xIcon/yIcon`). The strip, its strength sliders and the pad's
  popover are deleted.
- **Transform**: `ChoiceParam.tiles` → `ChoiceTiles` (Crop's Shape, with silhouettes); a choice's
  name stands above it; `SegmentedControl` takes glyphs (Orientation); action runs are
  `ActionGrid`s of well buttons; `TransformTool.footnote` gives the size. No tool titles inside
  the tab.
- **Markup**: no compact bars. The pen is a `GlyphSegmented`; colours and faces are headed rows;
  sizes and timing scrub rows; Corner its own row; a right-click resets a colour row; a footnote
  says where text is placed. The compact machinery — `lines`, `folded`, `wideInspector`, the
  stepper, the value and colour popovers, the accessory slot — had no other user and is deleted.

## Tests

Rewritten for what replaced the old controls: `PreviewChoiceRowUiTest` (four tiles, paging, the
hover preview), `PreviewChoiceWriteUiTest`, `StyleWellUiTest` (new: the pad's axes),
`EditorInspectorPanelUiTest`, `MarkupBarUiTest`, `MarkupSelectionReadbackUiTest`, the Markup
helpers (`tapSizeRow`, drag on the row). Deleted with their controls: `PadRowUiTest`,
`MarkupPanelLinesUiTest`, `MarkupSizingTest`'s compact-bar case.

## Not built

- `Reset` beside the size in Transform: the app has no action that clears crop and turns
  together; `Revert to original…` covers the file.

## Done when

- Styles, Transform and Markup look as the prototype draws them, both themes; Transform's Done
  right after opening writes nothing.
- Every touched `.kt` ≤ 150 lines; anti-FQN clean; `./gradlew.bat :shared:build` green.

## After the author's review — 2026-11-25

- The editor no longer slides in from the right and out to the right (`NativeMediaEditor`): the
  room and the inspector arriving around the picture are the only movement.
- Styles' pages slide over in the direction of the arrow (`AnimatedContent`).
- Rotate and flip play on the canvas (`CanvasMotion`, `EditorToolPanel.canvasMotion`,
  `CanvasMotionState`): the new picture is drawn turned back and runs to rest in 320 ms; a
  quarter turn scales from the old picture's size, a flip passes through its edge.
- Styles shows Palette only with an undertone, Intensity and the shape only with a look; at
  Neutral and Original the panel is the two rows of tiles. The footnote that explained the inert
  pad is gone with it. Prototype likewise.
- 16 dp between a tool's groups (`EditorTokens.Panel.groupGap`).
- Transform's tab takes the crop glyph, as the prototype's does.
- Tests: `TransformMotionTest` (new), `StyleWellUiTest` for the conditional rows.
- **No blink between the viewer and the editor** (the author, 2026-11-25). Entering: the editor
  decoded its own copy from disk and drew an empty room until it arrived — it now draws the
  viewer's picture from `ImageBitmapCache` meanwhile (already the file with its stored edit), and
  the canvas knows its size in the first pass (`BoxWithConstraints`, body in `EditorCanvasBody.kt`).
  Leaving after Done: the save evicts the file's cache and the viewer stepped down to the 400 px
  preview before the full decode — a reload of the picture on screen now keeps it until the new
  one lands (`MediaViewerStillLoad.kt`, split from `MediaViewerScreenEffects.kt`).
