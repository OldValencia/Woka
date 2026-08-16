# Phase 1: Foundation & Home Screen

This document is a strict, step-by-step tutorial for the AI to develop Phase 1 of Woka without getting lost.

## 🚨 Core Developer Rules
- **File Size Limit:** Files MUST strictly aim to be under 100-150 lines. If a file grows larger, break it down (e.g., extract UI components into their own files, or split ViewModel logic). This is a critical rule to maintain a clean, understandable codebase.
- **No Fully Qualified Names:** Always use imports.
- **Pure Desktop:** Never use or add Android/iOS dependencies or logic.

## Goal
Establish the project architecture, set up DI and Serialization, implement local JSON settings storage, and build the Home Screen UI (Empty state and Grid state) with a functional "Link Folder" dialog.

---

## Step 1: Project Cleanup & Dependencies
Since we are building a purely JVM (Desktop) app, the default KMP template might have iOS/Android targets.
1. **Clean build.gradle.kts:** Ensure `shared/build.gradle.kts` only configures the `jvm()` target. Strip out Android and iOS source sets.
2. **Add Core Dependencies:** 
   - `koin-core` (for DI).
   - `kotlinx-serialization-json` (for saving settings).
   - `kotlinx-coroutines-core` & `kotlinx-coroutines-swing` (for desktop UI threading).
3. **Add Recommended Helper Libraries (Crucial for avoiding boilerplate):**
   - **Coil 3 (Multiplatform):** For loading and caching thumbnails in the Grid. Writing our own disk/memory cache for images is reinventing the wheel. Coil 3 handles downsampling and async loading perfectly.
   - **FileKit (`io.github.vinceglb:filekit-compose`):** A modern, native file/folder picker for Compose Multiplatform. This eliminates the need to write legacy, bug-prone AWT/Swing (`JFileChooser`) dialog wrappers.

## Step 2: Architecture & Storage (Data Layer)
1. **Settings Model:** Create `data class WokaSettings(val linkedFolders: List<String> = emptyList())`. Must be `@Serializable`.
2. **Settings Repository:** Create an interface and its implementation in `jvmMain` (or `commonMain` since it's JVM-only anyway). 
   - Path: `System.getProperty("user.home") + "/Documents/OldValencia/Woka/settings.json"`.
   - Functions: `getSettings(): Flow<WokaSettings>`, `addLinkedFolder(path: String)`.
3. **DI Setup:** Create Koin modules and initialize them in `desktopApp/src/jvmMain/kotlin/main.kt`.

## Step 3: ViewModels (Presentation Layer)
- **MVVM Flow:** The UI ONLY observes `StateFlow`. It sends user intents via function calls (`viewModel.onLinkFolderClicked()`). 
- **State Mutation:** Always use `MutableStateFlow.update { it.copy(...) }`. Never mutate objects directly.
- Create `HomeViewModel` holding a `StateFlow<HomeState>`.
- Inject `SettingsRepository` into the ViewModel to load linked folders on initialization.

## Step 4: System Integration (Folder Picker)
- Use **FileKit** (`launchDirectoryPicker`) inside a coroutine in the UI layer (or passed as a callback to the ViewModel) to prompt the user for a folder.
- When a folder is picked, pass the absolute path to `viewModel.addLinkedFolder(path)`.

## Step 5: Theming (Light/Dark Mode)
- **Requirement:** The application MUST natively support both Light and Dark themes.
- **Implementation:** Rely on `isSystemInDarkTheme()` to dynamically detect the OS preference.
- **Design Impact:** All custom UI primitives (like `WokaButton`, `WokaSurface`, `WokaText`) must be built with theme-awareness. Never hardcode static hex values directly into Composables; instead, use a simple custom Theme object or check the boolean flag to provide the correct color (e.g., `#F2F2F7` for light background, `#1E1E1E` for dark).

## Step 6: UI Implementation & Design Rules (Apple HIG Style)
1. **No UI Frameworks:** DO NOT use Material 3. DO NOT use heavy frameworks like Jewel. Material ripples, shadows, and huge app bars contradict the Apple HIG minimalist aesthetic.
2. **Custom Primitives:** Build our own simple, reusable components (`WokaButton`, `WokaSurface`, `WokaText`) using pure Compose foundations (`Box`, `Row`, `BasicText`, `clickable` without ripple, or subtle alpha-fade click effects).
3. **Theme:** 
   - Typography: Clean sans-serif (Inter/Roboto) with varied font weights.
   - Geometry: 1.dp borders (subtle gray/translucent), 8.dp or 12.dp rounded corners (`RoundedCornerShape`).
   - Backgrounds: Solid `#F2F2F7` (Light) or `#1E1E1E` (Dark), avoiding pitch black or pure white unless for cards.
4. **HomeScreen Composable:**
   - **If empty:** Show a beautifully centered, minimalist column with a subtle icon, text "No folders linked", and a glassmorphic/rounded `WokaButton` "+ Link Folder".
   - **If populated:** Show a `LazyVerticalGrid`. Each item is a `FolderCard`.
5. **FolderCard Composable:**
   - Aspect ratio 1:1 or 4:3.
   - Rounded corners (12.dp).
   - Use **Coil 3** to async load the first image from the folder as a background cover, with a dark gradient overlay so the white folder name text is readable.

## Testing Criteria
- Running `./gradlew :desktopApp:run` shows the empty state.
- Clicking "Link Folder" opens the native folder dialog via FileKit.
- Selecting a folder updates `Documents/OldValencia/Woka/settings.json`.
- The UI instantly reacts and shows the folder card in the grid.
- Files strictly adhere to the 100-150 line limit.
