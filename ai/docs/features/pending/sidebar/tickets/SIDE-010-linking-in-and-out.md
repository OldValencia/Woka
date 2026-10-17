# SIDE-010 — Linking in, and undoing the way out

| | |
|---|---|
| **Order** | tenth |
| **Depends on** | SIDE-001 |
| **Feature** | [Sidebar](../sidebar.md) |

**Goal.**
- A folder dragged in from Explorer onto any part of the window links it and opens it.
- A dropped media file opens the viewer over its folder.
- An unlink can be undone for six seconds from a banner. The undo brings back the link at its old
  place, its pin, and the watch history and remembered tracks the unlink swept.

**Built:**
- `desktopApp/WindowFileDrop.kt` installs an AWT `DropTarget` on the window, which refuses anything
  that is not a file list.
- `app/shell/WindowDrops.kt` holds the `dragging` state, the `drop` decision and the full-window
  `DropOverlay`. It is on the window, as `WindowKeys` is, because a Compose node hears only what
  lands on it.
- `app/shell/sidebar/UnlinkUndo.kt` holds the pending unlink and the banner among `StatusBanners`.
  The sidebar's unlink offers it.
- `withFolderRestored` in `SettingsMutations`, behind `restoreUnlinkedFolder`, restores only what
  the sweep took. A key written since keeps its current value.
- Welcome now says "Drop a folder here", now that a drop works.

## Result

- 2026-10-17: built; `:shared:build` green. `UnlinkUndoTest` (`unit/`) passes 3 of 3: everything
  returns in place, what changed since is kept, and a folder relinked meanwhile is not doubled.
- 2026-10-17, from the running app: Explorer showed the "not allowed" cursor over the whole
  window. Compose installs its own drop target on the component that draws the content, and it
  answered before the window's AWT target. The drop is now Compose's own `dragAndDropTarget` on
  the shell's root (`WindowDrops.jvm.kt`), and the AWT file `WindowFileDrop.kt` is deleted.
- 2026-10-17: the by-hand re-check passed, run by the author.
