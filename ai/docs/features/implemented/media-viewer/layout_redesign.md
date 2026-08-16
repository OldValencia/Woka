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
- [x] **Build Smart Breadcrumbs:** Implement the path state, collapse logic, and the `...` expansion popup.
- [x] **Build Top Bar:** Integrate breadcrumbs and window controls. Native-like window dragging and snapping.
- [x] **Build Floating Dock:** Implement the dock, active state coloring, and routing integration.
- [x] **Implement Spotlight Search:** Build a centered floating search modal. Highlight & scroll to items.
- [x] **Implement Settings Animation:** Build the settings panel anchored to the dock's settings icon. macOS style layout.
- [x] **Build Home View:** Implement the linked folders dashboard.
- [x] **Update Theming (`Theme.kt`):** Add the new OLED-friendly palette and dock gradient/blur tokens.
- [x] **Folder Navigation & Zoom:** Implement discrete Zoom modes (Small, Grid, Big) and Sorting (Name/Date, Asc/Desc) per folder.
- [x] **Library & Pinned Folders:** Implement the Library dock popup that grows upwards and displays pinned folders, with a glowing badge indicator.
- [x] **Select Mode & File Operations:** Implement multi-item selection with vibrant borders. Integrate file actions (Delete, Duplicate, Copy, Move) using native Desktop directory pickers.

## Progress Log (Recent)
- **Search & Navigation:** 
  - Clicking on a search result (file or folder) now flawlessly navigates to the parent directory.
  - Automatically scrolls to the target item in the grid.
  - Applies a beautiful 2-second glowing highlight to the specific item ("You were searching for me!").
  - Smart routing: if the user is already in the target folder, the screen seamlessly updates without triggering redundant slide animations.
- **Window Management & Top Bar:** 
  - Fixed native window dragging and double-click maximization.
  - Implemented proper Unmaximize + Snap to Mouse (Floating mode) behavior using Compose `windowState.position` assignment directly before state changes, eliminating visual glitches and "jumping".
- **Settings Redesign:**
  - Revamped the Settings screen to follow an elegant macOS style: sidebar on the left (icons + text), content on the right.
  - Fixed theme-aware colors (dark text on light theme, white text on dark theme) and popup focus blocking.
  - Added smooth opening/closing animations sliding out from the Dock.
- **Folder View Enhancements:**
  - Added toggle to show/hide hidden files (files starting with `.`).
  - Polished the context menus and interactions.
