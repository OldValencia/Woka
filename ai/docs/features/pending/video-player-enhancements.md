# Video Player Enhancements

## 1. Zoom & Size Control (UI/UX) — ✅ Implemented
*Rewritten in **Step 8b**; the design record, the diagnosis of what the first version got wrong and the
implementation notes live in `implemented/zoom-size-control-redesign.md`. What follows is the shipped
behaviour.*

- **One scale, no modes.** The zoom percentage is measured from **native 1:1 pixels** — the base macOS
  Preview and every other viewer use — so **100% == Actual**. Fit / Fill / Actual are not a separate
  mode: each is a specific percentage on that same scale, and the state merely *sticks* to one of them.
  - *Definitions:* **Fit** = scaled to sit entirely inside the panel, aspect preserved (contain, no
    cropping) — the default. **Fill** = scaled to cover the panel, aspect preserved, overflow cropped.
    **Actual** = native 1:1, always exactly 100%. Fit and Fill are panel-dependent and re-resolve on
    every resize; Actual does not.
  - *Sticky points:* while stuck, the widget reads `Fit · 42%`; move off it with the wheel and it reads
    `43%`. Landing within **2.5%** of a point snaps back onto it (tie-break `Actual` → `Fit` → `Fill`),
    so the crossover is a place you can feel and see, not an invisible coincidence.
  - *Range:* **dynamic**, per file and panel — `zoomBounds()` gives an order of magnitude either side
    of the fitted size, then widens until Fit, Fill and 100% are all inside it (a snap point outside
    the bounds would be unreachable). Capped at 6400% *before* that widening — a snap point may still
    push the ceiling higher, which is the point of the widening.
- **Zoom step is geometric:** `×1.1` per wheel tick or `−` / `+` click, rounded to whole integers and
  never smaller than 1 point. A fixed step in percentage points feels wildly different at 42% and at
  400%; the geometric step also retired wheel acceleration as a separate mechanism.
- **`Ctrl + Scroll` always zooms** — there is no state in which the gesture is refused — and it anchors
  on the **cursor**, keeping the point under the pointer in place (`zoomAround`, shared with
  double-tap and the keyboard).
- **Keyboard:** `Ctrl +` / `Ctrl =` step up, `Ctrl −` down, `Ctrl 0` = Fit, `Ctrl 1` = Actual. Wired in
  both the main window (`MediaViewerKeyHandler`) and fullscreen (`PipKeyHandler`).
- **HUD:** a centred pill shows the percentage for 900 ms while the wheel or keyboard changes it — you
  are looking at the cursor, not at the top bar. Not raised by the menu, where the value is on screen
  anyway.
- **Panning:** whenever the media overflows the panel, **left-button drag** moves it (`Ctrl` + hold
  Right Mouse Button still works). The frame is clamped so its edges cannot pass the panel edges (no
  black void), with a `0.35` rubber band past the limit and a `spring(0.85)` settle on release; an
  axis on which the media is smaller than the panel is pinned to centre, which auto-recentres the
  frame as the zoom comes back down. Details in `implemented/media-pan-bounds.md`.
- **Main Panel Indicator:** `[−]  Fit · 42% ▾  [+]` at a **fixed 148 dp**, so the toolbar never
  re-flows when the label changes length. The `−` / `+` buttons step the zoom; the centre is a single
  target with a single meaning — open the menu.
- **Zoom Menu (opens on click, not hover):** a 220 dp card below the indicator, `Theme.colors.surface`,
  1 dp border, 14 dp radius. Contents:
  1. **Fit / Fill / Actual rows** — icon, name, and the percentage each currently resolves to; the
     active one is accent-tinted. The number is what makes the presets and the scale read as one axis.
  2. A **logarithmic slider** with detent dots at Fit / Fill / Actual. Linear travel would give
     everything below 100% less than a tenth of the track.
  3. A real **`TextField`** (`[ 42 ] %`) plus **Reset** (back to Fit, offset zeroed). `Enter` closes.
  - The popup is **focusable**, which is what allows a real text field: the previous hover popover had
    to be non-focusable (a focusable popup swallows scene pointer input and makes hover blink), so
    typing was captured on the anchor one digit at a time. Click-opening removed that, the grace timer
    and the fixed-width workaround with it.
- **Icons (macOS / SF Symbols):** `rectangle.arrowtriangle.2.inward` (Fit),
  `rectangle.arrowtriangle.2.outward` (Fill), `aspectratio` (Actual — enum `ScalePreset.Original`,
  labelled "Actual" in the UI), `chevron.down` on the indicator. Registered in `Icons.kt` under
  `Icons.Static`.
- **Settings Persistence:** the **default size state** applied when a file opens is configurable in
  Settings → Media Viewer: Preset or Zoom plus its value (Fit/Fill/Actual, or a percentage — **of
  actual size**, range `10…1600`), and a **"Remember zoom & size"** toggle. Off → files always open at
  that default (default = Fit) and in-file changes are ephemeral; on → whatever the user sets inside a
  file is written back as the new default. Fullscreen ⇄ windowed always preserves the current state
  (same file). `sizeSchemaVersion` in `WokaSettings` guards a one-off reset of `defaultZoomPercent`,
  which meant a percentage of the *fitted* size before Step 8b and cannot be converted honestly.

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
- **Sidecar files (Step 9)** are listed under the embedded tracks, labelled by the language parsed from the filename plus the format (`Russian · SRT`, `English · forced · SRT`). They are *not* folded into `MediaTracks`: that model is what the container holds, and the File Info dialog (§2.5) prints it as the stream list, so merging them there would make that dialog lie.
- Two action rows sit at the foot of the list: **"Add subtitle file…"** (native picker, `.srt`/`.ass`/`.ssa`) and **"Customize Subtitles"** (§3). *(Customize: ✅ Done in Step 3 — opens an inline Customize view within this popover.)*
- **Every sidecar row carries a hover ✕ that hides it** — it never deletes the file. The list is built by scanning the folder, so hiding needs its own persisted record or the next open finds the file and puts it straight back. Re-adding it through the picker is the way back. The ✕ is deliberately on *all* sidecar rows, not only hand-picked ones: nothing on screen distinguishes a scanned file from a picked one, so nothing about the row may behave differently.
- **Default & persistence:** subtitles default to **Off** on first open of a file. The chosen track (or Off, or a sidecar path) is persisted **per file** and restored on next open (see §2.6).
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

> **External subtitle files** (loading sidecar `.srt` / `.ass` from the same folder) were out of scope for §2 while it was being built. **Step 9 merged them into the §2.2 list** — see the bullets there and the step entry below.

### 2.7 Implementation Order

**How to read this list.** A step is **✅ Done** only when *its own* scope is finished. Anything dropped or deferred out of a step is **re-homed as a later step** — never left as a loose "remaining" note inside a completed one, or it stops being tracked work and becomes a footnote nobody schedules. Steps below cover **everything still outstanding in this document**; what is deliberately not covered is listed under *Out of scope* at the end.

**Test corpus:** `Avatar.The.Last.Airbender.S02` 2160p NF (8 audio + 3 subtitle tracks), the purpose-built `_TEST.dual-video.mkv` (2 video tracks — see Step 4), and five sidecar subtitle fixtures beside E01/E02 covering UTF-8, CP1251, language suffixes and ASS (see Step 9).

**Project context — read before proposing interim work.** Woka is personal software: not deployed, not shipped to anyone, single user. A half-finished capability that shows in the UI and does nothing is **acceptable** between steps. Do not spend steps on stopgaps, graceful-degradation states or "hide it until it works" mitigations for gaps a later step closes anyway — go straight at the real step instead.

**File-size watch (§2 mandate, real `wc -l`).** Over the 150-line limit, so each must be split before it is next edited: `MediaSlider.kt` 199, `ThumbnailLoader.kt` 177, `FolderGrid.kt` 169, `SubtitleCustomizePanel.kt` 155. *(Measure with `wc -l`; PowerShell `Measure-Object -Line` under-reports.)* Already cleared: `SettingsRepositoryImpl` and `VideoPlayerState` (Step 5 prep), `FolderViewModel` (Step 5), `MediaViewerScreen.kt` 226 → 145 (Step 7), `SettingsPanels.kt` 182 → 49 and `MediaViewerContent.kt` 148 → 132 (Step 8b). `SizeModeIndicator.kt` 177 and `GlobalControlButtons.kt` 153 left the list by deletion / shrinkage in Step 8. **At the line:** `VideoPlayerState.kt` 149, `PlayerSettingsSync.kt` 149, `MediaViewerKeyHandler.kt` 149, `MediaViewerSettingsPanel.kt` 149, `MediaViewerContentGestures.kt` 148, `SettingsRepositoryImpl.kt` 145 — split before adding to any of them.

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
  - **Descoped here, re-homed as real steps:** bitmap PGS/VobSub rendering → **Step 10**; subtitle persistence → **Step 5**; delay/advance → **Step 6**; external `.srt`/`.ass` → **Step 9**. The `Cue.bitmap` field already exists as the model slot Step 10 will fill.
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
  - **Not in this step's scope:** the rest of the §6 menu catalogue (Repeat / Shuffle / Playback Speed) → **Step 7**. Building the scaffold they hang off *was* this step's scope, per §2.4.
- **Step 5 — Track persistence, all three types (§2.6). — ✅ Done**
  - ~~Split `VideoPlayerState.kt` and `SettingsRepositoryImpl.kt` first.~~ **✅ Done** — `SettingsRepositoryImpl` 266 → 111 (disk layer to `SettingsStore.kt`; verbose setters routed through the `update {}` helper already in the file), `VideoPlayerState` 156 → 110 (transport to `VideoPlayerControls.kt` as extensions, matching how the package already organises behaviour). Moving the `@Volatile` sync fields into a holder was rejected: 46 references across the decoder and audio loops, a large mechanical diff through freshly stabilised concurrency code.
  - **Decisions taken up front (do not re-litigate):**
    - **Records are keyed by absolute file path, not a content hash.** §2.6 offered either, but the required cleanup — "drop every per-file entry whose path is under the unlinked folder" — is a path-prefix sweep, which a content-keyed map cannot answer. Path-keying also matches `folderLayouts` / `pinnedSessions`. Cost: renaming a file loses its record. Accepted.
    - **The per-folder layer covers audio + subtitles only; video is per-file only.** Carrying a video-track choice across episodes is meaningless — different files' second video streams are different angles or encodes, not "the same track".
  - **Known gap to close in this step:** subtitle selection currently writes `tracks.selectedSubtitleIndex` directly from `SubtitleControl.kt` and `SubtitleVerticalOverlay.kt`. Audio and video go through `selectAudioTrack` / `selectVideoTrack`; subtitles need the matching `selectSubtitleTrack(index: Int?)` before there is one place to hook persistence into. (This asymmetry is exactly what hid the Step 2 regression.)
  - **Model (commonMain):** `TrackPrefs.kt` — `TrackRef` (language / title / codec) + `TrackPrefs` (audio / subtitle / `subtitleOff` / video). `TrackMatching.kt` — `MediaTrack.toRef()` and `List<MediaTrack>.matchRef()`, priority full tuple → `title` → `language`, *not* language-first (§2.6). `WokaSettings` gained `trackPrefsPerFile` + `trackPrefsPerFolder`.
  - **Repository:** `setTrackPrefs(filePath, folderPath, prefs)` writes **both** layers on every manual switch, stripping `video` from the folder record. `clearTrackPrefsForFile(path)`. `removeLinkedFolder` now also sweeps the folder's own record plus every per-file record under that path prefix (`File.separatorChar`).
  - **Player (jvmMain):** new `VideoPlayerTrackPrefs.kt` — `readTrackPrefs()`, `persistTrackPrefs()` and **`selectSubtitleTrack(index: Int?)`**, closing the known asymmetry (both subtitle call sites now go through it). *(Step 9 renamed this to `selectSubtitle(SubtitleSelection)` when sidecars stopped fitting an `Int` index — same single-write-point role.)* `VideoPlayerTracks.apply(tracks, perFile, perFolder)` resolves per-file → per-folder → default disposition, video from per-file only, subtitles Off unless a record says otherwise. `selectAudioTrack` / `selectVideoTrack` persist.
  - **Ordering:** the decoder reads the records *before* `tracks.apply(...)`, i.e. before the audio grabber and subtitle decoder start, so a restored track is used from the first open — no re-open on launch. The decoder's own default-video reconcile writes `pendingVideoReopenIndex` directly and deliberately does **not** persist: it is not a user choice.
  - **File-size mandate (§2):** `FolderViewModel.kt` was 217 lines, so it was split before the delete hook went in — `FolderStateMapper.kt` (`FolderState` + the pure `buildFolderState`) and `FolderFileOperations.kt` (delete / duplicate / create / move / rename / copy as extensions, same pattern as `VideoPlayerControls`). Now 131 / 75 / 58; `scope` and `settingsRepository` became `internal`. An inline `java.io.File` FQN in `pinFolder` became an import.
  - Verify: subs on E01 → E02 auto-picks the same language; re-opening the same file restores exactly; unlinking a folder leaves no stale records; deleting a video drops its record.
- **Step 6 — Sync offset: subtitles + audio (§3). — ✅ Done** *(Split out of the original combined "external subtitles + sync offset" step — the two halves have nothing in common technically and are worlds apart in value here. External files are now **Step 9**; see the rationale there.)* Customization already shipped in Step 3.
  - **Why this half first:** it applies to the **embedded** tracks already shipped, i.e. to the actual test corpus. The offsets exist for releases whose subtitles or dub were muxed from a source that does not match the picture — repacks, third-party dubs, 23.976-vs-25 fps masters.
  - **New (jvmMain): `VideoPlayerSync.kt`** — `SYNC_STEP_MS = 50`, limit ±30 000 ms, `audioTimeFor()` / `subtitleTimeMs`, the `setSubtitleDelay` / `setAudioDelay` / `resetSync` entry points and `formatSyncOffset`. **Sign convention: positive = that stream runs *later* than the picture**, so what belongs at position P is the content originally at P − delay.
  - **Subtitles:** one line — `findActiveCue(cues, subtitleTimeMs)` in `SubtitleDecoder.kt`. No re-parse; seek/pause/repeat keep working because the lookup still follows `currentPositionMs`.
  - **Audio:** the offset had to go into **all five** places the audio loop binds to the video clock — the `aheadMs` comparison, the generation re-seek, the lag-resync, the track-reopen resume and `reopenAudioGrabber`. Plus a new `audioDelayGeneration` that forces a re-seek when the value changes: **waiting it out only works when the offset moves forward.** Backwards, the new target sits ahead of the decoded position, so the loop would just park on the `AUDIO_LEAD_MS` throttle and play silence until the clock caught up — up to half a second of nothing per click.
  - **UI — lives in the Settings ⚙ popover (§2.4), not the subtitle Customize panel.** *(Decided this session, overriding the earlier "stepper in Customize" sketch.)* An **audio** offset has no business inside a *subtitle appearance* panel, and ⚙ is exactly the "secondary playback controls" drawer §2.4 defined it as. It also retires the `"No additional options"` empty state on ordinary single-video files, which was the whole reason ⚙ looked hollow.
    - New `PlayerSettingsSync.kt` — a `SYNC` section with two dark-pill steppers (`Subtitles`, `Audio`) in the existing `SubtitlePercentStepper` visual language, value shown as `+250 ms` / `−500 ms`, plus a **Reset sync** row that zeroes both and goes dim and inert while they already are.
    - Rows are conditional: `Subtitles` only when the file has subtitle tracks, `Audio` only when it has audio — the section never offers a control that cannot do anything. The empty state now only appears on a file with no audio, no subtitles and one video stream.
    - Card widened 190 → 210 dp to match the subtitle popover. `PlayerSettingsOverlay` / `PlayerSettingsMenuList` moved from three derived video-track params to the `videoState` they were all read off anyway, matching `SubtitleVerticalOverlay` — which removed the duplication at both call sites (windowed + fullscreen).
    - `SubtitleCustomizePanel.kt` (155) consequently did **not** need splitting; it stays on the §2 watch list for whatever touches it next.
  - **Persistence:** per-file, via two new `TrackPrefs` fields. The folder layer zeroes them alongside `video` — timing is a property of one encode, not of a season.
  - Verified live: steppers and Reset behave, no regressions in playback, track switching or seeking.
  - **File-size watch:** `PlayerSettingsSync.kt` landed at **149** — one line under the limit. Anything added to it needs a split first.
- **Step 7 — Settings ⚙ menu catalogue (§6). — ✅ Done** Filled the Step 4 scaffold; sits above the `SYNC` section shipped in Step 6.
  - **New (commonMain): `PlaybackMode.kt`** — `RepeatMode { Off, File, Folder }` with `next()`/`label`, and `PlaybackSpeed` (0.5–2.0, `snap()` pulling to 1× within ±0.04, `label()`). Persisted in `WokaSettings` as `repeatMode` / `shuffle` / `playbackSpeed`.
  - **Repeat** cycles Off → This file → Folder from one row. Only `File` is a decoder concern; `Folder` means "play another file", which is navigation, so the decoder raises `reachedEnd` and the playlist layer decides.
  - **New (jvmMain): `MediaViewerPlaylist.kt`** — owns "what plays after this file". Videos only (auto-advancing onto a still would just park there). Shuffle supplies the *order*, repeat decides whether the end *wraps*; the permutation is cached by folder signature so every file plays once before any repeats. `MediaViewerPlaybackEffects` pushes the modes into the player and handles EOF advance.
  - **Speed:** `PlayerSpeedSlider.kt` + `PlayerSpeedSliderParts.kt` — thin track, detent dot at 1× that the knob snaps onto, `markerLeft()` shared by dot and knob so they line up. **Commits on drag release, not per drag sample:** every applied change re-seeks the audio grabber and rebuilds the `atempo` graph, and a drag was firing seven of those in two seconds.
  - **Engine (`VideoPlayerSpeed.kt`, `VideoPlayerAudioTempo.kt`):** presenter pacing divides by speed; audio runs `atempo`, whose 0.5–2.0 range is exactly the range offered. Two traps found in testing, both fixed: the filter must be built from the **live** `playbackSpeed` at grabber start (opening a file while 2× is already selected bumps no generation, so a change-driven build left the sound at 1×), and it must be **rebuilt on every discontinuity** — `atempo` does not survive the backwards timestamp jump a seek produces and then fails over to unfiltered 1× sound for good. `audioReseek()` owns both.
  - **`skipBy()`** replaces the raw `seek(±5000)` at all three skip sites: under Repeat File a forward skip off the end wraps to the start and keeps playing; Off and Folder keep the pin-and-pause.
  - **Performance work this step turned out to be the bulk of it.** Measurement, not guesswork: on 4K HDR the RGBA conversion cost ~31 ms a frame against ~10 ms for the decode, so the decode loop was ~97% busy **at 1×** and had no headroom at all.
    - **`VideoPlayerFramePacer.kt`** — frames already past their wall-clock slot are decoded but not converted, so the media clock (and the audio with it) holds the requested speed and the displayed frame rate pays instead. Reused scratch buffer removes a 33 MB allocation per 4K frame.
    - **`VideoPlayerRenderScale.kt`** — decode at the size actually drawn, never upscaling. Re-open only after the surface has settled 700 ms and differs by >25%; last surface size is cached so the second file onwards opens already correct. The UI zooms relative to the **stream's** native size, not the decoded frame's, so `Actual size` still means native 1:1. Cost, accepted deliberately: zoom past 100% has no extra detail to draw on.
    - **Presenter dead band** — lateness between 50 ms and the 1000 ms discontinuity threshold used to drop every frame forever and never rebase; a run of 10 drops now forces a re-anchor. This was the "video stream froze" report.
    - Frame channel 60 → 8 (60 × 33 MB is up to 2 GB of live Skia rasters); the sound-line writer polls instead of blocking, so pause stops the line even when the channel is empty; an empty PCM buffer no longer halts playback.
  - **Splits forced by the §2 mandate:** `MediaViewerScreen` 226 → 145 (`MediaViewerScreenEffects`, `MediaViewerShortcutEffect`, `MediaViewerPlaylist`), `VideoPlayerAudioDecoder` (`VideoPlayerAudioSamples`), `VideoPlayerSpeed` (`VideoPlayerAudioTempo`), `PlayerSpeedSlider` (`PlayerSpeedSliderParts`). `MediaSlider.kt` was **not** reused and stays at 199.
  - **Advisory, added on request:** `PlayerSpeedNotice.kt` under the slider — above 1× on a frame ≥3.5 Mpx it says frames get dropped to keep the sound in step. Threshold is frame **area**, not codec, because the cost that caps playback is per-pixel.
  - **Verified live:** all three repeat states including Folder advance, shuffle exhausts before repeating, speed holds A/V and subtitle sync at 0.5× and 2× with no pitch shift, slider snaps to 1×.
- **Step 8 — UI cleanup: Copy button + size-mode control (§7 / §1). — ✅ Done** Two independent items; the first was trivial, the second was the real work and started with design, not code.
  - **8a — Remove the Copy button** from the global controls (§7). — ✅ **Done.** `CopyButton` deleted from `GlobalControlButtons.kt` and dropped from `MediaViewerNormalControls`; the `copyTrigger` flash plumbing it was the only consumer of (`MediaViewerScreen` → `MediaViewerLayoutWrapper` → `MediaViewerTopBarSetup` → the row) went with it. **Copying still works** via `Ctrl+C` and the viewer context menu — `copyAction` and `copyFileToClipboard` are untouched; only the toolbar affordance is gone. **Also removed on request: the fullscreen `MagicWandButton`** — a placeholder with an empty `onClick` since it shipped, so the fullscreen row now matches the windowed one. `Icons.Static.MagicWand` / `DocumentOnDocument` stay registered, now unused.
  - **8b — Rework the zoom ⇄ preset control. — ✅ Done.** *The full plan, the diagnosis and the implementation notes are in `implemented/zoom-size-control-redesign.md`; §1 above was rewritten to the shipped behaviour. Summary here so this list stays readable on its own.*
    - **The problem, stated plainly: the author of the feature, knowing exactly how it is built, still did not orient in it on the first try.** That was the whole justification — no user research needed beyond it. Reading the code confirmed the four diagnosed causes (mode switch in two unrelated places; the centre label doing two things from one click; an invisible Fit/100% crossover; nothing naming the current mode) and turned up **six more**, of which two mattered most: **the percentage was measured from the *fitted* size, not from 1:1** — so "100%" meant Fit while a separate "Actual" preset meant 1:1 — and **`SizeMode.kt`'s own KDoc described the model wrongly** (*"relative to native pixels (100 = 1:1)"*, *"Zoom(100) and Preset(Original) render identically"* — both false). The second is the objective evidence behind the justification.
    - **Direction taken: one continuous scale, no modes.** The percentage is re-based on native 1:1 and Fit / Fill / Actual become **sticky snap points** on it, each showing the percentage it currently resolves to. `SizeMode` kept its shape — `Preset` is the sticky point, `Zoom` the free value; only the meaning of `percent` changed, and the UI stopped calling them modes. The listed risk ("the percentage reading is not uniform along the axis") turned out not to bite: every point is a plain multiple of native size. What is genuinely non-uniform is *ordering* — Actual can sit below Fit for small media — which the computed percentages make visible instead of hiding.
    - **New:** `ZoomScale.kt` (fit/fill/preset percentages, dynamic `zoomBounds`, geometric `stepUp`/`stepDown`, `snapToPreset`, log slider mapping, `zoomAround`), `SizeIndicatorButton.kt`, `ZoomMenuPopover.kt` + `ZoomMenuSlider.kt` + `ZoomMenuInput.kt` + `ZoomMenuPresetRow.kt`, `MediaViewerZoomActions.kt` (`zoomTo` / `resolveZoomKey`, shared by wheel, double-tap and keyboard), `PanBounds.kt`, `ZoomHudOverlay` (in `MediaOverlays.kt`).
    - **Deleted:** `SizeModeIndicator.kt` (177 — the §2 blocker, rewritten wholesale rather than split), `ZoomPopover.kt`, `ZoomSliderView.kt`, `ZoomInputView.kt`, `ScalingDisabledOverlay` (nowhere left to refuse a zoom), the `ZoomPopoverView` setting end to end (enum, `WokaSettings` field, `setZoomPopoverView`, and the threading through six files), the `savedPreset` parameter chain, and the wheel acceleration. Net **−904 / +343** lines.
    - **`computeGraphicsScale`'s preset branches deliberately divide by the un-rounded `fitScale`.** Routing them through the integer percentage cost "Actual" up to 2% on 4K in a small window, and 1:1 has to be pixel-exact. The integer is used only where a human chose it.
    - **Absorbed here:** `media-pan-bounds.md` (clamp to the panel edges, `0.35` rubber band, `spring(0.85)` settle, auto-recentre) — shipped as this step's Phase 4 because the clamp needs exactly the geometry the redesign reworked. That document moved to `implemented/` with this one.
    - **Splits forced by the §2 mandate** (mechanical, no behaviour change): `MediaViewerFrame.kt` out of `MediaViewerContent`, `MediaViewerTapGestures.kt` + `MediaViewerZoomActions.kt` out of `MediaViewerContentGestures`, `MediaViewerFullscreenSurface.kt` out of `MediaViewerFullscreenWindow`, `MediaViewerSettingsPanel.kt` out of `SettingsPanels.kt` (182 before this work), `ZoomMenuPresetRow.kt` out of `ZoomMenuPopover`.
    - **Verified live:** all of it works — snap points, cursor-anchored wheel zoom, keyboard, HUD, pan clamp and settle, window ⇄ fullscreen, defaults after restart. The acceptance criterion holds: Fit, Fill, Actual and an arbitrary percentage are all reachable cold, with no tooltip.
- **Step 9 — External subtitle files (§3). — ✅ Done** *(Was the other half of the original Step 6. Deliberately ordered below Steps 7–8: those close gaps in already-shipped functionality, this adds a scenario the project's own corpus does not contain.)*
  - **Why it stayed in the plan.** Sidecar `.srt` is standard practice for non-English audiences pulling subtitles separately from the release; `.ass` is anime fansub territory. Modern Web-DL/MKV muxes everything in, which is why the test corpus never hits it.
  - **Test fixtures, built for this step** (in the Avatar S02 folder, cues at 1 s / 20 s / 1:00 / 5:00 / 10:00): `…S02E01….srt` (exact-name match, UTF-8, multi-line cues, a deliberately over-long line, `<i>`/`<b>` tags and a bare `<angle>` word), `…S02E01….ru.srt` (language suffix, UTF-8 Cyrillic), `…S02E01….rus.srt` (**CP1251, no BOM** — verified to render as mojibake if decoded as UTF-8), `…S02E02….en.srt` (cross-episode carry-over), `…S02E02….ass` (styles, `\N`, `{\i1}`, `{\an8}`, `{\pos}`, `{\fad}`, Cyrillic).
  - **The parsing is *not* done through FFmpeg** — reversing the plan's assumption that it was free. `sub_charenc` only works when FFmpeg is built with iconv, and when it is not the option fails **silently**: the result is mojibake indistinguishable from a broken parser, one level below the trap the plan already warned about. Reading the bytes ourselves is also *less* code than the FFmpeg route (no `AVFormatContext`, no codec contexts, no packet loop), because `SubtitleParser.cleanText` already strips the ASS `Dialogue:` prefix, `{…}` override tags and normalises `\N` — only timestamp parsing was missing, ~20 lines for SRT and ~10 for ASS.
    - New `SubtitleFileCharset.kt`: BOM (UTF-8 / UTF-16 LE / BE) → **strict** UTF-8 decode (`CodingErrorAction.REPORT`, so a wrong guess throws instead of silently producing `�`) → CP1251. The single-byte fallback is CP1251 rather than CP1252 deliberately: this corpus is Russian and the two are indistinguishable without language detection.
    - New `SubtitleFileParser.kt`: SRT by blank-line blocks, ASS by `Dialogue:` lines (9 fields then the text), both into the same `Cue` list embedded tracks produce — so the overlay, §3 styling and the Step 6 sync offset apply unchanged.
    - `SubtitleParser` gained HTML-tag stripping, needed only on this path (FFmpeg rewrites `<i>` into ASS tags for embedded SRT). The regex covers **only** `b|i|u|s|font`; a generic `<[^>]*>` sweep would also eat ordinary angle-bracketed dialogue, which the fixture deliberately contains.
  - **Identity: `SubtitleSelection { Off | Embedded(index) | External(path) }`** (commonMain), replacing `selectedSubtitleIndex: Int?`. The reserved-negative-range option was rejected — it makes every consumer guess which numbering an index came from. `MediaTracks` was left alone and sidecars live in a parallel `externalSubtitles` list on `VideoPlayerTracks`, concatenated only for display; folding them in would have made the §2.5 File Info stream list lie.
  - **Discovery (`ExternalSubtitleScan.kt`):** same folder, extension in `srt|ass|ssa`, filename starting with the video's base name (case-insensitive). The suffix between base and extension is split on `. _ -` and space; `forced`/`sdh`/`cc`/`hi` set flags, the first 2–3 letter alphabetic token is the language. Scanning the whole set rather than only the exact-name match is what makes `movie.ru.srt` / `movie.eng.forced.srt` — the normal shape of a fansub — appear as distinct labelled entries. `movie.srt` has nothing to parse and its filename is the video's full release name, far too long for the popover, so it is labelled `Subtitle file`.
  - **Persistence (§2.6), four new `TrackPrefs` fields.** `subtitleExternal` (the selected path) and `subtitleExternalLanguage`; the folder layer **strips the path and keeps only the language**, because a path names one specific file while "this folder is watched with external Russian" is the preference that carries. Opening the next episode re-scans its own sidecars and matches the language. Plus `subtitleExternalAdded` and `subtitleExternalHidden` — see the corrections below. All four are stripped from the folder record alongside `video` and the sync offsets.
  - **Picker:** `openFilePicker` added to `DesktopUtils` as `expect`/`actual` with AWT `FileDialog` per the desktop-dialog mandate. The extension filter is applied in Kotlin as well as handed to the dialog, because Windows ignores `FilenameFilter`. Hand-picked files may live anywhere, not only beside the video.
  - **`VideoPlayerState.hasSubtitle` now counts sidecars.** Without that a file with no embedded tracks showed no subtitle icon at all, leaving the sidecar unreachable; it also enables the `Subtitles` row in the ⚙ SYNC section for such files.
  - **Two corrections after the first live test:**
    1. **Hand-picked files did not survive re-opening the video.** The path was written to `subtitleExternal`, but the list was rebuilt from the folder scan alone — a file from elsewhere is not in it, so the restore silently fell through to Off. Fixed with `subtitleExternalAdded`, a per-file list of picked paths, restored on open with dead paths and scan duplicates filtered out.
    2. **The ✕ was gated on "added by hand", which is the wrong cut.** It drew the line by *where the file sits* — a picked file that happens to be a sibling with a matching name is found by the scanner on the next open and loses its ✕. Two identical-looking rows, one removable and one not, for a reason nothing on screen explains. Now **every** sidecar row has the ✕, it **hides rather than deletes** (`subtitleExternalHidden`, per file; the scan is filtered by it), and re-adding through the picker clears the record. `ExternalSubtitle.isManual` survives but is purely internal — it marks the files the scan cannot re-find, i.e. the ones worth writing to `subtitleExternalAdded`.
  - **Known sharp edges, left as they are:** subfolders are not scanned, so a release that puts subtitles in `Subs/` needs the picker; the base-name match is a prefix, so with short names `Ep1.mkv` would also match `Ep10.srt` (harmless on release-length names, fixable by requiring the remainder to start with a separator); and `findActiveCue` returns the *first* matching cue, so overlapping ASS events (a sign over dialogue) show only one — acceptable while ASS is basic styled text, and properly a `subtitle-ass-full-rendering.md` concern.
  - **Verified live:** sidecars load, style and sync like embedded text tracks; the CP1251 file renders correctly; selection persists per file; the language carries E01 → E02; the ✕ hides and survives re-opening.
- **Step 10 — Bitmap subtitles: PGS / VobSub (§2.2).** *(Was descoped from Step 3 with no home; then tracked as Step 7; **moved after Step 9 this session** — see below.)*
  - **Why after external files, precisely.** Not because Step 9 unblocks this one — it does not. The two are independent: Step 9's hard part is the `Embedded`/`External` identity model, Step 10's is bitmap geometry and memory, and neither supplies the other. The reason is that **the bitmap path would otherwise be built twice.** Bitmap subtitles have sidecar forms — VobSub ships as an `.idx` + `.sub` pair and PGS is extracted to `.sup` — so doing Step 9 first means the bitmap work lands **once**, covering embedded PGS and sidecar PGS/VobSub in the same pass, instead of shipping embedded-only and re-opening the bitmap code later to add file loading.
  - **The hooks Step 9 left ready:** `SubtitleSelection.External(path)` already carries a sidecar of any kind, and `ExternalSubtitleScan.kt` has a single `SUBTITLE_EXTENSIONS` set to widen — today `srt|ass|ssa`, deliberately text-only. Adding `sup` / `idx` there plus a bitmap branch in `parseSubtitleFile` is the whole file-loading side. Note `.idx`+`.sub` is a **pair**, which the one-path-per-entry model does not express yet.
  - `SubtitleDecoder` pre-parses **text** tracks only. Add the bitmap path: `AVSubtitleRect` (PAL8 pixels + ARGB palette + its own `x/y/w/h`) → `ImageBitmap` into the existing `Cue.bitmap` slot.
  - `SubtitleOverlay` renders the bitmap positioned from the rect's own coordinate space, scaled into the video panel — unlike text, which the overlay places itself. Must honour the §1 zoom/pan. **Not** restyled by §3: these carry their own look.
  - **The architectural risk is memory, not the palette.** Text tracks are pre-parsed eagerly on open because the result is a few hundred KB of strings. Three PGS tracks of a two-hour film are *thousands of decoded images* — the eager model does not transfer. Decide first: lazy decode over a window around `currentPositionMs`, or keep rects compressed and expand on demand. This is the design decision the step opens with.
  - Until this ships a PGS-only file lists the track and renders nothing. Left as is — see the project-context note in the §2.7 preamble.
  - Risk: medium (memory model + rect geometry + palette). Verify: an embedded PGS release and an `.idx`/`.sub` sidecar both render subs that track seek/pause, and memory stays flat across a full film.

**Later phase — §4 Mega-Convenience.** Not part of the base phase, but tracked here so it is not lost:
- **Step 11 — Playback Resume (§4).** Remember last position per file; settings to disable entirely or only for videos under a threshold (e.g. < 10 min). Shares Step 5's per-file record store.
- **Step 12 — Smart Chapters (§4).** Read container chapters (MKV) and draw them as timeline segments.
- **Step 13 — System Media Controls (§4).** OS media-key integration (play/pause/next) — needs a JVM-side native hook; the largest unknown in this document.

**Out of scope for this document** *(deliberately, not forgotten)*:
- **§5 Web Streaming & Network Sources** — experimental; needs its own design doc before any step is written.
- **Full pixel-accurate ASS** (positioning / karaoke / effects) via libass — separate phase, see `subtitle-ass-full-rendering.md`. Step 9 keeps ASS as basic styled text.

**Remaining critical path.** The original risk — switching a stream inside `FFmpegFrameGrabber` — was retired in Steps 2 and 4, so nothing left is architecturally risky. Playback Speed (Step 7, audio resampling) is retired too, leaving **System Media Controls** (Step 13, native integration) as the only genuine unknown. Everything else is incremental. Step 7 did surface one standing constraint worth carrying forward: on 4K HDR the software RGBA path, not the codec, is what caps playback — see the decode-size policy shipped there before proposing anything that raises per-frame pixel cost. **File-size rule (§2):** check `wc -l` before editing; the watch list above is authoritative.

## 3. Subtitle Customization & Advanced Settings
This is the **95% scope** on the current stack (no new native deps). Full pixel-accurate ASS (positioning/karaoke/effects) is a separate deferred phase — see `subtitle-ass-full-rendering.md`.

- **Appearance customization** (opened via the "Customize Subtitles" button, §2.2): font, size, colour, background/box, outline/shadow. Applies to **text subtitles (SRT / basic ASS text)** only — bitmap (PGS/VobSub) and full-ASS tracks carry their own look and are not restyled.
- **External subtitle files** — ✅ **shipped in Step 9**, where the implementation notes live. Sidecar `.srt` / `.ass` / `.ssa` from the video's own folder, auto-matched by filename, plus a native picker for anything else. Parsed **in Kotlin, not through FFmpeg** (charset control: BOM → strict UTF-8 → CP1251) into the same `Cue` model as embedded text tracks (§2.0), and merged into the §2.2 subtitle list ("Off" + embedded + sidecars), with per-file persistence of the path and folder-level persistence of the language (§2.6).
- **Synchronization controls (delay/advance)** *(Step 6)*: offset applied at lookup time (`currentPositionMs − delayMs`) for subtitles, and an equivalent audio offset — no re-decode needed. Adjustable in small steps while playing. Applies to the **embedded** tracks already shipped, so it does not wait on external-file support.

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
