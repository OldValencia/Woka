# SIDE-002 — No Home

| | |
|---|---|
| **Order** | second |
| **Depends on** | SIDE-001 |
| **Feature** | [Sidebar](../sidebar.md) |

**Goal.** Delete the Home screen and the dock's Home item.
- Launch opens the folder that was open at quit, at the scroll it was left at.
- With nothing linked, launch opens a Welcome screen.
- The top bar gains Forward beside Back.

**Why now.** Until Home is gone the sidebar is a second list of the same folders, and SIDE-003
cannot strip the dock while one of its items is a destination.

**Size.** About twenty-five files, past §4a's eight. Half of them are deletions: the home feature,
its test, `DockItem`, and the `DockTab` plumbing. Launch restore and Home's removal are one outcome,
because without Home there is nothing else to launch into.

**What it leaves alone.**
- The dock keeps Search, Settings and the folder's accessories until SIDE-003.
- The Welcome screen says *link*, not *drop*: OS drag-and-drop arrives in SIDE-007, and the screen
  will not promise it before then. The mockup's "Drop a folder here" waits for that ticket.

## Steps

1. **The record.** Add `val lastSession: PinnedSession? = null` to `WokaSettings`, with a comment:
   the folder on screen and its scroll, which is what launch reopens; null until a folder has been
   shown. Add `fun setLastSession(session: PinnedSession)` to `LibrarySettings`, implemented in
   `LibrarySettingsImpl` as `mutator.update { it.copy(lastSession = session) }`.

2. **The router.**
   - In `Router.kt`, replace `Screen.Home` with `data object Welcome : Screen()`; the initial stack
     is `listOf(Screen.Welcome)`.
   - Add `private val _forward` and `val forward: StateFlow<List<Screen>>`.
   - `pop()` appends the popped screen to `forward`, disarmed with `withoutArrivalCommand()`.
   - Add `fun goForward()`. It moves `forward`'s last entry back onto the stack, disarming the
     current top the way `navigateTo` does.
   - `navigateTo` and `start` clear `forward`; `replaceTop` leaves it alone.
   - Delete `popTo`. Its only caller was the dock's Home tab.

3. **The launch route.** `startupStack(args, last: PinnedSession?, linked: List<String>)`:
   - a directory argument gives `[Folder(dir, offerPin = true)]`, and a file argument gives
     `[Folder(parent, openViewerForId, offerPin = true)]`. Neither puts anything under it any more;
   - with no usable argument, it tries `last`, which must be a directory inside a linked root
     (`isWithin`), so an unlinked or deleted folder does not come back;
   - then the first linked root that is a directory;
   - then `[Screen.Welcome]`.
   
   `initKoin` passes `koin.get<SettingsRepository>().settings.value`'s `lastSession` and
   `linkedFolders`.

4. **Writing it.** In `FolderContent`:
   - the initial scroll comes from the pinned session for `path`, or else from `lastSession` when
     `samePath(lastSession.path, path)`;
   - a `LaunchedEffect(path)` collects `snapshotFlow { firstVisibleItemIndex to offset }` with
     `collectLatest { delay(500); setLastSession(PinnedSession(path, index, offset)) }`. It also
     writes once on arrival, so a folder opened and never scrolled is still the one restored.
   
   The outgoing folder's effect is cancelled when `AnimatedContent` disposes it, so it never
   overwrites the incoming one. `SettingsStore` flushes a pending write on close, so the last
   settle survives quitting.

5. **Welcome: `feature/welcome/WelcomeScreen.kt` (new).** It draws the mockup's `.empty .drop` card:
   - 460.dp max width, a dashed 1.5.dp `border` outline at 16.dp radius, drawn with
     `drawBehind` + `PathEffect.dashPathEffect`;
   - the `Folder` glyph at 46.dp in `accentFolders`;
   - **Link a folder** at 17.sp SemiBold, then one line at 13.sp `textSecondary`: *Woka shows what
     is inside and keeps the folder in the sidebar. Nothing is copied or moved.*;
   - an accent button **Link Folder…**, which runs the picker, then `addLinkedFolder`, then
     `router.start(listOf(Screen.Folder(path)))`.
   
   `ScreenHost` draws it for `Screen.Welcome`.

6. **The sidebar's first run.** With `linked` empty:
   - `SidebarFolders` draws one muted row, *Nothing linked yet*;
   - `SidebarFoot(prominent = true)` draws the Link row in `accent` with `onAccent` text, as
     `.app.first .side-foot .link` does.
   
   Unlinking the root of the open folder now calls `router.start` with the next linked root that is
   a directory, or `Screen.Welcome`, instead of Home.

7. **The top bar.** `GlobalTopBar` takes `onNavigateForward: (() -> Unit)?` and draws a `>` button
   beside `<` in the same style. It is hidden while `backActionOverride` is set, because an overlay
   owns Back and a Forward under it would navigate the folder beneath the viewer. `App` passes
   `router::goForward` when `forward` is non-empty.

8. **Delete Home.**
   - Delete `feature/home/` (six files), `HomeViewModel` from `appModule`, and
     `jvmTest/integration/HomeViewModelTest.kt`.
   - Delete `DockTab`, the Home `DockItem` and `DockItem.kt`, whose only caller that was.
   - Remove `activeTab` and `onTabSelected` from `App`, `MainShell`, `DockLayer` and
     `FloatingDock`.
   - `App` falls back to `Screen.Welcome` on an empty stack.
   - In `FolderPinPrompt`, the offer reads *Link "<name>"?* with a **Link** button. In `FolderContent`,
     the comment naming "what Home lists" names the sidebar.

## Files

- New:
  - `shared/src/commonMain/kotlin/com/woka/feature/welcome/WelcomeScreen.kt`
  - `shared/src/jvmTest/kotlin/com/woka/unit/RouterTest.kt`
- Edit:
  - `…/core/data/WokaSettings.kt`
  - `…/core/data/SettingsRepository.kt`
  - `shared/src/jvmMain/kotlin/com/woka/core/data/LibrarySettingsImpl.kt`
  - `…/app/navigation/Router.kt`
  - `…/app/navigation/StartupRoute.kt`
  - `…/app/navigation/ScreenHost.kt`
  - `…/app/di/Koin.kt`
  - `…/app/App.kt`
  - `…/app/shell/MainShell.kt`
  - `…/app/shell/DockLayer.kt`
  - `…/app/shell/FloatingDock.kt`
  - `…/app/shell/GlobalTopBar.kt`
  - `…/app/shell/sidebar/Sidebar.kt`
  - `…/app/shell/sidebar/SidebarSections.kt`
  - `…/feature/folder/FolderContent.kt`
  - `…/feature/folder/FolderPinPrompt.kt`
  - tests: `StartupRouteTest.kt`, `PipReturnTest.kt`, `WokaSettingsTest.kt`
- Delete:
  - `…/feature/home/*`
  - `…/app/shell/DockItem.kt`
  - `HomeViewModelTest.kt`

## Done when

- `RouterTest` (`unit/`) proves:
  - Back then Forward returns to the same screen, disarmed;
  - a new navigation clears Forward;
  - `start` clears both.
- `StartupRouteTest` is re-asserted row by row:
  - no argument with a valid `last` inside a root opens it;
  - a `last` outside every root, or deleted, falls to the first root;
  - nothing linked opens Welcome;
  - a directory or file argument still wins and offers the link.
- `WokaSettingsTest`'s full round trip carries `lastSession`.
- By hand, with the result dated in this file:
  - quit inside a folder halfway down, relaunch, and land there;
  - Back and Forward walk the history;
  - unlinking every folder ends on Welcome, and linking from it opens the folder;
  - the dock has no Home item.
- No file over 150 lines; the anti-FQN grep is clean; `./gradlew.bat :shared:build` is green.

## Result

- 2026-10-17: built; `:shared:build` green with 1374 tests.
  - The new tests are `RouterTest`, 5 of 5, and `StartupRestoreTest`, 4 of 4. The rows for the
    remembered folder moved there out of `StartupRouteTest`, which would otherwise have passed 150
    lines.
  - The `settings.json … being used by another process` lines in the test output are not from this
    ticket. All 315 come from `SettingsStore`'s shutdown hook (`SettingsStore.kt:81`) at JVM exit,
    which is the case `code-style/test.md` § Storage isolation describes.
- 2026-10-17: found while building it. Back was gated on history alone, so a file launched from
  Explorer, now a one-screen stack, would have opened the viewer with no Back. Back now also shows
  while an overlay supplies `backActionOverride`.
- 2026-10-17: the author accepted the result and asked for the commit.

