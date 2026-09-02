# ME-168 — Opening a large photograph must not freeze the window

| | |
|---|---|
| **Order** | first of the 100 track — the only defect on a shipped surface |
| **Depends on** | ME-123 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Move the editor's still-proxy decode off the composition thread, so opening the editor on a
large photograph draws a frame instead of blocking.

**Why now.** `rememberEditorFrame` builds the proxy inside `remember(file, frameIndex)`, which runs
**synchronously while composing**. ME-010 measured `EditorProxy.load` at 667 ms for a 100 MP source
and ~150 ms for a 24 MP one, so the window is frozen for exactly that long on every editor open and
on every frame change. The animation branch of that same function is already async through
`ImageBitmapCache.ioDispatcher`; only the still is not.

## Steps
1. In `EditorAnimatedProxy.kt`, replace the `remember(file, frameIndex) { EditorProxy.load(...) }`
   binding inside `rememberEditorFrame` with a `produceState<ImageBitmap?>(null, file, frameIndex)`
   whose body calls `withContext(ImageBitmapCache.ioDispatcher) { EditorProxy.load(file, frameIndex) }`
   — the dispatcher the animation branch of this function already uses, and the same
   placeholder-then-fill idiom `EditorFrameStrip.kt` uses for its own decode.
2. Make `EditorFrame.bitmap` nullable so a caller can tell "not decoded yet" from "decoded". Do not
   add a second state holder — `EditorFrame` is the type the canvas already speaks in.
3. In `ImageEditorCanvas.kt`, have `rememberCanvasSource`/`ImageCanvas` draw the canvas chrome and
   nothing in the picture area while `bitmap` is null. §4.1 mounts the editor over a **frozen,
   still-mounted viewer**, so the right placeholder is that viewer showing through — not a spinner.
4. In `ImageEditorEngine.kt`, guard `Surface` and `Thumbnail` against the null bitmap. `drawnSize()`
   and `previewMatrix()` need no change: both work from `sourceSize`, which `nativeFrameSize` reads
   from the decoder header without decoding a pixel.
5. Cancellation: `produceState`'s scope is cancelled when `file`/`frameIndex` change or the composable
   leaves, which covers Back-mid-decode and a frame change mid-decode alike. Read
   `EditorHostControls.leave` and add nothing if nothing is missing.
6. Do not touch `EditorProxy.load`. It stays synchronous and blocking; this ticket changes who calls
   it, not what it does.

## Files
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/EditorAnimatedProxy.kt` (edit, 98 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/ImageEditorCanvas.kt` (edit, 82 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/ImageEditorEngine.kt` (edit, 139 lines — at the mandate, budget a split)

## Done when
- `EditorProxy.load` has no caller on the composition thread — grep it and every hit is inside a
  coroutine;
- opening the editor on a 100 MP source draws the shell immediately and the picture when it arrives;
- Back during that window closes the editor without waiting for the decode;
- `jvmTest/unit/` asserts `EditorFrame` reports no bitmap before the proxy resolves;
- `./gradlew.bat :shared:build` green.

## Note
§10's "very large sources" gap says the rail it points at is "keep the native bitmap out of the shared
cache" — that half shipped as ME-123. This is the other half, and closing it closes the gap.
