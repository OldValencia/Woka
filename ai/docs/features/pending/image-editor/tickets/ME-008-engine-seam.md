# ME-008 — Editor engine seam

| | |
|---|---|
| **Order** | 9 |
| **Depends on** | ME-007 |
| **Feature** | [Image Editor](../image-editor.md) |

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

## Result — 2026-08-16

`./gradlew.bat :shared:build` green. **530 tests, 0 failures, 0 errors** — 525 before, plus five.

### `SaveTarget` was declared here, not in ME-009

Step 1 puts `save(document, target: SaveTarget)` in the interface, and `SaveTarget` is ME-009's type
one ticket later. It is created now in exactly the shape ME-009 step 1 specifies — `Copy(name)` and
`Overwrite` — so that ticket finds it done and keeps the naming rules and the atomic write. Same
forward declaration as `ToolCardinality` in ME-004.

### Two files the ticket's list does not name

- **`jvmMain engine/EditorEngineFactoryImpl.kt`.** Step 3 puts `EditorEngineFactory` in `commonMain`,
  which therefore cannot name `ImageEditorEngine` — so the interface lives there and its JVM half
  here. Step 5 says "register the factory", which requires something to register.
- `EditorEngineFactory` is a plain `interface`, not a `fun interface`: Kotlin rejects a default
  parameter on a functional interface's single method, and defaulting `frameIndex` to the first
  picture is worth more than SAM conversion for a type whose only implementation is a class.

### Editability is decided from the format, never from the bytes

`create` routes on `File.mediaKind()` and touches no disk. `sourceSize` is `by lazy` behind
`StillDecoder.nativeSize`, which reads dimensions without decoding. The alternative — probing in the
factory — would mean decoding every file in a grid to know whether to grey out a menu item, and a
truncated `.png` would report itself as an unsupported *format*, which it is not. There is a test for
exactly that.

**Video resolves to null** until the video engine exists in Phase 5b, so Edit is correctly unavailable
on a video rather than opening a shell with no canvas behind it. One line in
`EditorEngineFactoryImpl` changes when ME-201 lands.

`Surface` and `save` are `TODO("ME-103")` and `TODO("ME-104")` as step 4 allows. `recipe()` is
`internal` on `ImageEditorEngine` and absent from `EditorEngine`: the shell must not be able to name
`RasterRecipe`, or the video editor inherits a shell that already knows about bitmaps.
