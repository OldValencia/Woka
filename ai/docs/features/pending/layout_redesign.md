# UI Layout Redesign — v3 ("Fluid Luxury")

## Status: Planned

This redesign shifts the application from a traditional sidebar layout to a more modern, minimal "Fluid Luxury" aesthetic, optimized for OLED displays and maximizing the content area.

## Design Language

- **Deep Midnight Graphite:** Base canvas is a deep `#0E1015` with floating elements using a blurred `rgba(22, 26, 34, 0.8)` island background.
- **Vibrant Accents:** Active states use distinct, vibrant native colors (Home: `#0A84FF`, Folders: `#FF9F0A`, Editor: `#BF5AF2`, Search: `#30D158`).
- **Floating Dock:** The sidebar is entirely replaced by a premium floating dock at the bottom center of the screen.
- **Smart Topbar:** A minimal header housing window controls and smart breadcrumb navigation.

## Global Top Bar & Navigation

- **Window Controls:** macOS dots on the left, Windows controls on the right.
- **Smart Breadcrumbs:** Centered in the top bar. Shows the current path.
  - If the path is too long, it collapses middle segments into an interactive `...` menu.
  - Clicking the `...` expands the full path inside a scrollable view (if needed).
  - Clicking any folder in the breadcrumb navigates directly to that level.

## Floating Dock (Bottom)

- Replaces the sidebar. Floating above the content with heavy blur and drop shadow.
- **Primary Tabs:** Home, Folders, Editor.
  - **Home:** A dashboard showing linked root folders. Clicking a folder here switches the view to "Folders" and opens that path.
  - **Folders:** The main file browsing interface.
  - **Editor:** Media editing workspace.
- **Divider:** Separates tabs from secondary actions.
- **Secondary Actions:** 
  - **Search:** Triggers a Spotlight-style centralized search overlay, rather than an inline text field.
  - **Settings:** Clicking the settings icon triggers an animated settings panel sliding out directly from the icon.

## Content View & Media Grid

- Expanding content area behind the top bar and dock (with appropriate padding).
- **Media Cards:** Hover animations (translate Y, border glow, shadow expansion).
- **Thumbnails:** Procedural gradient backgrounds (or actual images later) with floating file type badges (e.g., PNG, RAW).
- **Metadata:** Clean title with ellipsis and muted subtitle (size/type/resolution) below the thumbnail.

## Execution Plan

- [x] **Clean up existing UI:** Remove `AppSidebar.kt`, `GlobalTopBar.kt` (or heavily rewrite it), and the current `MainShell` layout.
- [ ] **Build Smart Breadcrumbs:** Implement the path state, collapse logic, and the `...` expansion popup.
- [ ] **Build Top Bar:** Integrate breadcrumbs and window controls.
- [x] **Build Floating Dock:** Implement the dock, active state coloring, and routing integration.
- [x] **Implement Spotlight Search:** Build a centered floating search modal.
- [x] **Implement Settings Animation:** Build the settings panel anchored to the dock's settings icon.
- [x] **Build Home View:** Implement the linked folders dashboard.
- [x] **Update Theming (`IrisTheme.kt`):** Add the new OLED-friendly palette and dock gradient/blur tokens.
- [x] **Folder Navigation & Zoom:** Implement discrete Zoom modes (Small, Grid, Big) and Sorting (Name/Date, Asc/Desc) per folder.
- [x] **Library & Pinned Folders:** Implement the Library dock popup that grows upwards and displays pinned folders, with a glowing badge indicator.
- [x] **Select Mode & File Operations:** Implement multi-item selection with vibrant borders. Integrate file actions (Delete, Duplicate, Copy, Move) using native Desktop directory pickers.

## Progress Log (Recent)
- **Dock Refinements:** Renamed "Navigation" to "Library" and added a glowing badge indicator when pinned items exist. Fixed the hovering logic and upward-growing behavior for the Library popup.
- **Folder Screen Features:**
  - Replaced percentage zoom with discrete 3-mode zoom (Small/Grid/Big) persisted per folder.
  - Implemented Sorting via the filter popup.
  - Added Select Mode UI and wired it to `FolderViewModel`.
  - Added thick vibrant orange borders for selected items.
  - Added Delete, Duplicate, Copy, and Move file operations in Select Mode. Integrated AWT `JFileChooser` for picking destination directories.
  - Fixed filter SVG icon aspect ratio so it perfectly aligns with surrounding buttons.
  - Context menu on elements: Open, Rename, Copy, Move, Delete.
  - Context menu on empty background: Create folder, Pin to Library (colored yellow).
- **Home Screen Features:**
  - Added elegant animated "Add Linked Folder" dock item sliding out from the left of the main Dock.
  - Custom shaped (16:10 rectangle) remove icon for Home folders to beautifully match the folder aspect ratio.
  - Disabled Select Mode on Home view entirely for a cleaner UI.
