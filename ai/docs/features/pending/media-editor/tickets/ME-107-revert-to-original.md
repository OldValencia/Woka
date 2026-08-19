# ME-107 — Revert to Original

| | |
|---|---|
| **Order** | 26 |
| **Depends on** | ME-133, ME-134 |
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

- **In the editor**, in the top bar's **leading** group, beside `Cancel` — and **red**. Asked for
  directly on 2026-08-19, overriding this ticket's original "beside Undo/Redo": Revert is the one
  control here that throws work away, and grouping it with two that give work back was reading it as
  a third step of the same kind. Red is the app's only destructive colour and this is the only
  destructive control the editor has.
- **In the grid's context menu**, beside Rename/Copy/Delete, so a photograph can be put back without
  opening the editor at all.

Distinct from `Reset`: `Reset` clears the working document and is undoable, Revert writes the file and
is not.

**The confirmation is not optional, and the placement is why.** Beside `Cancel` it sits where the hand
goes to leave, so a misclick is a question of when rather than whether. Revert is not destructive to
the picture — the pixels are untouched by definition — but the work is gone, and an edit somebody spent
ten minutes on deserves one question.

Woka has no red in `Theme.colors` for this yet — check before adding one, and if a destructive colour
is genuinely absent it is one token, not a palette.

## Steps

1. `recipe/EditRecipe.clear` already exists (ME-131). Add `EditorEngine.revert(): EditSaveResult` so
   the shell asks the engine rather than naming `EditRecipe`, which is `jvmMain` and which the
   `commonMain` shell must not see (§3.3).
2. `ImageEditorEngine.revert`: `clear` plus the same eviction ME-132 factored out. A file carrying
   nothing answers `NothingToDo`.
3. `shell/EditorTopBarControls.kt`: the control in the **leading** group beside `Cancel`, red, enabled
   only when the file carries a recipe. The engine answers whether it does — the shell must not name
   `EditRecipe` (§3.3), so this is a second question on `EditorEngine` or a value on the one added in
   ME-134's `storedDocument()`.
4. `shell/RevertDialog.kt` (new) or a reuse of `DiscardChangesDialog`'s shape — decide by reading it
   first; a second dialog with the same two buttons and different words is not a new component.
5. `feature/folder/FolderItemContextMenu.kt`: the entry, greyed for a file carrying nothing.
6. The session's document resets to empty on a successful revert, so the canvas shows the original
   without reopening. **After ME-134 this is a two-part reset**: the document *and* the session's
   `initial`, or the editor is instantly dirty against a document the file no longer carries.

## Files

`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorEngine.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/ImageEditorEngine.kt` (edit) ·
`.../shell/EditorTopBarControls.kt` (edit) · `.../shell/RevertDialog.kt` (new) ·
`shared/src/commonMain/kotlin/com/woka/feature/folder/FolderItemContextMenu.kt` (edit)

## Built 2026-08-19

Steps 1–4 and 6 are in. What Revert does, in one order: `revertRecipe` clears the segment, the engine
re-reads its stamp and evicts, then `EditorSession.revertedToOriginal()` empties the document, the
history **and** `openedWith` — the two-part reset step 6 warned about. A failed write moves neither.

Two things worth carrying forward:

- `Reset` **left the inspector entirely** (asked for directly). It cleared the whole working document
  from a header naming one tool, beside an Undo that already takes work back a step at a time. There
  is now one destructive control in the editor and it is this one. `EditorSession.reset()` survives
  with no caller.
- The top bar had no **leading** slot: the shell drew Back and nothing beside it. `TopBarAccessoriesState`
  grew `leadingAccessory`, saved and restored on dispose like the other two.

**Step 5 landed the same day.** "Revert to Original" sits above Delete in the grid's menu, red, greyed
for a photograph carrying nothing, and asks the same question in the same words before it writes. It
goes through the Koin engine factory rather than a copy of the removal, so a file put back from the
grid is put back exactly as one put back from the editor — asserted, not assumed.

One thing moved to make it possible: `core/ui/dialogs/Dialogs.kt` was in `jvmMain` while the whole
folder feature is `commonMain`, so the confirmation could not see the app's own dialog. The file holds
no JVM-specific code — the AWT pickers are elsewhere — so it moved whole, in the same package, with no
call site touched.

## Done when

- `jvmTest/integration/`: a JPEG edited, saved and reverted is **byte-identical** to a copy of it taken
  before the first save — the strongest form of the promise, and the reason the write must not touch
  the pixel data.
- Reverting a file that carries nothing changes no bytes and answers `NothingToDo`.
- `jvmTest/ui/`: the control is disabled for an unedited file and enabled for an edited one; the
  confirmation is required before anything is written.
- `./gradlew.bat :shared:build` green.
