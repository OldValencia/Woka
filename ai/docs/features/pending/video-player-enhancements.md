# Video Player Enhancements

## 1. Zoom & Auto Size Control (UI/UX) — ✅ Implemented
- **Smart Scaling Modes:** The system operates in one of two modes: **Zoom Mode** (percentage-based) or **Preset Mode** (Fit / Fill / Actual).
  - *Definitions:* **Fit** = scale to fit entirely inside the panel, preserving aspect ratio (contain, no cropping); this is the neutral **100%** baseline, the default preset, and the "nil" crossover point into Zoom Mode. **Fill** = scale to fill the whole panel, preserving aspect ratio, cropping the overflow (cover). **Actual** = native 1:1 pixels (may exceed the panel — pannable). Presets cycle Fit → Fill → Actual.
  - *Zoom Range:* The percentage is relative to the **fitted** size (100% = fits the panel, never larger). It is bounded to **`[10%, 1000%]`**; the base/default value is **100%** (== Fit). Zoom 100% and Preset Fit render identically.
  - *Note on Shortcuts:* In Preset Mode, the `Ctrl + Scroll` zoom shortcut is disabled. Instead of scaling, it surfaces a tooltip ("Scaling is disabled in preset mode") so the gesture is not silently ignored. The mode is **never** switched automatically by a gesture.
- **Zoom Step (Unified):** Both `Ctrl + Scroll` and the indicator's `-` / `+` buttons use the **same step of ±1 percentage point** and always produce **whole integers** (e.g. `114 → 115 → 116`). Values are clamped to `[10%, 1000%]`.
- **Panning:** When the media is larger than the panel (zoom > 100%, or the Fill / Actual presets when they overflow), the frame can be moved with **`Ctrl` + hold Right Mouse Button + drag**.
- **Main Panel Indicator:** The player panel displays the current state. Behaviour differs per mode:
  - **In Zoom Mode (`- 115% +`):** the `-` / `+` buttons step the zoom by ±1%. The percentage itself is a **passive label** and a **hover trigger** for the popover — it is *not* clickable. (Making it clickable caused a click/hover conflict that made the popover blink, so the reset / mode-switch action was moved into the popover instead.)
  - **In Preset Mode (`< ◱ Fill >`):** the preset shows as **icon + text** flanked by arrows that cycle Fit → Fill → Actual inline. Clicking the central text is state-dependent (with a hover tooltip):
    - If the preset is not Fit → **resets** to Fit and centers the video. *(Tooltip: "Reset to Fit".)*
    - If the preset is already Fit → **switches** to Zoom Mode. *(Tooltip: "Switch to zoom".)*
- **Hover Popover (Zoom Mode Only):** A frosted popover slides out *below* the indicator on hover, with a **fixed width** so switching views never drops the cursor. It contains:
  - **Layout:** `[<] [ Control Area ] [>]` with a clickable action line beneath. The arrows (`chevron.left` / `chevron.right`) switch the control area between:
    1. **Slider View:** a smooth slider; dragging applies the value live.
    2. **Manual Input View:** a bordered numeric field with a `%` suffix (styled so it clearly reads as editable — no `-`/`+` steppers, since Ctrl+Scroll already steps by 1). Type an integer directly; valid values apply live.
      - *Input Validation:* only positive integers within `[10, 1000]`. Empty / `0` / negative / non-numeric reverts to the current zoom value.
  - **Mode action (moved off the label):** a clickable line at the bottom — **"Reset to 100%"** when zoom ≠ 100%, or **"Switch to presets"** when at 100%. This is where the reset / mode-switch lives now.
  - **Closing:** both views close when the cursor leaves the popover (a short grace bridges the gap between indicator and popover). The popover is **non-focusable** (a focusable popup intercepts scene pointer input and makes hover blink); manual typing is instead captured on the indicator while the Input view is open. `Enter` / `Esc` also close it.
  - **Persistence:** the chosen view (Slider vs. Input) is saved in `settings.json`.
- **Icons (macOS / SF Symbols):** `chevron.left` (popover left arrow; the right arrow reuses the existing `ChevronRightMac`), `rectangle.arrowtriangle.2.inward` (Fit), `rectangle.arrowtriangle.2.outward` (Fill), `aspectratio` (Actual — enum `ScalePreset.Original`, labelled "Actual" in the UI). Registered in `Icons.kt` under `Icons.Static`.
- **Animations:** All mode switches and view transitions (both on the Main Panel and inside the Popover) must feature Apple-like sliding animations. For example, clicking the right arrow makes the current text slide out to the left while the new text slides in from the right.
- **Settings Persistence:** The popover view preference (Slider/Input) is always saved in `settings.json`. The **default size state** applied when a video opens is configurable in Settings → Media Viewer: a mode (Preset or Zoom) plus its value (Fit/Fill/Actual, or a percentage), and a **"Remember zoom & size"** toggle. When the toggle is **off**, videos always open at that configured default (default = Fit) and in-video zoom/preset changes are ephemeral. When **on**, any zoom/preset the user sets *inside a video* is written back as the new default and restored next time. Toggling fullscreen ⇄ windowed always preserves the current state (same file).

## 2. Stream Selection (Video, Audio, Subtitles)
- **Audio Track Selection (Volume Integration):**
  - Integrate audio track selection directly into the Volume hover element. 
  - If the file has only 1 audio track, the volume slider remains exactly as it is (clean and minimal).
  - If there are multiple audio tracks, a side "Extension" list seamlessly attaches to the side of the volume slider popover.
  - *UX Detail:* The extension displays a selectable list of track names. If a track name is too long to fit, it will animate with a smooth marquee effect (scrolling back and forth horizontally) on hover or when active.
- **Subtitle Track Selection:**
  - If the video contains subtitles, display a dedicated Subtitle icon (`Icons.Rounded.Subtitles`) directly to the **left** of the volume button. If there are no subtitles, the button is hidden.
  - *UX Detail:* When subtitles are explicitly turned "Off", the icon should change to a crossed-out version (e.g., `SubtitlesOff`), mirroring the muted state of the volume icon.
  - Upon interaction, it reveals a popover list (similar in design to the audio extension).
  - The list must include an explicit **"Off"** option to disable subtitles.
  - At the bottom of the subtitle list, include a **"Customize Subtitles"** button (implementation details deferred to the next development phase).
- **Video Track Selection:**
  - This function is moved out of the main stream selection UI and into the global "Settings" menu (see Section 6) to keep the player panel clean, since multiple video streams are extremely rare.
- **File Info Screen (Integration):**
  - Integrate stream information directly into the existing "File Info" dialog (opened via the 'i' icon at the top of the player).
  - *Design:* Format this dialog beautifully (Apple HIG style). Group information logically into clean sections or cards:
    - **General:** File size, resolution, duration.
    - **Video:** Codec, framerate, bitrate.
    - **Audio:** List of audio tracks with their codecs (e.g., AAC, AC3) and channel layouts (e.g., Stereo, 5.1).
    - **Subtitles:** List of subtitle tracks and their formats (e.g., SRT, ASS).

## 3. Subtitle Customization & Advanced Settings
- Add settings to customize the appearance of subtitles (font, size, color, background, etc.).
- Support loading external subtitle files (e.g., matching `.srt` or `.ass` files from the same folder).
- Add synchronization controls (delay/advance) for audio and subtitles if they are out of sync.

## 4. Mega-Convenience Features (Simple & Elegant)
- **Playback Resume:** Automatically remember the last playback position for each file and resume from there.
  - *Must be highly customizable via settings:* Option to turn it off completely, or disable it for short videos (e.g., < 10 mins) while keeping it active for long movies/shows, to prevent it from becoming annoying.
- **Smart Chapters:** If the video file contains chapters (like in MKV), display them as segments on the timeline (Apple TV / YouTube style).
- **System Media Controls:** Integrate with OS media keys (Play/Pause/Next on the keyboard should control Woka globally).

## 5. Web Streaming & Network Sources (Experimental)
- **Direct Link Streaming:** Ability to paste a YouTube URL or direct video link (m3u8, mp4) and stream it natively through our player. 
- **Web Folders:** "Link" a website (like a YouTube channel or a supported movie site) as a Woka folder, parsing its content into standard media cards so it looks completely native.

## 6. General Playback Settings Menu (More / Settings)
- **Layout & Placement:**
  - To preserve the minimal Apple HIG aesthetic and avoid panel clutter, introduce a single Settings icon (`⚙` or `⋮`) positioned on the right side of the panel, directly to the **right** of the Volume button (between Volume and Fullscreen).
  - Clicking this icon opens a unified popover menu for advanced/secondary playback controls.
- **Video Track Selection (Template):**
  - If a file contains multiple video angles or streams, this option dynamically appears in the Settings menu, allowing the user to select the active video track.
- **Repeat (Template):**
  - A toggle to repeat the current video or loop the entire folder/playlist.
- **Shuffle (Template):**
  - A toggle to shuffle playback when watching a folder of videos.
- **Playback Speed (Template):**
  - Controls for adjusting video speed (e.g., 0.5x, 1x, 1.25x, 1.5x, 2x).

## 7. UI Cleanup
- **Remove "Copy" Button:** Remove the "Copy" (or duplicate) button from the top toolbar in the media viewer, as this function is unnecessary and clutters the interface in the context of viewing media.
