# Iris: Development Roadmap

Iris is a minimalist, aesthetic (Apple HIG style) desktop media viewer and editor for JVM (Compose Multiplatform), focusing on a curated gallery experience through "Linked Folders".

## 📍 Architecture & CLI Routing

The app entry point (`main()`) routes based on CLI arguments:
- `Iris.exe` ➡️ Opens **Home Screen**.
- `Iris.exe "D:\Path\To\Folder"` ➡️ Opens **Folder View** (Grid of media in that folder).
- `Iris.exe "D:\Path\To\File.jpg"` ➡️ Opens **Viewer** directly.

---

## 📝 Execution Phases

### Phase 1: Home Screen & Linked Folders (Foundation)
- **UI:** Clean empty state with a prominent `+ Link Folder` button.
- **Data Storage:** Settings and linked folder paths are stored locally in `user.home/Documents/Iris/` (or similar standard documents directory).
- **Logic:** OS Folder Picker dialog to select folders to pin.
- **Result:** Home Screen displays a grid/list of "Linked Folder" cards (showing name and a cover image or item count).

### Phase 2: Folder View & Subfolder Navigation (Grid)
- **UX:** Clicking a linked folder opens the Grid view of its media.
- **Subfolders:** Support for subfolders *within* the linked folder. Users can navigate into subfolders to view and organize files hierarchically.
- **UI:** Breadcrumb navigation (e.g., `Home > My Photos > Summer 2026`). Adaptive Grid of square thumbnails.
- **Logic:** Async scanning for `.jpg`, `.png`, `.mp4`. Generation and caching of thumbnails to ensure smooth scrolling.

### Phase 3: UI/UX Redesign & Polish
- **Goal:** Transform the initial "draft" UI into a premium, macOS/Apple HIG-inspired desktop experience before building complex features.
- **Top Bar:** Unify the title bar, breadcrumbs, search, and action buttons into a sleek, blur-backed native-feeling toolbar.
- **Grid & Cards:** Refine folder and media cards with proper corner radii, smooth hover animations, selection states, and glassmorphism elements.
- **Typography & Spacing:** Adjust fonts (weight, size, color hierarchy) and padding to ensure a clean, uncluttered, professional aesthetic.
- **Empty States & Settings:** Polish the Settings overlay and empty folder views with engaging graphics or subtle instructions.

### Phase 4: The Viewer (Preview & Carousel)
- **UX:** Clicking a media file in the grid dims the background and opens the Viewer overlay.
- **UI:** Image centered, bottom carousel (context of the current folder), top toolbar (Title, Zoom, `X` button).
- **Direct OS Launch Behavior (Double Click):** 
  - If a file is opened directly from the OS, the Viewer opens.
  - Clicking `X` does **not** close the app. Instead, it transitions to the **Folder View** of that file's parent folder.
  - If the parent folder is not currently linked, the app presents a prompt/button: "Pin this folder to Home Screen?".
- **Logic:** High-quality rendering via Skia, zoom (scroll), pan (drag), and video playback via JavaCV.

### Phase 5: Media Editor (Editing tools)
- **UX:** Click `Edit` in the Viewer toolbar.
- **Features:** Crop, rotate, vector drawing (arrows, shapes, text annotations), and lossless video trimming.
- **Logic:** Keep an `EditorState` history. On save, overwrite the original file but keep a hidden backup of the initial state.

### Phase 6: OS Integration
- Register Iris in Windows Registry to handle `.jpg`, `.png`, `.mp4` file extensions.
- Add context menu entry for directories: `Open with Iris`.
- (Future) IPC/Integration with the Echo clipboard manager.
