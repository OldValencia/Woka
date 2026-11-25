# PT-201 — Sidebar, toolbar, grid

| | |
|---|---|
| **Order** | seventh, built with PT-202 |
| **Depends on** | PT-001 |
| **Feature** | [App Prototype](../app-prototype.md) |

**Goal.** Port the folder screen from the app as it is: the sidebar tree, the toolbar, the grid
with folder cards and media in both layouts.
**Why now.** The Library section is the last empty part of the prototype, and PT-301 (the visual
truth moves to `woka.html`) waits on it. What the author sees: `#library/pictures`,
`#library/movies`, `#library/series`, `#library/empty`, `#library/welcome`.

Source: read from the Compose code on 2026-11-25 (`GlobalTopBar`, `ToolbarButtons`,
`ToolbarSearchField`, `FolderToolbar`, `FolderViewOptions`, `SegmentedControl`, `FolderGrid*`,
`GridDensity*`, `FolderCard*`, `FolderCardStack`, `FolderCardTile`, `FolderCardProgress`,
`FolderItemContextMenu`, `FolderOverlays`, `WelcomeScreen`, `SidebarSections`), not from the older
`sidebar-redesign.html`, which the app has moved past (one cover per folder card, not a mosaic).

## Steps

1. **Folders.** `woka/library.js`: a `FOLDERS` tree (Movies › The Sea · Season 1, Music, Pictures ›
   Iceland 2025 / Phone 2024, Screenshots, Vacation, Videos) whose media drive both the grid and the
   viewer's carousel (`folderItems`); seven episodes with sizes and watched fractions.
2. **Sidebar.** The tree with counts and chevrons, Pinned with the parent as subtitle; the first-run
   state ("Nothing linked yet", the filled Link Folder… row). A folder row opens its grid.
3. **Toolbar.** Up button inside a sub-folder, title, View (Show / Size / Sort panel), Pin, search
   box, Info, Settings, window buttons; the viewer's back button returns to the grid.
4. **Grid.** Mosaic when fewer than half the media are videos, cards otherwise; columns per S/M/L;
   "Folders  N" / "Media  N" headings; folder cards with the two-sheet stack, one cover or the folder
   glyph; media cards calm until hover, caption, play badge, watched bar, check circle; Ctrl+wheel and
   Ctrl ± step the size with the HUD; context menus on items and on the background.

## Files

`ai/mockups/design/woka.html`, `woka/woka.js`, `woka/app.js`, `woka/sections.js`,
`woka/nowplaying.js`, `woka/tokens.css` (edit); `woka/library.js`, `woka/library-actions.js`,
`woka/library.css` (new).

## Done when

- Pictures: two folder cards (one with a cover, `Phone 2024` with the glyph and "Folder · empty") and
  a mosaic of six; Movies: a folder card and two video cards; the series: seven cards, three watched
  bars.
- View → Size S → HUD and smaller columns; Show → Photos keeps the folders, drops the videos.
- A card opens the viewer; the viewer's back button returns to the grid; the sidebar opens folders.

## Result — 2026-11-25

Built as above and checked in headless Edge. Viewer, editor and dialog scenes match PT-105's commit
in computed style outside the sidebar.

**Where the result differs from the app.** The context menus' actions other than Open, Edit, Pin,
Mark as watched, Revert and the size steps answer with a toast; the background menu's `View ›`
submenu is two flat items. The hover reel of five excerpts and the search-hit highlight are not
drawn. The Kind row reads `M4A audio` — the app shows `MP3 image` for audio (a bug, noted by the
reading). Sidebar folder glyphs keep the prototype's outline icon.
