# idea--Rethink Home

**Status: problem stated, nothing designed yet.**

Home is built for a library of many linked folders. In reality a user links one or two — a films
folder, maybe a screenshots folder — once, and never touches the list again. The screen then costs a
click on every launch and shows nothing that changed since yesterday.

Home has to be reworked around that reality. How is not decided. Whatever it becomes has to be
beautiful, correct as UX, and proportionate — no new concepts bolted on to justify the screen.

## What exists today

- `Screen.Home` is a `LazyVerticalGrid` of `linkedFolders` (`HomeScreen.kt:65`), `GridCells.Adaptive`
  with `contentPadding(top = 20, start = 40, end = 40, bottom = 120)`. Empty state is the string
  "No linked folders".
- Linking a folder has no affordance on the screen itself: a `+` injected into the dock
  (`HomeAddButton.kt:16`) and a right-click on the background.
- The dock's **Library** item lists `pinnedSessions` in a hover popup (`LibraryPopup.kt:66`), a
  different set of folders from the ones on Home. Clicking the item does nothing —
  `onTabSelected` handles only `DockTab.Home` (`App.kt:80`).
- `PinnedSession` is a path plus a scroll position. It can already be a folder *inside* a linked
  root: pinned from the dock with its scroll position (`FolderDockSetup.kt:39`), from a folder's
  context menu (`FolderItemContextMenu.kt:57`), or from Home (`HomeScreen.kt:83`). Nothing about
  pinned appears on Home.
- Launch always opens `[Home]` unless `Woka.exe [path]` supplies a target (`StartupRoute.kt:20`).

## Ideas raised, none chosen

- Library in the dock lists the linked folders rather than pinned sessions.
- Drag and drop on Home to order the linked folders.
- Launch opens whichever folder is first in that order.
- Pinned folders appear on Home rather than only in the dock popup.

## Open questions

- Where Home sits in the flow once a folder can open at launch, and whether that is right at all.
- What happens to Pinned — it must not be lost.
- How the screen is composed when it holds one or two cards instead of a gallery.
- Where "link a folder" lives so it is discoverable at zero folders and at twenty.
