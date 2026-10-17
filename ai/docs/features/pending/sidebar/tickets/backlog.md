# Sidebar — backlog

Five blocks, each one sitting that ends with something on screen. A block is promoted to
`tickets/<ID>-<slug>.md` when it is next, and re-cut against the tree as it is then (`tickets.md` §4).
There are no file paths here on purpose.

### SIDE-003 — The toolbar replaces the dock

The top bar becomes the mockup's toolbar, 52.dp high, with the sidebar header matched to it:

- the sidebar hide and show buttons, with the hidden state persisted;
- Back and Forward;
- the path;
- the All · Photos · Videos filter, the first control the existing `mediaFilter` has ever had;
- sort;
- the size control, still the three `ViewLayout` steps here;
- the search field, which opens the existing search for now, and `Ctrl+F` to focus it;
- Settings.

The folder's pin moves to a toolbar button, and select becomes the bar from the mockup, shown only
while something is selected. The pin offer becomes a strip at the top of the content. Then
`FloatingDock`, its items and `DockAccessories` are deleted, not left unused.

**Depends on.** SIDE-002.

### SIDE-004 — Two grids, and search in the content

A folder whose items are mostly video draws 16:9 titled cards, with the duration and the watched
bar. Anything else draws a square mosaic with 3 px gaps and no captions, with the title and an
item's details on hover. Sub-folders draw first, as the mockup's small mosaic cards.

The size control becomes a continuous cell width per folder. `Ctrl +/-` and Ctrl+wheel jump between
the old three steps.

Search results replace the grid instead of opening an overlay. They come with a This Folder / All
Folders scope, the match highlighted and the parent path under each result; Esc returns to the
folder.

**Depends on.** SIDE-003.

### SIDE-005 — The keyboard drives the grid

One focused item in the grid, ringed in `accent`:

- arrows move the focus, Shift+arrows extend the selection;
- Enter opens the viewer;
- Backspace goes up a level, but no further than the linked root;
- Ctrl+A selects all and Delete deletes.

**Quick Look.** Space shows the focused or hovered item above the grid. Arrows move through the
folder, Enter hands over to the full viewer, and Space or Esc closes. A video plays muted from its
watch position.

**Info.** `Ctrl+I` or the toolbar's ⓘ opens a right-hand panel for the focused item, fed by what the
viewer's file-info popover already reads. It follows the focus.

**Depends on.** SIDE-004.

### SIDE-006 — Hovering a video plays five excerpts

Hovering a video card plays five excerpts of about 2.5 s each, spread evenly across the runtime and
muted. A five-segment progress bar runs across the top. Leaving the card stops playback and restores
the poster.

The excerpts decode from the existing seek-preview path, not a second player. One card plays at a
time.

Measure the decode cost on a 4K HEVC file before the excerpt length is fixed, and record the result
with its date in the ticket.

**Depends on.** SIDE-004.

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
