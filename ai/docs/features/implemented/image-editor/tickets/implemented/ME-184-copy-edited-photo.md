# ME-184 — Copy an edited photo as both picture and file

| | |
|---|---|
| **Order** | 1.0.8, after ME-183 |
| **Depends on** | ME-183 |
| **Feature** | [Image editor](../../image-editor.md) |

**Goal.** Make Copy paste edited photos into Explorer as well as image applications.
**Why now.** Copy currently supplies only imageFlavor, so Explorer cannot paste a photo file.

## Decisions — author, 2026-11-21
- Only clipboard Copy and Render photo bake edits. Duplicate and copy-to-folder retain their existing behavior.
- Video Copy keeps the original and explains that Render video includes edits.

## Steps
1. Add a file-copy seam to `StillEdits`, implemented with ME-183's renderer in `RecipeStillEdits`.
2. Supply imageFlavor and javaFileListFlavor in one transferable; unedited photos point at the original.
3. Store baked copies in unique temporary directories, retain the last successful copy at exit, and clean old copies at startup.
4. Route both grid and viewer through one copy action, including the edited-video notice.
5. Verify clipboard flavors, decoded baked pixels and source preservation without changing the system clipboard.

## Files
`core/ui/image/StillEdits.kt`, `core/utils/ImageClipboard.jvm.kt`, `core/utils/ClipboardCopies.kt`,
`feature/editor/engine/image/RecipeStillEdits.kt`, `feature/editor/CopyMedia.kt`,
`feature/folder/FolderItemContextMenu.kt`, `feature/viewer/shell/MediaViewerCopyAction.kt`,
`desktopApp/StartupPrewarm.kt`, `integration/CopyImageTest.kt`.
This exceeds eight files because the two entry points, platform seam and temporary-file lifetime form one usable behavior.

## Done when
- Both flavors are offered, edited file pixels survive outside Woka, and source bytes remain unchanged.
- Shared build and native image audit pass.

## Follow-up — author, 2026-11-21 (same day, before the release)
The author found three gaps while testing:
- **Grid Copy did nothing for a GIF.** The menu launched the copy in its own `rememberCoroutineScope`, and `dismiss()` cancelled it at the first suspension. A photo escaped only because its whole copy sits inside one `withContext(IO)`. Now `copyMedia` launches into an app-lifetime scope in `CopyMedia.kt`, and both the grid and the viewer call it. Esc straight after Ctrl+C no longer cancels a copy either.
- **A GIF goes over as a file, with its edits baked in.** The clipboard picture is one frame, so a chat would receive a still. `StillCopy.copiesAsFileOnly` makes the transferable offer only the file list.
  - An edited GIF is baked frame by frame into a new GIF: `save/BakeGif.kt` + `save/GifWriter.kt`.
  - Animations take no per-frame edit (`EditorFrames.allowsPerFrame`), so every frame comes out at one size.
  - Encoding goes through FFmpeg `palettegen=stats_mode=single` → `paletteuse=new=1` → the `gif` encoder. Each frame gets its own palette, transparency is kept, and only one frame is held in memory at a time.
  - Measured first by `probe/GifEncodeProbe.kt`:
    - fixture frames come back with mean error 0;
    - the transparent area survives;
    - delays of 50/100/200 ms and the loop count are exact.
  - The filter renumbers pts, so frame times are ours.
- **One decode for an edited photo.** `StillEdits.copyOf` returns a `StillCopy` (file + baked picture). `renderStill(onImage)` hands the bake's pixels to the clipboard instead of a second `ImageLoader.load`.

Tests: `CopyGifTest` (an untouched GIF offers its own file only; an edited GIF becomes 240×320, eight frames of 120 ms, looping forever, with no recipe, and the source stays unchanged) and `CopyImageTest`.
Out of scope: Render photo… on an animated GIF (still one frame), animated WebP/APNG.

**Status:** implemented, 2026-11-21. Author manually verified: everything appears to work. Focused render/copy/safety tests passed; strict native audit clean over 11 runs. Full-suite run encountered an existing FolderSelectionTest assertion; no further reruns at the author's request.
