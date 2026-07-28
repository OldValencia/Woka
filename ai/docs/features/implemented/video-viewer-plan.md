# Video Player and Thumbnail Fixes (Planned)

## Current Problems
1. **"File is too big to fit in memory" when opening heavy videos (e.g. MKV):**
   The application tries to load the entire video file into RAM. This happens due to an error in `NativeMediaViewer.kt` where `loadThumbnail` calls `item.file.readBytes()` for absolutely all files, including gigabyte movies.
2. **Infinite loading and missing video controls:**
   Even if the video is small and fits into memory, `Image.makeFromEncoded()` cannot parse video formats and returns `null`. In `MediaViewerScreen.kt`, the player interface (video surface and control panel) is hidden behind an `if (state.imageBitmap != null)` check. As a result, the player and controls are not rendered, and the user only sees an endless `CircularProgressIndicator` (although spacebar pauses the video, as it is caught by the container's global handler).

## Solution (Implementation Plan)

1. **Optimize `loadThumbnail` (Thumbnail generation for videos):**
   - Remove `readBytes()` for video files.
   - Use FFmpeg (via `ProcessBuilder` or JavaCV) to extract the first frame.
   - **Caching:** Save the generated frames to disk (e.g., `.jpg`) in the hidden folder `user.home/Documents/OldValencia/Woka/.thumbnails/`. This will allow quick loading of previews even if there are 1000 video files in the folder, avoiding repeated generation.
   - **Multithreading:** Limit the number of parallel FFmpeg processes (e.g., `Dispatchers.IO.limitedParallelism(4)`), and also implement lazy thumbnail generation (create only when the video is displayed in the list/grid).

2. **Fix `MediaViewerScreen.kt`:**
   - Change the render condition: do not block the rendering of the player interface (and controls) if `state.imageBitmap == null`, provided that the current file is a video (`activeItem is MediaItem.Video`).
   - While the thumbnail is loading, a placeholder can be shown, but the controls and player must work independently of the thumbnail.

---

## Refinement
+ 1. **Control display logic:** If the video track is unavailable, why show the slider? Need to think about/disable unnecessary elements.
2. **Fullscreen mode:** Add the ability to open video in fullscreen.
3. **Slider color theme:** In the light theme, the video slider still remains black — needs to be fixed to match `Theme`.
4. **Slider smoothness:** Make switching/scrolling the slider for video smoother.
5. **Icon proportions:** The icons on the player buttons are stretched — fix `Modifier` or `ContentScale`.
6. **Smart thumbnail generation:** Rework the logic for video thumbnails. Currently, completely black frames appear (especially at the very beginning of the video). Need to make it so the thumbnail is taken from a more meaningful moment of the video (and not just the first frame) or filter out completely black frames.

---

## Global Player (Picture-in-Picture) and Shortcut Unification

1. **Global Architecture (Decoupling the player from the folder):**
   - Move the player state (`VideoPlayerState`, file path, scale) to the application level (Global State/Koin).
   - The `MediaViewerFullscreenWindow` should be created independently of `MediaViewerScreen`.
   - If the user navigates to another folder in the main application, the open PIP window does not close and the video continues to play.

2. **Unification of content and window management:**
   - Control modifier: **`Ctrl`** (on Windows) / **`Cmd`** (on Mac).
   - **Content zooming:** `Ctrl` + Scroll.
   - **Content panning (Pan):** `Ctrl` + Left mouse button (Drag).
   - **Window movement (PIP):** Simple left mouse button drag on any point of the video (without holding `Ctrl`) moves the window itself across the desktop.

3. **PIP Window Properties:**
   - The window initially opens in `WindowPlacement.Maximized` mode (borderless, non-exclusive).
   - The window title `Window(title = ...)` is passed the current file name (instead of Untitled).
   - The window can be freely scaled and resized (keep `undecorated = true` since resizing works on the OS user side).
   - Future: Always on Top and Click-through.

4. **Dialog Window Abstraction (Refactoring `FileInfoDialog.kt`):**
   - Build a strict hierarchy: `AbstractDialog` -> `InformationDialog` / `ConfirmationDialog` / `ErrorDialog` -> `[SpecificDialogs]`.
     - `InformationDialog` — info dialog with a single "Close" button (inherited by `FileInfoDialog`).
     - `ConfirmationDialog` — dialog with two buttons (Yes / No) for action confirmation.
     - `ErrorDialog` — similar to InformationDialog, but with an emphasis on error.

5. **Behavior when opening a new video (Conflict with PIP):**
   - If the PIP window is already open and playing a video, and the user clicks on **another** video in the main app:
   - Show a descendant of `ConfirmationDialog` — for example, `VideoAlreadyOpenedDialog`. This dialog should render on top of the **main application window** (where the focus and user cursor currently are).
   - Text: *"You are already watching a video. Close the current video and open the new one?"*
   - If the user selects "Yes" -> the current video in PIP closes, the new one opens.
   - If selects "No" -> the new video doesn't open, continue watching the current one.

6. **Memory cleanup (Lifecycle):**
   - When the PIP window is forcefully closed (close button, Alt+F4, or programmatic close), the global manager must call `.dispose()` on the `VideoPlayerState` instance to avoid memory leaks (free VLC resources).

---

## Playback Engine Architecture (Reference)

The player is built on **JavaCV / FFmpeg** (`FFmpegFrameGrabber`), not on VLC. All code is in `shared/src/jvmMain/kotlin/com/woka/ui/viewer/player/`.

### Files and Responsibilities
- **`VideoPlayerState.kt`** — player state + public API (`seek`, `pauseForSeek`/`resumeAfterSeek`, `togglePlayPause`, `updateVolume`, `dispose`). Compose state (`isPlaying`, `currentPositionMs`, `durationMs`, `currentFrame`, `volume`, `hasAudio`, `isRepeatMode`). Starts playback in `init`.
- **`VideoPlayerPresenter.kt`** — creates channels and two loops; **presenter loop** for frame timing. Defines `data class VideoFrame(bitmap, tsMs, generation)`.
- **`VideoPlayerDecoder.kt`** — **decoder loop**: grabs frames from FFmpeg, sends to channels, handles EOF/repeat.
- **`VideoPlayerSeek.kt`** — `handleSeekIfNeeded()`: the actual seeking of the grabber.
- **`VideoPlayerAudio.kt`** — initialization of `SourceDataLine` and **audio job** (PCM playback).
- **`VideoPlayerExt.kt`** — utilities (`toComposeBitmap`, etc.).

### Threads and Data Flow
```
[Decoder: Dispatchers.IO]  --videoChannel(cap 60)-->  [Presenter: Dispatchers.Default] --> currentFrame / currentPositionMs
        |                  --audioChannel(cap 500)--> [Audio Job: Dispatchers.IO]      --> SourceDataLine
        grabFrame()
```
- Decoder — the only one reading the grabber. Presenter is responsible for video timing (sleep until `frameTsMs`) and updating `currentFrame`/`currentPositionMs`. Audio job writes PCM to the sound line.
- All three loops cooperatively "park" on `while (!isPlaying || isSeeking) { delay(50) }`.
- EOF: without audio the decoder sends a marker frame `tsMs == -2L`; with audio — an empty `ByteArray(0)` into the audio channel. The receiver sets `isPlaying=false`, `currentPositionMs=durationMs`.

### Seeking — three mechanisms (important, easy to break)
1. **Seek generation (`seekGeneration`).** Every `seek()` increments a counter. Frames are tagged with a generation (`VideoFrame.generation`), which the decoder advances **only after** processing the seek in `handleSeekIfNeeded`. The presenter **discards outdated frames** — both upon receiving from the channel, and **again before writing `currentPositionMs`** (after `delay`/parking). Without the second check, there was a bug: during playback, a frame passed the check before seeking, but wrote the position after sleeping → rolled play head backwards. This fixes the "skip during play" race condition.
2. **Deterministic end.** If the `seek()` target is ≥ `durationMs - END_THRESHOLD_MS` (200ms) — immediately `currentPositionMs=durationMs`, `isPlayingBeforeSeek=false`, `isPlaying=false` (don't rely on playing until EOF, which was racy). The decoder still runs `handleSeekIfNeeded` (it is before the `isPlaying` check) and renders the last frame. The next Play restarts from zero (`togglePlayPause` sees position at end → `seek(0)`).
3. **Near-end backoff** (`handleSeekIfNeeded`). When seeking to the very end, `setTimestamp(durationMs)` often lands after the last frame → `grabFrame()==null` → thumbnail didn't update. Therefore, the start of decoding is shifted ~1000ms earlier; the loop still scrolls forward and retains the last real frame in `lastGoodFrame`.

### Pause for slider drag
`MediaViewerTimeSlider` → `pauseForSeek()` (saves `isPlayingBeforeSeek`, sets `isSeeking=true`, pauses) for the duration of the drag; `resumeAfterSeek()` restores playback. Skip buttons and Alt+arrows call `seek()` directly (without pause) — correctness relies on seek-generation. Deterministic end resets `isPlayingBeforeSeek` so that `resumeAfterSeek` doesn't "revive" the video at the end.

### Invariants (do not violate)
- `seek()` is called only from the UI thread (single-writer for `seekGeneration`/`seekTargetMs`).
- `seekTargetMs = -1L` means "no pending seek"; repeat mode reuses this by setting `seekTargetMs = 0L` on EOF.
- Any write to `currentPositionMs` from the presenter must be under a check for the current `generation`.

<!-- Add bugs found during testing below -->
### Bugs (from testing)

- [x] **PiP window not draggable after resize.** Drag was tied to `windowState.placement == Floating`, but when resizing an undecorated window by the corners, this property doesn't update. Fix (`MediaViewerFullscreenWindow.kt`): consider window maximized only if `extendedState & MAXIMIZED_BOTH` **and** window occupies the entire workspace (`graphicsConfiguration.bounds` minus `screenInsets`); otherwise — allow dragging.
- [x] **No focus/shortcuts for open video.** Fullscreen window was created while the main window is active, and lacked a key handler. Fix: `Window(onPreviewKeyEvent = handlePipKeyEvent(...))` (Space, ←/→ ±5s, ↑/↓ volume, Esc → return to main) + `window.toFront()` / `window.requestFocus()` in `LaunchedEffect` upon appearance.
- [x] **After clicking a button (max/min window, etc.), focus remains on it and Space "presses" it.** `clickable` by default is focusable and reacts to Space/Enter; chrome/toolbar buttons lie outside the player subtree (injected via `LocalTopBarAccessories`), so player shortcuts didn't reach them. Fix: global interception in `MainShell` via `onPreviewKeyEvent` (tunneling phase from root — to any focused button). `MediaViewerScreen` registers a handler in `GlobalShortcutState` (`LocalGlobalShortcuts`) while open; handled keys are "consumed" → button doesn't receive them, and shortcuts work with any focus. Logic extracted to `handleMediaViewerKeyEvent(...)` (shared by player and global hook).

---

## Validating the generated thumbnail

- If the generated thumbnail turns out to be completely monochromatic (black is a typical case at the beginning of a video, but also any solid rectangle) — **regenerate from a different moment**, repeating until a frame with at least some image (not monochromatic) is obtained. Relates to item 6 "Smart thumbnail generation".
