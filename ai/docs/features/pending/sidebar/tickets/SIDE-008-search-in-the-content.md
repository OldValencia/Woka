# SIDE-008 — Search in the content

| | |
|---|---|
| **Order** | eighth |
| **Depends on** | SIDE-004 |
| **Feature** | [Sidebar](../sidebar.md) |

**Goal.** The toolbar's box takes text. While it holds any, the results replace the grid: the
scope (This Folder / All Folders), a count, and the matches as tiles, each with the match marked in
its name and its place under it. Opening a folder goes there. Opening a file opens the viewer over
its folder, with its tile marked. Esc or an empty box gives the folder back.

**Built (written lean at the author's request, 2026-10-17):**
- `feature/search/LibrarySearch.kt`:
  - `SearchState` holds the query, the scope and the box's `FocusRequester`;
  - `searchLibrary` walks recursively and ignores case. It returns media and folders only, does not
    enter hidden folders, and stops at 300 hits or 200 000 entries visited. It is abandoned as soon
    as the query changes.
- `feature/search/SearchResults.kt` is the results. They are drawn **over** the screen, so the folder
  stays composed underneath and clearing the box returns to it exactly.
- `app/shell/ToolbarSearchField.kt` is a `BasicTextField`. Esc clears it, ✕ empties it, and Ctrl+F
  focuses it.
- `App.openHit` opens a hit.
- **Deleted:** `SearchOverlay`, `SearchInput` and `SearchResultsList`. The overlay looked one level
  down, so it found a folder by name and never the photograph inside it.

## Result

- 2026-10-17: built; `:shared:build` green with 1399 tests. `LibrarySearchTest`
  (`integration/`, a temp tree) passes 4 of 4: depth, case, hidden folders, and stopping.
- 2026-10-17, from the running app: typing "te" crashed the results grid on a duplicate key.
  Linked roots nest (`Heic` is linked on its own and inside a linked `Pictures`), so one file was
  found through both. Hits are now distinct by normalised path, and `LibrarySearchTest` has the
  nested-roots row.
- 2026-10-17: the by-hand re-check passed, run by the author.
