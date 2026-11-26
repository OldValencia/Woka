# RD-302 — Clip and Sound tabs, the scope line

| | |
|---|---|
| **Order** | eighth, after RD-301; RD-401 (Ways out) follows |
| **Depends on** | RD-202 (scrub rows, wells), RD-204 (the film's crumbs), RD-301 (the timeline, its menu, `VideoPlayerHolder.strip`) |
| **Feature** | [Viewer and Editor Redesign](../viewer-editor-redesign.md) |

**Goal.** Build the prototype's Clip and Sound panels (`#editor/video`, `panels.js` `clipPanel`,
`soundPanel`) and the scope line that follows the selection: `Whole video`, a clip, a Markup shape.

**Why now.** Since RD-301 a clip's speed cannot be set at all: the chip went with the clip bar. The
Clip tab is always there but shows one footnote. Sound's rows stand loose instead of in the
prototype's wells. And a selection can only be left with the `Whole video` crumb, not with Esc.

**Decided with the author (2026-11-26).**
- **Speed is 0.25–4×, read in octaves** (log), with 1× at the centre of the row. ½× and 2× sit at
  equal distances from it, and the row snaps to 0.5 / 0.75 / 1.25 / 1.5 / 2 / 3× as the old slider
  did (`snapClipSpeed`). The prototype's 0.25–3× linear row is corrected first.
- **Markup is the whole film's**, as in the prototype. Opening Markup clears the clip selection,
  as Transform does. The edits it makes go to the shared layer. A selected shape reads
  `Whole video › <shape>` in the scope line. A clip's own markup in files saved before this
  ticket is left as it is: the readers still fold it, and nothing in the editor writes or edits
  one any more. With no users (`woka-is-personal-undeployed`), no migration is written.

**Taken from the prototype as drawn (not asked):**
- **The Clip tab exists only while a clip is selected.** It is first, and grows in. When the
  selection goes, so does the tab, and the open tab becomes the first one (Styles).
- **`Whole video`** takes the selection back. From Clip or from Markup with a shape it opens
  Styles. **Esc** drops a shape, else a clip, and only then cancels the editor (`app.js` keydown).
- **A still frame** has no Speed or Mute row. Its crumb reads `Still frame`.

## The spec, measured (`base.css`, `editor.css`, room colours)

- **Toggle row in a well** (`.inspector .toggle`): min 42 dp, padding 0 × 12, gap 10, optional
  18 dp glyph in `muted`, 13 sp `text` label taking the width, the room's switch (34 × 20) at the
  end. Hover `hover`; a click anywhere on the row flips it. Hairlines between rows, as in a scrub well.
- **Group label** (`.group-label`): 12 sp, weight 500, `muted`, 4 dp in, 8 dp above its well.
- **Button pair** (`.button-pair`): two equal columns, gap 8. **Well button**: min 40 dp, radius 10,
  `well`, hover `well2`, glyph 17 `muted`, 13 sp `text`; `.danger` draws glyph and word in `danger`.
- **More actions** (`.text-action`): as `TextAction` draws it today, glyph `more`.
- **Clip panel:** a well of Speed (glyph `speed`, unit `×`) and `Mute sound` (glyph `mute`), then
  Split (scissors, as the timeline's) | Delete (`trash`, danger), then More actions. A freeze has
  only the pair and More. Tips: "Split at the playhead (S)", "Delete this clip (Del)".
- **Sound panel:** a well of `Mute` (glyph `mute`) and `Volume` (glyph `volume`), then `Fades`
  over a well of `Fade in` / `Fade out` (glyphs `fadeIn`, `fadeOut`). Last comes a well of
  `Even loudness`, for the whole film only (`filmOnly`, unchanged).
- **Clip tab entering:** its share of the bar grows 0 → 1 and its opacity 0 → 1 over 260 ms,
  `cubic-bezier(.22, 1, .36, 1)`. The indicator follows the tabs' positions as they move. It
  leaves without an animation.
- **Crumbs:** `Whole video › Clip 2  2.4 s`, `Whole video › Still frame  2.0 s`, or `Whole video ›
  <shape>`, where the shape reads as its Markup bar does (a text's words, else its kind) and
  carries no meta.

## Steps

**Prototype first**
1. `woka/`: `scrub` learns a log track for Speed. The range input runs in octaves (−2 … 2, 1× at
   the centre), the number field shows the speed, and the snaps above apply; the range is
   0.25–4×. Check headless (`shot.mjs`): the thumb sits at the centre at 1×, and at 75 % at 2×.

**Glyphs and the schema**
2. Drawables from `phosphor.js` (none of them ships yet): `speed`, `volume`, `mute`, `fade_in`,
   `fade_out`, `trash`, `more`. Add them as `EditorIcons` entries, the way RD-201 and RD-205 added
   theirs.
3. `ParamSpec.kt`: `BoolParam.icon: DrawableResource? = null`. `FloatParam.octaves: Boolean =
   false` means the track is read on a log₂ scale, and it requires `range.start > 0`.
   `SoundTool` sets icons on Mute, Volume, Fade in and Fade out, and `section = "Fades"` on the
   two fades.
4. `ScrubScale.kt` (new, pure): `trackFraction(spec, value)` and `valueAtFraction(spec, f)`, linear
   or in octaves. `ScrubRow.valueAt`, `ScrubTrack.at` and `snapToDetent` read through them. For
   an octave spec the snap distance is a share of the octave span.
5. `ParamRunGrouping.kt`: a `BoolParam` starts a level run and joins one. `wellBlocks(run)` (pure)
   cuts a run with at most one named section into consecutive blocks of equal `section`. Sound
   gives `[mute, volume] · Fades [fadeIn, fadeOut] · [even]`. A run with more than one section
   is left to the segmented control (Adjust, unchanged).
6. `ParamWellRows.kt` (new): `Well(rows: List<@Composable () -> Unit>)` is the plate with
   hairlines. `WellToggle(label, icon, checked, tag, onChange)` is the spec's toggle row. Add
   plain overloads of `WellButton` (label, icon, danger, enabled, tag, onClick) and of `TextAction`
   (label, icon, tag, onClick). The `ActionParam` versions call them, moving out of `ParamWell.kt`
   (145) as needed. `ScrubWell` builds a `Well`: a `BoolParam` becomes a `WellToggle` written
   through `ParamWriter.edit`, and a level stays a `ScrubRow`.
7. `ParamRowRuns.kt`: a run headed by a `BoolParam` goes to `ParamStrip`. `ParamRowStrip.kt`: with
   at most one section, it draws `wellBlocks`, with a `GroupLabel` over a named block. With more
   than one section it draws the segmented control as now. `ParamRowScalars.kt`: `BoolParamRow`
   becomes a well of one, as `FloatParamRow` is.

**The Clip tab**
8. `ToolManifest.clipOnly: Boolean = false`: the tab exists only while a clip is selected.
   `TrimTool` sets it and loses its `footnote`. Delete `CLIP_SPEEDS`, which only the old chip read.
9. `EditorTabState` takes `hidden: Set<String>`. `shown` is `tabs` without them, `tab` picks from
   `shown`, and `tabFor` still searches all `tabs`, so a clip click from Transform finds Clip in
   the same event. `rememberEditorTabs(tools, blocked, hidden)` clears `chosen` when it names a
   hidden tab, so the next clip selection does not jump back to Clip. `EditorScreen` hides the
   `clipOnly` tabs while `clipSelection == null`.
10. `EditorTabBar.kt` (144): `TabIndicator` and `TabButton` move to `EditorTabButton.kt` first.
    Then the bar lays tabs out by weight. A tab that was not in the last list animates its weight
    and opacity as the spec says, and the indicator's offset and width come from the same weights.
11. `EditorEngineSurface`: `fun drawsPanel(toolId: String): Boolean = false` and `@Composable fun
    ToolPanel(toolId: String, document: EditDocument, selectedClip: String?) = Unit`.
    `EditorInspectorPanel` takes `selectedClip` and draws `engine.ToolPanel` for such a tool in
    place of `EditorToolSection`.
12. `VideoEditorEngine` (150): `playback`, `Timeline` and `timelineKey` move into
    `VideoEditorClips`, which then answers `drawsPanel("trim")` and draws `ClipPanel`.
13. `ClipPanel.kt` (new, engine/video): reads `playback.strip` and draws nothing without it or
    without the selected clip.
    - **Speed** is a `ScrubRow` over `ScrubLevel(FloatParam("speed", "Speed", 0.25f..4f, 1f, unit
      = "×", icon = Speed, octaves = true))`. A drag shows `snapClipSpeed(v)` locally and writes
      nothing. A tap, a release or a typed value calls `strip.apply(clips.withSpeed(id, v))` once:
      one step, and none when nothing changed. This is the old chip's rule: a preview per move
      would re-lay every clip after this one. Reset returns to 1×.
    - **Mute sound** is a `WellToggle` over the clip's own sound
      (`SoundTool` params from `edits.ownOps(clip.key)`). The flip is one step through
      `edits.pasteLayer(clip.key, listOf(op with Mute flipped), { it.toolId == "sound" })`.
    - **Split** (`strip.splitAtPlayHead()`, enabled as the menu's `splits`, now `internal`) and
      **Delete** (`strip.deleteSelected(id)`, enabled with more than one clip). **More actions**
      opens `TimelineMenu` with `MenuTarget.Clip(id)` in a `TimelineMenuState` of its own,
      composed inside the button's box at `Offset(0, height + 4 dp)`.

**Sound, Markup, the scope line**
14. `FilmScope.kt`: `ToolDomain.Annotate` joins `WHOLE_FILM` and `clearsClipSelection`.
15. `MarkupShapeLabel.kt` (new, tools/markup): `MarkupShape.label`, moved from `MarkupBars`'
    private `labelOf`. `CanvasTool.selectionLabel(params: ParamSet, id: String?): String? = null`;
    `MarkupTool` answers with the selected shape's `label`.
16. `ClipScope.still: Boolean`, filled by `VideoEditorClips.clipScopeOf`. `EditorFrameControls.shape:
    String?`. `FilmScopeCrumbs(clip, shape, onWholeVideo)` draws the three forms in the spec
    (tag `editor-scope-shape` on the shape crumb).
17. `EditorScreen` (150): first, the right column (the animated `Box` and its
    `EditorInspectorPanel`) moves to `EditorInspectorDock.kt`. Then `FilmSelection.kt` (new) holds
    `wholeVideo()` and `back(): Boolean` for a film. `wholeVideo()` drops the clip
    (`pickClip(null, NO_CLIP)`) and the shape (`ToolSelection.id = null`), and opens the first shown
    tab when the open one is `clipOnly` or holds the selected shape. `back()` drops the shape if
    there is one, else the clip, and returns false when neither is selected. `EditorScreen` fills
    `shape` from the open tab's `selectionLabel`, passes `wholeVideo` to the crumbs, and writes
    `CanvasKeys.onBack`.
18. `CanvasKeys.onBack: (() -> Boolean)?`; `EditorKeys`: Esc → `if (onBack?.invoke() != true) cancel`.

**Tests**
19. Unit: `ScrubScaleTest` checks that 1× is at 0.5, 2× at 0.75 and 0.5 → ½×, and that a linear
    spec is unchanged. `ParamWellBlocksTest` checks Sound's three blocks and that Adjust's run is
    not cut. `EditorTabsTest` checks that a hidden `clipOnly` tab is not `shown` while `tabFor`
    finds it. `ClipFocusTest` checks that Markup opening clears a clip, as Transform does.
20. UI: `support/TimelineHarness.onTimeline` takes an optional `panel` drawn beside the timeline.
    New `ClipPanelUiTest`: a selected clip shows Speed at 1×. A tap at 75 % writes 2× as one step,
    and the clip's name reads `· 2×`. Reset gives 1×. Mute sound sets the clip's own mute.
    Delete closes the hole. More actions opens the menu with Duplicate. A freeze has no Speed row.
    New `SoundPanelUiTest`: Mute and Volume share one well, `Fades` heads the fades, and
    Even loudness shows only without a clip. `EditorFilmScopeUiTest` gains: no Clip tab without a
    clip, and Clip first with one. Esc drops the clip and keeps the editor open. A second Esc
    cancels. `Whole video` from Clip opens Styles. A shape selected under Markup reads
    `Whole video › Arrow`.

## Files

New: `ScrubScale.kt`, `ParamWellRows.kt`, `EditorTabButton.kt`, `ClipPanel.kt`,
`MarkupShapeLabel.kt`, `EditorInspectorDock.kt`, `FilmSelection.kt`, seven drawables, the new tests.
Edited: `woka/` (step 1), `EditorIcons`, `ParamSpec`, `SoundTool`, `TrimTool`, `TrimClip`,
`ToolManifest`, `ParamScrubRow`, `ParamScrubTrack`, `ParamRunGrouping`, `ParamWell`, `ParamRowRuns`,
`ParamRowStrip`, `ParamRowScalars`, `EditorTabs`, `EditorTabState`, `EditorTabBar`,
`EditorEngineSurface`, `EditorInspectorPanel`, `VideoEditorEngine`, `VideoEditorClips`,
`TimelineMenuItems`, `FilmScope`, `CanvasTool`, `MarkupTool`, `MarkupBars`, `EditorFrames`,
`EditorFrameControls`, `FilmScopeCrumbs`, `EditorScopeLine`, `EditorScreen`, `CanvasKeys`,
`EditorKeys`, `TimelineHarness`, the tests in step 19–20.

The author waived the size limit for a block (2026-11-25). Each touched `.kt` still stays ≤ 150
lines. Where a file passes that, the split is its own step (10, 12, 17 name the known ones).

## Done when

- With a clip selected, the Clip tab grows in first. Speed (log, 1× centred), Mute sound, Split,
  Delete and More all act on that clip, each as one undo step. The tab goes when the selection does.
- Sound reads as the prototype's three wells, with Even loudness only for the whole film.
- The scope line shows `Whole video`, a clip, a still frame, or a Markup shape. `Whole video` and
  Esc take the selection back, and Esc cancels the editor only with nothing selected.
- Opening Markup clears a clip selection, and a shape drawn then belongs to the whole film.
- `Grep "CLIP_SPEEDS|labelOf\("` over `shared/src`: no match.
- Every touched `.kt` ≤ 150 lines; anti-FQN clean; `./gradlew.bat :shared:build` green.

## Result — 2026-11-26

Built; `./gradlew.bat :shared:build` green (1778 tests, probes no longer among them); checked by
hand by the author. The prototype was changed first (`logScrub` in `panels.js`,
`snapSpeed` in `experience.js`) and checked headless (`shot.mjs`): the thumb at 50 % at 1×, a drag
near 2× snapped to it at 75 %. Where the build differs from the steps:

- **Split is always offered**, as in the prototype, rather than enabled as the menu's `splits`:
  asking the play head in composition would recompose the panel every frame a film plays. On a
  join it cuts nothing and pushes no step. `splits` stays private.
- **The inspector's sections are keyed on the selected clip** (`EditorInspectorPanel`). Found by
  `SoundPanelUiTest`: a clip with no edit of its own opens on a document equal to the film's, so
  the sections were skipped and kept the film's rows — Even loudness under a clip, and Sound's
  values the film's instead of the clip's neutral ones.
- **Esc is tested through `CanvasKeys.onBack`**, the slot `EditorKeys` calls first, not by a key
  press: `EditorUnderShell` composes the screen without `EditorHost`'s key effect.
- **Done when's grep** `labelOf\(` also matches `ChoiceParam.labelOf`, an unrelated function; the
  markup's private `labelOf(shape)` is gone (`MarkupShape.label`), and `CLIP_SPEEDS` too.
- **Files beyond the list:** `ClipSpeedRow.kt` (out of `ClipPanel`), `ParamWellButtons.kt` (out of
  `ParamWellRows`), `support/FilmHarness.kt` (the film fixture two UI tests share),
  `EditorClipTabUiTest`, `FilmScopeCrumbsUiTest`, `MarkupSelectionLabelTest`. The drawables are
  `gauge`, `speaker_high`, `speaker_slash`, `fade_in`, `fade_out`, `trash`, `dots_three`. The
  hidden-tab test is in `EditorTabsTest`, Markup clearing a clip in `TimelineTabSwitchTest`.
- **Probes left the build** (the author, the same day): `jvmTest` excludes `com.woka.probe.*`,
  `:shared:probeTest` runs them (`code-style/test.md`). They were 307 of the build's ~410 s.
