# ME-008 — Editor engine seam

| | |
|---|---|
| **Order** | 9 |
| **Depends on** | ME-007 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** The interface the shell talks to, so "the video editor inherits the shell" is enforced by the
compiler rather than by intention.

**Why now.** If the shell is written against a bitmap first, every one of its files becomes a video
ticket later.

## Steps

1. `EditorEngine.kt` (commonMain, `feature/editor/core/`): `val kind: MediaKind`,
   `val sourceSize: IntSize`, `@Composable fun Surface(document: EditDocument, modifier: Modifier)`,
   `suspend fun save(document: EditDocument, target: SaveTarget): EditSaveResult`. The recipe type
   stays **inside** the engine — the shell never names `RasterRecipe`.
2. `EditSaveResult.kt` (commonMain): `sealed interface` — `Saved(path: String)`, `Failed(reason)`,
   `NothingToDo`. The banner and the folder refresh both read it, and `Saved` carries the written path
   because a copy is a file the grid has never seen.
3. `EditorEngineFactory.kt` (commonMain): `fun create(path: String, frameIndex: Int): EditorEngine?`.
   Null means "not editable", which is what disables both Edit entry points — there is no second list
   of extensions anywhere.
4. `ImageEditorEngine.kt` (jvmMain, `engine/image/`): implement `kind`, `sourceSize` and the fold
   against ME-007. `Surface` and `save` may `TODO()` **only** with the id of the ticket that fills
   them — `TODO("ME-103")`, `TODO("ME-104")` — per `code-style/comments.md`.
5. Register the factory in `EditorModule`.

## Files

`feature/editor/core/EditorEngine.kt`, `core/EditSaveResult.kt`, `core/EditorEngineFactory.kt`
(commonMain, new), `jvmMain engine/image/ImageEditorEngine.kt` (new),
`feature/editor/di/EditorModule.kt` (edit)

## Done when

- compiles;
- `jvmTest/unit/EditorEngineFactoryTest.kt` asserts that a `.png`, a `.jpg`, a `.mp4` and an
  unsupported extension each resolve to the expected engine or to null, driven off `MediaKind` and
  not off a private string list.
