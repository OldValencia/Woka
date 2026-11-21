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

**Status:** implemented, 2026-11-21. Author manually verified: everything appears to work. Focused render/copy/safety tests passed; strict native audit clean over 11 runs. Full-suite run encountered an existing FolderSelectionTest assertion; no further reruns at the author's request.
