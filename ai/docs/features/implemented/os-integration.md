# Plan: Phase 6 — OS Integration & Packaging

**Goal.** Package Woka into standalone desktop installers (Windows MSI, macOS DMG, Linux DEB), register file associations so double-clicking media opens Woka, support single-instance routing so opening multiple files reuses the existing window, and provide folder integration ("Open with Woka").

---

## Phase 1: App Assets & Packaging Metadata

1. **Icons Generation:**
   - Generate multi-resolution icons from `logo/woka-icon.svg`:
     - Windows `.ico` (16, 32, 48, 64, 128, 256 px) in `desktopApp/src/main/resources/icons/woka.ico`.
     - macOS `.icns` in `desktopApp/src/main/resources/icons/woka.icns`.
     - Linux `.png` (512x512) in `desktopApp/src/main/resources/icons/woka.png`.
2. **Native Distributions DSL (`desktopApp/build.gradle.kts`):**
   - Package name: `"Woka"`, version: `"1.0.0"`.
   - Vendor: `"OldValencia"`, copyright: `"Copyright 2026 OldValencia"`.
   - License file: `LICENSE` (LGPLv3 notices for bundled FFmpeg/JavaCV natives).
   - JDK Modules: `modules("java.instrument", "java.management", "jdk.unsupported", "java.desktop", "java.base", "java.logging", "java.prefs")`.
   - Windows installer options:
     - Upgrade UUID: `d7e98a3e-43f1-4c17-91a5-8d5a2307ef01` (upgrades replace previous version).
     - Menu shortcut, desktop shortcut, user-level installation (`perUserInstall = true`).
   - macOS options: bundle ID `com.woka.app`, dock name `"Woka"`.
   - Linux options: menu group `"Graphics;AudioVideo;"`.

**Files:** `desktopApp/build.gradle.kts`, `desktopApp/src/main/resources/icons/*`

---

## Phase 2: File Associations

1. **Association Declarations:**
   - In `desktopApp/build.gradle.kts`, declare `fileAssociation` blocks for every media extension in `MediaKind.kt`:
     - Images: `jpg`, `jpeg`, `png`, `webp`, `bmp`, `gif`, `heic`, `heif`, `avif`, `tif`, `tiff`, `svg`, `tga`, `qoi`, `jp2`, `pcx`, `ppm`.
     - Videos: `mp4`, `mkv`, `avi`, `mov`, `webm`, `m4v`, `3gp`, `mpg`, `mpeg`, `m2ts`, `mts`, `wmv`, `flv`.
   - Provide MIME types and icons where applicable.
2. **CLI & Path Handling Verification:**
   - Existing `startupStack` in `StartupRoute.kt` already resolves media files directly into viewer mode over the parent folder view.

**Files:** `desktopApp/build.gradle.kts`

---

## Phase 3: Single Instance & File Opening IPC

1. **Single Instance Server / Client (`SingleInstance.kt`):**
   - On startup before Compose initializes:
     - Check local port / lock (`127.0.0.1:48123` or lockfile in user app data).
     - If an existing instance is active, send CLI arguments over socket, bring the window to front, and exit process immediately.
     - If this is the primary instance, bind socket and listen for incoming path arguments in background daemon coroutine/thread.
2. **Runtime Route Updates:**
   - When a new path is received by the running instance:
     - Post to `AppDependencies.router.navigateTo(...)`.
     - Un-minimize window and request focus (`window.toFront()`, `window.requestFocus()`).
3. **macOS OpenFileHandler:**
   - Register `Desktop.getDesktop().setOpenFileHandler` for native macOS Finder open events.

**Files:** `desktopApp/src/main/kotlin/com/woka/SingleInstance.kt`, `desktopApp/src/main/kotlin/com/woka/main.kt`

---

## Phase 4: Folder Context Menu ("Open with Woka")

1. **Windows Explorer Integration:**
   - Register `Directory\shell\Woka` and `Directory\Background\shell\Woka` in `HKCU\Software\Classes`.
   - Provide helper / settings toggle:
     - Settings item in Settings dialog: "Explorer context menu" toggle (enable/disable without requiring admin privileges).
2. **Linux & macOS Folder handling:**
   - Linux `.desktop` file handles `inode/directory`.
   - macOS handles folder drag & drop and folder paths via `startupStack`.

**Files:** `shared/src/jvmMain/kotlin/com/woka/feature/settings/SystemSettingsPanel.kt` (or settings registration helper).

---

## Phase 5: Verification & Build

1. Build Windows MSI installer via `./gradlew :desktopApp:packageMsi`.
2. Verify package output in `desktopApp/build/compose/binaries/main/msi/`.
3. Verify file line counts stay within 130–150 lines and anti-FQN mechanical check passes.
