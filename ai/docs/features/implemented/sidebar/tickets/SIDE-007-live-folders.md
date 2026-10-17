# SIDE-007 — Live folders

| | |
|---|---|
| **Order** | seventh |
| **Depends on** | SIDE-001 |
| **Feature** | [Sidebar](../sidebar.md) |

**Goal.** The open folder follows the disk. A file created, deleted or renamed there by anything —
a screenshot tool, Explorer, a download — appears or leaves within a moment, with no announcement
and without the scroll moving. The sidebar's counts for that folder and its parent follow too.

**Why now.** The author asked for it in the plan's discussion: one person at one computer moved the
file and expects to see it. Today the grid re-reads only after Woka's own Copy, Move or Delete.

**Re-cut from the backlog block.** It also held linking by drop and an undoable unlink. Those two
are the "linking in and out" pair, one sitting, and are now SIDE-010.

## Steps

1. **The watch: `core/utils/DirectoryWatch.kt` (`expect`) and `DirectoryWatch.jvm.kt` (`actual`,
   new).** `fun directoryChanges(path: String): Flow<Unit>` emits once per batch of entry
   creations, deletions or modifications directly inside [path], including an overflow. It is
   cold: collecting it opens a `WatchService` and stops collecting closes it.
   - The blocking `take()` runs on `Dispatchers.IO` inside a `callbackFlow`.
   - `awaitClose` closes the service, which ends `take()` with `ClosedWatchServiceException`.
     That exception is the expected way out, not an error.
   - A path that cannot be watched gives an empty flow. A folder on a network share may refuse,
     and then the grid is as it was.

2. **The grid follows it.** `FolderScreen` collects `directoryChanges(path)` in a
   `LaunchedEffect(path)`, debounced 300 ms, and calls `viewModel.refresh(quiet = true)`.
   - **Tied to the screen, not the ViewModel.** The ViewModel's scope is never cancelled, so a
     watch started there would outlive every folder the user ever opened.
   - **The debounce** is there because one screenshot is a creation and several modifications in a
     row, and one re-read answers all of them.

3. **A quiet re-read.** `FolderViewModel.refresh(quiet = true)` re-reads the listing without raising
   `isLoading`, so the grid is never swapped for "Loading…". The grid's items are keyed by path,
   which is what keeps the scroll and the selection where they were. A selected file that has gone
   leaves the selection.

4. **The sidebar follows it.**
   - `SidebarTreeState.relist(path)` re-reads a cached row in place.
   - `Sidebar` collects the same flow for the open folder and re-lists that folder and its parent,
     because a new sub-folder changes the parent's chevron and the count lives on the folder's own
     row.
   - The root rows' counts recompute when the cache changes.

## Files

- New:
  - `core/utils/DirectoryWatch.kt`
  - `jvmMain/…/core/utils/DirectoryWatch.jvm.kt`
  - `jvmTest/integration/DirectoryWatchTest.kt`
- Edit:
  - `feature/folder/FolderScreen.kt`
  - `feature/folder/FolderViewModel.kt`
  - `app/shell/sidebar/SidebarTreeState.kt`
  - `app/shell/sidebar/Sidebar.kt`
  - `app/shell/sidebar/SidebarSections.kt`

## Done when

- `DirectoryWatchTest` (`integration/`, a temp folder) proves:
  - a created file is reported, and so is a deleted one;
  - a change inside a sub-folder is not;
  - cancelling the collector closes the watch, so a later change is not delivered and nothing
    throws.
- By hand, with the result dated in this file:
  - a screenshot taken into the open folder appears without a click;
  - a file deleted in Explorer leaves;
  - the scroll does not move;
  - the sidebar count follows.
- No file over 150 lines; the anti-FQN grep is clean; `./gradlew.bat :shared:build` is green.

## Result

- 2026-10-17: built; `:shared:build` green with 1392 tests.
- `DirectoryWatchTest` passes 4 of 4, measured on this Windows machine:
  - a creation and a deletion each arrive in about 0.5 s, the 500 ms registration wait included;
  - a file written inside a sub-folder produced no event in 1.5 s, so the non-recursive watch does
    not wake on a sub-folder's contents here.
- Changed from the steps: `reloadQuietly()` became `refresh(quiet = true)`.
  `FolderViewModel` stood at 147 lines, and a parameter cost none of them.
- Changed from the steps: the watch service is opened inside the `callbackFlow`, not when
  `directoryChanges` is called. A flow built and never collected would otherwise have held a
  `WatchService` open with nothing to close it.
- 2026-10-17: the by-hand check passed, run by the author, who asked for the commit.

