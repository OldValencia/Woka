# ME-221 — The clip tabs are the scope

| | |
|---|---|
| **Order** | next in 1.0.6, before ME-285; promoted 2026-11-05 |
| **Depends on** | ME-215 (This Clip), ME-267 (tabs in every tool, `LayerChange`) |
| **Feature** | [Timeline](../timeline.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** For a film, the selected clip *is* the scope: no `All Clips` / `This Clip` switch, and
every selection change is a step `Ctrl+Z` takes back.
**Why now.** ME-267 put clip tabs in every tool, so the switch now says what the tabs already say.
The author wants the editor kept simple (KIS): one way to say *which clip*, not two.

## Shape — the author's choices, 2026-11-05

- **A tab selects; a click on the selected tab deselects.** Selected = that clip's own layer is
  edited (today's This Clip); nothing selected = the whole film (today's All Clips). One clip has
  no tabs and is always the whole film. A click on the film's body only seeks, in every tool.
- **The caption stays**, alone and not clickable: `Applies to every clip` / `Applies to clip 2`.
- **Whole-video tools clear the selection when opened** — Crop, Transform, Resize, Cover (the
  `Geometry` and `Output` domains) — and the tab that lost it **pulses once** (a scale up and
  back), so the author sees that it went. `Clips` keeps the selection: pick a clip, then work on
  it with every tool; a cut made in `Clips` still lands in the shared layer.
- **Every selection change is an undo step**, and so is every edit, in one line: select clip 2,
  Adjust, `Ctrl+Z` takes back the Adjust, `Ctrl+Z` again the selection. Leaving a clip no longer
  loses the shared layer's history (today `detachFrame` starts it afresh).
- **Deleting the selected clip clears the selection in the same step**; `Ctrl+Z` brings back the
  clip *and* its selection.
- **Tabs are not coloured** — orange is the selection's, grey every other tab's, the dot a look.
- **Animated stills are untouched**: a GIF keeps `All Frames` / `Single Frame` exactly as it is.

## Steps

1. `core/SessionShot.kt` (new): `data class SessionShot(document, base, frame, elsewhere,
   openedWith, selection: String?)` — the whole of a film session's state, immutable.
2. `EditorHistory`: a third parallel list `shots: List<SessionShot?>`, kept in step with
   `entries` and `changes` by `push(document, change, shot)` and by the cap.
3. `EditorSession`: `linear` (off by default — a still and a GIF never set it), `clipSelection:
   StateFlow<String?>`, and `focus: StateFlow<Int>` backing `frame`. `moveTo` restores the target
   entry's shot when it has one, else does what it does today (`stepLayers`).
4. `core/EditorSessionFocus.kt` (new): `useLinearHistory(sharedTools)` — gives the opening
   entries their shots and records which tool ids write the shared layer; `pickClip(id, key)` —
   stashes the open clip, composes the next (`NO_CLIP` = the shared layer), measures it against
   what the file holds for it, and pushes one step; `record(document, change)` — the one push
   every linear-mode step goes through.
5. `EditorSessionEdits.commit`: in linear mode, an op of a shared tool committed while a clip is
   open goes into `base`, and the open clip's own ops are recomposed over it; every commit records.
   `pasteOnClip` onto the **open** clip changes the document rather than a stash; it records.
   `reset` records.
6. `EditorTimelineScope`: `pick(id: String?, key: Int)` (the step) and `select(id)` becomes quiet
   — no step, used inside one that is already being made (delete, duplicate, join, reset cut).
   `StripActions`: the layer copy and the quiet select happen **before** `apply`, so the step's
   shot holds them. `ClipTabs`: a click on the selected tab picks `null`.
7. Shell, video only (`frames.clips`): `EditorFrameBand` draws the caption instead of the
   segmented control; `EditorHost.editScope` follows `session.focus`; `unavailableIn` returns
   nothing for a film; `EditorScreen` clears the selection (a pick) when a `Geometry`/`Output` tool
   opens, and the strip pulses the tab that lost it. `StripGestures.scrub` no longer focuses.
8. Tests: `jvmTest/unit/ClipFocusTest` — select/edit/undo/undo walks back in one line; deselect
   keeps the shared history; a cut under a selection lands in `base`; delete then undo restores
   the selection; `isDirty` false after selecting with nothing changed; the GIF path untouched
   (existing `EditorSessionSwitchTest`, `EditorFrameScopeUiTest` green). `jvmTest/ui/ClipLookUiTest`
   — a second click on the selected tab deselects; opening Crop deselects.

## Files

`core/SessionShot.kt` (new), `core/EditorSessionFocus.kt` (new), `core/EditorHistory.kt`,
`core/EditorSession.kt`, `core/EditorSessionEdits.kt`, `core/EditorSessionClips.kt`,
`core/EditorSessionResets.kt`, `core/EditorTimelineScope.kt`, `shell/EditorTimelineScopeImpl.kt`,
`shell/EditorBottomPanel.kt`, `shell/EditorScreen.kt`, `shell/EditorHost.kt`,
`shell/EditorFrameBand.kt`, `shell/EditorFrameScopeStrip.kt`, `engine/video/VideoEditorClips.kt`,
`engine/video/ClipStrip.kt`, `engine/video/ClipTabs.kt`, `engine/video/StripActions.kt`,
`engine/video/StripGestures.kt`, tests. **Over §4a's eight on purpose (§4 outranks it):** the
smallest slice the author can see is the switch gone *and* undo walking through selections — half
of it is a switch that is gone and an undo that loses work.

## Done when

- a film shows no `All Clips` / `This Clip` control, a GIF still does;
- the selected tab decides what Adjust, Styles, Markup and Audio edit; the caption says so;
- `Ctrl+Z` / `Ctrl+Y` walk through edits and selection changes in one line, shared-layer steps
  included after a clip was opened and left;
- opening Crop, Transform, Resize or Cover clears the selection with a pulse; `Clips` keeps it;
- `./gradlew.bat :shared:build` green.
