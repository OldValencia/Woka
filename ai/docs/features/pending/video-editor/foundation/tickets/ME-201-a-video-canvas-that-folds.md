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

1. **Measure first, in a probe, before any of the below.** Add `VideoFoldProbe` in
   `com/woka/probe/`, following `EditorMemoryProbe`'s shape — a `@Test` that generates its sources,
   prints a table and names its own Gradle invocation. It draws a generated 3840×2160 `ImageBitmap`
   into an offscreen Skia `Surface` through `drawRecipeImage`, 200 times per case, and reports
   ms/draw for: **(a)** an empty recipe, **(b)** a colour-matrix-only recipe, **(c)** Adjust's full
   shader chain, **(d)** (c) plus one markup paint. Record the table in this ticket with the date.
   **The gate: (c) under 16 ms.** Above it, stop and re-plan
   [`picture/`](../../picture/picture.md) rather than continuing — the rest of this ticket is still
   correct, but Adjust and Filters are not.
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

---

## Files

`shared/src/jvmTest/kotlin/com/woka/probe/VideoFoldProbe.kt` (new) ·
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditCapability.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/ImageEditWrites.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/EditorEngineFactoryImpl.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/video/VideoEditorEngine.kt` (new) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/video/VideoEditorCanvas.kt` (new) ·
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/MediaViewerScreenEffects.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/unit/VideoEditorEngineTest.kt` (new)

Eight files, which is the line `tickets.md` §4 draws. Nothing in it can be deferred without leaving a
state that is worse than not shipping: dropping step 7 plays two soundtracks at once, and dropping
step 2 or 3 makes `Done` fail silently.

---

## Done when

- `VideoFoldProbe`'s table is **in this ticket, with a date**, and case (c) is under 16 ms — or the
  ticket is stopped at step 1 and [`picture/`](../../picture/picture.md) is re-planned.
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

---

## Measured

*(the probe's table goes here, with its date, when step 1 runs)*
