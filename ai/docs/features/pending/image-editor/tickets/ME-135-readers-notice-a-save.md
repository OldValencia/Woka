# ME-135 — Readers notice a save

| | |
|---|---|
| **Order** | 27 |
| **Depends on** | ME-133 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Make an edit appear on screen the moment `Done` lands, without leaving the viewer or the
folder.

**Why now.** Found by hand on 2026-08-19, immediately after ME-133: editing from the viewer and
pressing `Done` changes nothing on screen, and the picture only turns after leaving the viewer and
re-entering it. ME-133 made every reader *apply* the recipe; nothing makes a reader **read again**.

## Eviction is not invalidation

ME-132 evicts `ImageBitmapCache` and Coil's memory entry on every save (§5.2), and that is correct and
insufficient. A cache is only consulted when something asks it, and nothing asks:

```kotlin
// MediaViewerScreenEffects.kt — the decode
LaunchedEffect(activeItem, showConflictDialog, state.frameIndex) { … state.imageBitmap = … }
```

While the editor is mounted over the viewer none of those three keys changes, so on return the effect
does not re-run and `state.imageBitmap` is still the bitmap decoded before the edit. Re-entering the
viewer works only because that remounts the composition. The carousel has the same shape
(`LaunchedEffect(item.file.absolutePath)` per tile), and so does the grid — where Coil additionally
holds a resolved painter keyed on a `File` that compares equal before and after the write.

**So the missing piece is a signal, not a cache change.** Every save already funnels through one
function, which is what makes this small.

## Why a holder in `core` rather than a parameter

The alternative is threading a `reloadKey` from `FolderScreen` through the `NativeMediaViewer`
`expect`/`actual`, `MediaViewerScreen` and into the effects — four signatures, and it reaches only the
readers that happen to be below that call. PiP and the fullscreen window draw the same bitmap from
their own trees and would each need their own thread.

A `StateFlow` in `core` that `evictCachesFor` bumps is one write and any number of observers, and it is
the shape `AppliedStillEdits` already established in ME-133: declared in `core`, written by the editor,
read by whoever draws.

## Steps

1. `core/ui/image/MediaInvalidation.kt` (new, **commonMain** — `StillCoverImage` is a `commonMain`
   composable and must see it): `object MediaInvalidation` holding
   `data class Invalidated(val path: String, val generation: Long)`, a
   `StateFlow<Invalidated?>`, and `fun invalidate(path: String)` incrementing the generation. The
   generation is what makes a second save of the same path a second signal rather than an equal value
   the flow drops.
2. `feature/editor/save/SaveEviction.kt`: `evictCachesFor` calls `MediaInvalidation.invalidate(path)`
   as its last line — after the caches are cleared, never before, or a reader woken early re-reads the
   entry that is about to be dropped.
3. `feature/viewer/shell/MediaViewerScreenEffects.kt`: collect the flow and add
   `invalidated?.takeIf { it.path == activeItem?.file?.absolutePath }` to the decode
   `LaunchedEffect`'s key list, so an unrelated file's save does not re-decode the open one.
4. `feature/viewer/shell/MediaViewerCarousel.kt`: the same filter on the per-tile
   `LaunchedEffect(item.file.absolutePath)`.
5. `core/ui/components/StillCoverImage.kt`: the same filter on its `LaunchedEffect`, **and** the Coil
   branch needs the signal too — `AsyncImage`'s `model` is a `File` that compares equal across the
   write, so it never re-requests. Give the request a key that changes: `model = file` becomes an
   `ImageRequest` carrying `memoryCacheKey`/`diskCacheKey` unchanged but built inside a
   `key(generation)`, or re-route the file to `StillPreview` once it carries a recipe. **Decide by
   measuring which actually re-requests** — this is the one step whose mechanism is not yet proven.

   **Settled 2026-08-19, and neither option was needed whole.** The `LaunchedEffect` filter already
   moves an edited file **off** Coil's road — `carries` goes true and the tile takes the Woka one —
   so the recipe case fixes itself. What is left is the case that stays on Coil's road across a
   write: a bucket-B re-encode, and a revert that takes a tile *back* to it. There the fix is
   `key(reloadKey) { AsyncImage(…) }` — a new composition identity is a new `AsyncImagePainter` and
   therefore a new request, which then misses the memory entry ME-132 evicted. No `ImageRequest`
   builder and no second road.

## Files

`shared/src/commonMain/kotlin/com/woka/core/ui/image/MediaInvalidation.kt` (new) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/save/SaveEviction.kt` (edit) ·
`.../feature/viewer/shell/MediaViewerScreenEffects.kt` (edit) ·
`.../feature/viewer/shell/MediaViewerCarousel.kt` (edit) ·
`shared/src/commonMain/kotlin/com/woka/core/ui/components/StillCoverImage.kt` (edit)

## Done when

- `jvmTest/unit/MediaInvalidationTest.kt`: two invalidations of the same path publish two distinct
  values; an unrelated path does not match the filter.
- `jvmTest/ui/`: a viewer showing a file re-reads its bitmap when that path is invalidated, and does
  **not** when a different path is.
- By hand — this ticket exists because of a by-hand finding, so it is settled by one: edit from the
  viewer, `Done`, and the picture turns without leaving the viewer; the carousel thumbnail and the grid
  tile behind it follow.
- ME-133's own by-hand check is re-run here, because it was never actually run: the grid tile changes
  without leaving the folder.
- `./gradlew.bat :shared:build` green.
