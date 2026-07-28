# Video Player Enhancements

## 1. Zoom & Auto Size Control (UI/UX)
- **Smart Scaling Modes:** The system operates in one of two modes: **Zoom Mode** (percentage-based) or **Preset Mode** (Fit / Fill / Original).
  - *Note on Shortcuts:* In Preset Mode, the `Ctrl + Scroll` shortcut for zooming is strictly disabled, as manual scaling contradicts preset behavior.
- **Main Panel Indicator & Click Behavior:** The player panel displays the current state. Clicking the central text follows a strict logical flow:
  - **In Zoom Mode (e.g., `115%`):**
    - If zoom is not 100%, clicking **resets** the zoom to 100% and centers the video.
    - If zoom is exactly 100%, clicking **switches the mode** to Preset Mode.
  - **In Preset Mode (e.g., `< Fill >`):**
    - The panel directly shows the preset with arrows (e.g., `< Fill >`) to switch between Fit, Fill, and Original inline.
    - If the preset is not Original, clicking the central text **resets** the state to Original (and centers the video).
    - If the preset is already Original, clicking the text **switches the mode** to Zoom Mode.
- **Hover Popover (Zoom Mode Only):** The frosted glass popover slides out gracefully *below* the main panel indicator when hovered in Zoom Mode. It contains two control views:
  - **Layout:** `[<] [ Control Area ] [>]`. The left/right arrows switch the control area between:
    1. **Slider View:** A smooth slider (`----[]----`) for visual zooming.
    2. **Manual Input View:** Displays just the number (e.g., `115`) alongside `-` and `+` buttons for ±1 stepping, plus the ability to manually type the `Int` value.
      - *UX Polish:* Pressing `Enter` applies the value and immediately closes the popover (regardless of mouse position). If the user types a value but moves the mouse away, the popover auto-applies the value and closes gracefully.
  - **Persistence:** The user's preferred view in the popover (Slider vs. Input) is saved globally in `settings.json`.
- **Animations:** All mode switches and view transitions (both on the Main Panel and inside the Popover) must feature Apple-like sliding animations. For example, clicking the right arrow makes the current text slide out to the left while the new text slides in from the right.
- **Settings Persistence:** Global presets (Fit, Fill, Original) and the popover view preference are saved in `settings.json`. Any custom percentage zoom applies **only to the current video** and resets upon opening a new file.

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
