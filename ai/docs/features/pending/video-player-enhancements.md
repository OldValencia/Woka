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
- A **"Customize Subtitles"** button sits at the bottom of the list; it opens the subtitle-appearance settings (§3). *(✅ Done in Step 3 — opens an inline Customize view within this popover.)*
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

**Chosen approach (decided this session, audio — to apply the same way to every folder / track type):**
- **Two layers, resolved per-file → per-folder → default:** on open, use `audioTrackPerFile[path]` if present (authoritative — an explicit per-episode choice sticks); else match `audioTrackPerFolder[folderPath]`; else fall back to the **default disposition / track 0**. Every manual switch writes **both** records.
- **Match by the full identity tuple, NOT language-first.** Priority: exact `(title == && language == && codec ==)` → `title` → `language` → default. *(Language-first — as originally written above — fails the Avatar case where 7 of 8 audio tracks are `rus`; the `title` is the real discriminator.)*
- **Scope = folder** (a season lives in one folder). Fingerprint-of-track-set grouping is a possible future upgrade but not needed for the common case.
- **Cleanup (must be handled to avoid stale `settings.json`):**
  - On **folder unlink** (`removeLinkedFolder`): drop `audioTrackPerFolder[path]` **and** every `audioTrackPerFile` entry whose path is under that folder.
  - On **video deletion** (in-app delete): drop that file's `audioTrackPerFile` entry.
  - Bloat is otherwise a non-issue (each record is a path + tuple; the file stays tiny), so no aggressive global pruning is needed.

> **External subtitle files** (loading sidecar `.srt` / `.ass` from the same folder) are **out of scope for §2** — they are handled in §3 and will later merge into the §2.2 list.

### 2.7 Implementation Order

**How to read this list.** A step is **✅ Done** only when *its own* scope is finished. Anything dropped or deferred out of a step is **re-homed as a later step** — never left as a loose "remaining" note inside a completed one, or it stops being tracked work and becomes a footnote nobody schedules. Steps below cover **everything still outstanding in this document**; what is deliberately not covered is listed under *Out of scope* at the end.

**Test corpus:** `Avatar.The.Last.Airbender.S02` 2160p NF (8 audio + 3 subtitle tracks) and the purpose-built `_TEST.dual-video.mkv` (2 video tracks — see Step 4).

**File-size watch (§2 mandate, real `wc -l`).** Over the 150-line limit *today*, so each must be split before it is next edited: `MediaViewerScreen.kt` 226, `MediaSlider.kt` 199, `ThumbnailLoader.kt` 177, `SizeModeIndicator.kt` 177, **`VideoPlayerState.kt` 156**, `SubtitleCustomizePanel.kt` 155, `GlobalControlButtons.kt` 153. `VideoPlayerState.kt` is the one the steps below will actually touch — split its sync fields (`seekTargetMs`, `seekGeneration`, `videoReadyGen`, `pending*ReopenIndex`, `rebasePresenterClock`, `tracksProbed`) into a holder alongside `VideoPlayerTracks` first. *(Measure with `wc -l`; PowerShell `Measure-Object -Line` under-reports.)*

- **Step 0 — Foundation: stream enumeration + track model (§2.0). — ✅ Done**
  - New (commonMain): `MediaTrack` (data class — index/number/type/codec/language/title/isDefault/isForced + audio channels/layout + subtitle isTextBased; `label`, `identity` tuple), `MediaTracks` (video/audio/subtitle lists + `hasSubtitle`/`hasMultiple*`). Pure Kotlin so UI can observe.
  - New (jvmMain): `MediaTrackProbe.kt` — reads `grabber.formatContext.streams()` on open, builds `MediaTracks`; label `title ?: language ?: "Track {n}"`.
  - New (jvmMain): `VideoPlayerTracks.kt` — track state holder split out of `VideoPlayerState` (mediaTracks/hasSubtitle/selected indices) per the file-size mandate.
  - Edit: `VideoPlayerState` — add `tracks` holder + `hasSubtitle` passthrough. Edit: `VideoPlayerDecoder` — probe on open + verification log.
  - Verified: Avatar S02 `.mkv` logs 1 video / 8 audio (rus+eng, ac3/eac3/aac, 5.1/stereo) / 3 subrip subtitles; channel-layout garbage fixed (read only `av_channel_layout_describe`'s returned length).
- **Step 1 — File Info: stream sections (§2.5). — ✅ Done**
  - Model: `MediaTrack` gained video-only `width`/`height`/`frameRate`/`bitRate`; `MediaTrackProbe` populates them (codecpar + `avg_frame_rate`).
  - New (jvmMain): `FileInfoStreamCards.kt` (Apple HIG General/Video/Audio/Subtitles cards from `MediaTracks`) + `FileInfoFormat.kt` (duration/fps/bitrate/subtitle-format helpers) — split out per the file-size mandate.
  - Edit: `FileInfoDialog.kt` — new `mediaTracks`/`durationMs` params, renders sections (wider dialog) when present; images unchanged. Wire: `MediaViewerPlayer` passes `videoState?.tracks?.mediaTracks` + `durationMs`.
  - Verify: 'i' on an Avatar file shows tracks + codecs.
- **Step 2 — Audio tracks + RETIRE THE MAIN RISK (§2.1). — ✅ Done**
  - **Engine (risk retired):** the spike proved `reconfigure+reopen` worked but lagged (4K HEVC re-decode on `setTimestamp`). Chose the **separate-decode-context** path: video and audio run on **two independent grabbers** (`VideoPlayerDecoder` = video-only master clock; `VideoPlayerAudioDecoder` = audio-only). Switching audio re-opens ONLY the audio grabber → video never freezes; the audio grabber has video disabled (`videoStream = Int.MAX_VALUE`) so its seeks don't decode HEVC. Audio stays locked to `currentPositionMs` (generation re-seek + lead throttle + lag-resync), and a `videoReadyGen` gate stops audio starting before the frame lands on slider seeks. `openGrabber`/`reopenAudioGrabber`/`selectAudioTrack` in `VideoPlayerGrabber.kt`. Fixed a concurrent-first-open race (audio loop waits on `tracksProbed`; `hasAudio` from probe).
  - **UI:** reusable `Marquee.kt` (commonMain, back-and-forth). `MediaViewerAudioExtension.kt` (track list docked to the volume popover, selected = accent, long names marquee). `MediaViewerVolumeOverlay.kt` (`VolumeVerticalOverlay` extracted here per §2 size mandate + extension hook). File Info highlights the active audio track (`accentFolders`, §2.5).
  - Verified: live eng↔rus switch, no desync, no video freeze; slider seek keeps A/V in sync.
  - **Not in this step's scope:** remembering the chosen audio track across opens — that is **Step 5** (§2.6), which does all three track types at once.
- **Step 3 — Subtitles: decode + overlay (§2.2) + appearance customization (§3). — ✅ Done**
  - **Decode engine:** `SubtitleDecoder.kt` (separate `AVFormatContext`, `avcodec_decode_subtitle2`, pre-parses **all text** tracks on open into `Cue` lists, synced to `currentPositionMs` on a 50 ms loop, launched after the probe so there is no first-open race) + `SubtitleParser.kt` (SRT/ASS/SSA text cleanup — strips `Dialogue:` prefixes and `{…}` override tags, normalizes `\N`/`\h`, PTS→ms). New (commonMain): `Cue` (startMs/endMs/text|bitmap). State: `selectedSubtitleIndex` + derived `activeCue` on `VideoPlayerTracks`.
  - **Overlay:** `SubtitleOverlay.kt` renders over the clean frame (keeps §1 zoom/pan intact). Text cues styled from §3; bitmap path stubbed (PGS still text-only — deferred). Full ASS shows as basic styled text.
  - **Popover & icon:** `SubtitleControl.kt` (icon left of Volume, crossed-out `SubtitlesOff` at 0.5 alpha when Off), `SubtitleVerticalOverlay.kt` + `SubtitlePopover.kt` — standalone popover in §2.1 style (same card, marquee, accent selection), "Off" entry + "Customize Subtitles". Edit: `Icons.kt` — `SubtitlesOff` registered.
  - **Hidden when absent (§2.2):** `SubtitleControl` is only composed when the file has ≥1 subtitle track; the time slider (`weight 1f`) reclaims the freed space — no empty gap. Settings ⚙ stays always visible.
  - **Appearance customization (§3, brought forward from Step 6):** "Customize Subtitles" now opens an **inline** panel inside the popover (List ↔ Customize views, horizontal slide + back chevron, fixed 210 dp width so the cursor never drops), `SubtitleCustomizePanel.kt` + `SubtitleCustomizeControls.kt` (dark pill groups / colour swatches / opacity stepper) + `SubtitleStyle.kt` (resolve). Controls: **Size** (`Auto` + S/M/L/XL), **Font** (System/Serif/Mono), **Colour** (swatches), **Background** box opacity, **Outline** (black stroke pass under the fill), and a **Reset to defaults** row. Every change applies live and persists.
  - **Auto size:** the font size is always relative to the video-panel height (`BoxWithConstraints.maxHeight`, clamp `[16sp, 52sp]`); `Auto` is the neutral base, S/M/L/XL are fixed multipliers of it. A window/fullscreen resize keeps the user's step proportional instead of resetting it.
  - **Settings (§3 / Step 5 groundwork):** `WokaSettings` gained `subtitleSize` / `subtitleFont` / `subtitleColorArgb` / `subtitleBoxOpacity` / `subtitleOutline` (+ `resetSubtitleAppearance()`), all persisted globally in `settings.json`.
  - **Fixes this session:**
    - **Popover flicker loop (Volume ⇄ Subtitles):** consolidated the two mutually-resetting hover booleans into a single `PopoverHoverState` FSM (`None/Volume/Subtitle`, one grace timer; Exit only clears the target that still owns the pointer), de-duplicated across `MediaViewerScreen` and `MediaViewerFullscreenWindow`. **Root cause of the residual loop:** the `slideInVertically(initialOffsetY = { 50 })` enter/exit made the popover briefly slide down **over the toolbar icons** mid-animation, re-firing Enter/Exit → both popovers oscillated. Fixed by dropping the vertical slide (fade only) on both the Volume and Subtitle overlays.
    - **Fullscreen subtitle position:** was too high (115 dp); lowered to sit near the bottom and only lift to clear the control bar (fullscreen 72 dp; windowed 100 dp / 180 dp with carousel).
    - **Volume not persisted between videos:** `mediaVolume` lived in a local `remember`; now stored in `WokaSettings.mediaVolume` (+ `setMediaVolume`), restored on open and written on change (windowed + fullscreen). Survives file switches and restarts.
  - Verify: on/off/switch, sync on seek/pause, crossed-out icon when Off, no hover loop, live customization + Reset, auto-size on resize, volume restored on reopen.
  - **Descoped here, re-homed as real steps:** bitmap PGS/VobSub rendering → **Step 7**; subtitle persistence → **Step 5**; external `.srt`/`.ass` + delay/advance → **Step 6**. The `Cue.bitmap` field already exists as the model slot Step 7 will fill.
- **Step 4 — Settings ⚙ + Video track (§2.3 / §2.4). — ✅ Done**
  - **Opens on hover, not click.** §2.4 originally said "clicking opens"; that predates the hover popovers shipped in §2.1/§2.2. A click-opened popover sitting between two hover-opened ones needs its own dismiss logic and re-opens the class of Enter/Exit fight that `PopoverHoverState` was built to kill, so Settings joins the same FSM: `HoverTarget` gained `Settings`, one grace timer for all three.
  - **UI:** `PlayerSettingsOverlay.kt` (standalone popover above the ⚙ icon, `end = 60.dp` — one icon slot right of the Volume popover's 96.dp; fixed 190 dp width; fade-only, same anti-flicker reason as §2.2), `PlayerSettingsMenu.kt` (contents — Video Track section, `"No additional options"` empty state since the icon is always visible), `PopoverListRow.kt` (reusable accent/hover/marquee row extracted for the new list). Edit: `PlayerSettingsControl.kt` — placeholder icon gained the hover hook.
  - **Engine (§2.3):** `VideoPlayerVideoTrack.kt` — `reopenVideoGrabber` + `readStreamHdrInfo` + `selectVideoTrack`. Unlike audio (§2.1) this re-opens the video decode context **in place**: multi-video containers are rare, so a brief re-decode beats keeping a second video decoder alive permanently. `openVideoGrabber` now takes an absolute stream index; the decode loop drains `pendingVideoReopenIndex`, swaps the grabber, re-reads HDR metadata and `videoSeekTo`s back to the play head. No generation bump — the position never moves, so audio keeps running untouched.
  - **Fixed a Step 2 regression:** `selectAudioTrack()` existed but was never called — both call sites wrote `tracks.selectedAudioIndex` directly, so `pendingAudioReopenIndex` stayed null and the audio grabber never re-opened. The list highlighted the new track while the sound stayed on the old one. Both sites now call `selectAudioTrack`/`selectVideoTrack`.
  - **File-size mandate (§2):** `MediaViewerFullscreenWindow.kt` was 199 lines before this step — split first, into `MediaViewerFullscreenOverlays.kt` (popovers + bottom control bar + top-right size controls). Both files now ~129.
  - Wiring: `onSettingsHoverChange` threaded `MediaViewerPlayer` → `MediaViewerToolbar` → `MediaViewerVideoControls`, and through the fullscreen overlays.
  - **Fixes after first live test** (test file: `_TEST.dual-video.mkv`, stream 0 = 4K HDR HEVC `AV_DISPOSITION_DEFAULT`, stream 1 = 540p SDR H.264 grayscale):
    - **Wrong track on open.** FFmpeg's automatic video-stream pick is not necessarily the one flagged `AV_DISPOSITION_DEFAULT`, so the file came up on stream 1 while the menu highlighted stream 0 — the grabber and the UI disagreed. The decode loop now reconciles right after the probe: if the resolved default differs from `grabber.videoStream`, it queues a swap. The play head is still 0 there, so it costs an open and no re-decode, and single-video files are untouched.
    - **Untonemapped flash when switching back to HDR.** `videoSeekTo` published `currentFrame` **without** touching `currentFrameHdrInfo`. The bitmap is always plain RGBA — tone mapping is a UI decision made by `currentFrameHdrInfo` selecting `HdrImagePainter` — so the landed HDR frame was drawn with the *grayscale* track's SDR metadata until the next presenter frame corrected it. Both writes now go through `publishFrame()` inside `Snapshot.withMutableSnapshot`, and `videoSeekTo` takes the target track's `HdrInfo`. This also fixes the same latent flash on ordinary seeks within an HDR file.
    - **Frozen picture for ~2 s after the swap — presenter clock, not the decoder.** Instrumenting the phases settled it: the swap itself costs ~780 ms (`close` 6 ms + `open` 5 ms + `seek` 769 ms), the decoder then runs *faster* than realtime (958 ms of content in 892 ms) and audio comes back at `lead=88ms`. The stall was in `startPresenterLoop`: the wall clock spent swapping inflated `elapsedMs` while `firstVideoTsMs` still referenced the previous track, so every incoming frame looked ~800 ms late. That is under the 1000 ms discontinuity-reset threshold, so instead of rebasing, the presenter took the `sleepTime < -50` drop-to-catch-up path — which advances `currentPositionMs` but never assigns `currentFrame`, freezing the image until the decoder clawed an 800 ms gap back at ~1.07× (seconds). Fixed with `rebasePresenterClock`: the decode loop raises it after a swap and the presenter clears `firstVideoTsMs`, re-anchoring its baseline to the frame it actually resumed on. Widening the threshold would have been the wrong fix — it hides the gap instead of accounting for it.
    - **~5 s swap now has a spinner, and audio no longer runs away.** Re-opening a 4K HEVC stream and decoding back to the play head is genuinely seconds of software decode — not a bug, and not worth a second permanent video decoder for a case this rare. New `isReloadingVideo` on `VideoPlayerState`: `VideoReloadOverlay.kt` floats a spinner card over the retained last frame (nothing blanks), and the audio loop parks on the same flag so sound and picture resume together instead of needing a lag-resync.
  - **Test fixture — `_TEST.dual-video.mkv`** (in the Avatar S02 folder; multi-video containers are otherwise unobtainable). A reported "switching to Grayscale skips 5 s" turned out to be the fixture, not the player: the naive build had the second stream starting at **pts 6.166 s** with keyframes ~10.4 s apart, so the timeline's opening seconds had no decodable frame at all. `-ss` before `-i` fast-seeks to a keyframe, and `-c:v:0 copy` keeps the pre-roll from it while the decoded/filtered branch drops everything before the requested time — the two video streams end up offset by exactly that difference. **Build it in three passes:** (1) cut a self-contained slice with `-c copy` so timestamps rebase to 0; (2) encode the grayscale track *from that slice* with **no** `-ss`, `-g 24 -keyint_min 24 -sc_threshold 0`; (3) mux the two. The SDR colour tag will not stick via container remux (the Matroska Colour element wins) — rewrite the bitstream VUI with `-bsf:v:1 h264_metadata=colour_primaries=1:transfer_characteristics=1:matrix_coefficients=1`, otherwise the SDR track is tagged PQ/BT.2020 and gets falsely tone-mapped. Verified good: both streams `pts=0`, v:1 keyframe gap 1 s, v:1 `bt709`.
  - Verified on that fixture — every swap lands where it was asked (`want ≈ landed ≈ first shown`, within a single frame ~40 ms), zero seek-backoff attempts, no `restart()`, and ordinary slider seeks are just as exact.
  - Verify: ⚙ popover opens on hover in windowed + fullscreen; empty state on single-video files; Video Track list only on multi-video; opens on the disposition-default track; switching either way shows the spinner and comes back in sync with correct HDR from the first frame; audio switch actually changes the sound.
  - **Not in this step's scope:** the rest of the §6 menu catalogue (Repeat / Shuffle / Playback Speed) → **Step 8**. Building the scaffold they hang off *was* this step's scope, per §2.4.
- **Step 5 — Track persistence, all three types (§2.6).** Cross-cutting memory over the finished selectors. **Blocks nothing; unblocked now.**
  - ~~Split `VideoPlayerState.kt` and `SettingsRepositoryImpl.kt` first.~~ **✅ Done** — `SettingsRepositoryImpl` 266 → 111 (disk layer to `SettingsStore.kt`; verbose setters routed through the `update {}` helper already in the file), `VideoPlayerState` 156 → 110 (transport to `VideoPlayerControls.kt` as extensions, matching how the package already organises behaviour). Moving the `@Volatile` sync fields into a holder was rejected: 46 references across the decoder and audio loops, a large mechanical diff through freshly stabilised concurrency code.
  - **Decisions taken up front (do not re-litigate):**
    - **Records are keyed by absolute file path, not a content hash.** §2.6 offered either, but the required cleanup — "drop every per-file entry whose path is under the unlinked folder" — is a path-prefix sweep, which a content-keyed map cannot answer. Path-keying also matches `folderLayouts` / `pinnedSessions`. Cost: renaming a file loses its record. Accepted.
    - **The per-folder layer covers audio + subtitles only; video is per-file only.** Carrying a video-track choice across episodes is meaningless — different files' second video streams are different angles or encodes, not "the same track".
  - **Known gap to close in this step:** subtitle selection currently writes `tracks.selectedSubtitleIndex` directly from `SubtitleControl.kt` and `SubtitleVerticalOverlay.kt`. Audio and video go through `selectAudioTrack` / `selectVideoTrack`; subtitles need the matching `selectSubtitleTrack(index: Int?)` before there is one place to hook persistence into. (This asymmetry is exactly what hid the Step 2 regression.)
  - Edit: `WokaSettings` + `SettingsRepositoryImpl` — per-file records (path + size/mtime hash) storing audio / subtitle / video choice by **stable identity tuple** + the subtitle **Off** flag; per-folder "last used" record for the series heuristic. Resolution order per-file → per-folder → default disposition.
  - Match priority `(title && language && codec)` → `title` → `language` → default — *not* language-first (§2.6).
  - Wire: apply on open in `VideoPlayerTracks.apply()`; write on every `selectAudioTrack` / `selectVideoTrack` / subtitle pick.
  - Cleanup: drop per-file + per-folder records on `removeLinkedFolder` and on in-app video delete (§2.6).
  - Risk: medium (series heuristic). Verify: subs on E01 → E02 auto-picks the same language; re-opening the same file restores exactly; unlinking a folder leaves no stale records.
- **Step 6 — External subtitles + sync offset (§3).** Customization already shipped in Step 3.
  - External `.srt`/`.ass` (auto-match by filename + manual pick) → parsed into the same `Cue` model, merged into the §2.2 list alongside "Off" + embedded, sharing Step 5's persistence.
  - Delay/advance — offset applied at lookup (`currentPositionMs − delayMs`) for subtitles plus an equivalent audio offset. No re-decode.
  - Risk: low. Verify: sidecar `.srt` loads and styles like an embedded text track; offset fixes a desynced release.
- **Step 7 — Bitmap subtitles: PGS / VobSub (§2.2).** *(Was descoped from Step 3 with no home — now tracked.)*
  - `SubtitleDecoder` currently pre-parses **text** tracks only. Add the bitmap path: `AVSubtitleRect` → `ImageBitmap` into the existing `Cue.bitmap` slot.
  - `SubtitleOverlay` renders the bitmap positioned from the rect's own coordinates, scaled to the video panel. **Not** restyled by §3 — these carry their own look.
  - Until this ships, a PGS-only file shows the track in the list but renders nothing; decide whether to hide such tracks in the meantime.
  - Risk: medium (rect geometry + palette). Verify: a PGS release renders subs that track seek/pause.
- **Step 8 — Settings ⚙ menu catalogue (§6).** Fills the Step 4 scaffold.
  - **Repeat** — engine support already exists (`VideoPlayerState.isRepeatMode`, honoured by the decode loop at EOF) but **has no UI at all**; this is a menu entry over dead state. Extend to loop-the-folder as §6 asks.
  - **Shuffle** — new: shuffled order over the folder playlist.
  - **Playback Speed** (0.5×–2×) — the only non-trivial one: the presenter's pacing must scale *and* audio needs resampling (`atempo`) or it changes pitch. Subtitle lookup follows `currentPositionMs`, so it comes along for free.
  - Risk: low for Repeat/Shuffle, **medium-high for Speed** (audio pipeline). Verify: each entry appears in ⚙; repeat loops; speed keeps A/V and subtitles in sync at 0.5× and 2×.
- **Step 9 — UI cleanup (§7).** Remove the Copy button from the global controls. Trivial, any time.

**Later phase — §4 Mega-Convenience.** Not part of the base phase, but tracked here so it is not lost:
- **Step 10 — Playback Resume (§4).** Remember last position per file; settings to disable entirely or only for videos under a threshold (e.g. < 10 min). Shares Step 5's per-file record store.
- **Step 11 — Smart Chapters (§4).** Read container chapters (MKV) and draw them as timeline segments.
- **Step 12 — System Media Controls (§4).** OS media-key integration (play/pause/next) — needs a JVM-side native hook; the largest unknown in this document.

**Out of scope for this document** *(deliberately, not forgotten)*:
- **§5 Web Streaming & Network Sources** — experimental; needs its own design doc before any step is written.
- **Full pixel-accurate ASS** (positioning / karaoke / effects) via libass — separate phase, see `subtitle-ass-full-rendering.md`. Step 6 keeps ASS as basic styled text.

**Remaining critical path.** The original risk — switching a stream inside `FFmpegFrameGrabber` — was retired in Steps 2 and 4, so nothing left is architecturally risky. The two genuine unknowns remaining are **Playback Speed** (Step 8, audio resampling) and **System Media Controls** (Step 12, native integration). Everything else is incremental. **File-size rule (§2):** check `wc -l` before editing; the watch list above is authoritative.

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
