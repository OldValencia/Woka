# Plan: Zoom & Size Control Redesign (Step 8b)

Implements **Step 8b** of `video-player-enhancements.md` and **supersedes §1** of that document: once
this lands, §1 is rewritten to match this, not the other way round. The presets themselves
(`ScalePreset.Fit/Fill/Original`), size persistence and the `Ctrl + RMB` gesture from §1 survive; the
two-mode model does not.

## Justification

The four causes listed in Step 8b were confirmed by reading the code, and five more turned up.

1. **The percentage is not measured from what people read it against.** `ScaleMath.kt` — zoom is a
   multiplier on top of `ContentScale.Fit` (`MediaViewerContent.kt`), i.e. **100% == Fit**, not 1:1.
   In macOS Preview, which `general_idea.md` names as the reference, 100% is the native pixel. And
   "Actual" exists as a separate preset, so the user sees "100%" and "Actual" as different things.
2. **The model's own KDoc describes it wrongly.** `SizeMode.kt` claims *"percentage relative to
   native pixels (100 = 1:1)"* and *"Zoom(100) and Preset(Original) render identically"* — both are
   false. That is the objective evidence behind "the author of the feature does not orient in it".
3. **`ContentScale.Fit` scales small media up too.** A 100×100 image in a 1000 px panel is drawn at
   10× at "100%". Its true 1:1 is 10% on the scale, and `MIN_PERCENT = 10` — so 1:1 is sometimes not
   reachable at all.
4. **The main zoom gesture is dead in the default state.** `Ctrl + Scroll` is disabled in Preset mode
   (`MediaViewerContentGestures.kt`), and the default is `Preset(Fit)`. The wheel answers with a
   "disabled" tooltip until you find the mode switch.
5. **Wheel zoom goes to the centre, double-tap goes to the cursor.** Same file, adjacent blocks.
6. **Panning never unwinds.** The offset is neither clamped nor reset on zoom-out: zoom to 400%, pan,
   wheel back down — the picture stays off-centre.
7. **The `−`/`+` step is 1 percentage point** (90 clicks from 10% to 100%) while the wheel
   accelerates up to ×24.
8. **The `‹ ›` chevrons mean two different things 20 px apart:** "cycle preset" on the indicator,
   "switch view" inside its popover.
9. **The widget's width jumps** when the mode changes, and the toolbar jumps with it.
10. **Hover-opening accumulated workarounds:** a non-focusable popup, digit capture on the anchor, a
    fixed width, a 350 ms grace timer (`SizeControl.kt`). Click-opening removes all of them and
    allows a real `TextField`.

## The solution

**One scale, no modes.** The percentage is measured from native 1:1. `Fit` / `Fill` / `Actual` become
sticky points on that same scale, each with a computed percentage:

| Point  | Percentage       | Panel-dependent |
|--------|------------------|-----------------|
| Actual | always 100       | no              |
| Fit    | `fitScale · 100` | yes             |
| Fill   | `fillScale · 100`| yes             |

While the state is stuck to a point the widget reads `Fit · 42%`; once the wheel moves off it, just
`43%`. Resizing the window re-resolves Fit/Fill and keeps the stickiness. The crossover stops being
invisible — it is written out. `Ctrl + Scroll` always works, because there is nothing to switch.

**The `SizeMode` type does not change** — `Preset(preset)` stays the sticky point, `Zoom(percent)` the
free value. What changes is the *meaning* of `percent`, and that the UI no longer calls these modes.

### Decided up front (do not re-litigate during implementation)

- **The percentage is based on native pixels.** This breaks the meaning of the stored
  `defaultZoomPercent`; an honest conversion is impossible (the old value was a percentage of the
  fitted size, and the fitted size depends on a panel that does not exist at load time), so the
  default is reset once. Woka is personal software with no users — there is nothing to migrate.
- **The zoom step is geometric**, `×1.1` per tick. On a 1:1 base, one percentage point at 42% and at
  400% are wildly different steps. A geometric step also retires wheel acceleration as a separate
  mechanism.
- **The slider is logarithmic.** On a linear track a range like `20…2000` gives everything below 100%
  less than a tenth of the travel.
- **The menu opens on click, not hover.** The three hover popovers in the player's bottom bar
  (`PopoverHoverState`) are neighbours competing for the pointer. The size widget stands alone in the
  top bar with no competitor, and a click gives a focusable popup — meaning a real input field.
- **`ZoomPopoverView` (Slider ⇄ Input) is deleted outright** — the new menu shows the slider and the
  field at the same time, so there is nothing to switch. The settings field, the repository method
  and the threading through six files all go with it.

---

## Phase 1: Model and maths

- Rewrite the KDoc in `SizeMode.kt` for the new base (it currently describes behaviour that does not
  exist). Drop `MIN_PERCENT` / `MAX_PERCENT` / `clampPercent` — the bounds become dynamic.
- New file `ZoomScale.kt` (jvmMain, beside `ScaleMath.kt`):
  - `fitPercent(container, native): Int` = `min(cw/nw, ch/nh) · 100`.
  - `fillPercent(container, native): Int` = `max(cw/nw, ch/nh) · 100`.
  - `presetPercent(preset, container, native): Int` — `Fit`/`Fill` through those, `Original` = `100`.
  - `zoomBounds(fitPercent): IntRange` — `lo = max(1, fitPercent / 10)`,
    `hi = max(fitPercent · 10, 800).coerceAtMost(6400)`; both bounds are then widened so the range is
    guaranteed to contain `fitPercent`, `fillPercent` and `100` — otherwise a snap point would be
    unreachable.
  - `stepUp(p)` = `max(p + 1, round(p · 1.1))`, `stepDown(p)` = `min(p - 1, round(p / 1.1))`, constant
    `ZOOM_STEP_RATIO = 1.1f`.
  - `snapToPreset(percent, container, native): ScalePreset?` — returns the point when
    `abs(p − presetPercent) <= max(1, presetPercent · 0.025)`. Tie-break order: `Original` → `Fit` →
    `Fill`.
  - `sliderFraction(percent, bounds)` / `percentFromFraction(fraction, bounds)` — the logarithmic
    mapping `ln(p/lo) / ln(hi/lo)`.
- In `ScaleMath.kt`, replace the body of `computeGraphicsScale`: `Zoom(p)` → `p / fitPercent`; resolve
  `Preset` through `presetPercent` and the same formula, so both branches share one path.
  `contentOverflows` is untouched — it already works off the result of `computeGraphicsScale`.
- In `MediaViewerState.kt` add `containerSize: IntSize` and `nativeSize: IntSize` (both
  `mutableStateOf(IntSize.Zero)`) — the top bar does not know the panel geometry, and without it a
  preset cannot be shown as a percentage. Filled from `MediaViewerContent` via a new `onMetricsChange`
  callback.
- Add the same two fields to `PipManager.kt` for the fullscreen window (`sizeMode` already lives
  there).

**Files:** `SizeMode.kt`, `ZoomScale.kt` *(new)*, `ScaleMath.kt`, `MediaViewerState.kt`,
`MediaViewerContent.kt`, `pip/PipManager.kt`

## Phase 2: Widget and menu

- Delete `SizeModeIndicator.kt` (177 lines — over the §2 mandate and rewritten wholesale),
  `ZoomPopover.kt`, `ZoomSliderView.kt`, `ZoomInputView.kt`.
- New `SizeIndicatorButton.kt` — a fixed-width inline row (`180.dp`, so the toolbar never re-flows):
  `[−]  Fit · 42% ▾  [+]`. The step buttons call `stepUp`/`stepDown`; the centre is one clickable
  target that opens the menu (one action, not two chosen by state). The label comes from `sizeMode`:
  `Preset` → `"${label} · ${percent}%"`, `Zoom` → `"${percent}%"`.
- New `ZoomMenuPopover.kt` — the menu card (`Popup`, `focusable = true`, width `220.dp`, radius
  `14.dp`, `1.dp` border, `Theme.colors.surface`):
  - three preset rows, tick on the active one, computed percentage on the right
    (`PopoverListRow.kt` has existed since Step 4 — reuse it rather than writing another row);
  - a `1.dp` divider;
  - the slider, with detent marks at Fit / Fill / Actual;
  - a `[ 42 ] %` row using a real `TextField` plus a `Reset` button (back to `Fit`, offset zeroed).
- New `ZoomMenuSlider.kt` — the logarithmic slider (`sliderFraction` / `percentFromFraction`), preset
  detent marks, knob pulled in by `snapToPreset`. Take the visual language from
  `PlayerSpeedSlider.kt` (the 1× detent) — that problem is already solved there.
- Rewrite `SizeControl.kt` as a thin host: "menu open" state, the anchor, the `Popup`. Gone:
  `hoveredRaw`, `expanded`, `digitOf`, `onPreviewKeyEvent`, `inputText`, `editFocus`, `anchorHeight` —
  the whole hover-opening and manual digit-capture apparatus.
- Remove `ZoomPopoverView` and the `zoomPopoverView` field from `WokaSettings.kt`; remove
  `setZoomPopoverView` from `SettingsRepository.kt` / `SettingsRepositoryImpl.kt`; drop the
  `popoverView` / `onPopoverViewChange` parameters from `MediaViewerGlobalControls.kt`,
  `MediaViewerLayoutWrapper.kt`, `MediaViewerEffects.kt`, `MediaViewerScreen.kt`,
  `MediaViewerFullscreenOverlays.kt`, `MediaViewerFullscreenWindow.kt`.

**Files:** `SizeControl.kt`, `SizeIndicatorButton.kt` *(new)*, `ZoomMenuPopover.kt` *(new)*,
`ZoomMenuSlider.kt` *(new)*, `WokaSettings.kt`, `SettingsRepository.kt`, `SettingsRepositoryImpl.kt`,
`MediaViewerGlobalControls.kt`, `MediaViewerLayoutWrapper.kt`, `MediaViewerEffects.kt`,
`MediaViewerScreen.kt`, `MediaViewerFullscreenOverlays.kt`, `MediaViewerFullscreenWindow.kt`; deletes
`SizeModeIndicator.kt`, `ZoomPopover.kt`, `ZoomSliderView.kt`, `ZoomInputView.kt`

## Phase 3: Gestures and keyboard

- `MediaViewerContentGestures.kt`:
  - the `Ctrl + Scroll` branch no longer distinguishes `Preset` from `Zoom`: it resolves the current
    percentage through `presetPercent` when sticky, applies `stepUp`/`stepDown` per tick, then
    `snapToPreset` — land inside the tolerance and the state is sticky again.
  - **Zoom to the cursor.** Lift the offset maths out of `onDoubleTap` into
    `zoomAround(tap, from, to, ...)` in `ZoomScale.kt` and call it from both branches. Today the
    formula exists only in the double-tap.
  - delete `onScalingDisabledAttempt` and the acceleration (`scrollAccel`, `lastScrollNanos`) — both
    exist only to serve the old model.
- Delete `ScalingDisabledOverlay` and `MediaViewerState.scalingDisabledTrigger`: there is nowhere left
  to refuse a zoom. Check both call sites — main window and fullscreen.
- New `ZoomHudOverlay.kt` — a centred pill showing the percentage while zooming (fade in/out `150 ms`,
  auto-hide after `900 ms`, radius `10.dp`, translucent dark background). Raised by gesture or
  keyboard, not by the menu, where the value is already on screen.
- Add to `handleMediaViewerKeyEvent` (`MediaViewerKeyHandler.kt`): `Ctrl +` / `Ctrl =` steps up,
  `Ctrl −` down, `Ctrl 0` is `Fit`, `Ctrl 1` is `Actual`. There is no keyboard zoom at all today. The
  handler needs access to `sizeMode` and a change callback — two more parameters; there are two call
  sites (`mediaViewerKeyHandler` and the global hook in `MediaViewerShortcutEffect.kt`).

**Files:** `MediaViewerContentGestures.kt`, `ZoomScale.kt`, `ZoomHudOverlay.kt` *(new)*,
`MediaViewerKeyHandler.kt`, `MediaViewerShortcutEffect.kt`, `MediaViewerState.kt`,
`MediaViewerContent.kt`, `MediaViewerFullscreenWindow.kt`; deletes `ScalingDisabledOverlay`

## Phase 4: Panning

Closes the separate `media-pan-bounds.md` — when this phase ships, that file moves to `implemented/`
rather than being left hanging.

- New `PanBounds.kt` — `clampOffset(offsetX, offsetY, sizeMode, container, native)`: the content
  cannot travel past the panel edges; on an axis where it is smaller than the panel, that axis is
  pinned to centre.
- Overscroll with a rubber band: past the limit, movement is applied at `0.35f`, and on button release
  `animateFloatAsState` (`spring`, `dampingRatio = 0.85f`) takes it back to the edge.
- Auto-recentre on the way back: as soon as the content stops overflowing (`contentOverflows == false`)
  the offset animates to zero. Today it simply stays where it was.
- Left-button panning whenever the content overflows the panel. `detectTapGestures` already separates
  a tap from a drag, so tap-to-play/pause on video is not broken. `Ctrl + RMB` stays as it was.

**Files:** `PanBounds.kt` *(new)*, `MediaViewerContentGestures.kt`, `MediaViewerContent.kt`,
`MediaViewerScreen.kt`, `MediaViewerFullscreenWindow.kt`

## Phase 5: Settings and persistence

- Add `sizeSchemaVersion: Int = 0` to `WokaSettings.kt`. In `SettingsStore.kt`, on load: version `0` →
  `defaultUseZoom = false`, `scalePreset = Fit`, `defaultZoomPercent = 100`, `sizeSchemaVersion = 1`,
  written back. A one-off reset instead of a wrong conversion.
- `MediaViewerSettingsPanel` (`SettingsPanels.kt`): stepper caption becomes `% of actual size`, range
  `10…1600` (there is no panel there, so dynamic bounds do not apply). The `Preset | Zoom` segmented
  control and the "Remember zoom & size" toggle stay.
- `persistSizeState` / `startSizeMode` in `SizeMode.kt` need no structural change — only the meaning of
  the number being written changes.

**Files:** `WokaSettings.kt`, `SettingsStore.kt`, `SettingsPanels.kt`, `SizeMode.kt`

## Status — ✅ shipped, all six phases, verified live

`:shared:compileKotlinJvm` — exit 0. The anti-FQN grep is clean across all 30 touched files (the
violations the sweep did find are in `HomeScreen.kt`, `App.kt`, `DockItem.kt` and others, which were
not touched). No file exceeds 149 lines. The Phase 6 manual pass was run and everything behaved —
snap points, cursor-anchored wheel zoom, the keyboard set, the HUD, the pan clamp and settle,
window ⇄ fullscreen, and the default after a restart. §1 of `video-player-enhancements.md` has been
rewritten to this behaviour, and `media-pan-bounds.md` shipped with Phase 4.

**Deviations from the plan, taken during implementation:**

- **`PopoverListRow.kt` was not reused.** It hard-codes `Color.White` — the style of the dark popovers
  in the player's bottom bar. The size widget lives in the top bar on `Theme.colors.surface`, where
  white text is invisible. `ZoomMenuPresetRow.kt` uses theme colours instead.
- **The HUD went into the existing `MediaOverlays.kt`** rather than a new file: it replaced
  `ScalingDisabledOverlay` line for line in both size and structure (`trigger` + `delay` +
  `AnimatedVisibility`). The file stayed at 133 lines.
- **The preset branches of `computeGraphicsScale` work off the un-rounded `fitScale`.** Going through
  the integer percentage cost "Actual" up to 2% on 4K in a small window — and 1:1 has to be
  pixel-exact. The integer percentage is used only where a human chose it.
- **`savedPreset` dropped out of the whole chain** (`MediaViewerNormalControls` → `LayoutWrapper` →
  `TopBarSetup` → `Screen`, plus fullscreen). It existed solely for the "switch back to preset mode"
  action, which no longer exists.
- **The settle spring** is done with `animateFloatAsState` on the offsets in `MediaViewerContent`,
  switching the spec: `snap()` while a drag is live, `spring(dampingRatio = 0.85f)` after release.
  Otherwise the frame would lag behind the cursor for the whole drag.
- **`fsSettings` / `onFsSizeModeChange` / `fsZoomHud` were lifted above the `Window(...)` call** — the
  key preview is a window parameter and cannot see anything declared in the content lambda.
- **The zoom keys were added to `PipKeyHandler` as well**, or they would be silent in fullscreen.
- **The settings default stepper was widened 1000 → 1600** to follow the new percentage base.

**Splits the §2 mandate forced along the way** (all mechanical extractions, no behaviour changed):
`MediaViewerFrame.kt` (out of `MediaViewerContent`), `MediaViewerTapGestures.kt` +
`MediaViewerZoomActions.kt` (out of `MediaViewerContentGestures`), `MediaViewerFullscreenSurface.kt`
(out of `MediaViewerFullscreenWindow`), `MediaViewerSettingsPanel.kt` (out of `SettingsPanels.kt`,
which was 182 lines before this work), `ZoomMenuPresetRow.kt` (out of `ZoomMenuPopover`).

## Phase 6: Verification

- §2 mandate (`wc -l`, not `Measure-Object`): each new file targets ≤ 130 lines. The likely
  overflow candidates are `ZoomMenuPopover.kt` and `MediaViewerContentGestures.kt` (145 before the
  edits): if a phase pushes them past 150, the split happens in that phase, not "later".
- The anti-FQN grep on every touched `.kt`, and a `:shared:compileKotlinJvm` build, both waited on.
- Manual pass: `Ctrl + Scroll` works immediately after opening a file, with no switching; the zoom
  goes to the cursor; the knob is pulled onto Fit/Fill/Actual and the label flips between `Fit · 42%`
  and `43%` and back; resizing the window re-resolves Fit/Fill without breaking stickiness;
  `Ctrl 0` / `Ctrl 1` / `Ctrl ±`; the picture cannot travel past the edge and re-centres on the way
  back to the fitted size; window ⇄ fullscreen preserves the state; after a restart the default is
  Fit.
- Acceptance criterion from Step 8b: cold, with no tooltips, Fit, Fill, Actual and an arbitrary
  percentage are all reachable.
