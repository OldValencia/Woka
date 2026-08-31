# ME-121 — The viewer's chrome reads the onScrim tokens

| | |
|---|---|
| **Order** | 70 — independent of everything; nothing waits on it |
| **Depends on** | ME-012 (the tokens exist) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Move the viewer's own on-media chrome — toolbar, global controls, navigation arrows, tooltip,
slider style — off `Theme.colors` and onto `ViewerChrome`'s `onScrim*` foregrounds, and write down the
rule that decides which surfaces follow.

**Why now.** It is the only open item that makes the shipped product worse today. `ViewerChrome` gained
the foreground half of its on-media pair in ME-012 — `onScrim`, `onScrimSecondary`, `onScrimDisabled`,
`onScrimBorder`, `onScrimHover`, `onScrimSelected` — light in both themes for the same reason its
scrims are dark in both: legibility over a photograph cannot depend on a theme preference. The editor
has read them from its first commit. The viewer has not, so text and icons on a scrim measure between
**1.05 : 1** and 6.68 : 1 in the light theme depending on the photograph behind them. At 1.05 : 1 the
control is invisible.

## The block's own selection rule is wrong, and that is why this is a ticket rather than a sweep

`backlog.md` said the files to convert are "the intersection of *reads `Theme.viewer`* and *reads
`Theme.colors`*". Counted 2026-09-01: **8** files under `feature/viewer/` read `Theme.viewer`, **28**
read `Theme.colors`, and the intersection is **empty** — the two sets are disjoint. The rule selects
nothing and always did.

The real question is per file and cannot be grepped: **does this surface draw on top of a
photograph?** So this ticket converts one cluster, states the rule in one place, and leaves the other
two clusters to ME-163 with the mapping already settled.

**And not everything on `Theme.colors` is wrong.** A dialog is an app surface that happens to appear
above a photograph and keeps the app palette — the rule `DiscardChangesDialog`'s KDoc already states
for the editor's own confirmations. `FileInfoDialog`, `FileInfoMeta`, `FileInfoStreamCards` and
`VideoAlreadyOpenedDialog` are therefore **out of scope and stay as they are**, and step 1 records that
so a later sweep does not "finish the job" by converting them.

## Steps

1. **`core/ui/theme/ViewerChrome.kt` (edit)** — one KDoc paragraph naming the rule: chrome drawn *on*
   the media takes `onScrim*`; a dialog, a popover with its own opaque plate, and anything that would
   read the same over a black background take `Theme.colors`. Cite the 1.05 : 1 measurement and its
   date. This is the sentence ME-163 will be applied from, so it is written once, here.

2. **`shell/MediaViewerToolbar.kt` (edit)** — every `Theme.colors.textPrimary` → `onScrim`,
   `textSecondary` → `onScrimSecondary`, a disabled foreground → `onScrimDisabled`, `border` →
   `onScrimBorder`. No layout change, no new composable.

3. **`shell/GlobalControlButtons.kt`, `shell/MediaViewerGlobalControls.kt` (edit)** — the same mapping,
   plus `onScrimHover` where a hover state currently derives from the app palette.

4. **`shell/MediaViewerNavigationButtons.kt` (edit)** — the arrows sit directly on the picture and are
   the worst case in the measurement; same mapping.

5. **`shell/ViewerTooltip.kt` (edit)** — its plate keeps its own scrim; what moves is the label on it.

6. **`shell/slider/MediaSliderStyle.kt` (edit)** — the track, the fill and the knob to
   `onScrimSecondary` / `onScrim`, and the selected accent to `onScrimSelected`. It is a style object,
   so this one file is what the three sliders that read it inherit.

7. **`shell/MediaViewerPlayerModifier.kt` (edit)** — checked last, because it may hold nothing on-media
   at all; if it does not, it is left alone and step 1's rule is what says why.

## Files

`core/ui/theme/ViewerChrome.kt` (edit), `feature/viewer/shell/MediaViewerToolbar.kt` (edit),
`GlobalControlButtons.kt` (edit), `MediaViewerGlobalControls.kt` (edit),
`MediaViewerNavigationButtons.kt` (edit), `ViewerTooltip.kt` (edit),
`shell/slider/MediaSliderStyle.kt` (edit), `MediaViewerPlayerModifier.kt` (edit) under
`shared/src/jvmMain/kotlin/com/woka/`; `ai/docs/features/pending/image-editor/image-editor.md` (edit —
a §9 row and the status table), `tickets/backlog.md` (edit — the ME-121 block is already gone; ME-163
gains the settled mapping).

## Verification

- `jvmTest/ui/ViewerChromeTokensUiTest.kt` (new) — the assertion that survives a later sweep: render
  the toolbar and the navigation buttons under **both** themes and assert the foreground colour is the
  *same* in each, which is what "light in both themes" means and what no per-file eyeballing can catch;
- `jvmTest/unit/ViewerChromeTest.kt` (new or edit) — `onScrim*` values are identical in the light and
  dark palettes, so a future palette edit cannot silently theme them;
- `jvmTest/ui/ViewerToolbarUiTest.kt`, `ViewerControlsUiTest.kt`, `ZoomMenuUiTest.kt` — unchanged and
  still green, which is what says nothing but colour moved;
- **by hand, and this is the real verification**: a light theme, a white photograph and a black one,
  every converted control legible on both.

## Done when

- the seven files above read no `Theme.colors` foreground for anything drawn on the media;
- the four dialogs still read `Theme.colors`, and `ViewerChrome`'s KDoc says why;
- both themes draw the converted chrome identically;
- `grep -rln "Theme.colors" shared/src/jvmMain/kotlin/com/woka/feature/viewer/ | wc -l` falls from 28
  to 21;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
