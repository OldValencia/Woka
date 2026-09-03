# ME-207 — The canvas opens paused, and a drag holds it

| | |
|---|---|
| **Order** | third in the feature, after ME-206 |
| **Depends on** | ME-201 and **ME-206** — step 1 edits `VideoPlayerHolder`, which ME-206 introduces |
| **Feature** | [Foundation](../foundation.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** Make the editing surface stand still by default, and hold still while a control is being
dragged.

**Why now.** ME-201 hands over a canvas that plays from the moment it opens, which is the wrong
default for every tool in the rail: a crop rectangle, a markup arrow and a split all have to be
placed on a particular frame, and a picture moving under the cursor is what stops that from being
possible.

**The three answers are the author's, given 2026-09-03**, and this ticket is only their wiring:

| Question | Answer |
|---|---|
| Playing or paused on open? | **Paused, on the frame it opens at.** A click or the space bar starts it |
| What does the space bar do? | play / pause |
| A slider dragged while it plays? | **It holds for the length of the drag** and resumes on release, only if it was playing |
| How loud? | **The app's own media volume**, read the way the viewer reads it. Answered 2026-09-03 after ME-201 shipped muted and the author's first run reported the silence |

---

## Steps

1. Where `VideoPlayerHolder` builds the player (ME-206 step 6), set `isPlaying = false` on the new
   `VideoPlayerState` immediately. Its `init` calls `startPlayback()`, which starts the decode loop —
   so the first frame is presented and *then* the presenter parks, which is exactly the wanted state:
   a picture on screen, standing. **Not `togglePlayPause()`**: that seeks to 0 when the position is
   within `END_THRESHOLD_MS` of the end and clears `reachedEnd`, and neither is wanted at open.
2. Confirm `engine.togglePlayback()` reaches `player.togglePlayPause()`. The canvas end is already
   wired — `editorCanvasGestures` has called `engine.togglePlayback()` on a tap since ME-140, for the
   animated still, and its KDoc says the video player is its second implementation.
3. `EditorKeys`: add a parameter `onTogglePlayback: () -> Unit = {}`, held through
   `rememberUpdatedState` like every other callback in that file, and a branch
   `event.key == Key.Spacebar -> consume { curTogglePlayback() }`. It must go **after** the
   `curCanvasKeys?.typing == true` early return, or a space typed into a markup caption stops the
   video instead of reaching the field. Defaulted, so the image editor's behaviour is unchanged.
4. `EditorHost`: pass `onTogglePlayback = engine::togglePlayback` to `EditorKeyEffect`. One line; the
   file is 140 lines and stays inside the mandate.
5. **A drag holds the picture, and the document already says when one is in flight.**
   `EditDocument.pending` is the operation being dragged: `preview(op)` sets it and `commit()` clears
   it, decided by the tool's `ToolInteraction` rather than by its code. So `VideoEditorCanvas` — which
   is handed the document on every recomposition — runs a `LaunchedEffect(document.pending != null)`
   that pauses on true, remembering whether it was playing, and restores on false. One rule covers a
   `Continuous` slider and a `Canvas` tool's drag, and **no new plumbing reaches the shell**.
6. **Do not use `pauseForSeek()` / `resumeAfterSeek()` for step 5**, tempting as the remember-and-
   restore is: they also set the `@Volatile isSeeking` flag, which is the seek handshake all three
   decode coroutines read through `PlaybackGeneration`, and a brightness drag is not a seek. Two
   private fields on the canvas, and a comment saying this.
7. Tests:
   - `jvmTest/kotlin/com/woka/ui/EditorKeysUiTest.kt` (exists): the space bar asks the engine to
     toggle, and does **not** while `CanvasKeys.typing` is true. Against `FixtureEngine`, whose
     `playbackToggles` counter `EditorCanvasPlaybackUiTest` already uses.
   - `jvmTest/kotlin/com/woka/ui/EditorCanvasPlaybackUiTest.kt` (exists): extend it with the drag
     rule — a document carrying a pending op pauses, and clearing the pending op resumes only if it
     was playing. Asked of the shell's contract, so `FixtureEngine` grows a `pauseRequests`-shaped
     counter rather than the test opening a decoder.
   - `jvmTest/kotlin/com/woka/ui/VideoCanvasHoldUiTest.kt` (new) for the drag rule — **the planned
     `FixtureEngine` counter does not reach it**, because the rule lives in the video canvas and the
     shell knows nothing about pausing. It drives `HoldWhileDragging` against a real player from the
     corpus, and covers the half a naive implementation gets wrong: releasing a drag must not *start*
     a video the user had stopped. `startKoinOnce()` is required — the decode loop reads
     `AppDependencies.settingsRepository` for playback resume.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/video/VideoPlayerHolder.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/video/VideoEditorCanvas.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorKeys.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorHost.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/support/FixtureEngine.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/ui/EditorKeysUiTest.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/ui/EditorCanvasPlaybackUiTest.kt` (edit)

**Nothing is added to a shortcuts popover, because there is none.** Checked 2026-09-03: the image
editor's design names a `?` popover built from the manifests and no such file exists under
`feature/editor/`. Space is a shell key like `Esc` and `Ctrl+Z`, which are equally unlisted; building
the popover is not this ticket's work and inventing a home for one key would be.

## What the author's run of ME-201 confirmed — 2026-09-03

All three of this ticket's answers arrived as observations of the ME-201 build, which is the build
that had none of them:

- *"звука нет"* — ME-201 opened the player at `0f` and named this ticket as the owner of the number.
  The answer is the app's media volume.
- *"если нажал на инструмент, то пробел открывает опять этот инструмент, а не ставит видео на
  паузу"* — **a rail button keeps focus after it is clicked.** This is the confirmation that step 3's
  mechanism is the right one rather than a guess: `EditorKeyEffect` installs its handler in
  `GlobalShortcutState.onPreviewKey`, which runs in the **preview (tunneling) phase from the root**,
  before focus reaches any button — so consuming `Spacebar` there takes it away from the button. The
  bug was not a focus bug to chase; it was the absence of this step.
- *"запускается не на паузе"* — step 1, and the same build.

## Done when

- A video opens in the editor showing a frame and not moving.
- A click on the canvas plays; a second click pauses. The space bar does the same, and typing a space
  into a markup caption does neither.
- Dragging Brightness while it plays holds the picture for the drag and resumes on release; dragging
  it while paused leaves it paused.
- A photograph's editor behaves exactly as before: the new `EditorKeys` parameter is defaulted and
  `FixtureEngine`'s counters prove the shell asks rather than acts.
- The anti-FQN grep over every edited file is clean.
- `./gradlew.bat :shared:build` green.
