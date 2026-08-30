# ME-126 — A recipe keyed by frame

| | |
|---|---|
| **Order** | 63 — the model, before the two tickets that show it |
| **Depends on** | ME-108 (the ISOBMFF carrier that made a burst editable), ME-133 (readers apply the recipe), ME-134 (a save merges rather than replaces) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Make the recipe a **payload keyed by frame** — an un-keyed form that still means "every frame"
plus optional per-frame overrides — and teach the readers and the save to use it. **No UI.**

**Why now.** A frame set is several pictures in one file and one recipe covers all of them, so turning
one frame of a burst turns the burst. That was confirmed acceptable by hand on 2026-08-19 and it is the
honest default, but per-frame is wanted, and it is a *format* change: every file written before it must
keep meaning what it meant. Doing the model first, with no control that can write a key, is what makes
that provable — this ticket ships with **no observable change at all**, and the tests are the deliverable.

The three tickets that spend it are [ME-156](ME-156-all-frames-or-this-one.md), which adds the switch,
[ME-157](ME-157-frames-that-carry-their-own-edit.md), which marks the frames that carry one, and
[ME-158](ME-158-the-editors-frame-strip.md), which lets the editor walk between them.

## The shape, and why compatibility is free

An edit on disk today is `editorJson.encodeToString(EditDocument)`, and `editorJson` leaves defaults
out — so a real recipe is `{"ops":[…]}` and nothing else. Add a sibling field rather than a wrapper:

```kotlin
@Serializable
data class EditPayload(
    val ops: List<ToolOp> = emptyList(),
    val frames: Map<Int, EditDocument> = emptyMap()
)
```

- **Backwards.** `{"ops":[…]}` decodes as this with `frames` empty, which is exactly "every frame". Not
  a migration, not a version field, not a fallback parse — the old form *is* a valid new one.
- **Forwards.** An older build decoding `{"frames":{"3":{…}}}` as an `EditDocument` drops the unknown
  key (`editorJson.ignoreUnknownKeys`, ME-003) and sees an empty document, so the picture draws
  unedited rather than the file being refused. That is the degradation `code-style/model.md` asks for.
- **No carrier changes.** Every `RecipeCarrier` — JPEG's `APPn`, PNG's chunk, the `uuid` box, the RIFF
  chunk, GIF's Application Extension, the TIFF IFD tag — carries an opaque payload string. None of them
  is touched by this ticket, and that is worth stating because six formats would otherwise look like
  six risks.

**`pending` does not go on disk.** `EditPayload` has no such field: an operation in flight is a drag,
not a record, and `EditDocument.pending` only ever reached a file when `Done` landed mid-gesture.
Dropping it is the correction, not a loss.

## The four questions the backlog raised, answered

**`Done` writes into one slot.** `recordRecipe` reads the payload the file carries, merges, and writes
the merged result — the same failure ME-134 fixed one level up, now at frame granularity. Editing
frame 3 in a later sitting must not drop frame 1's edit.

**Scoping to "all frames" clears the overrides.** `with(document, AllFrames, i)` returns a payload with
`ops` and **no** `frames`, because "every frame draws this" is not true if an override wins. It is
destructive of per-frame work by design; saying so where the user can see it is ME-156's obligation,
not this ticket's silence.

**An empty override is a real value.** `frames[3] = EditDocument()` means "frame 3: nothing", which is
the only way to say *this frame alone is unedited while the others are not*. So an emptied per-frame
document keeps its key rather than being pruned, and `EditPayload.isEmpty` is
`ops.isEmpty() && frames.values.all { it.isEmpty }` — which keeps §5.2's rule intact: a payload that
folds to nothing removes the recipe, exactly as Revert does from the other direction.

**Revert stays the whole file** (decided 2026-08-30). It is the one action that removes everything Woka
wrote into this photograph (ME-107), it is red and confirmed, and a red confirmed action that means two
different things depending on a segmented control is a worse control than a second menu item would be.
`revertRecipe` is therefore unchanged, and this ticket adds no per-frame revert.

## Steps

1. **`core/EditScope.kt` (new, `commonMain`)** — `enum class EditScope { AllFrames, ThisFrame }`, with
   the semantics above in its doc. Introduced here rather than with the switch because the merge is
   what gives the two words meaning; ME-156 only draws them.

2. **`core/EditPayload.kt` (new, `commonMain`)** — the type above plus four members:

   ```kotlin
   val isEmpty: Boolean
   fun documentFor(frameIndex: Int): EditDocument = frames[frameIndex] ?: EditDocument(ops)
   fun scopeOf(frameIndex: Int): EditScope           // ThisFrame iff a key exists
   fun with(document: EditDocument, scope: EditScope, frameIndex: Int): EditPayload
   ```

   `scopeOf` is what lets an editor open in the mode the file was last written in, so a second sitting
   on frame 3 continues per-frame rather than silently going back to all-frames.

3. **`recipe/EditRecipe.kt` (edit)** — `read` returns `EditPayload?`; `write` takes an `EditPayload`;
   `carries` is unchanged (it asks whether a payload exists, not what is in it). Add
   `fun documentIn(file: File, frameIndex: Int): EditDocument?` for the two readers that want one
   frame and nothing else. The file is at **127 lines**.

4. **`core/EditorEngine.kt` (edit)** — `suspend fun save(document: EditDocument, scope: EditScope =
   EditScope.AllFrames): EditSaveResult`. Defaulted, so every existing caller and the video editor's
   future engine compile unchanged, and the default is today's behaviour. Its doc says what the
   parameter means for a medium with one picture in it: nothing, and `AllFrames` is the honest word
   for that.

5. **`engine/image/ImageEditWrites.kt` (edit)** — two changes. `recordRecipe` gains `scope` and
   `frameIndex` and does the merge:

   ```kotlin
   val existing = EditRecipe.read(file) ?: EditPayload()
   val next = existing.with(document, scope, frameIndex)
   return if (next.isEmpty) EditRecipe.clear(file) else EditRecipe.write(file, next)
   ```

   …with the stale-source guard still first, and the same `fold` onto `Saved`/`writeFailed`. And a new
   `writeFor(capability, …)` holding the `when (capability)` branch lifted out of the engine — see
   step 6. The file is at **65 lines**.

6. **`engine/image/ImageEditorEngine.kt` (edit)** — `storedDocument()` becomes
   `EditRecipe.documentIn(file, frameIndex) ?: EditDocument()`, `save` takes the scope and passes it
   with `frameIndex` into `recordRecipe`. **The file is at 148 lines**, so this step is also the split:
   the `when (capability)` block and its seven-line comment move into step 5's `writeFor`, leaving
   `save` a four-line body that re-stamps and evicts. Net negative before the additions.

   `reEncode` — bucket B — takes no scope and is documented as unable to: there is no payload to key,
   the pixels themselves are rewritten, and bucket B is empty today anyway (§5.1).

7. **`engine/image/RecipeStillEdits.kt` (edit)** — `apply` reads
   `EditRecipe.documentIn(file, frameIndex)`. **This is the ignored parameter finally being read**, and
   it is the whole reader half of the ticket: `StillEdits.apply` has taken a frame index since ME-133
   and thrown it away. Nothing else in the method changes — the fold is still against native size and
   still scaled to the bitmap.

8. **`../image-editor.md` §9 (edit)** — amend the 2026-08-19 row *What does an edit mean for a frame
   set?* with a 2026-08-30 continuation: the payload is keyed, the un-keyed form still means every
   frame, and Revert stays whole-file.

9. **`backlog.md` (edit)** — remove the ME-126 block; it is replaced by this ticket and by ME-156 and
   ME-157.

## Files

`core/EditScope.kt` (new), `core/EditPayload.kt` (new), `core/EditorEngine.kt` (edit) under
`shared/src/commonMain/kotlin/com/woka/feature/editor/`; `recipe/EditRecipe.kt` (edit),
`engine/image/ImageEditWrites.kt` (edit), `engine/image/ImageEditorEngine.kt` (edit),
`engine/image/RecipeStillEdits.kt` (edit) under `shared/src/jvmMain/kotlin/com/woka/feature/editor/`;
`ai/docs/features/pending/image-editor/image-editor.md` (edit),
`ai/docs/features/pending/image-editor/tickets/backlog.md` (edit).

## Verification

- `jvmTest/unit/EditPayloadTest.kt` (new) —
  - `documentFor` returns the un-keyed ops for a frame with no key, and the override for one with;
  - `scopeOf` answers `ThisFrame` only where a key exists;
  - `with(…, AllFrames, 2)` drops every key; `with(…, ThisFrame, 2)` leaves the un-keyed ops and every
    other key alone;
  - `isEmpty` is true for an empty payload and for `frames = {3: EditDocument()}` with no ops, and
    false as soon as anything carries an operation;
  - **the compatibility pair**: `editorJson.decodeFromString<EditPayload>("""{"ops":[…]}""")` reads a
    pre-ME-126 recipe as all-frames, and `decodeFromString<EditDocument>` of a keyed payload yields an
    empty document rather than throwing;
- `jvmTest/integration/FrameRecipeTest.kt` (new), the ticket's real test, over a copy of `burst.heic`
  (`BURST_HEIC`, five frames, each plate painted with its own index — `MediaCopies`):
  - write a `ThisFrame` transform for frame 3 and assert through `AppliedStillEdits.apply` that
    frame 3's bitmap changed and frames 0, 1, 2 and 4 came back **byte-identical** to their decodes;
  - then write a different `ThisFrame` edit for frame 1 and assert frame 3's is still there — the
    merge ME-134 fixed one level up;
  - then write an `AllFrames` edit and assert every frame draws it and no key remains in the payload;
  - undo to empty and `Done`: `EditRecipe.carries` is false and the file is back to as-shot;
- `jvmTest/integration/EditRecipeTest.kt`, `HeicRecipeTest.kt`, `TiffCarrierTest.kt`,
  `WebpCarrierTest.kt`, `GifRecipeFramesTest.kt`, `StillEditsTest.kt`, `ReopenEditTest.kt`,
  `RevertTest.kt`, `EditorSaveTest.kt` — **unchanged and still green**. That list is the deliverable:
  this ticket changes the format and nothing that reads it may notice.

## Done when

- a recipe written before this ticket still applies to every frame of the file that carries it;
- a per-frame override applies to its frame and to no other;
- a save merges into the stored payload instead of replacing it;
- an all-frames save clears the overrides, and a payload that folds to nothing removes the recipe;
- `Revert` still removes everything, for every frame;
- **nothing in the UI can yet write a key** — `grep -rn "EditScope.ThisFrame" shared/src/jvmMain` hits
  only `EditPayload`'s callers in the save path;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
