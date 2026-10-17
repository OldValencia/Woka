# SIDE-009 — The Info panel

| | |
|---|---|
| **Order** | ninth |
| **Depends on** | SIDE-005 |
| **Feature** | [Sidebar](../sidebar.md) |

**Goal.** `Ctrl+I`, or the toolbar's ⓘ, opens a 272.dp panel beside the grid for the focused item:
a preview, the name, path, size and date, and for a video its streams. It follows the keyboard's
focus.

**Built:**
- `feature/viewer/FileInfoPanel.kt` (`expect`) and `.jvm.kt` (`actual`) reuse the File Information
  dialog's `FileMetaBlock` and `StreamInfoSections`. A video is probed off the main thread with the
  seek preview's capped probe.
- `feature/folder/FolderLayers.kt` holds `InfoPanel` (open for the run, not persisted),
  `FolderInfoSide`, and `FolderFloating`. The link offer, the selection bar and Quick Look moved
  there out of `FolderContent`, to make room.
- The ⓘ button sits in `FolderToolbar`, and Ctrl+I in `FolderKeys`.

**Not done:** camera and exposure for a photograph. The dialog it reuses does not read EXIF beyond
orientation, and adding it is its own piece of work.

## Result

- 2026-10-17: built; `:shared:build` green.
- 2026-10-17, from the running app: the panel read as a draft and the ⓘ button looked broken.
  - The panel is laid out for itself now (`FileInfoPanel.jvm.kt`, data in `FileInfoPanelData.kt`):
    the picture, the name in the strong weight, the folder, then General, Video, Audio and
    Subtitles in two columns, with accent-coloured section titles. A photo also shows its
    dimensions.
  - ⓘ moved beside Search and is drawn on a Canvas (`ToolbarInfoButton`), because the SVG's
    hairlines broke up at 15.dp.
- 2026-10-17: the by-hand re-check passed, run by the author.
