# ME-288 — mp4 and mov carry an edit, and `Preview` is deleted

| | |
|---|---|
| **Order** | second in `save/`, straight after ME-280 |
| **Depends on** | ME-280 (the in-place write) |
| **Feature** | [Video Editor — Save (`Done`)](../save.md) |

**Goal.** Ask a video the same carrier question an image is asked, so an `mp4` or a `mov` keeps its
edit in its own `uuid` box — and delete the capability that existed only to say it could not.

**Why now.** ME-280 made the write O(payload); this is what reaches it. `Done` on a video currently
fails with a sentence, which is what `EditCapability.Preview` was added to say honestly for one
phase. That phase ends here, and `Preview`'s own KDoc says so.

**The other half of the block promoted as ME-280.**

---

## Design notes read against the tree, 2026-09-04

**`mkv`, `webm` and `avi` lose the `Edit` entry, and that is the recorded decision rather than a side
effect.** `EditCapability.Preview`'s KDoc: *"a video answers `Recorded` or `Unavailable` like anything
else"*. `EditorEngineFactoryImpl`'s own KDoc holds the principle — *"Refusal moved forward,
2026-08-19… A format with neither a carrier nor an encoder now has no engine at all, so both doors
grey out before anything is edited"* — and the alternative is opening the editor, letting the author
crop, and refusing at `Done`, which spends the work before saying it cannot be kept.

It is worth stating plainly because **the test corpus is almost all `mkv`**, so this is the visible
change: a Matroska video stops offering `Edit` until **ME-282** gives it a carrier, which is the
second-next ticket here and was sequenced second for exactly this reason (the library is mixed,
author 2026-09-03).

**`formatFor` is not asked for a video.** The image table asks two questions in order — a carrier,
then a Skia encoder for bucket B. There is no encoder for a video on that path and there will not be
one: re-encoding a video is `render/`, which produces a *new file* by decision (§3.5). So the video
answer is one question wide.

**The engine reuses the image engine's write, and must not grow a second one.** `recordRecipe` and
`revertRecipe` are `internal` in `engine.image`, and `VideoEditorEngine` already imports `fold` and
`RasterRecipe` from there — so the precedent is set and the alternative is two merge implementations
that will disagree. What the video engine adds is the stamp, the eviction and nothing else.

**Reading back is not optional.** `storedDocument` is the interface's empty default today. Without it
a saved video reopens blank, `Revert` draws disabled on a file that carries an edit, and the next
`Done` hands `recordRecipe` an empty base — which prunes to nothing and **clears the recipe it just
wrote**. One override covers all three: `storedDocumentIn` and `storedBase` default to it.

**Revert comes with it, for the same reason.** Once `storedBase()` is real,
`EditorHostControls.carriesStoredEdit` is true for a saved video and the red `Revert` button
*enables*. Leaving `revert` at the interface's `NothingToDo` would ship a live-looking control that
does nothing — the thing `MediaViewerFullscreenControls` already refuses. It is three lines over
`revertRecipe`. **ME-286 still owns** the `Done ▾` menu and the per-container reason it prints; this
is only the button being honest.

**Nothing folds a video's recipe on a read path yet** — the viewer's frame, the grid poster, PiP and
the seek preview all still show the original. That is **ME-284**, which waits on `playback/`'s clip
walk, and until then a saved video shows its edit in the editor and nowhere else. Stated because it
looks like a bug and is a sequence.

**`VideoEditorEngine.kt` is at 150**, so the split is step 4 and it is `ImageEditorReads`' precedent
exactly: the questions the engine asks a *file*, which is the `codedFrameSize` already sitting at the
bottom of that file as a private top-level function.

---

## Steps

1. **Two extensions.** In `carrierFor` (`RecipeCarrier.kt`, 133 lines):
   `"mp4", "mov" -> IsoBmffRecipe`, with a comment saying it is the same container family the
   photographs use — `livephoto.mov` in the corpus opens `\0\0\0\x14ftypqt  `, measured — and that
   `IsoBmffRecipe.accepts` is what refuses an older QuickTime file with no `ftyp`. Update the `bmp`
   sentence in the KDoc above it, which says bucket C is one format wide: it now also holds the three
   video containers with no carrier yet.

2. **The factory asks a video the carrier question.** In `EditorEngineFactoryImpl` (83 lines),
   `MediaKind.Video -> videoCapability(file)`, a private one-liner:
   `if (carrierFor(file) != null) EditCapability.Recorded else EditCapability.Unavailable`. Its
   comment says why `formatFor` is not in it. The class KDoc's `Preview` paragraph is rewritten to
   say what the answer is now.

3. **Delete `EditCapability.Preview`** — the enum case and its KDoc (`EditCapability.kt`, 55 lines),
   the `EditCapability.Preview ->` branch in `writeByCapability` (`ImageEditWrites.kt`, 148 lines,
   which drops to 144 and is a relief), and the sentence naming it in `EditorEntry.kt`'s KDoc. The
   enum's own header paragraph — *"plus `Preview`, which is a phase rather than a bucket"* — goes
   with it, so the type reads as §5.1's three buckets again.

4. **Split `VideoEditorEngine.kt` (150 lines) — twice, as it turned out.** Measured while
   implementing: moving the reads out freed 25 lines and step 5 wanted 40, so the file reached 176
   and needed the second cut as well. Both are precedented and both are verbatim moves:

   New `VideoEditorReads.kt` beside it:
   `codedFrameSize` moved **verbatim with its KDoc**, plus
   `internal fun storedVideoDocument(file: File): EditDocument = EditRecipe.documentIn(file, 0) ?: EditDocument()`.
   Its header says what `ImageEditorReads`' says — every function is a read of a path, no engine
   state — and why frame 0 is the only frame a video has.

   New `VideoEditorWrites.kt`, which is `ImageEditWrites`' cut: a `class` rather than functions,
   unlike its image sibling, because the stamp is state and there is nowhere else for it to live.
   It holds `save`, `revert` and the `afterWriting` both share, and it takes the file and the
   capability.

5. **The engine saves, reverts and reads back.** In `VideoEditorEngine`:
   - `override fun storedDocument(): EditDocument = storedVideoDocument(file)`;
   - `private val writes = VideoEditorWrites(file, capability)`;
   - `save` and `revert` each one line on `Dispatchers.IO` over it. Inside `VideoEditorWrites`:
     `recordRecipe(file, layers, stamp)` when `capability` is `Recorded`, otherwise
     `EditSaveResult.Failed("${file.extension.uppercase()} videos cannot keep an edit yet.")` so the
     branch is total; `revertRecipe(file)` for the revert, with **the parameters ignored on
     purpose** — which the interface's own KDoc licenses (*"a video timeline has no frame to key
     on"*); a `SourceStamp.of(file)` re-read and `evictCachesFor(file)` after either.
   - The class KDoc's *"It cannot save, and says so"* paragraph is replaced by what it now does.

6. **Tests.**
   - `jvmTest/unit/VideoEditorEngineTest.kt` (100 lines) — `a video is editable and answers Preview`
     becomes two assertions over the two answers: `LIVE_PHOTO_MOTION` is `Recorded` and the factory
     builds an engine for it; `episodeTwoFile()`'s `mkv` is `Unavailable` and `create` returns
     **null**. `a save fails with the sentence the banner prints` keeps its shape against the `mkv`,
     whose engine no longer exists — so it moves to asserting `create` refuses rather than that
     `save` explains, and the sentence assertion goes with `Preview`. The crop/`drawnSize` case keeps
     `episodeTwoFile()` by constructing the engine directly? **No** — it goes through the factory, so
     it moves to `LIVE_PHOTO_MOTION`. The image-table case is untouched and is the regression proof.
   - `jvmTest/unit/EditorEntryTest.kt` (81 lines) — `a video is editable` becomes *an `mp4`/`mov` is,
     an `mkv` is not yet*, with the ME-282 reason in the comment. This is the test that records the
     visible change.
   - `jvmTest/integration/VideoRecipeTest.kt` (new) — the round trip on a real container, over a
     copy of `LIVE_PHOTO_MOTION`: an engine's `save` of a one-op document succeeds; the file grew;
     `EditRecipe.read` returns the op; a **second** engine on the same path answers `storedDocument`
     with it (the reopen that step 5 exists for); a second `save` leaves the length unchanged
     (ME-280's in-place write, now reached by a video); `revert` returns the file to its original
     bytes.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/recipe/RecipeCarrier.kt` (edit) ·
`.../engine/EditorEngineFactoryImpl.kt` (edit) ·
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditCapability.kt` (edit) ·
`.../commonMain/kotlin/com/woka/feature/editor/EditorEntry.kt` (edit) ·
`.../jvmMain/kotlin/com/woka/feature/editor/engine/image/ImageEditWrites.kt` (edit) ·
`.../engine/video/VideoEditorEngine.kt` (edit) · `.../engine/video/VideoEditorReads.kt` (new) ·
`.../engine/video/VideoEditorWrites.kt` (new) ·
`shared/src/jvmTest/kotlin/com/woka/unit/VideoEditorEngineTest.kt` (edit) ·
`.../jvmTest/kotlin/com/woka/unit/EditorEntryTest.kt` (edit) ·
`.../jvmTest/kotlin/com/woka/integration/VideoRecipeTest.kt` (new)

## Done when

- an `mp4` or `mov` answers `EditCapability.Recorded`, and `Done` in the editor writes a `uuid` box
  the same way a `heic` does — one `RandomAccessFile`, no whole-file read;
- reopening that video shows its edit, and a second `Done` leaves the file the same length rather
  than clearing the recipe;
- `Revert` on it removes the box and the file is byte-identical to before the first save;
- an `mkv`, `webm` or `avi` answers `Unavailable`, both doors grey out, and `EditorEntryTest` says so
  in words;
- `EditCapability.Preview` does not appear anywhere — `grep -rn "EditCapability.Preview" shared/src`
  is empty, comments included;
- the image table is unmoved: `VideoEditorEngineTest`'s PNG/BMP case passes unedited, and
  `HeicRecipeTest` and `RecipeInPlaceTest` are byte-identical;
- `VideoBandUiTest` and `VideoCanvasHoldUiTest` are untouched — measured, they drive
  `VideoPlayerHolder` directly and never ask the factory;
- `wc -l` ≤ 150 for every file above;
- the anti-FQN grep (`mandates.md`) has been run on every edited `.kt` and is clean;
- `./gradlew.bat :shared:build` green.
