# SIDE-004 — The photo mosaic

| | |
|---|---|
| **Order** | fourth |
| **Depends on** | SIDE-003 |
| **Feature** | [Sidebar](../sidebar.md) |

**Goal.** A folder that is mostly photos draws its media as the mockup's square mosaic: 3.dp
gaps, no captions, and the name on hover. Its sub-folders come first under their own heading. Every
tile, in both layouts, gets a check circle on hover, which is the visible way into select mode.

**Why now.** The grid is the screen. The toolbar ticket left it as it was: a 16:10 captioned card
for everything, which says "a file listing" where the folder is a photo album.

**Re-cut from the backlog block.** It also held search in the content. That is a separate visible
outcome and its own sitting, so it is now SIDE-008. A folder that is mostly video keeps today's card,
which already carries the title, the duration badge and the watched bar the mockup asks of it.

## Steps

1. **Tile sizes.** Add `val ViewLayout.tileSize: Dp` to `core/ui/components/GridDensity.kt`, beside
   `gridColumnSize`: S 96.dp, M 150.dp, L 240.dp. The values match the mockup's slider range
   (`--cell` 90–260).

2. **Which layout.** Add `fun List<MediaItem>.isMostlyPhotos(): Boolean` to
   `feature/folder/FolderGridLayout.kt` (new). It is true when the folder has media and fewer than
   half of its non-folder items are videos. It is a count over the items the grid already holds,
   never a setting.

3. **The tile body: `core/ui/components/FolderCardTile.kt` (new).**
   `internal fun FolderCardTileBody(data, style, isHovered, isSelected, watch, dimWatched)`:
   - a square, 3.dp radius, `surfaceElevated` behind;
   - the same `FolderCardPreview` the card uses;
   - `FolderCardCaption` only while hovered;
   - `FolderCardProgress(watch)`;
   - while selected, the picture insets to 90 % at 6.dp radius over an `accentSoft` ground, as
     `.tile.picked` does.

4. **The card learns both.** `FolderCard` takes:
   - `tile: Boolean = false`, which draws `FolderCardTileBody` in place of `FolderCardBody`;
   - `onCheck: (() -> Unit)? = null`, which draws a 20.dp check circle top-right while hovered or
     selected. The circle has a white 1.5.dp ring, fills with `accent` when selected, and its click
     toggles selection without opening the item.
   
   Both default to today's behaviour, so the card's other callers are untouched.

5. **The grid.** In `FolderGrid`, when `isMostlyPhotos`:
   - columns are `GridCells.Adaptive(layout.tileSize)` and both spacings 3.dp;
   - a full-span **Folders** heading leads the folders and a full-span **Media** heading leads the
     media, each with its count, 12.sp SemiBold `textTertiary`. A heading is drawn only when there
     is more than one kind of item;
   - each folder card spans `ceil(gridColumnSize / tileSize)` cells, clamped to `maxLineSpan`, and
     is padded 10.dp, so folders keep their card size beside the small tiles;
   - media items pass `tile = true`.
   
   Otherwise the grid is today's. Both layouts pass `onCheck`, which calls
   `viewModel.toggleSelection`. The bottom padding drops from 120.dp, which the dock needed, to
   80.dp, which the selection bar needs.

6. **Wiring.** `FolderGridItem` takes `tile: Boolean` and passes it and `onCheck` through.

## Files

- New:
  - `core/ui/components/FolderCardTile.kt`
  - `feature/folder/FolderGridLayout.kt`
  - `jvmTest/unit/FolderGridLayoutTest.kt`
- Edit:
  - `core/ui/components/GridDensity.kt`
  - `core/ui/components/FolderCard.kt`
  - `feature/folder/FolderGrid.kt`
  - `feature/folder/FolderGridItem.kt`

## Done when

- `FolderGridLayoutTest` (`unit/`) covers:
  - all photos means a mosaic;
  - half videos or more means cards;
  - folders do not count either way;
  - an empty folder means cards.
- By hand, with the result dated in this file:
  - a photo folder is a mosaic at S, M and L;
  - its sub-folders lead under a heading;
  - a video folder is unchanged;
  - hovering a tile shows its name and the check circle, and the circle selects and shows the
    selection bar;
  - opening, the context menu and Shift-click still work on a tile.
- No file over 150 lines; the anti-FQN grep is clean; `./gradlew.bat :shared:build` is green.

## Result

- 2026-10-17: built; `:shared:build` green with 1380 tests.
  - `FolderGridLayoutTest` passes 7 of 7.
  - It added `hasMosaicHeadings` and `gridIndexOf`. The headings take places in the lazy grid,
    so every index the screen hands it goes through `gridIndexOf`. That covers the viewer's last
    file and the highlighted search result; without it they landed one or two items short.
- 2026-10-17: found by the build. `FolderGridUiTest` found cells by their name, and a mosaic tile
  draws no name until hovered. The tile now carries its name as semantics
  (`clearAndSetSemantics`), so it answers to its name for a screen reader and for a test alike, as
  one node whether hovered or not. The test was right and was not changed.
- 2026-10-17: the by-hand check passed, run by the author, with one fault. Unticking the only
  selected tile left a "0 selected" bar behind. Deselecting the last item now leaves select mode
  (`FolderViewModel.toggleSelection`), with a regression row in `FolderSelectionTest`. The full
  build passed on its second run. The first failed once in that same class, on a row this change
  does not reach: the known flake, which passed 4 of 4 in isolation.
- 2026-10-17, second fault from the running app. The headings depended on the mosaic, so a film
  folder never had them, and filtering a photo folder to Videos took both away. Now:
  - the sections depend only on whether the folder has sub-folders (`hasSections`), in either
    layout. Media under a filter that finds nothing reads "Media 0";
  - the mosaic is decided on the folder before the filter (`FolderState.isMosaic`), so narrowing
    what is shown no longer changes the layout.
  
  New rows in `FolderGridLayoutTest` and `FolderStateMapperTest`; the build is green.
- 2026-10-17: the author confirmed both fixes in the running app and asked for the commit.
