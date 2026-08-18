# ME-134 — Reopening an edited file

| | |
|---|---|
| **Order** | 26 |
| **Depends on** | ME-133 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Open the editor on a file that already carries a recipe and continue from it, rather than
starting again over a picture that is already edited.

**Why now.** Without it the second edit of a photograph is wrong in both directions. The canvas draws
the edited picture (ME-133) while the document is empty, so the tools are all at their defaults over a
result they did not produce — and pressing `Done` writes an empty document, wiping the first edit.

**It never degrades.** Every render starts from the original pixels, so a photograph edited twenty
times has been compressed exactly as many times as it was when it came off the camera. That is the
property the recipe model exists for, and this is the ticket where it becomes visible.

## `isDirty` has to change meaning

`EditorSession.isDirty` is `!document.isEmpty` (ME-004), which was right when the document always
started empty. Reopening an edited file would then be dirty before it is touched: `Cancel` would ask
about discarding changes nobody made, and the `· Edited` mark in Information would be on from the
first frame.

So it becomes **"differs from what is in the file"** — a comparison against the document the session
opened with. `EditDocument` is a `data class` of serialisable values, so equality is structural and
free.

`EditorHistory` is seeded with the same document, and `reset()` returns to it rather than to empty:
"back to where I started this sitting" is what the control means, and it is not the same thing as
Revert (ME-107), which discards every sitting.

## Steps

1. `core/EditorSession.kt`: `class EditorSession(val media: MediaItem, initial: EditDocument =
   EditDocument())`. `_document` starts at `initial`; `isDirty` maps to `it != initial`; `reset()`
   restores `initial`.
2. `core/EditorHistory.kt`: seeded with `initial` so the first undo goes back to the file's state, not
   to an empty document that was never on screen.
3. `core/EditorEngine.kt`: `fun storedDocument(): EditDocument` — the shell asks the engine, because
   `EditRecipe` is `jvmMain` and the shell is not (§3.3). Defaulted to `EditDocument()` so an engine
   with no notion of stored edits is unaffected.
4. `engine/image/ImageEditorEngine.storedDocument`: `EditRecipe.read(file) ?: EditDocument()`.
5. `shell/EditorHost.kt`: `remember(target) { EditorSession(MediaItem.Image(file), engine.storedDocument()) }`.
6. Check every `isDirty` reader against the new meaning — the discard dialog, `EditorKeyEffect`, and
   `CanvasOverrideState.edited`, which draws the `· Edited` mark in the Information dialog.

## Files

`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorSession.kt` (edit) ·
`.../core/EditorHistory.kt` (edit) · `.../core/EditorEngine.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/ImageEditorEngine.kt` (edit) ·
`.../shell/EditorHost.kt` (edit)

## Done when

- `jvmTest/unit/EditorSessionTest.kt`: a session opened with a non-empty document is **not** dirty;
  becomes dirty on a commit; stops being dirty when undone back to where it started; `reset()` returns
  to the initial document rather than to empty.
- `jvmTest/integration/`: a JPEG rotated, saved, reopened and rotated again ends at a half turn and
  carries **one** recipe holding both operations.
- Pixel equality: the file after two sittings decodes identically to the file after one sitting that
  did both rotations — proof there is no generational loss.
- `jvmTest/ui/`: reopening an edited file shows the Transform panel with its values, not its defaults.
- `./gradlew.bat :shared:build` green.
