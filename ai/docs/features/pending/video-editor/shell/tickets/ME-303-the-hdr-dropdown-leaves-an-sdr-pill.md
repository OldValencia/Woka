# ME-303 — The HDR dropdown leaves an SDR picture's pill

| | |
|---|---|
| **Order** | second in `shell/`, straight after ME-302 |
| **Depends on** | ME-302 (`EditorEnginePicture.sourceColor`) |
| **Feature** | [The shared editor shell](../shell.md) |

**Goal.** Hide the top bar's `HDR ▾` control on both pills when the open picture has no high dynamic
range, and keep it reachable in the one case where hiding it would trap the user.

**Why now.** The other half of the report of 2026-09-03. ME-302 took the tool out of the rail; the
pill still offers `HDR · Auto` on an SDR photograph, where the menu changes nothing. The author's
decision covers both: *"скрывать только на SDR картинках… Поэтому скрыть во вьювере и редакторе."*

---

## Design notes read against the tree, 2026-09-04

**Two pills, two sources for one fact, and that is not a duplication.** There are exactly two call
sites of `HdrModeControl()`:

| Site | How the editor reaches it | Where the colour comes from |
|---|---|---|
| `MediaViewerGlobalControls.MediaViewerNormalControls` line 95, behind `showHdrMode` | the viewer's own pill, borrowed by the editor when it opens *over* a viewer (`EditorTopBarSetup` leaves the centre slot alone) | `state.stillColor`, already in `MediaViewerScreen` |
| `CanvasZoomPill` line 45 | the editor's own pill, brought only when there is no viewer beneath — the grid→editor path | nothing today; ME-302's `engine.sourceColor` |

Neither can borrow the other's answer: the viewer has no engine and the grid path has no viewer. They
do not disagree, because both roads end at `readStillColor(file)` — `MediaThumbnails.stillColor` on
one side and `ImageEditorEngine.sourceColor` on the other.

**One clause the rail did not need.** The pill *writes* `hdrViewMode`, so a bare
`transfer != SDR` traps a real case: `Force HDR` set on an HDR photograph stays set when the user
walks to an SDR one, `colourToBelieve` then tone-maps that picture as PQ, and the control that would
undo it is gone. So the predicate is

```
needsToneCurve(transfer) || mode == HdrViewMode.ForceHdr
```

— shown whenever the control is doing something or could undo something, hidden only when it has
nothing to say *and* nothing in force. `ForceSdr` needs no clause: it changes nothing on a picture
that was never going to be tone mapped.

**Wide-gamut SDR stays hidden**, as in ME-302 and for the same reason — `TransferKind`'s own KDoc
says BT.2020 primaries with an ordinary transfer are not HDR. The cost is real and accepted: such a
file *is* gamut-mapped, and `Force SDR` is no longer reachable to turn that off.

**Measured, and it makes the change safer than it looks:** `MediaThumbnails.stillColor` already
answers `StillColorInfo.Sdr` for `MediaItem.Video` unconditionally, so the new clause alone hides the
control for a video. `showHdrMode = videoState == null` stays anyway — it is ME-180's own rule, it
stands without this one, and `state.stillColor` lags the active item by a frame on a walk.

**File sizes.** `MediaViewerScreen.kt` is **156 lines, already over the mandate** — this ticket swaps
one argument line and rewrites its two comment lines in place, so the file does not grow, and its
split is still nobody's ticket. `EditorCanvasHost.kt` is 134 and gains one call.

**Why the editor's colour travels through `CanvasOverrideState`.** `CanvasZoomPill` is registered as
a top-bar accessory lambda by `EditorTopBarSetup`, which has no engine and cannot be given one:
`EditorScreen.kt` is at 150 lines and one more named argument would push it over. The override state
is the channel the size control already travels, its KDoc is already *"who currently owns the picture
the top bar's controls address"*, and the publisher is `EditorCanvasHost` — the one composable that
holds both the engine and a view of the pill. A `TransferKind` rather than a resolved boolean, so the
predicate stays in one place.

---

## Steps

1. **The predicate, once, in `commonMain`.** In `core/data/HdrView.kt` (67 lines):
   `fun hdrControlApplies(transfer: TransferKind, mode: HdrViewMode): Boolean = needsToneCurve(transfer) || mode == HdrViewMode.ForceHdr`,
   importing nothing — `needsToneCurve` and `TransferKind` are the same package. Its KDoc carries the
   `ForceHdr` trap above, because that clause looks removable to anyone who has not met it.

2. **The composable that reads the mode for you.** In `HdrModeControl.kt` (97 lines):
   `@Composable fun hdrControlVisible(transfer: TransferKind): Boolean`, resolving
   `HdrModeDependencies` the way `HdrModeControl` beside it already does and returning
   `hdrControlApplies(transfer, mode)`. It lives here rather than in the two call sites because
   `HdrModeDependencies` is this file's, and because the control and the question of whether to draw
   it belong together.

3. **The viewer's pill.** `MediaViewerScreen.kt:151–153` — the two comment lines are rewritten to
   name both reasons and the argument becomes
   `showHdrMode = videoState == null && hdrControlVisible(state.stillColor.transfer),`. No new line.

4. **The channel.** `CanvasOverrideState` (`app/shell/CanvasOverride.kt`, 55 lines) gains
   `var sourceTransfer by mutableStateOf(TransferKind.SDR)`, importing
   `com.woka.core.data.TransferKind`. Named after `EditorEnginePicture.sourceColor`, defaulted to
   SDR so a pill nobody has published to hides the control rather than showing one that moves
   nothing.

5. **The editor publishes it.** New `EditorCanvasColor.kt` in `feature/editor/shell`:
   `@Composable internal fun PublishCanvasColor(color: StillColorInfo)`, a `SideEffect` writing
   `override.sourceTransfer` and a `DisposableEffect` putting it back to `TransferKind.SDR` on the
   way out — the shape `PublishCanvasZoom` uses for `override.zoom`, and a separate file because that
   one is named after zoom and this is not zoom. `EditorCanvasHost` calls it with
   `engine.sourceColor` beside its existing `PublishCanvasZoom(...)`.

6. **The editor's pill.** `CanvasZoomPill` (47 lines) holds the override in a local, and the divider,
   the two gaps and `HdrModeControl()` go behind
   `if (hdrControlVisible(override.sourceTransfer))`. The gaps must be **inside** the branch:
   `MediaViewerControlsContainer`'s own KDoc records that a child measuring to nothing still leaves
   its neighbours' spacing behind, which is why these pills space themselves with `PillGap`.

7. **Tests.**
   - `jvmTest/unit/HdrControlAppliesTest.kt` — the whole truth table, and it is the ticket's real
     verification: SDR+`Auto` false, SDR+`ForceSdr` false, **SDR+`ForceHdr` true** (the trap),
     wide-gamut SDR+`Auto` false, PQ+`Auto` true, HLG+`ForceSdr` true.
   - `jvmTest/ui/CanvasPillHdrUiTest.kt` — `CanvasZoomPill` under a `CanvasOverrideState` carrying a
     zoom (it returns early without one): `hdr-mode-anchor` absent at `TransferKind.SDR`, displayed
     at `PQ`, and the size control's own readout displayed in both so an empty pill cannot pass
     either case. `startKoinOnce()` in `@BeforeTest`, because `HdrModeControl` injects
     `SettingsRepository`. A **new file**: `CanvasPillUiTest.kt` is 182 lines and nothing may be
     added to it.
   - **Not** written: a viewer-level test of step 3. `ViewerHarness` mounts `MediaViewerPlayer`, and
     the top bar's pill is not in that tree at all — `CanvasPillUiTest` already proves
     `showHdrMode = false` hides the control, step 1 is exhaustively unit-tested, and what is left is
     the `&&` between them. Confirmed by hand instead, recorded with the date in
     [`../shell.md`](../shell.md) §3.

## Files

`shared/src/commonMain/kotlin/com/woka/core/data/HdrView.kt` (edit) ·
`.../commonMain/kotlin/com/woka/app/shell/CanvasOverride.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/HdrModeControl.kt` (edit) ·
`.../viewer/shell/MediaViewerScreen.kt` (edit) · `.../viewer/shell/CanvasZoomPill.kt` (edit) ·
`.../editor/shell/EditorCanvasColor.kt` (new) · `.../editor/shell/EditorCanvasHost.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/unit/HdrControlAppliesTest.kt` (new) ·
`.../jvmTest/kotlin/com/woka/ui/CanvasPillHdrUiTest.kt` (new)

## Done when

- an SDR photograph shows no `HDR ▾` in the pill, on either path, and a PQ or HLG one shows it;
- `Force HDR` left in force keeps the control on screen over an SDR picture, so it can be undone;
- a video is unaffected — `showHdrMode = videoState == null` is still there and
  `VideoEditorEngine.sourceColor` is still the inherited SDR;
- `MediaViewerNormalControls`'s signature is unchanged: only the argument moved, and
  `CanvasPillUiTest`'s four existing cases are green with no edit;
- `wc -l` reports ≤ 150 for every file above except `MediaViewerScreen.kt`, which reports **156, the
  count it already had**;
- the anti-FQN grep (`mandates.md`) has been run on every edited `.kt` and is clean;
- `./gradlew.bat :shared:build` green.
