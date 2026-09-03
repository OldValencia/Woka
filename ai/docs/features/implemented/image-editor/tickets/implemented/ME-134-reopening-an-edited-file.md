# ME-134 — Reopening an edited file

| | |
|---|---|
| **Order** | 25 — **first**, it is losing work |
| **Depends on** | ME-133 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Open the editor on a file that already carries a recipe and continue from it, rather than
starting again over a picture that is already edited.

**Why now.** Without it the second edit of a photograph is wrong in both directions, and since ME-132
landed it is **actively destroying work**.

**Confirmed in the field, 2026-08-19.** A JPEG was edited in two sittings and its segment read back
between them. After the first it held one `rotateLeft`. After the second it held two `rotateRight` and
**no `rotateLeft` at all**: the session starts from `EditDocument()` every time, so `Done` writes only
what that sitting did and replaces the file's whole edit. Not "appended to", not "emptied" — replaced.

Three symptoms, one cause, all reported from the same run:

- **the editor opens on the original.** The document is empty and `EditorProxy` reads the file raw by
  design (ME-133), so the canvas draws unedited pixels under tools sitting at their defaults;
- **`Done` wipes the previous sitting**, as above;
- **Undo and Redo are dead on reopening.** `EditorHistory()` starts as one empty entry at `index = 0`
  and `canUndo` is `index > 0`, so there is nothing behind the file's own state to step back to. Step 2
  is what gives the first undo somewhere to go.

The canvas is corrected by seeding the session, not by touching `EditorProxy`: the editor folds the
document live over raw pixels, and applying the recipe on the way in as well would draw two rotations
for one.

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

`EditorHistory` is seeded from the same document, and `reset()` returns to it rather than to empty:
"back to where I started this sitting" is what the control means, and it is not the same thing as
Revert (ME-107), which discards every sitting.

**Amended 2026-08-19, by the by-hand run.** Seeding the history with one entry — the file's document
— left an edited photograph opening with nothing to undo, which reads as an unedited file however
correct the canvas is. So the seed is `EditorHistory.startingAt(document)`: **the recipe replayed one
operation per entry**, beginning on the empty document. Undo now crosses sittings and reaches the
original at the end of them. The `· Edited` mark moved with it — it answers "this file carries an
edit" (the document) rather than "there is unsaved work" (`isDirty`, which the exit dialog keeps).

## Steps

1. `core/EditorSession.kt`: `class EditorSession(val media: MediaItem, initial: EditDocument =
   EditDocument())`. `_document` starts at `initial`; `isDirty` maps to `it != initial`; `reset()`
   restores `initial`.
2. `core/EditorHistory.kt`: `startingAt(initial)` — the recipe replayed one operation per entry, so
   undo walks back through earlier sittings and the first entry is the original picture.
3. `core/EditorEngine.kt`: `fun storedDocument(): EditDocument` — the shell asks the engine, because
   `EditRecipe` is `jvmMain` and the shell is not (§3.3). Defaulted to `EditDocument()` so an engine
   with no notion of stored edits is unaffected.
4. `engine/image/ImageEditorEngine.storedDocument`: `EditRecipe.read(file) ?: EditDocument()`.
5. `shell/EditorHost.kt`: `remember(target) { EditorSession(MediaItem.Image(file), engine.storedDocument()) }`.
6. Check every `isDirty` reader against the new meaning — the discard dialog and `EditorKeyEffect`
   keep it. `CanvasOverrideState.edited`, which draws the `· Edited` mark in the Information dialog,
   does **not**: it takes `!document.isEmpty`, so a file that carries an edit says so on opening.

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
- `jvmTest/unit/`: a session opened with a non-empty document reports `canUndo` **true**, and undo
  steps back through the operations the file carries, one at a time, to the original — the
  dead-buttons symptom asserted rather than eyeballed. **The bullet stands as written**: it was
  narrowed while implementing to "false at open, one undo back to the file's document", and the
  by-hand run rejected that on sight. `EditorHistory.startingAt` is what makes the original bullet
  true, and a history test asserts the replay separately.
- By hand, against the case that produced this ticket: rotate, `Done`, reopen, rotate again, `Done`,
  and the segment holds **both** turns. Read it back with
  `grep -aob $'Woka\x00\x00' <file>` and check the payload — that is how the loss was found.
- `./gradlew.bat :shared:build` green.
