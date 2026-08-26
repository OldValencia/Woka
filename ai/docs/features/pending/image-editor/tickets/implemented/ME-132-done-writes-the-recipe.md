# ME-132 — `Done` writes the recipe, and Export is removed

| | |
|---|---|
| **Order** | 23 |
| **Depends on** | ME-131 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Make `Done` the only save there is: it writes the recipe into the file that was opened, and
nothing anywhere mints a second file.

**Why now.** ME-131 built the slot and nothing puts anything in it. Until this lands, the editor still
writes `photo-edited.jpg` on every `Done`, which is the defect the whole rewrite is answering.

## This ticket deletes more than it adds

`SaveTarget`, `OverwriteConsent` and `SaveFileNaming` exist to answer "where does this go and what does
it cost". §5 removed the question. They are deleted rather than left unused — a `SaveTarget.Overwrite`
sitting in the tree is an invitation to reach for it.

What is **kept** is ME-104's write path, unchanged: `NativeRender`, `ExifSplice`, `AtomicWrite`,
`SourceStamp`. It stops being the default save and becomes bucket B's in-place re-encode (§5.1), which
today is `webp` alone and will be nothing at all once ME-108's RIFF carrier lands.

## Saving an emptied document is a Revert

`ImageSaveJob` answers `NothingToDo` for an empty document, which was right when a save minted a file
and is wrong now: undoing every operation on a photograph that carries a recipe and pressing `Done`
must **remove** the recipe. Same operation as ME-107, reached from the other direction, and it is one
branch — but it is the branch that decides whether undo-then-save means anything.

## Invalidation stops being conditional

ME-104 invalidated only on overwrite, because a copy was a path nothing had cached. Every save now
changes what an existing path draws as, so every save evicts: the two `ImageBitmapCache` entries
`ImageSaveJob.invalidate()` already names, **plus** Coil's memory and disk entry for the path — a
`jpg` tile is drawn by Coil today and is about to change roads in ME-133, and a tile still showing the
pre-edit picture is the bug §5.2 names.

## Steps

1. `core/EditorEngine.kt`: `save(document, target)` → `suspend fun save(document: EditDocument):
   EditSaveResult`.
2. Delete `core/SaveTarget.kt`, `core/OverwriteConsent.kt`, `core/SaveFileNaming.kt` and
   `save/ImageSaveJob.copyNameFor`, with their tests.
3. `engine/image/ImageEditorEngine.save` branches on `EditCapability`: `Recorded` → `EditRecipe.write`,
   or `EditRecipe.clear` when `document.isEmpty`; `Destructive` → `ImageSaveJob`; `Unavailable` →
   `Failed`, which is unreachable through the UI and is the honest answer if it is ever reached.
4. `save/ImageSaveJob`: drop the target parameter and the copy branch; it writes to `file` and nothing
   else. Keep the stale-source guard, the format lookup, the render, the EXIF splice and the atomic
   write exactly as they are.
5. Move the cache eviction out of `ImageSaveJob` into a function both branches call, and add the Coil
   memory and disk eviction for the path.
6. `shell/EditorHost.kt`: `done` calls `engine.save(session.document.value)` — no name to resolve, no
   `copyNameFor`.
7. `shell/EditorNoHistoryNotice.kt` (new): the bucket B strip, following `shell/EditorSkippedNotice.kt`
   — this format keeps no history and `Done` will re-encode it. Shown from `EditorScreen` when the
   capability is `Destructive`. **Not a modal**: a warning that costs a click has been turned into a
   step (§4.5).

## Files

`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorEngine.kt` (edit) ·
`.../core/SaveTarget.kt`, `.../core/OverwriteConsent.kt`, `.../core/SaveFileNaming.kt` (delete) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/ImageEditorEngine.kt` (edit) ·
`.../save/ImageSaveJob.kt` (edit) · `.../shell/EditorHost.kt` (edit) ·
`.../shell/EditorScreen.kt` (edit) · `.../shell/EditorNoHistoryNotice.kt` (new)

## Done when

- `jvmTest/integration/EditorSaveTest.kt`: a JPEG edited and saved twice carries **one** recipe and
  decodes to the same pixels it started with; the directory it lives in has exactly the file count it
  started with, before and after; saving an emptied document leaves the file carrying nothing.
- A `webp` save re-encodes in place, mints no second file, and the notice composable is shown for it
  and not for a `jpg` (`jvmTest/ui/`).
- `grep` proves `SaveTarget`, `OverwriteConsent` and `copyNameFor` appear nowhere in the tree.
- `./gradlew.bat :shared:build` green.
