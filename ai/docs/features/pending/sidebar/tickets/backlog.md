# Sidebar — backlog

Four blocks, each one sitting that ends with something on screen. A block is promoted to
`tickets/<ID>-<slug>.md` when it is next, and re-cut against the tree as it is then (`tickets.md` §4).
There are no file paths here on purpose.

### SIDE-007 — The disk: live folders, linking in, undoing the way out

**Live folders.** The open folder is watched on disk through `expect`/`actual`, with `WatchService`
on the JVM side, and the listing re-reads on a debounced change. A new screenshot appears and a
deleted file leaves, without an announcement. The sidebar's expanded rows re-list on the same
signal.

**Linking in.** A folder dragged in from Explorer lights the window and links it. A dropped file
opens the viewer. Nothing in the codebase does OS drag-and-drop yet, so this ticket also sets the
pattern.

**Undoing an unlink.** Unlinking sweeps the watch history and remembered tracks for the whole tree.
It gets an undo banner for a few seconds that restores all of it, not only the link.

**Depends on.** SIDE-001; it is independent of 002–006.

### SIDE-008 — Search in the content

The toolbar's search box takes text. Results replace the grid rather than opening an overlay: a
This Folder / All Folders scope, the match highlighted in each name, the parent path under each
result. Esc, or clearing the box, returns to the folder. The overlay and its files are deleted
once nothing opens them. Split out of SIDE-004 on 2026-10-17, because the photo mosaic and search
are two outcomes, each a sitting.

**Depends on.** SIDE-004 — results draw as its tiles.

### SIDE-009 — The Info panel

`Ctrl+I` or the toolbar's ⓘ opens a right-hand panel for the focused item, fed by what the
viewer's file-info popover already reads:
- a photo shows its kind, size, dimensions, dates, camera and exposure;
- a video shows its codec, HDR, frame rate, and audio and subtitle tracks.

The panel follows the focus SIDE-005 introduced, and stays open as it moves. Split out of
SIDE-005 on 2026-10-17.

**Not yet decided:** playing a video inside Quick Look, muted from its watch position. SIDE-005 shows
the poster. Playing needs a player instance of its own and its lifetime, so it waits until it is
asked for.

**Depends on.** SIDE-005.

