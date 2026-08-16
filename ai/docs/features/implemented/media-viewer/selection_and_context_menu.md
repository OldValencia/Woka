# Plan: Selection & Context Menu Feature

## Phase 1: ViewModels State Updates
- Add `isSelectMode` (Boolean) and `selectedPaths` (Set<String>) to `FolderState` and `HomeState`.
- Implement `toggleSelectMode()` to flip state and clear `selectedPaths`.
- Implement `toggleSelection(path: String)` to add/remove path from `selectedPaths`.
- Implement `clearSelection()` and `selectAll()`.
- Add `refresh()` to reload items from repository.

## Phase 2: Selection Mode UI Implementation
- Modify `LibraryHeader`: Replace "Select" button with "Cancel" when `isSelectMode == true`.
- Modify `LibraryHeader` Subtitle: Display `${selectedPaths.size} selected` when `isSelectMode == true`.
- Modify `FolderCard`: Apply blue border and checkmark icon overlay if its path is in `selectedPaths`.
- Override `FolderCard` click listener: If `isSelectMode == true`, invoke `toggleSelection(path)` instead of navigating.
- Implement `SelectionBottomMenu`: Display buttons "Move", "Copy", "Duplicate", "Delete". Mount this menu in `FolderScreen` and `HomeScreen` in place of `BottomAppMenu` when `isSelectMode == true`.

## Phase 3: Context Menu (Right-Click) UI
- Apply `pointerInput` with `awaitPointerEventScope` to the `LazyVerticalGrid` container to detect `PointerEventType.Press` with secondary button.
- Create `DropdownMenu` triggered by secondary press coordinates on an empty area, containing "Create Folder" option.
- Apply `pointerInput` to `FolderCard` to detect secondary button press.
- Create `DropdownMenu` triggered by secondary press on `FolderCard`, containing "Delete", "Duplicate", "Copy", "Move" options.

## Phase 4: Business Logic (File System Operations)
- Implement `createNewFolder(parentPath, name)` with name collision handling (e.g., "New Folder", "New Folder 1").
- Implement `deleteFiles(paths)` using `java.awt.Desktop.getDesktop().moveToTrash()`.
- Implement `duplicateFiles(paths)` using `File.copyRecursively()` and collision handling ("copy").
- Implement `moveFiles(sourcePaths, targetPath)` and `copyFiles(sourcePaths, targetPath)` using system directory picker `vinceglb.filekit` for target selection.
- Execute all file operations inside `Dispatchers.IO` block.
- Invoke `viewModel.refresh()` upon completion of file operations to reload the view.
