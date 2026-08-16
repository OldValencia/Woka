> **ARCHIVE — not a source of truth.**
> Written for the earlier clipboard-manager codebase: it talks about `ClipboardRepository`, clipboard
> item ids and `.media/*-versions/`, none of which exist in Woka. Superseded on 2026-08-16 by
> `../architecture.md`. Kept **only** for its video decisions — JavaCV/Bytedeco (LGPL) as the engine,
> stream-copy trim with keyframe-snapped handles, ffmpeg run as a background process — which carry
> forward into the ME-2xx track.

# Plan: Lightweight Media Editor (Phase 7)

This feature introduces a minimalistic, iOS-style media editor and viewer. The goal is to provide basic, quick editing capabilities directly within the app without needing third-party software.

---

## Design & Technical Specifications

1. **Aesthetics (iOS Style & Themes):**
   - True to Apple HIG: Minimalist interface, adapting to both Light and Dark themes.
   - Clean toolbar focusing only on essential tools, without overwhelming the user.
2. **Architecture (Plugin-based):**
   - Designed as extensible plugins, similar to the Text Editor approach.
   - Distinct plugins for image editing operations and video editing operations.
3. **Image Editing Features:**
   - **Supported Formats:** Strictly limited to most popular formats (`png`, `jpg`/`jpeg`).
   - **Crop & Resize:** Basic canvas operations.
   - **Rotate & Flip:** 90° rotation steps and horizontal/vertical mirroring (cheap via Skia matrix transforms).
   - **Filters & Adjustments:** Minimal preconfigured filters and manual adjustments (Brightness, Contrast, etc.).
   - **Drawing / Markup:** Ability to draw simple shapes:
     - Circle, Square, and Arrow (with curve support).
     - Text annotation: editable text label with font size and color (the most common screenshot markup combo is arrow + text).
     - Editable properties: Stroke color, background/fill color, and stroke width.
4. **Video Editing Features:**
   - **Supported Formats:** Strictly limited to most popular formats (e.g., `mp4`).
   - **Trim:** Simple timeline slider to cut start and end times.
   - **Audio:** Toggle mute/unmute.
5. **History & State Management:**
   - **No complex versioning:** The system only stores two states for any media item (`initial` and `edited`).
   - **Storage mechanism:** The `edited` version replaces the original file in the main storage directory. The `initial` (original) version is copied and stored in a hidden directory at the same level as `.deleted`, specifically `.media/image-versions/UUID.ext` (or `.media/video-versions/`), where `UUID` is the clipboard item id.
   - **Backup lifecycle invariants:**
     - The `initial` backup is created **only before the very first edit**. Subsequent edit sessions must **never overwrite** it — otherwise the "original" would silently become the first edit.
     - **Revert:** the `edited` file is deleted and replaced by the `initial` copy, and the backup is **removed** from the versions directory. The item returns to a clean "never edited" state (a later edit creates a fresh backup).
     - **Deletion:** when an item is permanently deleted (trash purge), its backup in the versions directory must be deleted too. Add an orphan-cleanup pass (e.g., on startup) that removes version files whose item id no longer exists.
   - **Local Undo/Redo:** During an active editing session, changes (e.g., drawing shapes) must be tracked via an immutable `EditorState` to support `Ctrl+Z`/`Ctrl+Y` before saving.

6. **Integration & Engines:**
   - **Entry Point:** Add "Edit Media" to the context menu of image/video clipboard items. **Double-click opens the built-in viewer** (opening in the default external app will be a separate future feature, e.g. "Open in Default App" in the context menu).
   - Verify that video files are actually captured as `ClipboardType.VIDEO` (not generic `FILE`) — the entry point depends on it; add extension-based detection if needed.
   - **Video Engine (decision): JavaCV / Bytedeco FFmpeg.** A single self-contained dependency, no user-installed VLC or codecs:
     - Playback: `FFmpegFrameGrabber` decodes frames rendered into Compose `ImageBitmap`; audio via `javax.sound` (`SourceDataLine`).
     - Timeline thumbnails: frame extraction via the same grabber.
     - Processing (trim / audio strip): the ffmpeg executable bundled by Bytedeco, launched via `Loader` as a background process.
     - Use the **LGPL** build for license cleanliness.
   - **Trim Strategy (decision): stream-copy with keyframe snapping.** Trimming uses `-c copy` (instant, lossless, no re-encoding). Since stream-copy can only cut on keyframes, the **trim handles in the UI snap to the nearest keyframes** (keyframe positions read via ffprobe/grabber) so the preview never lies about the actual cut point. Precise (re-encoding) trim may be added later as an explicit option — out of scope for MVP.
   - **Image Rendering:** Utilize Compose Multiplatform's native Skia engine for fast, high-quality canvas rendering and matrix transformations.

7. **Data Integrity & Post-Save:**
   - **Thumbnail / cache invalidation:** after saving, regenerate the item's thumbnail and invalidate the hover-preview / any image caches — the list must immediately show the edited version. If deduplication relies on a content hash, recompute/update it as well.
   - **Windows-safe atomic write:** never write into the target file directly (it may be locked by the preview/editor on Windows). Write to a temp file next to the target, then atomically replace (move with retry).
   - **JPEG specifics:** apply EXIF orientation on load (photos must not open sideways); save with quality ~90.
   - **Markup rasterization:** shapes are drawn over a zoomed on-screen image but must be rendered in the **native resolution / coordinate space of the source image** on save — never at screen resolution. Committed operations (crop, adjust, markup) are applied to the working bitmap in the order the user performed them; the undo stack restores previous `EditorState` snapshots.

8. **UX Details:**
   - `Esc` closes the viewer/editor; closing with unsaved changes shows a confirmation dialog.
   - `Ctrl+S` saves; `Ctrl+Z`/`Ctrl+Y` undo/redo.
   - Keyboard shortcuts are **scoped to the editor overlay** while it is open — they must not trigger global app shortcuts underneath.

---

## Architecture Guidelines

### Core Media State & History
- Implement a two-state storage model (`initial` and `edited`) with the backup lifecycle invariants above (backup only before first edit; revert consumes the backup; delete/purge removes it; orphan cleanup).
- Configure file system paths for `.media/image-versions/` and `.media/video-versions/` to store `initial` backups (filename = clipboard item id + original extension).
- Define constraints to restrict support to `.png`, `.jpg/.jpeg`, and `.mp4`.

### Plugin System Architecture
- Create base interfaces for media editor plugins.
- Separate pipelines for Image plugins and Video plugins.

### Image Editing Engine
- **Adjustments & Filters:** Implement basic brightness/contrast and minimal preset filters.
- **Crop & Resize:** Implement interactive cropping bounding box and scale algorithms.
- **Rotate & Flip:** 90° rotations and mirroring via Skia matrix transforms.
- **Markup Engine:** Implement vector drawing for circles, squares, arrows (with curve/bezier points), and text annotations, including color/thickness/font-size properties. All markup is stored in source-image coordinates and rasterized at native resolution on save.
- **Save pipeline:** EXIF-orientation-aware load, ordered application of committed operations, atomic temp-file write, JPEG quality ~90.

### Video Editing Engine
- **Playback Controls:** Implement basic media viewer controls (Play, Pause, Scrub) on top of JavaCV `FFmpegFrameGrabber` (frames → Compose, audio → `javax.sound`).
- **Trim & Audio:** Implement stream-copy trimming (`-c copy`) with keyframe-snapped handles and audio-track stripping via the Bytedeco-bundled ffmpeg process.

### UI Integration
- Build the editor UI ensuring strict adherence to Apple HIG.
- Support dynamic switching between Light and Dark themes.
- Implement the minimalistic toolbars for both Image and Video views.
- Hook into the main clipboard view (Context Menu -> "Edit Media", double-click -> built-in viewer).
- Implement `Ctrl+Z`/`Ctrl+Shift+Z` undo routing scoped to the active editor overlay (global shortcuts suppressed while open).

---

## Execution Plan

### 1. Media Viewer for Images (Fully Completed & Tested)
- [x] Create the base modal/overlay UI for media preview (iOS HIG style).
- [x] Add "Edit Media" context menu action in the clipboard list to launch the viewer.
- [x] Route double-click on image/video items to the built-in viewer.
- [x] Implement image rendering (scaled to fit, EXIF-orientation-aware).
- [x] Support mouse wheel for zooming and mouse drag for panning (desktop-first interactions).
- [x] Support Light/Dark theme switching for the viewer background and controls.
- [x] Wire `Esc` to close; scope keyboard handling to the overlay.

### 2. Media Viewer for Videos
- [x] Add the JavaCV / Bytedeco FFmpeg (LGPL) dependency; verify bundled natives work on the target platforms.
- [x] Extend the modal UI to support video playback.
- [x] Implement a basic video player (Play, Pause) via `FFmpegFrameGrabber` + `javax.sound` audio.
- [x] Implement a timeline scrubber for seeking (with extracted frame thumbnails).
- [x] Verify video items are captured as `ClipboardType.VIDEO`; add extension-based detection if they currently fall into `FILE`.

### 3. Media Editor for Images
- [ ] **State Management:** Implement the logic to backup the `initial` file to `.media/image-versions/` before the first edit (never overwriting an existing backup), and replace the main file on save via atomic temp-file write.
- [ ] **Plugin System:** Scaffold the image editing plugin architecture.
- [ ] **Crop & Resize:** Implement the crop bounding box UI and resolution scaling logic.
- [ ] **Rotate & Flip:** Add 90° rotate and horizontal/vertical flip actions.
- [ ] **Adjustments & Filters:** Add sliders for Brightness/Contrast and preset filter buttons (e.g., Vivid, Mono).
- [ ] **Markup Tools:** Implement vector drawing (Square, Circle, Curve-able Arrow, Text annotation).
- [ ] **Markup Properties:** Add color picker, background fill toggle, stroke width slider, and text size control.
- [ ] **Native-resolution rendering:** Rasterize markup and apply operations in source-image coordinates on save; JPEG saved with quality ~90.
- [ ] **Undo/Redo:** Wire up `Ctrl+Z`/`Ctrl+Y` to the `EditorState` to revert shape drawings or filter applications (scoped to the editor overlay).
- [ ] **Save/Revert:** Add "Save" (commits to `edited`, regenerates thumbnail, invalidates caches/hash) and "Revert to Original" (restores from `initial`, deletes the backup, item returns to "never edited") buttons.
- [ ] **Unsaved changes:** Confirmation dialog when closing with uncommitted edits; `Ctrl+S` saves.

### 4. Media Editor for Videos
- [ ] **State Management:** Implement `initial`/`edited` backup logic for videos in `.media/video-versions/` (same lifecycle invariants as images).
- [ ] **Trim Plugin:** Add dual-handle UI on the timeline scrubber to define start and end trim points, with handles snapping to the nearest keyframes.
- [ ] **Audio Plugin:** Add a toggle button to mute/unmute the audio track.
- [ ] **Processing Engine:** Implement background execution of the Bytedeco-bundled ffmpeg to apply the trim (`-c copy`) and audio strip instantly without re-encoding.
- [ ] **Save/Revert:** Wire up saving and reverting mechanisms for video files (revert consumes the backup; save regenerates thumbnail and invalidates caches).

### 5. Housekeeping
- [ ] Delete the version backup when an item is permanently deleted (trash purge).
- [ ] Orphan cleanup on startup: remove files in `.media/*-versions/` whose clipboard item no longer exists.

---

### 🐛 Troubleshooting & Bugs (To investigate later)

#### 1. Integrity Check Behavior on manual file addition
**Issue:** If a user manually adds files via "+ Add" -> "File" (or drops them into the directory) and restarts, the Integrity Check misidentifies them as "duplicates" or transforms names into "new categories" (OrphanCategories). 
**Why this happens:** The app's storage architecture relies on `UUID.extension` file naming and specific directory mapping for categories. Previously, `getPhysicalFileName` wrapped files in a `UUID/filename` directory, which was incorrect and broke the architectural invariant of "1 file = 1 file".
**Action taken:** Fixed `getPhysicalFileName` in `ClipboardRepository` to save files as `UUID.extension` directly. No new action items needed here.

#### 2. Compose Resources `ZipException` (Runtime Crash)
**Issue:** App crashes when left open for a long time with `java.util.zip.ZipException: ZipFile invalid LOC header (bad signature)`.
**Stacktrace Context:** Occurs at `org.jetbrains.compose.resources.JvmResourceReader.read` inside `ImageResourcesKt.svgPainter` (`CustomTitleBar.kt:148`).
**Why this happens:** This is a known Compose Desktop issue when the underlying `.jar` file containing resources is overwritten/recompiled by Gradle while the app is still running. When the app later tries to lazily load an SVG from the jar, it hits a corrupted/changed ZIP header.
**Action item:** Not a critical app bug (it's a dev-environment artifact from rebuilding while running), but we should ensure all icons in `CustomTitleBar` are correctly bundled or preloaded to avoid runtime crashes during dev.

### 8. Finalization and Cleanup
- Add and resolve missing icons in Icons.kt
- Adapt logic to domain models.
- Perform successful test builds and run the application.
