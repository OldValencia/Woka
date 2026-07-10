# Phase 2: Folder View & Subfolder Navigation

This document is a strict, step-by-step tutorial for the AI to develop Phase 2 of Iris.

## 🚨 Core Developer Rules
- **File Size Limit:** Files MUST strictly aim to be under 100-150 lines. Break down UI components and logic to stay within this limit.
- **No Fully Qualified Names:** Always use imports.
- **Pure Desktop:** No Android/iOS dependencies.
- **Theming:** No hardcoded colors. We must implement Light/Dark mode via `isSystemInDarkTheme()`. (We will set up a basic `IrisTheme` before starting UI work).

## Goal
Implement navigation from the Home Screen to a specific linked folder. Inside this folder, display a grid of subfolders and media files (`.jpg`, `.png`, `.mp4`). Provide breadcrumb navigation to go back to Home or parent folders.

---

## Step 1: Basic Theming & Primitive Updates
Before building new UI, we need to finalize the theming rule we discussed.
1. Create `IrisTheme.kt` with a simple composition local or object holding colors (Background, Surface, TextPrimary).
2. Update `IrisButton`, `HomeScreen`, and `FolderCard` to use these semantic colors instead of hardcoded `Color.White` or `Color(0xFFF2F2F7)`.

## Step 2: Simple Navigation System
Since we want to avoid heavy Android-like Navigation libraries (like Jetpack Navigation), we will build a simple stack-based router in pure Compose.
1. Create `sealed class Screen { data object Home : Screen; data class Folder(val path: String) : Screen }`.
2. Create a `Router` or just hold a `MutableState<List<Screen>>` in `App.kt`.
3. Update `FolderCard`'s `onClick` in `HomeScreen` to push `Screen.Folder(path)` to the stack.

## Step 3: Domain & Data Layer (Media Scanner)
1. **Model:** Create `sealed class MediaItem { data class Folder(val file: java.io.File) : MediaItem; data class Image(val file: java.io.File) : MediaItem }`.
2. **Repository:** Create `LocalMediaRepository`. It should have a function `fun getFiles(directory: String): Flow<List<MediaItem>>`. 
   - It should filter for directories and supported image/video formats.
   - It should sort folders first, then files alphabetically.

## Step 4: Folder ViewModel
1. Create `FolderViewModel(path: String)`.
2. Inject `LocalMediaRepository`.
3. Expose `val state: StateFlow<FolderState>` holding the current path and `List<MediaItem>`.

## Step 5: UI Implementation
1. **Breadcrumb TopBar:** A horizontal row at the top showing the navigation path (e.g., `Home > Images > Vacation`). Clicking "Home" pops the stack.
2. **FolderScreen:** 
   - Uses `LazyVerticalGrid`.
   - Displays `MediaCard` for each item.
3. **MediaCard:**
   - If it's a folder, show a nice folder icon and the name.
   - If it's an image, use `Coil 3` (`AsyncImage`) to load the thumbnail.

## Testing Criteria
- Clicking a linked folder on the Home Screen opens `FolderScreen`.
- The Breadcrumb shows `Home > FolderName`.
- The grid shows subfolders and images.
- Clicking a subfolder navigates deeper.
- Clicking breadcrumb "Home" goes back to the home screen.
- Changing OS theme (Light/Dark) updates the app colors dynamically without restarts.
