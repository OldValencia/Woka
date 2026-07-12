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

1. **Clean up existing UI:** Remove `AppSidebar.kt`, `GlobalTopBar.kt` (or heavily rewrite it), and the current `MainShell` layout.
2. **Build Smart Breadcrumbs:** Implement the path state, collapse logic, and the `...` expansion popup.
3. **Build Top Bar:** Integrate breadcrumbs and window controls.
4. **Build Floating Dock:** Implement the dock, active state coloring, and routing integration.
5. **Implement Spotlight Search:** Build a centered floating search modal.
6. **Implement Settings Animation:** Build the settings panel anchored to the dock's settings icon.
7. **Build Home View:** Implement the linked folders dashboard.
8. **Update Theming (`IrisTheme.kt`):** Add the new OLED-friendly palette and dock gradient/blur tokens.
