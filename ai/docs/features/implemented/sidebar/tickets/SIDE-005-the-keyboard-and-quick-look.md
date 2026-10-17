# SIDE-005 — The keyboard and Quick Look

| | |
|---|---|
| **Order** | fifth |
| **Depends on** | SIDE-004 |
| **Feature** | [Sidebar](../sidebar.md) |

**Goal.** One focused item in the grid, ringed in the accent, that the keyboard drives:
- arrows move the focus, Shift+arrows extend the selection;
- Enter opens the item;
- Backspace goes up a level, but not past the linked root;
- Ctrl+A selects all, Delete deletes the selection;
- Esc leaves Quick Look, then select mode.

Space shows the hovered or focused item above the grid, Quick Look, and arrows walk the folder
inside it.

**Why now.** With history gone, Backspace is the only way up that is not the mouse. Quick Look is
the "quick preview" `general_idea.md` names as a use case.

**Re-cut from the backlog block.** It also held the Info panel. That is its own outcome and sitting,
now SIDE-009.

**What it leaves out.** A video in Quick Look shows its poster, duration and watched bar, and Enter
plays it in the viewer. Playing it muted inside Quick Look needs a player instance of its own and
its lifetime, which is a body of work; it is a backlog line in SIDE-009's neighbourhood, not
something half-built here.

## Steps

1. **Where keys arrive.** On the grid's own focusable box, as Ctrl +/- already are, never on the
   global slot.
   - The global slot would take Backspace and Space from the rename dialog's text field and from
     search.
   - The grid takes focus on arrival, already, and again when the viewer closes, which is new: on
     `scrollToMediaId`.

2. **Focus: `feature/folder/FolderFocus.kt` (new).** It is a UI holder, so `mutableStateOf` is
   allowed; it is not a ViewModel. It holds:
   - `focused: String?`, set by a click on a tile and by the viewer's last file;
   - `hovered: String?`, reported by the card;
   - `quickLook: Boolean`.
   
   `FolderCard` gains `isFocused` and `onHoverChange`. The focus ring is a 2.dp `accent` border
   over the card or tile.

3. **Moving: `feature/folder/FolderKeys.kt` (new).** `folderKey(event, …)`, made of pure decisions
   over the `LazyGridState`'s `layoutInfo`:
   - Left and Right step through the list.
   - Up and Down move to the nearest item in the row above or below by x. When that row is off
     screen, they move by the current row's length.
   - The moved-to item is scrolled into view only if it is not already.
   - Shift selects the item left behind and then ranges to the new one (`selectRange`).

4. **Acting.** In `folderKey`:
   - **Enter** opens a folder or the viewer.
   - **Backspace** opens the parent while the current folder is inside a linked root and is not
     that root.
   - **Ctrl+A** is `selectAll`.
   - **Delete** deletes the selection, when there is one, and leaves select mode.
   - **Esc** closes Quick Look, else leaves select mode.
   - **Space** toggles Quick Look on the hovered item, else the focused one, and focuses it.

5. **Quick Look: `feature/folder/QuickLook.kt` (new).** A scrim (black at 50 %) over the folder
   content, and a centred card, 74 % wide at most 860.dp, `surfaceElevated`, 14.dp radius:
   - **The picture:** an image through `StillCoverImage` with the `contentScale = Fit` it gains in
     this ticket, defaulted to `Crop` so every other caller is unchanged. A video draws through
     `FolderCardPreview`, so it has its poster and watched bar.
   - **The caption:** the name, then the hint *← → next · Enter open · Space close*.
   - **Behaviour:** a click on the scrim closes it. While it is open, the arrows move the focus and
     the card follows it.

6. **Backlog.** Add SIDE-009, the Info panel, and the line about playing a video inside Quick Look.

## Files

- New:
  - `feature/folder/FolderFocus.kt`
  - `feature/folder/FolderKeys.kt`
  - `feature/folder/QuickLook.kt`
  - `jvmTest/unit/FolderKeysTest.kt`
- Edit:
  - `core/ui/components/FolderCard.kt`
  - `core/ui/components/StillCoverImage.kt`
  - `feature/folder/FolderGrid.kt`
  - `feature/folder/FolderGridItem.kt`
  - `feature/folder/FolderContent.kt`
  - `tickets/backlog.md`

## Done when

- `FolderKeysTest` (`unit/`) covers:
  - the pure parts of moving, from item positions: nearest by x in the row below, the row length
    when it is off screen, and clamping at both ends;
  - the parent rule for Backspace.
- By hand, with the result dated in this file:
  - the arrows move a visible ring and Enter opens;
  - Shift+arrows select;
  - Backspace climbs to the root and stops;
  - Ctrl+A and Delete work;
  - Space opens and closes Quick Look on the hovered tile, and the arrows walk it;
  - after the viewer closes, the keys still work without a click.
- No file over 150 lines; the anti-FQN grep is clean; `./gradlew.bat :shared:build` is green.

## Result

- 2026-10-17: built; `:shared:build` green with 1387 tests.
  - `FolderKeysTest` passes 5 of 5.
  - The pure decisions live in `FolderMoves.kt`, split from `FolderKeys.kt` so the test needs no
    grid.
  - `FolderCard`'s focus ring and check circle moved into `FolderCardMarks`, because the card
    passed 150 lines.
- Found while building: Ctrl+A would never have arrived. The grid's Ctrl branch ended in
  `else -> false` for anything that was not a zoom key, so it now falls through to the folder's
  handler.
- 2026-10-17: the by-hand check passed, run by the author, with one fault. Ctrl+A selected
  everything but showed no selection bar: `selectAll` never turned select mode on, because its only
  caller had been the bar's own Select All. It enters select mode now, and `FolderSelectionTest`
  asserts it. The build is green.
- 2026-10-17: the author confirmed the fix and asked for the commit.
