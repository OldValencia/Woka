# Sidebar

**Status: designed, nothing built.** The visual spec is `ai/mockups/design/sidebar-redesign.html`.
Once a UI ticket is written, disagreements are settled by changing the mockup first. Replaces
`home-rethink`, archived in `archive/` beside this file with the reason it lost.

## The problem

Home is a grid built for a library of many linked folders. In practice a person links one or two,
once, and never opens the list again. So every launch costs a click and shows nothing new. The dock
that moves between screens floats over the grid. It also carries a Library item that lights up where
you cannot be, lists a different set of folders from Home's, and does nothing when clicked. The
author's verdict on the screen as it stands, and on the `home-rethink` answer to it, was that both
are unusable, not unpolished.

## What the window becomes

**One window that always shows a folder, with the places it can show in a sidebar.** It is the shape
of Finder, Photos and Music, which is the shape the HIG gives an app that browses a few roots. There
is no Home screen and no dock.

```
┌──────────────┬──────────────────────────────────────────────────────────────┐
│ W  Woka    ▣ │ ‹ ›  Movies › Series         All|Photos|Videos  ▭━━▭  🔍  ⓘ  ⚙ │
│              ├──────────────────────────────────────────────────────────────┤
│ Folders      │  sub-folders, as small mosaic cards                         │
│ ▾ Movies 128 │                                                              │
│   ▸ Series   │  the grid: a square mosaic for photos,                       │
│ ▸ Pictures   │  16:9 titled cards for a folder that is mostly video         │
│ Pinned       │                                                              │
│ 📌 Andor S1  │            [ 3 selected · Copy · Move · Delete · Done ]      │
│ + Link…      │                                                              │
└──────────────┴──────────────────────────────────────────────────────────────┘
```

- **Sidebar, `Folders`**: the linked roots as a tree. A row expands in place to its direct
  sub-folders, each with the number of media files directly inside it. The tree opens itself along
  the path of the folder on screen.
- **Sidebar, `Pinned`**: `PinnedSession` rows. Each has a second line naming its parent relative to
  its root, so two `Season 1` rows can be told apart. A pinned folder still restores the scroll it
  was left at, as it does today.
- **Sidebar foot**: `Link Folder…`. The sidebar hides from its own header button.
- **Toolbar**: Back and Forward, the path (parents muted, the current folder bold), an
  All · Photos · Videos filter, sort, a continuous thumbnail-size slider, the search field, Info,
  Settings, then the window controls.
- **Content**: the folder's sub-folders first, then its media. A folder that is mostly photos is a
  tight square mosaic with 3 px gaps and no captions. A folder that is mostly video is 16:9 cards
  with a title, the duration and the watched bar, because an episode needs its name.
- **Selection bar**: the only floating element, and it exists only while something is selected.

## Architecture, by layer

### Navigation and launch

`Screen.Home` is deleted. The stack's root is always a folder, or `Screen.Welcome` while nothing is
linked. Back pops; Forward replays what Back popped and is cleared by any new navigation, as in a
browser. A sidebar click is an ordinary navigation, so Back undoes it.

**Launch opens the folder that was open at quit, at its scroll.** It is stored as one
`PinnedSession`-shaped record, written as the top folder changes and as its scroll settles, rather
than at exit. Exit is not a moment the app can rely on writing in. A record that no longer resolves
to a directory falls back to the first linked folder, then to Welcome. `Woka.exe <path>` still
wins over all of that.

### The sidebar

It lives in `app/shell/` because it is navigation, which belongs to the shell, the same as the top
bar. The tree is a state holder with no Compose in it: expanded paths, plus a cache of
`path → children`. It is filled off the main thread from a non-recursive directory listing, and the
count it shows is the same non-recursive listing filtered by `File.isMedia`. A recursive count would
make expanding a row cost a walk of the whole subtree.

### The toolbar

`GlobalTopBar` keeps its accessory slots, and the screens keep claiming them the way they do now.
The dock's tenants move into it: the folder's pin, select and sort controls, and the search and
settings items. `DockAccessories`, `FloatingDock` and their items are then deleted, not left unused.
The pin offer for a folder launched from Explorer becomes a strip at the top of the content.
Previously it rode on the dock.

### The grid

`mediaFilter` already exists in `WokaSettings` and is already applied in `FolderStateMapper`. It
has never had a control. The filter is that control.

Thumbnail size goes from the three `ViewLayout` steps to a continuous cell width per folder. The
steps stay as the values `Ctrl +/-` and Ctrl+wheel jump between.

Whether a folder is mostly video is a count over the items the grid already holds, not a setting.

### Over the grid

- **Quick Look** draws above the grid, inside the folder screen. Enter hands over to the existing
  viewer; it does not duplicate it.
- **Info** is a right-hand panel fed by what the viewer's file-info popover already reads.

Both follow one focused item that the keyboard moves.

### The disk

The open folder is watched (`WatchService`, JVM-side behind `expect`/`actual`), and the grid
re-reads on a debounced change. A folder dragged in from Explorer links. A dropped file opens the
viewer, as a double-click from Explorer already does.

## What this deliberately does not add

- **No "Continue watching".** Five films each started for fifteen minutes are five unfinished
  films, and the app cannot tell which one the user means to resume. The watched bar on each card
  says it already.
- **No grouping by month, no drag out to other apps, no `Ctrl+K` jump, no "new since last visit"
  badge.** Search covers jumping to a folder. Copy and paste covers sending a file out. A folder
  that updates itself makes a badge about change redundant.

## Status

Seven tickets, each one sitting and one green build that ends with something on screen. Only the
next one is specified. The rest are blocks in `tickets/backlog.md`.

| ID | Ticket | Status |
|---|---|---|
| [SIDE-001](tickets/SIDE-001-the-sidebar.md) | The sidebar: the folder tree, Pinned, Link Folder, and the dock's Library item gone | **done 2026-10-17** |
| SIDE-002 | No Home: launch restores the last folder, Welcome for a first run, Back and Forward | **← NEXT** |
| SIDE-003 | The toolbar replaces the dock: filter, sort, size, search, settings, selection bar | backlog |
| SIDE-004 | Two grids, chosen by content, a continuous size, and search results in the content | backlog |
| SIDE-005 | The keyboard drives the grid: focus, Quick Look, Info | backlog |
| SIDE-006 | Hovering a video plays five excerpts | backlog |
| SIDE-007 | The disk: the open folder updates itself, a folder dropped in links, an unlink can be undone | backlog |

## Decisions

| Date | Question | Choice |
|---|---|---|
| 2026-10-17 | Keep Home in some form? | **No.** A window with a sidebar, as in Finder or Photos. With one or two roots, a page of them is a click between the user and the files, and `home-rethink`'s two-zone library was judged unusable by the author. |
| 2026-10-17 | Keep the dock? | **No.** Each item has a native place: Link at the sidebar's foot, Search and Settings in the toolbar. The bottom of the window goes back to the content. |
| 2026-10-17 | What opens at launch? | **The folder open at quit, at its scroll.** This supersedes `home-rethink`'s per-card launch flag and its "no inference" rule. Without a Home screen there is nothing else to land on. |
| 2026-10-17 | Keep the All · Photos · Videos filter? | **Yes.** The setting and the filtering already exist and have never had a control. |
| 2026-10-17 | "Continue watching"? | **No.** Partial watches of several films do not say which one to resume. The per-card progress bar stays as it is. |
| 2026-10-17 | A badge for files new since the last visit? | **No — update the folder instead.** One user at one computer moved the file and expects to see it; announcing it adds nothing. |
| 2026-10-17 | `Ctrl+K` folder jump? | **No.** The global search is enough. |
| 2026-10-17 | Drag files out of Woka? | **No.** Copy and paste a file covers sending one to another app. Dragging a folder *in* to link it stays. |
| 2026-10-17 | How many tickets? | **Seven, each a sitting.** One person builds this. A ticket is the smallest slice that changes the screen, not a layer of the code. |
