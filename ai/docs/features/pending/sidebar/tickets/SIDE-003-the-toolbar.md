# SIDE-003 — The toolbar replaces the dock

| | |
|---|---|
| **Order** | third |
| **Depends on** | SIDE-002 |
| **Feature** | [Sidebar](../sidebar.md) |

**Goal.** Delete the dock, and with it navigation history. The top bar becomes the mockup's
toolbar, 52.dp high.

- Left, after the sidebar: a show-sidebar button when the sidebar is hidden, the overlay's Back,
  the path.
- Right: the folder's controls, then Search, Settings and the window controls. The folder's controls
  are the All · Photos · Videos filter, sort, the three-step size slider and pin.

Selecting shows the mockup's bar at the bottom of the grid. The link offer becomes a strip at the
top of the content. A folder re-entered in the same run comes back at its scroll.

**Why now.** The dock is the last thing floating over the grid, and SIDE-004's grid is laid out for
a content area with nothing on top of it.

**Back and Forward go (decided 2026-10-17).** The tree, the path and Backspace (SIDE-005) already
say where to go, and history said only where you had been. The router keeps a single screen. The
`<` button stays only as the viewer's and editor's way out (`backActionOverride`), which was never
history.

**Size.** About twenty-five files. Most of them are deletions of the dock and its tenants.

## Steps

1. **Settings.** Add these to `WokaSettings`, `LibrarySettings` and `LibrarySettingsImpl`:
   - `sidebarVisible: Boolean = true` with `setSidebarVisible`. It is on by default because the
     sidebar is the navigation.
   - `setMediaFilter(filter: String)`. The field has existed since before this feature and never had
     a setter.

2. **The router keeps one screen.**
   - Delete `pop`, `forward`, `goForward`, `disarmTop` and `withoutArrivalCommand` from `Router.kt`.
     There is no history to replay an arrival command from.
   - `navigateTo` becomes the same as `replaceTop`. `stack` stays a list of one entry, so the launch
     route and `PipManager` keep their call sites.
   - Rewrite `RouterTest` to the new contract: navigation replaces, and `start` with an empty list is
     ignored.

3. **The right slot is claimed by key.** Add `setRight` and `clearRight` to `TopBarAccessoriesState`,
   mirroring `setLeading`. The folder claims the slot; the viewer and the editor still assign it and
   restore it directly. The reason is the one `setLeading` documents: `AnimatedContent` disposes the
   outgoing folder after the incoming one has claimed the slot.

4. **The top bar: `GlobalTopBar.kt`, with its buttons split into `app/shell/ToolbarButtons.kt` (new).**
   - **Height and padding:** 52.dp high, 10.dp horizontal padding.
   - **Left:**
     - while the sidebar is hidden, a `SidebarLeft` button that shows it;
     - the `<` button, only while `backActionOverride` is set;
     - the path.
   - **Right:** `rightAccessory`, then Search and Settings, then `WinControls`. Search is a magnifier
     button that opens the existing overlay; SIDE-004 makes it a field.
   - **Overlays:** Search and Settings hide while an overlay holds `centerAccessory`, as the dock hid
     under the viewer.
   - **Narrow windows:** a `BoxWithConstraints` provides `LocalToolbarCompact`, true under 760.dp, so
     the folder's controls can drop their two widest items at the 800 px minimum window.

5. **The sidebar hides.**
   - The header gets a `SidebarLeft` button that calls `setSidebarVisible(false)`.
   - The header moves to `SidebarHeader.kt`, because `Sidebar.kt` is at 149 lines.
   - The width animates between 236.dp and 0 over 250 ms with `FastOutSlowInEasing`.
   - `HeaderHeight` becomes 52.dp to match the top bar.

6. **Shortcut.** In `MainShell`, Ctrl+F opens search when no screen handler consumed the key.
   `MainShell` loses `DockLayer` and `LocalDockAccessories`.

7. **The folder's controls: `feature/folder/FolderToolbar.kt` (new).** `FolderToolbarSetup` claims
   the right slot keyed on the path. It holds:
   - `SegmentedControl` over All · Photos · Videos, bound to `mediaFilter`;
   - a `Sort` icon button opening a `ContextMenuPopup` with the two sort rows `LibraryFilterMenu` had;
   - a 72.dp `DetentSlider` with detents at 0, ½ and 1, mapped to `SmallGrid`, `Grid` and `BigGrid`
     and snapping to the nearest step;
   - a `Pin` icon button in `accent` while pinned. Unpinning removes the pin; pinning stores the
     current scroll, as the dock's pill did.
   
   Under `LocalToolbarCompact`, the filter and the slider are not drawn. Ctrl+wheel and Ctrl+/-
   still size the grid.

8. **Selection: `feature/folder/FolderSelectionBar.kt` (new).** It is drawn while `isSelectMode` is
   true, bottom-centred over the grid in `floatingPanel`, and reuses `AccessoryPill`:
   - *N selected*;
   - Select All, Copy…, Move…, Duplicate and Delete, with the bodies `FolderDockSetup` had;
   - **Done**, which leaves select mode.
   
   Modifier-click on a tile already enters select mode. A Select button is no longer needed, and
   SIDE-004's hover check circles are the visible way in.

9. **Scroll per folder: `feature/folder/FolderScrollMemory.kt` (new),** a Koin `single`.
   - It holds `path → PinnedSession`, replaced and never mutated, and lives for the run.
   - `FolderContent` writes it beside `setLastSession`.
   - The initial scroll comes from the memory first, then the pinned session, then `lastSession`.

10. **The link offer.** `FolderPinPrompt` is drawn at the top-centre of the content, 12.dp down, in
    place of the dock's top slot.

11. **Delete.**
    - Remove `DockLayer`, `FloatingDock`, `DockSecondaryItem`, `DockAccessories`, `FolderDockSetup`,
      `FolderTopBar.kt` (`LibraryLeftAccessory`) and `feature/library/`. Move `AccessoryPill` to
      `core/ui/components/AccessoryPill.kt` first.
    - `FolderScreen` stops toggling dock visibility.
    - `EditorTokens`' comments cite the dock's geometry as the source of their values. They say the
      dock is gone and the values stay.

## Files

- New:
  - `app/shell/ToolbarButtons.kt`
  - `app/shell/sidebar/SidebarHeader.kt`
  - `feature/folder/FolderToolbar.kt`
  - `feature/folder/FolderSelectionBar.kt`
  - `feature/folder/FolderScrollMemory.kt`
  - `core/ui/components/AccessoryPill.kt`
- Edit:
  - `WokaSettings`, `SettingsRepository`, `LibrarySettingsImpl`
  - `Router`, `App`, `Koin`
  - `MainShell`, `GlobalTopBar`, `TopBarAccessories`
  - `Sidebar`, `SidebarTokens`
  - `FolderContent`, `FolderScreen`
  - `EditorTokens`
  - `RouterTest`
- Delete:
  - `DockLayer`, `FloatingDock`, `DockSecondaryItem`, `DockAccessories`
  - `FolderDockSetup`, `FolderTopBar`
  - `feature/library/*`

## Done when

- `RouterTest` covers the one-screen contract.
- `WokaSettingsTest`'s round trip carries `sidebarVisible`.
- By hand, with the result dated in this file:
  - no dock anywhere;
  - the filter changes the grid;
  - sort and size work from the toolbar;
  - pin toggles and shows in the sidebar;
  - Ctrl or Shift-click selects and the bar acts;
  - Search and Settings open, and Ctrl+F opens search;
  - the sidebar hides and shows, and stays hidden after a restart;
  - a folder left and re-entered keeps its scroll;
  - the viewer's `<` still closes it;
  - at the 800 px minimum window the toolbar does not overlap.
- No file over 150 lines; the anti-FQN grep is clean; `./gradlew.bat :shared:build` is green.

## Result

- 2026-10-17: built; `:shared:build` green with 1373 tests.
  - The first full run failed once in `FolderSelectionTest`. It then passed three runs in
    isolation and the next full run, which is the known flake rather than a regression.
  - `RouterTest` was rewritten to four rows for the one-screen contract.
- The search button opens the existing overlay. SIDE-004 makes it a field with results in the
  content.
- 2026-10-17: three faults were reported from the running app, and all three are fixed. The build
  is green again.
  - **The filter was drawn over the path, and it pushed the rest of the toolbar off the window.**
    Search, Settings and the window controls went with it. `SegmentedControl` fills the width it
    is given. It now gets a fixed 186.dp, and the bar is one `Row` whose path takes what is left
    and clips, so the two sides cannot overlap again.
  - **The sidebar icon looked unfinished.** The SVG's hairlines landed under a pixel at 15.dp. It
    is now drawn on a Canvas at 1.5.dp (`ToolbarSidebarButton`).
  - **Ctrl+F did nothing.** The root node sees keys only while focus is beneath it. Ctrl+F is now
    the window's own key (`WindowKeys`, hooked in `main.kt`).
  - Added `ToolbarSearchField`, the mockup's search box. It opens the overlay until SIDE-004
    makes it take text.
- 2026-10-17, second round from the running app: the bar was too busy, and the slider could not get
  back to M once moved. Changed, following the author's choices, with each decision in the feature
  doc's log:
  - Filter, size and sort moved behind one **View** button (`FolderViewOptions`).
  - The size is now S · M · L, one for every folder (`gridLayout` replaces `folderLayouts`).
  - The path became the folder's name (`ToolbarTitle`; `SmartBreadcrumbs` deleted).
  - The first window opens at 80 % of the screen, at most 1440×900 (`main.kt`).
  - The mockup was updated first.
- 2026-10-17, third round: the View panel was widened to 330.dp, and its segments are taller and
  filled with the accent. A second click on View now closes the panel instead of reopening it.
- 2026-10-17: the author accepted the result and asked for the commit.

