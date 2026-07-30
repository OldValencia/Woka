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

## 2. Stream & Track Selection

We build **all three track directions at once** — Audio, Subtitles, and Video — plus the Settings button and the File Info integration. They share one foundation (below) and are split into their own subsections.

### 2.0 Engine Foundation (shared by 2.1 – 2.5)
The player runs on JavaCV `FFmpegFrameGrabber` (`VideoPlayerDecoder.kt`), which today grabs only the **default** video + **default** audio stream and downmixes audio to stereo. Real track *selection* needs a shared base first:
- **Stream enumeration:** on open, walk `grabber.formatContext.streams()` and build a `MediaTracks` model. Expose on `VideoPlayerState`: `hasAudio` (already exists), **`hasSubtitle`** (new), and multi-track flags.
- **Track model (`MediaTrack`):**
  - `index` — absolute container stream index.
  - `type` — Video / Audio / Subtitle.
  - `codec` — e.g. `aac`, `ac3`, `subrip`, `ass`, `hdmv_pgs_subtitle`.
  - `language` — ISO 639 from the `language` metadata tag (nullable).
  - `title` — from the `title` metadata tag (nullable).
  - `isDefault` (`AV_DISPOSITION_DEFAULT`), `isForced` (`AV_DISPOSITION_FORCED`).
  - Audio adds `channels` + `channelLayout` ("Stereo", "5.1").
  - Subtitle adds `isTextBased` (text SRT/ASS vs bitmap PGS/VobSub).
  - **Display label:** `title ?: language ?: "Track {n}"`.
  - **Stable identity** (for matching & persistence): the tuple **`(type, language, title, codec)`** — *never* the raw `index`, which differs between files.
- **Switching a track** means reconfiguring the grabber to a specific stream index (audio/video) or attaching a subtitle decode path — i.e. re-open/seek at the chosen stream, not just a UI flag.

### 2.1 Audio Track Selection (Volume integration)
- Selection is built directly into the Volume hover element.
- **1 audio track →** the volume slider stays exactly as it is (clean, minimal).
- **Multiple audio tracks →** a side "Extension" list seamlessly attaches to the side of the volume-slider popover.
- The extension shows a selectable list of track display-labels. **If a name is too long to fit, it animates with a smooth back-and-forth marquee** on hover or when active.
- The selected audio track is persisted per file (see §2.6).

### 2.2 Subtitle Track Selection
- **Icon & placement:** when the file has ≥1 subtitle track, show a Subtitle icon (`Icons.Static.Subtitles`, asset `captions_bubble`) directly to the **left** of the Volume button. If there are **no** subtitle tracks, the icon is **hidden** entirely.
- **Off state:** when subtitles are explicitly **Off**, the icon swaps to a crossed-out `Icons.Static.SubtitlesOff` (asset `captions_bubble_slash`, **custom-drawn — SF Pro has no crossed-out captions glyph**), tinted at ~0.5 alpha, mirroring the muted-speaker treatment in `MediaViewerVolumeControl.kt`.
- **Popover:** clicking reveals a popover list, **visually identical in style to the audio extension (§2.1)** — same card, same **back-and-forth marquee for long names**. It is a *standalone* popover anchored to the subtitle icon (not attached to the volume popover).
- The list always includes an explicit **"Off"** entry to disable subtitles.
- A **"Customize Subtitles"** button sits at the bottom of the list; it opens the subtitle-appearance settings (§3). *(Behaviour deferred to that phase.)*
- **Default & persistence:** subtitles default to **Off** on first open of a file. The chosen track (or Off) is persisted **per file** and restored on next open (see §2.6).
- **Rendering note (engine):** subtitles render as a **Compose overlay** over the clean video frame (never burned in — keeps §1 zoom/pan intact), synced to `currentPositionMs`. Three paths:
  - **Text (SRT / ASS-as-plain-text):** styled `Text`, restylable by §3.
  - **Bitmap (PGS/VobSub):** decoded image overlay, **not** restylable by §3.
  - **Full ASS (positioning/karaoke/effects):** deferred to a separate phase via libass — see `subtitle-ass-full-rendering.md`. In this phase ASS shows as basic styled text.
  - The overlay must honour seek, pause, repeat and playback speed.

### 2.3 Video Track Selection (in the Settings menu)
- Multiple video streams are extremely rare, so this lives in the **Settings menu (§2.4)**, not on the main panel — keeping the panel clean.
- When a file has >1 video stream, a "Video Track" entry appears in the Settings menu listing the tracks (display-label from §2.0); otherwise it is hidden.

### 2.4 Settings Menu (⚙)
- A single Settings icon (`⚙`) sits to the **right** of the Volume button (between Volume and Fullscreen). It is **always visible** — even when it currently holds nothing but Video Track — because we keep adding items to it over time (Repeat, Shuffle, Playback Speed — see §6).
- **Toolbar order:** `[Subtitles?] [Volume] [Settings ⚙] [Fullscreen]` — **Subtitles hidden when the file has none; Settings always shown.**
- Clicking opens a unified popover for secondary playback controls. Video Track Selection (§2.3) appears here dynamically. Full item catalogue: §6.

### 2.5 File Info Dialog (stream info)
- Integrate stream information into the existing "File Info" dialog (the 'i' icon at the top of the player).
- **Design:** Apple HIG. Group logically into clean cards/sections:
  - **General:** file size, resolution, duration.
  - **Video:** codec, framerate, bitrate.
  - **Audio:** each audio track — codec (AAC, AC3…) + channel layout (Stereo, 5.1).
  - **Subtitles:** each subtitle track — format (SRT, ASS, PGS…) + language.

### 2.6 Track Persistence (audio + subtitle + video)
- **Storage:** `settings.json` (project convention — no DB).
- **Per file (authoritative):** keyed by a file identity (path, or path + size/mtime hash to survive moves). Stores the chosen audio / subtitle / video track by **stable identity tuple** (§2.0), plus the subtitle **Off** flag. Re-opening the *same* file restores the exact same tracks. → *"Enable subs on `file1.mp4` → same subs next time"* is satisfied here.
- **Cross-file within a folder (heuristic, for series):** a whole season (e.g. `Avatar.The.Last.Airbender.S02…`, 7 separate `.mkv` files) has files whose track indices/titles may not line up. When opening a file that has **no** per-file record yet, resolve by:
  1. **Folder-level "last used" language:** remember the last audio/subtitle **language** chosen in this folder. If the new file has a track whose `language` matches, pre-select it (audio always; subtitle only if the user last had subs **on**).
  2. Otherwise fall back to the file's `forced`/`default` disposition for audio, and **Off** for subtitles.
  - Match by **`language` → then `title` → then `codec`, never by `index`** (indices differ per episode).
  - Net effect: *"turned on English subs on E01 → E02 auto-selects English subs"* — without assuming identical structure, and still defaulting to **Off** when that language isn't present.

> **External subtitle files** (loading sidecar `.srt` / `.ass` from the same folder) are **out of scope for §2** — they are handled in §3 and will later merge into the §2.2 list.

### 2.7 Implementation Order (Base Phase — 95%)
Ordered so each step builds on the previous and the **one real risk** (switching a stream inside `FFmpegFrameGrabber`) is retired early. Facts at time of writing: `VideoPlayerState.kt` = 109 lines (little headroom — track fields must be split out per §2 file-size mandate), `FileInfoDialog.kt` already exists, the Copy button lives in the global controls. Test corpus: `Avatar.The.Last.Airbender.S02` 2160p NF (multiple audio + subtitle tracks).

- **Step 0 — Foundation: stream enumeration + track model (§2.0).**
  - New (commonMain): `MediaTrack` (data class — index/type/codec/language/title/isDefault/isForced + audio channels/layout + subtitle isTextBased), `MediaTracks` (audio/subtitle/video lists). Pure Kotlin so UI can observe.
  - New (jvmMain): `MediaTrackProbe.kt` — reads `grabber.formatContext.streams()` on open, builds `MediaTracks`; label `title ?: language ?: "Track {n}"`.
  - Edit: `VideoPlayerState` — add `hasSubtitle`, `mediaTracks`, selected ids. **⚠ 109 lines** → split track fields/logic into `VideoPlayerTracks.kt` (or a delegate); do not bloat the state.
  - Risk: low. Verify: build + log tracks on an Avatar `.mkv` (see >1 audio and subtitles).
- **Step 1 — File Info: stream sections (§2.5).** First visible result, read-only, validates Step 0.
  - Edit: `FileInfoDialog.kt` — General / Video / Audio / Subtitles cards (Apple HIG) from `MediaTracks`.
  - Risk: minimal. Verify: 'i' on an Avatar file shows tracks + codecs.
- **Step 2 — Audio tracks + RETIRE THE MAIN RISK (§2.1).** First stream switch + the reference popover/marquee style reused by subtitles.
  - **⚠ Spike first:** switching audio track in `FFmpegFrameGrabber` (no clean "set audio stream" API). Approach: **reconfigure/re-open the grabber on the chosen index + seek to `currentPositionMs`** for A/V continuity. Do not proceed until the spike is proven.
  - New: volume-popover side "Extension" list, reusable `Marquee.kt` (back-and-forth). Edit: `MediaViewerVolumeControl.kt` (92 lines — headroom exists, but watch it).
  - Risk: high (spike). Verify: switch eng↔other language live without desync.
- **Step 3 — Subtitles: decode + overlay (§2.2, text + bitmap).** Largest new subsystem; reuses Step 2 style.
  - New (jvmMain): `SubtitleDecoder.kt` — separate `AVFormatContext`, `avcodec_decode_subtitle2`, pre-parse **all text** tracks on open into `Cue` lists; bitmap (PGS) on-demand to `ImageBitmap`.
  - New (commonMain): `Cue` (startMs/endMs/text|bitmap). State: `activeSubtitleTrack`, derived `activeCue` from `currentPositionMs`.
  - New (UI): `SubtitleOverlay.kt` (overlay over the clean frame), `SubtitleControl.kt` (icon left of Volume + popover in §2.1 style, "Off" + "Customize Subtitles").
  - Edit: `Icons.kt` — register `SubtitlesOff = Res.drawable.captions_bubble_slash` (asset already created).
  - Risk: medium. Verify: on/off/switch, sync on seek/pause, crossed-out icon when Off.
- **Step 4 — Settings ⚙ + Video track (§2.3 / §2.4).** Settings-menu scaffold (base for §6) + video-stream selection.
  - New: `PlayerSettingsMenu.kt` — icon right of Volume, **always visible**, popover. "Video Track" entry only when >1 video stream (same switch mechanism as Step 2).
  - Edit: toolbar row → `[Subtitles?] [Volume] [Settings⚙] [Fullscreen]`.
  - Risk: low (switch mechanism already retired in Step 2). Verify: menu opens on any file; video-track visible only on multi-video.
- **Step 5 — Track persistence (§2.6).** Cross-cutting memory over the finished selectors.
  - Edit: `settings.json` model (`SettingsRepositoryImpl`) — per-file records (path + size/mtime hash) by **stable-identity tuple** + subtitle Off; folder-level "last language" heuristic. Load on open, write on change.
  - Risk: medium (series heuristic). Verify: subs on E01 → E02 auto-picks same language; re-opening same file restores exactly.
- **Step 6 — §3 base scope.** External subs + customization + sync.
  - External `.srt/.ass` (auto-match by name + manual pick) → same `Cue` model, merged into §2.2 list.
  - Text-sub customization UI (font/size/color/bg/outline) behind "Customize Subtitles".
  - Delay/advance — offset `currentPositionMs − delayMs` (subs + audio), no re-decode.
  - Risk: low. Verify: external srt loads; style applies to text; offset fixes desync.
- **Step 7 — UI Cleanup (§7).** Remove Copy button from the global controls. Trivial, any time.

**Critical path:** Step 0 → **Step 2 spike** (stream switching — the only genuine risk; if the grabber can't switch cleanly, decide here: reconfigure+seek vs. a separate decode context). Everything else is incremental. **File-size rule (§2):** check size before editing; split candidates — `VideoPlayerState.kt` (Step 0), possibly the volume control (Step 2); new subsystems split into small files up front.

## 3. Subtitle Customization & Advanced Settings
This is the **95% scope** on the current stack (no new native deps). Full pixel-accurate ASS (positioning/karaoke/effects) is a separate deferred phase — see `subtitle-ass-full-rendering.md`.

- **Appearance customization** (opened via the "Customize Subtitles" button, §2.2): font, size, colour, background/box, outline/shadow. Applies to **text subtitles (SRT / basic ASS text)** only — bitmap (PGS/VobSub) and full-ASS tracks carry their own look and are not restyled.
- **External subtitle files:** load sidecar `.srt` / `.ass` from the same folder (auto-match by filename; also allow manual pick). Parsed into the same `Cue` model as embedded text tracks (§2.0) and merged into the §2.2 subtitle list ("Off" + embedded + external), with the same per-file / folder-language persistence (§2.6).
- **Synchronization controls (delay/advance):** offset applied at lookup time (`currentPositionMs − delayMs`) for subtitles, and an equivalent audio offset — no re-decode needed. Adjustable in small steps while playing.

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
