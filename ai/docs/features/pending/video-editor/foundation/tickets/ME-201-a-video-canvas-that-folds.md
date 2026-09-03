# ME-201 — A video canvas that folds

| | |
|---|---|
| **Order** | first in the feature; nothing in the other six folders can start without it |
| **Depends on** | nothing unshipped. ME-001…ME-182 and ME-106 are all in [the image editor](../../../../implemented/image-editor/image-editor.md) |
| **Feature** | [Foundation](../foundation.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** Open a video in the existing editor, drawing the player's current frame through the folded
`EditDocument`, so the five reused tools work on a moving picture.

**Why now.** Everything in the feature is priced against one unmeasured number — whether a document
folds at frame rate — and nothing else can be built until a video opens at all. Both doors already
ask one function whether a file is editable, and it answers no for every video.

**Phase 0 deliberately cannot save.** The author's call, 2026-09-03. See
[`foundation.md`](../foundation.md) §3.5 for the capability that says so and
[`save/`](../../save/save.md) for the ticket that deletes it.

---

## Steps

1. **Measure the shape of the cost first, in a probe.** Add `VideoFoldProbe` in `com/woka/probe/`,
   following `EditorMemoryProbe`'s shape — a `@Test` that generates its sources, prints a table and
   names its own Gradle invocation. It draws a generated frame into an offscreen Skia `Surface`
   through `drawRecipeImage` and reports ms/draw at 1080p and 2160p for: **(a)** an empty recipe,
   **(b)** a colour-matrix-only recipe, **(c)** Adjust's full shader chain, **(d)** (c) plus one
   markup paint. Record the table in this ticket with the date.
   **Amended 2026-09-03, before it was written.** The step used to read "the gate: (c) under 16 ms",
   and that gate cannot live here: a headless test can only make a **CPU raster** surface, while the
   app draws through skiko's hardware backend, where a shader chain is a fragment shader rather than
   per-pixel SkVM. Absolute milliseconds from a raster surface compared against a frame budget would
   be measuring the wrong machine, and the gate would fail spuriously. So the probe reports the
   **ratio** between the cases — a property of the work rather than of the backend — and the 16 ms
   question moves to step 9, by hand, against the real canvas.
2. Add `Preview` to `EditCapability`, after `Recorded`, with KDoc saying it is temporary, that it
   means "the editor opens and neither save exists yet", and that **ME-280 deletes it**.
3. In `ImageEditWrites.writeByCapability`, add `EditCapability.Preview -> EditSaveResult.Failed(
   "Video edits cannot be saved yet.")`. Say in a comment that it is unreachable for an image — the
   factory answers `Preview` only for a video — and that the branch exists so the `when` stays total.
4. In `EditorEngineFactoryImpl.capability`, replace the single `mediaKind() != MediaKind.Image`
   guard with a branch: `null` → `Unavailable`; `MediaKind.Image` → the existing carrier/encoder
   `when`, unchanged; `MediaKind.Video` → `Preview`. In `create`, build a `VideoEditorEngine` for
   `MediaKind.Video` and keep `ImageEditorEngine` for `MediaKind.Image`. Delete the "video returns
   null for now" paragraph from the class KDoc and replace it with what is now true.
5. Add `VideoEditorEngine` in `feature/editor/engine/video/`, mirroring `ImageEditorEngine`:
   `kind = MediaKind.Video`, `capability` passed in, `sourceSize` `by lazy` from a one-shot
   `FFmpegFrameGrabber` open reading the video track's coded `width`/`height` — with
   `probesize`/`analyzeduration` tuned as `SeekPreviewSource` does, and the grabber closed
   immediately. `recipe(document)` calls the existing `fold(document, registry, sourceSize)`;
   `drawnSize` and `previewMatrix` come off the recipe exactly as the image engine's do.
   `save(layers)` and `revert(...)` return `EditSaveResult.Failed("Video edits cannot be saved
   yet.")` and `NothingToDo` respectively. `Surface` and `Thumbnail` delegate to step 6.
6. Add `VideoEditorCanvas` in the same package. It `remember`s a `VideoPlayerState` keyed on the
   path, disposes it in `onDispose`, and draws `player.currentFrame` through
   `RasterSurface(source, sourceSize, recipe, modifier)`. **Read `currentFrameId` in the same
   composition as the bitmap** and call `player.frameRetention.acknowledge(id)` from a
   `drawWithContent` after `drawContent()`, as `MediaViewerFrame` does — without it the decoder frees
   nothing. Draw nothing until the first frame arrives. `togglePlayback()` on the engine calls
   `player.togglePlayPause()`. **No HDR branch** — that is ME-205, and an HDR video draws
   untone-mapped until then.
7. In `MediaViewerScreenEffects.MediaViewerActiveItemEffects`, pause the viewer's player while an
   editor is open: a `LaunchedEffect` on `EditorPresence.isOpen` that calls `togglePlayPause()` when
   it becomes true and restores the previous state when it becomes false. **Without this the editor's
   player and the viewer's decode the same file at once, with two audio streams.** Check the file's
   length with `wc -l` first — it was 130 lines on 2026-09-03, and `MediaViewerScreen.kt` is already
   **156** and must not be touched: if the effect does not fit here, the split is its own numbered
   step.
8. Add `VideoEditorEngineTest` in `jvmTest/kotlin/com/woka/unit/`, using `episodeTwoFile()` from
   `PlayerHarness`: the factory answers `Preview` for the sample and builds a non-null engine;
   `kind` is `MediaKind.Video`; `sourceSize` is the corpus file's coded size; `drawnSize` follows a
   crop op rather than the source; `save` answers `Failed` and never throws; `capability` for a
   `.png` fixture is still `Recorded` and for `still.bmp` still `Unavailable`.
9. **Then the gate, by hand, against the real canvas** — the measurement step 1 cannot make. It is
   last because it needs everything above it to exist, and it gates
   [`picture/`](../../picture/picture.md) rather than this ticket. The conditions are in
   **Done when**.

---

## What changed while building it — 2026-09-03

Recorded rather than edited into the steps above, so the plan and what happened can be told apart.

- **The player holder arrived here, not in ME-206.** Step 5 said `Surface` *and* `Thumbnail`
  delegate to the canvas, and that makes `Thumbnail` a **second reader of the same player** — a
  filter strip asks for fourteen of them. So the player cannot be `remember`ed inside either
  composable, and `VideoPlayerHolder` is in `VideoEditorCanvas.kt` from this ticket. ME-206's step 6
  was amended to use it rather than introduce it.
- **Step 7 named the wrong file.** `MediaViewerActiveItemEffects` is called at
  `MediaViewerScreen.kt:32` and `videoState` is not created until line 57, so the pause cannot live
  there. It went into `MediaViewerAmbientEffects` (`MediaViewerAmbient.kt`, 44 lines), which already
  takes the player and whose KDoc scopes it to *session* effects — which is what standing still
  while an editor is open is.
- **Two files the plan did not count**, both consequences rather than new work: `EditorEntry.kt`'s
  KDoc said the factory "returns null for video (until ME-201)", and `EditorEntryTest`'s
  `a video is not editable` asserted exactly the behaviour this ticket reverses. A comment and a test
  that say the opposite of the code are worse than none.

---

## Files

`shared/src/jvmTest/kotlin/com/woka/probe/VideoFoldProbe.kt` (new) ·
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditCapability.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/ImageEditWrites.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/EditorEngineFactoryImpl.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/video/VideoEditorEngine.kt` (new) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/video/VideoEditorCanvas.kt` (new) ·
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/MediaViewerScreenEffects.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/unit/VideoEditorEngineTest.kt` (new) ·
`shared/src/commonMain/kotlin/com/woka/feature/editor/EditorEntry.kt` (edit — one KDoc sentence) ·
`shared/src/jvmTest/kotlin/com/woka/unit/EditorEntryTest.kt` (edit — one reversed assertion)

Eight planned, ten written, and the two extra are the comment and the assertion that stated the old
behaviour. Nothing in it can be deferred without leaving a state that is worse than not shipping:
dropping step 7 plays two soundtracks at once, and dropping step 2 or 3 makes `Done` fail silently.

---

## Done when

- `VideoFoldProbe`'s table is **in this ticket, with a date**. It is a reading, not a gate: what is
  checked is that the ratios are recorded and that nothing in the table is absurd.
- `Edit` is live on a video in both doors — the viewer's control pill and the grid's context menu —
  and greyed on nothing that was editable before.
- Opening a video shows the frame running on the canvas; Crop, Transform, Adjust, Filters and Markup
  each visibly change it, and undo takes each back.
- Clicking the canvas starts and stops it; the viewer underneath is silent while the editor is up and
  resumes as it was on `Cancel`.
- Memory is flat over two minutes of an open editor on a 2160p file — the retention call is the thing
  being checked, and its absence shows as unbounded growth rather than as a wrong picture.
- `Done` closes the editor and the banner says "Video edits cannot be saved yet." Nothing is written,
  and `SourceStamp` reports the file unchanged.
- No still-image behaviour changes: `EditorSaveTest`, `FrameRecipeTest`, `FrameScopeSaveTest` and
  `HdrBakeTest` pass untouched.
- The anti-FQN grep over every edited file is clean.
- `./gradlew.bat :shared:build` green.

**And the gate, measured by hand once the canvas exists (step 9).** Open a 2160p file in the editor,
open Adjust, move Exposure, Highlights, Shadows, Noise and Sharpness off their detents so the whole
chain is folded, and let it play. **The gate is that it plays at the rate the file declares** —
`PlaybackHealth` already reports displayed rate against declared rate, which is the number to read
rather than an impression. Record the result here with the date. Below the declared rate, ME-205 and
[`picture/`](../../picture/picture.md) are re-planned; the rest of this ticket stands either way,
which is why it is a gate on that folder and not on this one.

---

## Measured

**`VideoFoldProbe`, 2026-09-03.** CPU raster, mean per draw, each case stopped at 1500 ms or 60
draws — `draws` is how many that mean is over, so a row of 1 is one sample and reads as such.

| size | document | draws | ms / draw | × plain |
|---|---|---|---|---|
| 1080p | empty | 60 | 1.3 | 1.0× |
| 1080p | matrix (Exposure) | 60 | 7.1 | 5.4× |
| 1080p | tone curve | 60 | 23.0 | 17.7× |
| 1080p | noise | 51 | 29.5 | 22.6× |
| 1080p | sharpen | 14 | 114.0 | 87.5× |
| 1080p | all four | 3 | 577.3 | 443.1× |
| 1080p | all four + a paint | 3 | 578.0 | 443.6× |
| 2160p | empty | 60 | 5.7 | 1.0× |
| 2160p | matrix (Exposure) | 52 | 29.0 | 5.2× |
| 2160p | tone curve | 16 | 96.9 | 17.5× |
| 2160p | noise | 13 | 117.3 | 21.2× |
| 2160p | sharpen | 4 | 462.0 | 83.6× |
| 2160p | all four | 1 | 2311.9 | 418.1× |
| 2160p | all four + a paint | 1 | 2318.5 | 419.3× |

**The stages multiply, they do not add.** 5.4 + 17.7 + 22.6 + 87.5 is 133×, and the four together
cost **443×**. The reason is the chain's own shape: each effect takes the previous one as its
*child*, so every tap of the sharpen convolution re-evaluates noise, then the tone curve, then the
colour filter, then the image. Nine taps over a three-deep chain is ≈ 9 × 43 ≈ 390, which is the
number in the table. **A trailing convolution multiplies everything before it by its tap count.**

Three readings follow, and they are for [`picture/`](../../picture/picture.md) rather than for this
ticket:

- **A colour matrix is cheap and safe at 5×** — so Filters, and the eight Adjust sliders that fold to
  a matrix, are not in question at any size. What is in question is the four sliders ME-162 made
  shaders: Highlights, Shadows, Noise and Sharpness.
- **Order is a cost decision as well as a correctness one.** `adjustStage` puts sharpen last
  deliberately — "sharpen last because it is what the eye checks, and it should check finished
  colour" — and last is also the position that multiplies the most. Reversing it is ME-165's decision
  to reopen with a new fact, not this ticket's.
- **The 2160p rows are a worst case, not the common one.** `RenderScale` already decodes a video at
  about the surface's size, so a 4K file on a 1080p-sized canvas folds 1080p frames. The 4K rows are
  what a genuinely 4K canvas would pay.

**A backend swap does not make a 443× multiplier free**, which is why step 9's by-hand check is
load-bearing rather than a formality: the GPU has thousands of cores but the same texture-fetch
count. The plausible outcome is that matrix-only documents run at frame rate on any canvas and the
four shader sliders do not, at 4K.

**Partly run, 2026-09-03.** The author opened a video in the editor and reported: *"всё
регулируется, инструменты рисуются, видео воспроизводится… всё работает 1 в 1 как и в image
editor"*. So the **functional** half of step 9 has passed — the canvas draws the player's frame
through the fold, the five reused tools change it live, and nothing about the shell behaves
differently from a photograph's editor.

**The gate itself is still open**, and it is a narrower question than "does it work": what has not
been reported is the *file's resolution*, whether **Highlights, Shadows, Noise and Sharpness** were
moved off their detents at the same time — the four sliders that fold to shaders, and the only ones
the probe found expensive — and what `PlaybackHealth` made of the displayed rate against the declared
one. Until that is answered, [`picture/`](../../picture/picture.md) stays planned on the assumption
that matrix work is free and shader work is not.

Three defects came out of the same run and all three were ME-207's three answers, against the build
that had none of them: no sound, the space bar re-opening the last tool instead of pausing, and the
canvas opening playing. See ME-207.
