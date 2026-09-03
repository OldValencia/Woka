# ME-157 — The frame strip becomes shared, and says which frames carry their own edit

| | |
|---|---|
| **Order** | 65 — after ME-156, which is what first creates an edit that can hide |
| **Depends on** | ME-126 (the keyed payload), ME-156 (the control that writes a key), ME-133 (the `StillEdits` seam), ME-135 (the save signal the mark re-reads on) |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Move `FrameStrip` into `core/ui/components` and give it a **mark on any frame that carries an
edit of its own**, driven through the existing `StillEdits` seam.

**Why now.** ME-156 makes it possible to edit frame 3 of a burst and leave the other four alone. Nothing
on screen then says which frame that was: five thumbnails, one of them different from the file's own
rule, and no way to tell them apart short of clicking each. The backlog's sentence is the whole ticket —
*"a burst becomes a place where edits hide"*. The strip is also what the editor mounts in
[ME-158](ME-158-the-editors-frame-strip.md), so it stops being the viewer's private component in the
same change.

## What the mark means, and what it deliberately does not

**It marks a frame with its own key in the payload**, not "a frame that draws an edit". If the file
carries an un-keyed recipe, every frame draws it and marking all five would say nothing that
"photo.heic · Edited" does not already say better. A per-frame override is the thing that can be
invisible, so it is the thing that gets a mark.

A 5-dp filled dot in the thumbnail's top-right corner, `Theme.colors.accentEditor` — the colour the
editor already uses for a selected tool and the `Done` pill, so "this is the editor's doing" needs no
legend. Not a badge with a glyph: `MotionBadge` beside it is already a glyph, and two badges on a
48-dp square is a wall.

## Where it comes from

`AppliedStillEdits` is the process-wide holder ME-133 built precisely so a `commonMain` composable can
ask about an edit without importing the editor. It answers `carries` and `apply` today; **the frame
keys are the third question of the same kind**, so the seam grows a member rather than the strip
growing a dependency. `NoStillEdits` answers with an empty set, so `core` and the viewer still build
and behave exactly as they did before the editor existed.

## The move

`FrameStrip` and `MotionBadge` go to `core/ui/components/frames/`. They are `internal`, which in a
single-module project already made them reachable from `feature/editor` — so this is not about
visibility, it is about the layout rule: `core/` is shared by everyone and a `feature/` is shared by no
one, and this component is about to have two features drawing it. `core/ui/components/` is where the
project already keeps `SegmentedControl`, `IosSwitch`, `Marquee`, `DetentSlider` and `FolderCard`, and
this belongs with them. Nothing about the component itself is viewer-specific: it takes a `MediaItem`, a
list of `ImageFrame`, an index and a `loadPreview` lambda, all of which are `core` types already.

**`FrameStripState.kt` stays where it is.** `rememberFrameStrip` reads a decoder, looks for a Live
Photo's motion sibling and models what the *viewer* shows; it is the viewer's use of the component, not
the component.

## Steps

1. **`core/ui/image/StillEdits.kt` (edit)** —

   ```kotlin
   suspend fun editedFrames(file: File): Set<Int> = emptySet()
   ```

   Defaulted on the interface, overridden in `AppliedStillEdits` by delegation, and left at the default
   in `NoStillEdits`. `suspend` for the reason `carries` is: it touches the disk, and the implementation
   owning the move to IO is the difference between a rule and a rule somebody has to remember. The file
   is at **76 lines**.

2. **`feature/editor/engine/image/RecipeStillEdits.kt` (edit)** — implement it as
   `EditRecipe.read(file)?.frames?.keys ?: emptySet()` inside `withContext(ImageBitmapCache.ioDispatcher)`
   and `runCatching { … }.getOrDefault(emptySet())`. **Never throws**, exactly as `carries` does not: a
   malformed payload must not stop a strip from drawing. The file is at **74 lines**.

3. **`core/ui/components/frames/EditedFrames.kt` (new)** —

   ```kotlin
   @Composable
   fun rememberEditedFrames(path: String): Set<Int>
   ```

   A `LaunchedEffect` keyed on the path **and on `rememberReloadKey(path)`**, so a save re-reads the
   answer instead of leaving a stale mark — the same key `FrameThumbnail` already uses for its bitmap
   (ME-135). Returns empty until the first read completes, which is the honest initial state: no mark
   is better than a mark that may be wrong.

4. **`core/ui/components/frames/FrameStrip.kt` (moved, edit)** — package changes; `FrameStrip` and
   `FrameThumbnail` take `editedFrames: Set<Int> = emptySet()`, and `FrameThumbnail` draws the dot when
   `frame.index in editedFrames`. Defaulted, so a caller that has nothing to say draws what it drew
   before. The file is at **138 lines**, so the dot is drawn by a `private fun EditedDot()` in the new
   sibling of step 3 rather than inline here if `wc -l` goes over 150.

5. **`core/ui/components/frames/MotionBadge.kt` (moved)** — package change only.

6. **`feature/viewer/shell/MediaViewerPlayerFrames.kt` (edit)** — the two imports, plus
   `editedFrames = rememberEditedFrames(activeItem.file.absolutePath)` on the `FrameStrip` call. The
   file is at **64 lines**.

7. **`feature/viewer/shell/MediaViewerPlayer.kt` (edit)** — the `FRAME_STRIP_WIDTH` import's package.

8. **`jvmTest/ui/ViewerInvalidationUiTest.kt` (edit)** — its `FrameStrip` import's package. Listed as a
   step because it is a compile break, not a test change.

## Files

`core/ui/image/StillEdits.kt` (edit), `core/ui/components/frames/FrameStrip.kt` (moved from
`jvmMain/…/feature/viewer/shell/frames/`), `core/ui/components/frames/MotionBadge.kt` (moved),
`core/ui/components/frames/EditedFrames.kt` (new) under `shared/src/commonMain/kotlin/com/woka/`;
`feature/editor/engine/image/RecipeStillEdits.kt` (edit),
`feature/viewer/shell/MediaViewerPlayerFrames.kt` (edit),
`feature/viewer/shell/MediaViewerPlayer.kt` (edit) under `shared/src/jvmMain/kotlin/com/woka/`;
`shared/src/jvmTest/kotlin/com/woka/ui/ViewerInvalidationUiTest.kt` (edit).

**The move is jvmMain → commonMain.** Both files name only `androidx.compose.*` and `com.woka.core.data`
types, so nothing in them needs a `jvm` half; that is checked by the compile, not assumed.

## Verification

- `jvmTest/integration/StillEditsTest.kt` (edit) — `editedFrames` answers empty for a file with no
  recipe, empty for a file carrying an un-keyed one, and `{1, 3}` for a payload with those two keys;
  a truncated or garbage payload answers empty rather than throwing;
- `jvmTest/ui/FrameStripUiTest.kt` (edit) — with `editedFrames = setOf(2)` the strip draws the mark on
  the third thumbnail and on no other, asserted through a `testTag` on the dot; with the default it
  draws none;
- `jvmTest/ui/ViewerInvalidationUiTest.kt` — still green, which is what says the reload key still drives
  the thumbnail; extended so that a save signal raised after a keyed write makes the mark **appear**
  without the strip remounting;
- `jvmTest/ui/FramePlaybackUiTest.kt`, `ViewerNavigationUiTest.kt` — unchanged and still green.

## Done when

- `FrameStrip` and `MotionBadge` live in `core/ui/components/frames/` and no `feature/` package is
  imported by either;
- a frame with its own edit shows the mark in the viewer's strip; a file-wide edit marks nothing;
- the mark appears after a save without leaving and re-entering the viewer;
- an unedited file draws exactly the strip it drew before;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
