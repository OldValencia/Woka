# ME-133 — Every reader applies the recipe

| | |
|---|---|
| **Order** | 24 |
| **Depends on** | ME-132 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Make an edited file *look* edited everywhere in the app — grid tile, viewer, carousel, PiP,
frame export.

**Why now.** ME-132 writes a recipe that nothing reads. Between the two tickets `Done` appears to do
nothing at all, so this lands in the same sitting or immediately after; a build in between is green but
not shippable.

## The seam, and why it is not an import

The fold lives in the editor (`engine/image/RasterFold.fold`) and the readers live in
`feature/viewer/image` and `core`. A reader importing `feature/editor` is a feature reaching into
another feature, which the layout forbids for good reason: `core/` is shared by everyone, a `feature/`
is shared by no one.

So the seam is an interface **in `core`**, implemented in the editor and registered in Koin by
`di/EditorModule.kt`:

```
interface StillEdits {
    fun carries(file: File): Boolean
    fun apply(file: File, frameIndex: Int, decoded: ImageBitmap): ImageBitmap
}
```

Absent an implementation it is the identity, so `core` and the viewer both build and both behave
exactly as they do today with no editor in the graph — which is also what makes it testable without one.

**No second render path.** The implementation is `EditRecipe.read` → `RasterFold.fold` →
`NativeRender.renderAtNativeScale`, all three unchanged. That is the same routine the save uses and the
same one the canvas draws, which is what keeps "what you saw is what the file shows" a property rather
than an intention.

## The grid is the awkward one

`StillCoverImage` draws `jpg`/`png` tiles with Coil, which cannot know about a recipe, and routes only
`needsWokaCover` formats through Woka's own preview. Pushing every still off Coil would be a large
behavioural change to the whole grid for the sake of the few files that are edited.

Instead the existing `LaunchedEffect(file.absolutePath)` gains a `carries` check — a small read of the
file's head, off the main thread, in a coroutine that already exists — and only a file that carries a
recipe leaves Coil for `StillPreview`. Everything else keeps Coil, its memory cache and its fade
exactly as now.

`StillPreview` itself needs no cache work: its disk entry is keyed on path, size **and** mtime by
`WokaStorage.thumbnailFile`, and a recipe write changes all three, so the 400 px preview re-derives
itself once and is free thereafter.

## Steps

1. `core/ui/image/StillEdits.kt` (new): the interface above, plus the identity default and a
   process-wide holder the readers ask. Registered by `di/EditorModule.kt`.
2. `feature/editor/engine/image/RecipeStillEdits.kt` (new): the implementation. Resolves
   `EditorToolRegistry` the way `EditorHost`'s `EditorDependencies` already does.
3. `feature/viewer/image/ImageLoader.load`: apply before caching, so the cache holds what is drawn.
4. `feature/viewer/image/StillPreview.build`: apply before scaling — **in `build` only**, never on the
   `readCached` path, which is already the edited picture.
5. `core/ui/components/StillCoverImage`: the `carries` check and the `StillPreview` road for a file that
   answers true.
6. `core/utils/FrameExport.jvm.kt`: apply per frame; an exported frame is the picture the user sees.
7. **Do not touch** `engine/image/EditorProxy.kt`. The editor folds the recipe live over the proxy, and
   applying it on the way in would apply it twice — the one place in the app that must read the file
   raw. A comment saying so belongs in the file, because it reads like an omission.

## Files

`shared/src/jvmMain/kotlin/com/woka/core/ui/image/StillEdits.kt` (new) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/RecipeStillEdits.kt` (new) ·
`.../feature/editor/di/EditorModule.kt` (edit) · `.../feature/viewer/image/ImageLoader.kt` (edit) ·
`.../feature/viewer/image/StillPreview.kt` (edit) ·
`shared/src/commonMain/kotlin/com/woka/core/ui/components/StillCoverImage.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/core/utils/FrameExport.jvm.kt` (edit) ·
`.../feature/editor/engine/image/EditorProxy.kt` (comment only)

## Done when

- `jvmTest/integration/StillEditsTest.kt`: a JPEG carrying a quarter-turn decodes through `ImageLoader`
  with its axes swapped; the same file with the recipe cleared decodes as the original; a file with no
  recipe returns the **same instance** it was handed, so nothing pays for a fold it does not need.
- The 400 px preview rebuilds after a write and the rebuilt one is the edited picture.
- `MediaThumbnails` and PiP are **not** edited and are asserted to follow anyway, since both go through
  `ImageLoader`/`StillPreview`.
- By hand: edit a JPEG, `Done`, and the grid tile changes without leaving the folder.
- `./gradlew.bat :shared:build` green.
