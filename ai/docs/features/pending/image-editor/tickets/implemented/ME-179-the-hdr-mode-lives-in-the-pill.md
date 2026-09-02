# ME-179 — The HDR mode lives in the pill

| | |
|---|---|
| **Order** | immediately after ME-119, which put the control in the wrong place |
| **Depends on** | ME-119, ME-177 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Move the HDR view mode out of Settings and into the top bar's pill, in the viewer and in
the editor, as a dropdown.

**Why now.** ME-119 shipped it as a `SegmentedControl` in the viewer settings panel and the author's
verdict was immediate: *"это всё-таки далековато расположено"*. That is right, and the reason is
worth writing down rather than treating as taste.

**The mode is a question about the picture in front of you**, not a preference. *Is this file really
HDR? Is the tone map helping or hurting this photograph?* `Force SDR` in particular is a **probe** —
its whole use is flipping it and watching what moves — and a probe three screens away from what it
probes cannot be compared against anything. The pill is where the other question of the same kind
already lives: `SizeControl` is also about this picture, also transient, also in the pill.

## What this supersedes

**ME-178 is closed by this ticket, not shipped separately.** It existed to mirror the setting into
the editor's top bar. There is nothing to mirror: ME-177 put the tone map under the decode, so the
editor and the viewer read the same mode, and the pill they share is the same composable. One control
in the pill serves both, and the editor-specific ticket dissolves.

## Steps
1. `HdrModeControl` in `feature/viewer/shell/` — the anchor plus a `Popup`, modelled on `SizeControl`
   line for line: click to open, `focusable = true`, offset by the anchor's height.
2. `HdrModeButton` matching `SizeIndicatorButton`'s centre — same height, radius, hover tint, weight
   and chevron. Copied rather than extracted: the size control's centre is wrapped by two step
   buttons and owns a fixed outer width, so the common part is six lines of styling behind a
   parameter bag. A third pill control is when it earns a component.
3. `HdrModeMenu` — three rows in `ZoomPresetRow`'s style, **each with a caption**. "HDR" alone reads
   as *send HDR to the monitor*, which Woka cannot do; only prose says that the switch changes how
   the file is **read**.
4. It resolves `SettingsRepository` itself, the way `EditorHost` does. The pill is reached by two
   routes that pass no repository down, and four files of plumbing to hand one object to one control
   is worse than the control asking.
5. `PillGap`, `PillDivider` and `PILL_GAP` become `internal` and move to `MediaViewerPillParts.kt`:
   `CanvasZoomPill` now builds the same row and a second copy of an 8.dp spacer is how two pills that
   must look identical stop doing so. `MediaViewerGlobalControls` was at 154 lines with the control
   added, so this is a split rather than a convenience.
6. The control sits **outside** the group that retracts while editing. How the colour is read matters
   *more* in the editor, not less — it decides what the adjustments are applied to.
7. `HdrViewSettingsSection` is deleted and its call removed.

## Files
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/HdrModeControl.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/HdrModeButton.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/HdrModeMenu.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/MediaViewerPillParts.kt` (new, extracted)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/MediaViewerGlobalControls.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/CanvasZoomPill.kt` (edit)
`shared/src/commonMain/kotlin/com/woka/feature/settings/HdrViewSettingsSection.kt` (deleted)
`shared/src/commonMain/kotlin/com/woka/feature/settings/MediaViewerSettingsPanel.kt` (edit)

## Done when
- `jvmTest/ui/`: the menu offers all three with their captions, and picking one reports it once;
- `jvmTest/ui/`: `CanvasPillUiTest` passes unchanged — the pill gained a control without the
  retracting group or the size control moving;
- `grep` finds no HDR row left in `feature/settings/`;
- confirmed by hand: the dropdown is in the pill in both the viewer and the editor, and flipping to
  SDR changes the picture in place.
