# SIDE-001 — The sidebar

| | |
|---|---|
| **Order** | first |
| **Depends on** | nothing |
| **Feature** | [Sidebar](../sidebar.md) |

**Goal.** Put a sidebar on the left of the window. It shows the linked folders as a tree, the pinned
folders under them, and `Link Folder…` at its foot. The dock's Library item and its hover popup are
removed.

**Why now.** Every later ticket assumes the sidebar is where navigation lives. SIDE-002 cannot
delete Home until the linked folders have somewhere else to be.

**What it leaves alone, on purpose.** Home still exists and is still the launch screen. The dock
keeps Home, Search and Settings, and the folder's accessories. Both go in SIDE-002 and SIDE-003.
Unlinking still fires immediately, as it does from Home's card today; SIDE-007 makes it undoable.

**Size.** About fourteen files, past §4a's eight. Most are new and small, and the smallest slice the
author can see is the whole sidebar. A tree without Pinned, or a sidebar without the Library item
removed, would show two places for the same folders.

Visual values are from `ai/mockups/design/sidebar-redesign.html`, except the header height. It is
60.dp here, to line up with today's top bar; SIDE-003 brings both to 52.dp.

## Steps

1. **Make room in `MainShell.kt` (146 lines).** Move the dock block into
   `app/shell/DockLayer.kt` as `@Composable fun BoxScope.DockLayer(activeTab, onTabSelected,
   onOpenSearch, onOpenSettings)`, with behaviour unchanged. The dock block is the
   `if (dockAccessories.isVisible)` branch: the gradient, the floating `Box`, and the private
   `AnimatedAccessory`. `MainShell` calls it in the same place.

2. **The tree's data: `app/shell/sidebar/SidebarTree.kt` (new). Plain functions, no Compose.**
   - `data class SidebarNode(val path: String, val name: String, val mediaCount: Int)`.
   - `fun listSubfolders(path: String): List<SidebarNode>`:
     - list `File(path).listFiles()` and keep `isDirectory && !isHidden`;
     - sort by `name.lowercase()`;
     - `mediaCount` is `child.listFiles()?.count { it.isFile && it.isMedia } ?: 0`.
     
     **Non-recursive on purpose**: see the feature doc, *The sidebar*.
   - `fun ancestorsWithin(root: String, path: String): List<String>` returns the directories from
     `root` down to `path`'s parent, both ends included. It is empty when `path` is not under `root`.
     Compare normalised absolute paths, so `D:\A` and `D:/A/` match.
   - `fun pinnedParentLabel(path: String, roots: List<String>): String` returns the pinned folder's
     parent, relative to the root that contains it, with segments joined by `" › "`. The root's own
     name comes first. Outside every root, it is the parent's name.

3. **The tree's state: `app/shell/sidebar/SidebarTreeState.kt` (new).** A `@Stable` holder with no
   ViewModel and no Compose UI.
   - Fields: `var expanded: Set<String>` and `var children: Map<String, List<SidebarNode>>`, both
     `mutableStateOf`, always replaced with `+`/`-` and never mutated.
   - `suspend fun expand(path)` loads `listSubfolders` under `withContext(Dispatchers.IO)` if the
     path is not cached, then adds it to `expanded`.
   - `fun collapse(path)` removes it from `expanded`.
   - `suspend fun reveal(current: String?, roots: List<String>)` expands every `ancestorsWithin` of
     the root that contains `current`.
   - `@Composable fun rememberSidebarTreeState()`.

4. **The pin icon.** Add `commonMain/composeResources/drawable/pin.svg`, using the mockup's pin path
   on a 16-unit viewBox. Add `val Pin = Res.drawable.pin` to `Icons.Static` in `core/ui/icons/Icons.kt`.

5. **The rows: `app/shell/sidebar/SidebarRow.kt` (new).** Draw both rows with
   `Theme.colors.surfaceHover` on hover. A row with `selected = true` gets a `Theme.colors.accentSoft`
   background and an `accent`-tinted icon.
   - `SidebarFolderRow(node, depth, expandable, isExpanded, selected, onClick, onToggle,
     onSecondaryClick: (IntOffset) -> Unit)`:
     - 30.dp high, radius 7.dp, indent `depth * 16.dp`;
     - `Icons.Static.ChevronRightMac` rotated 90° when expanded; it is hidden but keeps its space
       when the row cannot expand, and a click on it only toggles;
     - a `Folder` icon tinted `accentFolders`, the name at 13.sp in `textPrimary`, and the count at
       11.sp in `textTertiary`.
   - `SidebarPinnedRow(session, label, selected, exists, onClick, onSecondaryClick)`:
     - 40.dp high, with the `Pin` icon in `textSecondary`;
     - the folder name over `label` at 11.sp in `textTertiary`;
     - when `exists` is false, the whole row is drawn in `textTertiary` and the click is ignored.
   
   Right-click detection copies `homeGridInput`'s secondary-press handling, offset included.

6. **The menus: `app/shell/sidebar/SidebarMenu.kt` (new).** `SidebarMenu(target, offset,
   isRoot, isPinned, onDismiss, …)` built from `ContextMenuPopup` and `DropdownItem`:
   - a root shows **Unlink Folder** in `danger`;
   - a non-root tree row shows **Pin** or **Unpin**;
   - a pinned row shows **Unpin**.

   The actions:
   - **Unlink** is `removeLinkedFolder` followed by `removePinnedSession`, exactly
     `HomeViewModel.removeLinkedFolder`. If the folder on screen is under that root, it calls
     `router.start(listOf(Screen.Home))`.
   - **Pin** is `addPinnedSession(PinnedSession(path))`.
   - **Unpin** is `removePinnedSession`.

7. **The sidebar: `app/shell/sidebar/Sidebar.kt` (new).** `Sidebar(currentPath: String?,
   topBarArea)` reads `AppDependencies.settingsRepository` and `AppDependencies.router`, as
   `FloatingDock` does, so `App.kt` grows nothing.
   - **Shell:** 236.dp wide, `Theme.colors.surface` background, a 1.dp `border` hairline on its right
     edge.
   - **Header:** 60.dp, wrapped in `topBarArea` so it drags the window like the top bar. It holds a
     20.dp logo square and `Woka` at 13.sp SemiBold.
   - **Scrolling column:**
     - a `Folders` section header (11.sp SemiBold, `textTertiary`);
     - the roots sorted by name, with their expanded children drawn recursively;
     - a `Pinned` header and the pinned rows, both absent when nothing is pinned.
   - **Foot:** behind a 1.dp hairline, `Link Folder…` with `Icons.Static.Add`. It runs
     `openDirectoryPicker("Link folder")`, then `addLinkedFolder`, then
     `router.navigateTo(Screen.Folder(path))`.
   - **Behaviour:**
     - a click on a folder or pinned row calls `router.navigateTo(Screen.Folder(path))`, unless it
       is already the folder on screen;
     - `LaunchedEffect(currentPath, linkedFolders)` calls `tree.reveal`, so the open folder is
       always visible and selected.

8. **Into the shell.** `MainShell` wraps its existing `Column` in a `Row`:
   `Sidebar(currentPath, topBarArea)` comes first, and the `Column` takes `weight(1f)`. The dock
   therefore centres on the content, not the window.

9. **The Library item goes.**
   - In `FloatingDock.kt`, delete the Library `Box`: `DockItem`, the hover state, the popup and
     `pinnedSessions`. Make `DockTab` just `Home`.
   - In `App.kt`, `activeTab` becomes `DockTab.Home` on Home and `null` elsewhere. Make the
     parameter `DockTab?` through `MainShell`, `DockLayer` and `FloatingDock`.
   - Delete `feature/library/LibraryPopup.kt`. `LibraryFilterMenu` and `LibraryTopAccessory` stay;
     the folder's accessory still uses them.

## Files

- New:
  - `shared/src/commonMain/kotlin/com/woka/app/shell/DockLayer.kt`
  - `…/app/shell/sidebar/SidebarTree.kt`
  - `…/app/shell/sidebar/SidebarTreeState.kt`
  - `…/app/shell/sidebar/SidebarRow.kt`
  - `…/app/shell/sidebar/SidebarMenu.kt`
  - `…/app/shell/sidebar/Sidebar.kt`
  - `shared/src/commonMain/composeResources/drawable/pin.svg`
  - `shared/src/jvmTest/kotlin/com/woka/integration/SidebarTreeTest.kt`
- Edit:
  - `…/app/shell/MainShell.kt`
  - `…/app/shell/FloatingDock.kt`
  - `…/app/App.kt`
  - `…/core/ui/icons/Icons.kt`
- Delete:
  - `…/feature/library/LibraryPopup.kt`

## Done when

- `SidebarTreeTest` (`integration/`, because it builds directories in a temp folder) proves:
  - files and hidden directories are left out and the order ignores case;
  - `mediaCount` counts only media directly inside a folder, never in its sub-folders;
  - `ancestorsWithin` matches across separators and a trailing slash, and is empty outside the root;
  - `pinnedParentLabel` names `Movies › Series` for `…/Movies/Series/Andor/Season 1`.
- By hand, with the result dated in this file:
  - the tree expands and collapses;
  - opening a folder from Home reveals and highlights it in the sidebar;
  - Pinned restores scroll;
  - Link Folder… links and opens the folder;
  - unlinking the open root returns to Home;
  - the dock has no Library item.
- `MainShell.kt` is at or under 150 lines, and every new file is under 150.
- The anti-FQN grep over every touched `.kt` is clean.
- `./gradlew.bat :shared:build` is green.
