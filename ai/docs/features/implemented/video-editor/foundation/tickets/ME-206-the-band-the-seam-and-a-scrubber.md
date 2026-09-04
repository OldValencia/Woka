# ME-206 — The band, the seam, and a scrubber

| | |
|---|---|
| **Order** | second in the feature, straight after ME-201 |
| **Depends on** | ME-201 |
| **Feature** | [Foundation](../foundation.md) · umbrella [Video Editor](../../../../pending/video-editor/video-editor.md) |

**Goal.** Give the shell a timeline slot the engine fills, and fill it with a scrubber, so a video in
the editor can be moved through.

**Why now.** ME-201 leaves a video that plays and cannot be positioned: every picture tool needs a
particular frame on screen, and there is no way to reach one. It is also the slot **everything** in
[`timeline/`](../../../../pending/video-editor/timeline/timeline.md) is drawn into, so nothing in that folder can start until
this exists.

**Design:** [`../../video-editor.md`](../../../../pending/video-editor/video-editor.md) §3.4 and §4.1–4.2 · the spec for every
measurement here is [`video-editor.html`](../../../../../../mockups/design/video-editor.html), state 1.

---

## Steps

1. Add to `EditorEngineSurface`:
   `@Composable fun Timeline(document: EditDocument, modifier: Modifier) = Unit`. KDoc it the way
   `Thumbnail` in the same file is: defaulted so a medium with no timeline draws nothing, and it
   **names no player and no bitmap** — the constraint the whole seam exists for. `ImageEditorEngine`
   is untouched and no still changes.
2. **Split `EditorBottomPanel.kt` first — it is 149 lines** (checked 2026-09-03) and step 3 pushes it
   over the mandate. Move the `if (activeTool != null) { … }` body into a new
   `EditorInspectorCard.kt` as an `internal @Composable`, taking `session`, `engine`, `registry`,
   `document`, `activeTool`, `selectedId`, `railWidth` and the `InspectorAccessory`. The seam is the
   one the file already has: everything moved is *the card*, and what stays is *the column*. No
   behaviour changes and every inspector UI test must pass untouched.
3. In `EditorBottomPanel`'s `Column`, between the inspector card and `EditorRail`, call
   `engine.Timeline(document, Modifier)`. **No new parameter** — the panel already takes the engine as
   an interface — and **no new token**: the column's `Arrangement.spacedBy(EditorTokens.Inspector
   .bottomInset)` gives the gap for free. `EditorScreen.kt` is **150 lines** and is not touched.
4. Add `VideoEditorBand.kt` in `feature/editor/engine/video/`, implementing the band: a `Row` in the
   rail's own container — `Theme.viewer.popoverScrim`, `EditorTokens.Rail.containerRadius`,
   `EditorTokens.Rail.containerPadding` — holding a play/pause glyph, the slider, and a
   `formatTime(position) / formatTime(duration)` readout. Test tag `editor-band`. Its geometry is
   private top-level constants in this file, with a comment naming the mockup: `EditorTokens` is
   146 lines and its KDoc scopes it to the rail, the inspector and the top bar, and the band's
   contents belong to the medium rather than to the shell.
5. **Reuse, not addition** — `tickets.md` §6, and each of these is why nothing new is written:
   - `MediaSlider` (`feature/viewer/shell/slider`) is the shared generic slider. Its `value` is a
     `() -> Float` *precisely* so a caller tracking a play head at the presenter's frame rate does
     not perform the state read in its own scope; pass a lambda and do not hoist it.
   - `MediaSliderStyle` carries the colours. Build one from `Theme.viewer.onScrim` and
     `onScrimSecondary` rather than calling `defaultMediaSliderStyle()`, which is the accent: the
     band is chrome on the picture, and a slider marks *level*, which the accent does not mean here.
   - `formatTime` in `MediaViewerTimeSlider.kt` is `internal` in the same module. Do not write a
     second formatter.
   - the seek is `pauseForSeek()` → `seek(ms)` → `resumeAfterSeek()`, the sequence
     `MediaViewerTimeSlider` already uses, so a drag holds the picture and releases into play only if
     it was playing.
   - `MediaSlider`'s `segments: List<SliderSegment>` parameter is what the clip strip will use in
     ME-211. Leave it on its default here and add nothing for it.
6. **`VideoPlayerHolder` already exists** — ME-201 introduced it in `VideoEditorCanvas.kt`, because
   `Thumbnail` was already a second reader of the same player. The band is its third: read
   `holder.state`, and draw an empty track and `0:00` until it is non-null. Nothing about its
   lifetime changes — the canvas's `DisposableEffect` still owns it.
7. `VideoEditorEngine` overrides `Timeline` and hands the band the holder it already carries.
8. Tests, `jvmTest/kotlin/com/woka/ui/EditorBandUiTest.kt`: the band is composed for an engine that
   overrides `Timeline` and **absent for one that does not** — asked of the shell against
   `FixtureEngine`, exactly as `EditorCanvasPlaybackUiTest` asks about the click, so what is proved is
   that the panel asks its engine rather than what a video does about it. Plus a band test over
   `episodeTwoFile()` from `PlayerHarness`: dragging it moves `currentPositionMs`, and the readout
   shows the duration once it is known.

## Files

`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorEngineSurface.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorBottomPanel.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorInspectorCard.kt` (new) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/video/VideoEditorBand.kt` (new) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/video/VideoEditorEngine.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/ui/EditorBandUiTest.kt` (new)

## Done — 2026-09-03

Every step as planned, and **two files were split rather than one**. `EditorBottomPanel` went to
`EditorInspectorCard` as step 2 said (149 → 107 + 95); `VideoEditorBand` reached 164 the moment it
was written, so its three pieces — the style, the transport and the readout — went to
`VideoEditorBandParts`, which is the cut `MediaSlider` and `MediaSliderTrack` already make: the band
is *a layout and a seek*, and those are what it lays out.

**`FixtureEngine` grew a `timeline` lambda** rather than a boolean, so the shell test can tag what
the engine returned and prove the panel placed *that* rather than something of its own. The absent
case is asserted too: `Timeline` is defaulted to `Unit` so no photograph's layout moves, and a
default nobody asserts is one that quietly stops holding.

**What the tests do not cover, and why**: the scrub. A drag is a pointer gesture over a slider whose
travel is in pixels, and asserting where a swipe landed tests the test's arithmetic rather than the
band's. It went on the by-hand list instead.

**The by-hand pass found two, both of them things no assertion here would have caught** — which is
the argument for the pass rather than a mark against the tests:

- **The transport glyph sat against the left edge, half clipped.** A `BasicText` given only a `size`
  lays its text out at the box's top-left; it does not centre in it. Every other round control in the
  app is a `Box` with `Alignment.Center` and a glyph inside, and this is now that.
- **The unplayed track read as white.** `Theme.viewer.onScrimSecondary` is a *text* colour — #CDCDD2 —
  and as a track it drew a solid pale line the played half was indistinguishable from: only the knob
  said where the play head was. It is `onScrimHover` now, 8 % white, which is what the mockup's
  `--on-scrim-hov` is and what the inspector's own `DetentSlider` track uses. `MediaSliderTrack`
  lightens it under the pointer, so the affordance does the work rather than the resting state.

## Done when

- A video in the editor draws a band between the inspector and the rail; a **photograph draws none**,
  and its layout is pixel-identical to before this ticket.
- Clicking the band seeks; dragging it scrubs and the picture holds while the pointer is down.
- The band does not move when a tool is opened or closed — the inspector grows above it.
- `EditorBottomPanel.kt` and every file it was split into are inside 130–150 lines, measured with
  `wc -l`, not by eye.
- No file over the mandate was added to: `EditorScreen.kt` (150) and `EditorTokens.kt` (146) are
  unchanged.
- Every existing editor UI test passes untouched — the split of step 2 is the thing being checked.
- The anti-FQN grep over every edited file is clean.
- `./gradlew.bat :shared:build` green.
