# ME-107 — Revert to Original

| | |
|---|---|
| **Order** | 25 |
| **Depends on** | ME-133 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Put an edited photograph back exactly as it was shot, in one action, at any time.

**Why now.** This ticket was **dropped** — "there is no backup store to revert from" — and un-dropped
on 2026-08-19, because the model changed underneath it. There is still no backup store; there does not
need to be one. The original pixels never left the file, so reverting is deleting a few hundred bytes
of metadata.

It is also the answer to "let the user remove Woka's data from a file". The segment holds the
serialised `EditDocument` and **nothing else** (§5.2) — no paths, no timestamps, no identifiers — so
"remove your data" and "revert" are the same operation, and giving them two buttons would be two
controls doing one thing. A **bulk** removal across a folder is a different concern, hygiene rather
than editing, and belongs in Settings as its own later ticket.

## Where it lives

Two places, both only for a file that `carries` a recipe:

- **In the editor**, beside Undo/Redo in the top bar's action group. Distinct from `Reset`: `Reset`
  clears the working document and is undoable, Revert writes the file and is not.
- **In the grid's context menu**, beside Rename/Copy/Delete, so a photograph can be put back without
  opening the editor at all.

Both go through a confirmation. Revert is not destructive to the picture — the pixels are untouched by
definition — but the work is gone, and an edit somebody spent ten minutes on deserves one question.

## Steps

1. `recipe/EditRecipe.clear` already exists (ME-131). Add `EditorEngine.revert(): EditSaveResult` so
   the shell asks the engine rather than naming `EditRecipe`, which is `jvmMain` and which the
   `commonMain` shell must not see (§3.3).
2. `ImageEditorEngine.revert`: `clear` plus the same eviction ME-132 factored out. A file carrying
   nothing answers `NothingToDo`.
3. `shell/EditorTopBarControls.kt`: the control, enabled only when the file carries a recipe.
4. `shell/RevertDialog.kt` (new) or a reuse of `DiscardChangesDialog`'s shape — decide by reading it
   first; a second dialog with the same two buttons and different words is not a new component.
5. `feature/folder/FolderItemContextMenu.kt`: the entry, greyed for a file carrying nothing.
6. The session's document resets to empty on a successful revert, so the canvas shows the original
   without reopening.

## Files

`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorEngine.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/ImageEditorEngine.kt` (edit) ·
`.../shell/EditorTopBarControls.kt` (edit) · `.../shell/RevertDialog.kt` (new) ·
`shared/src/commonMain/kotlin/com/woka/feature/folder/FolderItemContextMenu.kt` (edit)

## Done when

- `jvmTest/integration/`: a JPEG edited, saved and reverted is **byte-identical** to a copy of it taken
  before the first save — the strongest form of the promise, and the reason the write must not touch
  the pixel data.
- Reverting a file that carries nothing changes no bytes and answers `NothingToDo`.
- `jvmTest/ui/`: the control is disabled for an unedited file and enabled for an edited one; the
  confirmation is required before anything is written.
- `./gradlew.bat :shared:build` green.
