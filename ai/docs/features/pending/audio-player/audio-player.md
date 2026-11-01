# Audio player — `next_steps.md` §2.3 and the 1.0.5 task

**Goal.** Woka plays music files the way it shows photos and plays video: a file opens in the
context of its folder, plays, and keeps playing while the author browses other folders. One sound
source for the whole app, visible from anywhere as **Now Playing** — a sidebar row, or a floating
bubble when the sidebar is hidden. The same slot leads back to a video in PiP.

**It is a file player, not a music library.** The line was drawn by the author on 2026-11-01 and is
the reason this fits the Linked Folders paradigm (`general_idea.md`) rather than becoming a second
app inside the first.

**In scope.**
- Playback of audio files: title, seek bar, volume, cover, speed, repeat, shuffle.
- Next / previous track = the next audio file of the same folder.
- Tags are **read for display only**: title and artist, and the embedded cover; else `cover.jpg` /
  `folder.jpg` beside the file; else a placeholder.
- One app-wide sound source, a Now Playing row in the sidebar, a floating bubble when it is hidden.
- File associations for every format that measures playable (§5).

**Out of scope — declined by the author, not deferred.**
- A library by tags (artists, albums, genres), tag editing.
- Equalizer, crossfade, ReplayGain, scrobbling — sound settings belong to the system.
- Playlists (`.m3u`, `.pls`, `.cue` …) — already excluded in `next_steps.md` §3.
- Several sources at once. Woka is not a mixer.

---

## 1. Measured — the bundled FFmpeg (2026-11-01)

Bundled FFmpeg 8.1.2 (`org.bytedeco:ffmpeg 8.1.2-1.5.14`), `ffmpeg.exe -buildconf / -demuxers /
-decoders` from the jar (how to unpack it: `video-formats.md` §2).

- **Demuxers present:** `mp3` (MP2/3), `wav`, `w64`, `flac`, `aac`, `asf`, `mov,mp4,m4a…`, `aiff`,
  `ape`, `tta`, `caf`, `ac3`, `eac3`, `dts`, `dtshd`, `mlp`, `truehd`, `ogg`, `amr`, `rm`, `qcp`,
  `oma`, `voc`, `vqf`, `xa` (**Maxis** XA), `psxstr`, `mpc`, `mpc8`, `mpeg` (PS).
- **Decoders present:** `mp1`/`mp2`/`mp3`, `aac`, `alac`, `flac`, `ape`, `tta`, `wmav*`,
  `wmapro`, `wmalossless`, `ac3`, `eac3`, `dca`, `mlp`, `truehd`, `opus`, `vorbis`, `speex`,
  `amrnb`/`amrwb`, `cook`, `qcelp`, `atrac1/3/3+/9`, `twinvq`, `mpc7`/`mpc8`, `adpcm_xa`.
- **No `libopenmpt`, no `libmodplug`.** FFmpeg has no native tracker decoder, so `mod`, `xm`, `it`,
  `s3m`, `669` **cannot be played** with this build → cut (§8).

## 1a. Through the player's grabbers — `AudioFormatProbe`, 2026-11-01 (AP-001)

Committed fixtures in `shared/src/jvmTest/resources/media/audio/`, generated with the bundled
encoders (commands in that folder's `README.md`). "Video grabber" = opened as `openVideoGrabber`
opens it on first open; everything else = opened as `openAudioGrabber` opens it (stereo S16, video
disabled). "Last" = media time of the last decoded chunk; "landed" = media time of the first chunk
after `setTimestamp` to 50 % of the shown length.

| File | Demuxer | Codec | Declared → shown ms | Origin ms | Video grabber | To EOF | 50 % → landed | Tags |
|---|---|---|---|---|---|---|---|---|
| `tone.mp3` | mp3 | mp3float | 2000 → 2000 | 50 | no video stream | 78 chunks, 1987 | 1000 → 1020 | — |
| `tone-cover.mp3` | mp3 | mp3float | 2000 → 2000 | 50 | **stream 1 png 64×64, 1 picture** | 78, 1987 | 1000 → 1020 | title, artist |
| `tone.mp2` | mp3 | mp2 | 2037 → 2037 | 0 | none | 39, 1985 | 1018 → 1044 | — |
| `tone.flac` | flac | flac | 2000 → 2000 | 0 | **stream 1 png, 1 picture** | 20, 1985 | 1000 → 1044 | title, artist |
| `tone.m4a` | mov | aac | 2000 → 2000 | 0 | **stream 1 png, 1 picture** | 44, 1996 | 1000 → 1021 | title, artist |
| `tone.aac` | aac (ADTS) | aac | 2031 → 2031 | 0 | none | 45, 2043 | 1015 → 1021 | — |
| `tone-alac.m4a` | mov | alac | 2000 → 2000 | 0 | none | 11, 1857 | 1000 → 1114 | — |
| `tone.3ga` | mov (3gp) | amrnb | 2000 → 2000 | 0 | none | 101, 1993 | 1000 → 1013 | — |
| `tone.amr` | amr | amrnb | 2085 → 2085 | 0 | none | 101, 2000 | 1042 → 1060 | — |
| `tone.wav` / `tone.w64` | wav / w64 | pcm_s16le | 1000 → 1000 | 0 | none | 11, 928 | 500 → 500 | — |
| `tone.aiff` | aiff | pcm_s16be | 1000 → 1000 | 0 | none | 11, 928 | 500 → 500 | — |
| `tone.caf` | caf | alac | 1000 → 1000 | 0 | none | 6, 928 | 500 → 557 | — |
| `tone.voc` | voc | pcm_u8 | 1000 → 1000 | 0 | none | 45, 991 | 500 → 506 | — |
| `tone.tta` | tta | tta | 3000 → 3000 | 0 | none | 3, 2089 | 1500 → **2089** ¹ | — |
| `tone.ac3` / `tone.eac3` | ac3 / eac3 | ac3 / eac3 | 2016 → 2016 | 0 | none | 63, 1984 | 1008 → 1024 | — |
| `tone.dts` | dts | dca | 501 → 501 | 0 | none | 47, 490 | 250 → 256 | — |
| `tone.mlp` / `tone.thd` | mlp / truehd | mlp / truehd | **0 → 0** ² | 0 | none | 1200, 999 | 0 → 0 | — |
| `tone.ogg` | ogg | vorbis | 2002 → 2002 | 0 | none | 44, 1996 | 1001 → 1021 | title, artist |
| `tone.opus` | ogg | opus | 2006 → 2006 | 0 | none | 101, 1993 | 1003 → 1013 | — |
| `tone.spx` | ogg | speex | 2000 → 2000 | −8 | none | 100, 1980 | 1000 → 1000 | — |
| `tone.wma` | asf | wmav2 | 1999 → 1999 | 0 | none | 43, **0** ³ | 999 → 1021 | title, artist |
| `tone.ra` | rm | real_144 | **0 → 0** | 0 | none | 101, **0** ⁴ | 0 → 0 | — |

¹ A TTA frame is ~1.045 s and a seek lands on the next frame start — seeking is frame-coarse.
² Raw MLP / TrueHD state no length; timestamps are right (last 999 of 1000 ms). `lengthMs` counts
  **video** packets only (`measuredDurationMs`), so it returns 0 here.
³ The last chunk of the WMA (the decoder's flush) carries timestamp 0; every chunk before it is right.
⁴ Every packet's pts is 0 in `ffprobe` too — the bundled **`rm` muxer** wrote it so. Says nothing
  about real RealAudio files; needs a real sample.

**Not measured — no encoder in the bundle, and `samples.ffmpeg.org` refused connections for the
whole session (2026-11-01, `curl` code 28 on every path but one index):** `ape`, `mpc`, `qcp`,
`oma`, `vqf`, `xa`, `aob`, `mp1`, and a real `ra`. Candidate URLs, unverified:
`A-codecs/mp1-sample.mp1`, and the folders `A-codecs/musepack/`, `qcp/`, `ATRAC3/`, `ATRAC3+/`,
`lossless/` (ape), `real/` — download into a folder and point `WOKA_AUDIO_SAMPLES` at it.

## 2. Facts about the current code (2026-11-01)

Paths under `shared/src/`. These are the seams the design rests on; tickets name the rest.

- **The viewer is not a screen.** `Router` knows `Welcome` and `Folder(path, highlightFile,
  openViewerForId, nonce, offerPin)`. The viewer and the editor are overlays inside `FolderScreen`;
  opening a file from elsewhere = `navigateTo(Screen.Folder(parent, openViewerForId = path))`, as
  search hits and `PipManager.closePip` already do.
- **The player is `VideoPlayerState`**, not in Koin. The viewer gets it from
  `PipManager.createOrGetState` (same path → same instance) and gives it back through
  `releaseState` on leaving, which disposes it unless PiP holds it. `PipManager.pipScope` is never
  cancelled — PiP is already an app-level owner of one player.
- **The engine assumes a video stream.** `readStreamHdrInfo` indexes `videoStream` unguarded; the
  loop grabs video only (`grabFrame(false, true, …)`); `currentPositionMs` is written only by the
  presenter from video timestamps; the audio loop gates on `isVideoAtSeekTarget` and stays at most
  200 ms ahead of that clock. An audio-only file today: silence, then EOF.
- **Audio output** is a `SourceDataLine` (S16, stereo downmix), volume as `MASTER_GAIN` plus
  `SoundBoost`, speed through our own `atempo` graph. All of it is reusable as is.
- **Repeat, shuffle, speed, volume already exist** as settings (`RepeatMode { Off, File, Folder }`,
  `shuffle`, `playbackSpeed`, `mediaVolume`). `MediaViewerPlaylist.nextPath` walks `MediaItem.Video`
  only.
- **Media keys** go through an AWT listener and **work only while a Woka window has focus**. Windows
  SMTC was built and cut (`implemented/media-viewer/system-media-controls.md`, "Why Now Playing was
  dropped"). So music playing in a minimised Woka **cannot be paused from the keyboard** (§7).
- **PiP window** is a separate `Window`; `toFront()` is called once on open, and nothing outside the
  window can call it again.
- **Sidebar** hides completely (`sidebarVisible`, width animates 236 → 0 dp over 250 ms).
  `Link Folder…` is the foot row (`SidebarFoot`). The root `Box` in `App.kt` draws slots above
  the sidebar and all content (`StatusBanners`, `SettingsOverlay`); a bubble goes there.
- **`MediaKind` has two kinds**; 4 exhaustive `when`s break on a third, `MediaItem.Video` is matched
  in 19 main files, `MediaItem.Image` in 9. The grid filter is `All / Photos / Videos`.

## 3. Architecture

### 3.1 A third media kind

`MediaKind.Audio`, `MediaItem.Audio`, `AUDIO_EXTENSIONS`. **The kind comes from the extension**,
as for image and video: `.ogg` is audio even when it holds Theora. Sniffing the streams to route a
file would be a guess per file, and a Theora `.ogg` still plays — its sound.

The editor rejects audio (`ToolManifest.supports` stays `Image, Video`; the `Edit` entry is not
shown). The grid filter grows `Music`.

### 3.2 The engine plays a file with no picture

The same `VideoPlayerState`, not a second player — decode, output, volume, boost, tempo, seek and
the system-controls bridge are all there. What changes is **who owns the clock**: with no video
stream the audio writer advances `currentPositionMs` from the samples it has handed to the line,
and the video loop does not run. The presenter's video-driven clock stays exactly as it is for
video.

**Measured (AP-001, §1a): an embedded cover arrives as a video stream with exactly one picture** —
`png` stream 1 in `.mp3`, `.flac` and `.m4a`, found by the grabber the player opens first. Left as
is, the video loop would draw the cover, hit video EOF one frame later and end playback after a
moment of sound. So the player treats a stream flagged `attached_pic` as **the cover, not the
picture**: read once for the viewer and the tile, never the clock. A file whose only video stream
is a cover is an audio file to the engine, whatever its extension.

**The clock counts samples, not timestamps.** The audio writer advances the position by what it
has handed to the line. AP-001 found chunk timestamps that cannot be trusted at the edges — the
WMA flush chunk at 0 (§1a ³) — while the sample count is exact by construction.

**Length for raw streams.** MLP and TrueHD state none (§1a ²). `measuredDurationMs` counts video
packets; for a file with no picture it must count audio packets the same way, so the seek bar has
a length. Same rule as VF-001 for video: count, never estimate.

**Rejected: a separate `AudioPlayerState`.** It would duplicate the seek, tempo, repeat and EOF
paths that VF-001…VF-103 just made correct, and every later fix would be made twice.

### 3.3 One sound source — the Now Playing slot

One app-level owner (Koin `single`) holds **at most one** playing thing: an audio player, or the
video player PiP holds. Rules, from the author:

- Starting **any** playback with sound — another audio file, a video in the viewer — stops what the
  slot holds and empties it. No mixing, no stack, no "resume the music after the video".
- An **audio** player is put in the slot when it starts and **survives leaving the viewer**: the
  viewer's `releaseState` does not dispose a player the slot owns. A **video** player is in the slot
  only while it is in PiP — a video closed in the viewer stops, as today.
- Opening the viewer on the file the slot holds reuses that player — the same rule
  `createOrGetState` already applies to PiP.
- The window's close button quits as before; minimising does not stop playback.

PiP's own slot (`PipManager.videoState`) is folded into this owner, not kept beside it — two owners
of "the player that outlives its screen" is how the conflict dialog and the slot would disagree.

### 3.4 The viewer for an audio file

The viewer overlay, not a new screen: the picture area shows the cover (or placeholder) with title
and artist; the bottom bar is the video bar minus picture-only controls (fullscreen, subtitles,
video tracks, deinterlace, ambient fill). Arrows and auto-advance walk **audio files only**; repeat
and shuffle are the existing settings. Audio does not record resume points or "watched" marks.
Layout: the mockup (§6).

### 3.5 Now Playing — row and bubble

- **Row** (sidebar open): above `Link Folder…`. Cover / poster, name, play/pause. Shown only while
  the slot holds something.
- **Click:** audio → navigate to the file's folder with the viewer open on it; PiP video → bring the
  PiP window to the front (a `toFront` request PiP can observe; it cannot be called from outside).
- **Bubble** (sidebar hidden): cover + play/pause, in one of the **four corners** of the window,
  dragged between them and snapping to the nearest; corner persisted, default bottom-left. Hover
  expands it into a capsule with the name, play/pause and a `×` that stops playback and empties the
  slot. Hiding the sidebar flies the row's content to the corner, showing it flies it back.
- Geometry, states and timings: `ai/mockups/design/now-playing.html`.
- **Bubble hidden:** slot empty; the viewer is open on the slot's file; fullscreen video; the editor
  is open — and back as soon as the editor closes.

## 4. Formats — candidates for the probe

From `next_steps.md` §2.3, after the cuts in §8. Every one is kept or cut by AP-001's measurement on
a real or generated sample, as VF-001 did.

| Group | Extensions |
|---|---|
| Common / lossless | `mp3`, `wav`, `wave`, `flac`, `aac`, `wma`, `m4a`, `m4b`, `m4r`, `aiff`, `aif`, `ape`, `tta`, `w64`, `caf` |
| Multichannel | `ac3`, `eac3`, `a52`, `dts`, `mlp`, `aob` |
| Speech / streaming | `opus`, `ogg`, `oga`, `spx`, `amr`, `3ga`, `ra`, `qcp`, `oma` |
| Vintage | `voc`, `vqf`, `xa`, `mp1`, `mpga`, `mpc` |

Questions only the probe answers: `xa` (FFmpeg's `xa` is Maxis; PlayStation XA may need
`psxstr`), `aob` (DVD-Audio, often CPPM-encrypted), `oma` (ATRAC, may be encrypted), `ra` (old
RealAudio 1/2 codecs).

## 5. Status

**Build order (the author, 2026-11-01): Now Playing first, the audio player after.** The slot,
the row and the bubble ship with PiP video as their only source — a visible slice on its own, since
PiP is the thing that is hard to find today — and audio joins the slot when it can play.

| Ticket | Title | Status |
|---|---|---|
| mockup | [`now-playing.html`](../../../mockups/design/now-playing.html) — row, bubble, capsule, drag, flight | **accepted** 2026-11-01 — the spec for AP-004…AP-006 |
| [AP-001](tickets/AP-001-audio-format-probe.md) | Probe: every candidate format through the player's grabber | **measured** 2026-11-01 for every encodable format (§1a); open: 9 formats need downloaded samples — nothing on screen |
| [AP-004](tickets/AP-004-now-playing-row.md) | Now Playing: the slot and its sidebar row, PiP video as the source (absorbs AP-005) | **done** 2026-11-01 — `:shared:build` green; checked by the author in the running app |
| AP-005 | merged into AP-004 | retired |
| [AP-006](tickets/AP-006-now-playing-bubble.md) | The bubble when the sidebar is hidden | **done** 2026-11-01 — checked by the author in the running app |
| [AP-008](tickets/AP-008-now-playing-flight.md) | The cover flies between row and corner | **done** 2026-11-01 — checked by the author in the running app |
| AP-007 | SMTC, second attempt (media keys with Woka in the background) | backlog |
| mockup | [`audio-player.html`](../../../mockups/design/audio-player.html) — the viewer and the grid tile for an audio file | **accepted** 2026-11-01 — the spec for AP-003 |
| [AP-003](tickets/AP-003-audio-plays-in-the-viewer.md) | An audio file opens in the viewer, plays, and carries on in Now Playing (absorbs AP-002) | **built** 2026-11-01, `:shared:build` green but for the known lock race — open: the by-hand check |
| AP-002 | merged into AP-003 | retired |
| AP-009 | `Music` filter, file associations | backlog, after AP-003 |

## 6. Mockups

New surfaces get a mockup before their UI ticket (`feature-docs.md` §6), and there are two:

- `ai/mockups/design/now-playing.html` — the row, the bubble, the capsule, the four corners, drag
  and snap, the flight between row and corner, visibility per screen; sources audio, audio without
  a cover, PiP video, empty; both themes.
- `ai/mockups/design/audio-player.html` — the viewer for an audio file. Written when AP-003 is next.

## 7. Open

- **Media keys in the background — reopened** by the author (AP-007). Today the keys work only with
  Woka focused, so a minimised Woka playing music cannot be paused from the keyboard. Read
  `implemented/media-viewer/system-media-controls.md`, "Why Now Playing was dropped", first.

## 8. Decision log

| Date | Question | Choice | Why |
|---|---|---|---|
| 2026-11-01 | Audio at all, in an app named after an eye? | yes, as a file player | completes the VLC / WMP replacement in `next_steps.md`; the engine already decodes and outputs sound |
| 2026-11-01 | Library, tags, equalizer | none; tags read for display only | the author: a music library is a second app with its own navigation; sound settings are the system's |
| 2026-11-01 | Other media types after audio | none planned | documents and PDF are already out; 3D and archives are a different domain |
| 2026-11-01 | How many sources at once | one; any new playback with sound stops the current one | the author: "это не миксер" |
| 2026-11-01 | Clicking Now Playing for a PiP video | brings the PiP window to the front | the author; the video was sent to PiP deliberately |
| 2026-11-01 | Row content | cover, name, play/pause | the author; seek and the rest stay in the viewer |
| 2026-11-01 | Where Now Playing lives with the sidebar hidden | a floating bubble, flies to and from the row | the author; a sidebar that hides completely would take the row with it |
| 2026-11-01 | Bubble position | four corners, snap; persisted | the Apple PiP idiom; a free position overlaps content and has to be re-clamped on every resize |
| 2026-11-01 | Name on the bubble | a capsule on hover | the author; a hidden sidebar means the author wants the room |
| 2026-11-01 | Bubble in the editor | hidden, back on leaving it | the author; the canvas needs every corner |
| 2026-11-01 | Tracker modules `mod` `xm` `it` `s3m` `669` | cut | the bundled FFmpeg has no `libopenmpt`/`libmodplug` (`-buildconf`) and no native decoder |
| 2026-11-01 | `alac` | not an extension | it is a codec, played inside `.m4a` / `.caf` |
| 2026-11-01 | `m4p` | cut | FairPlay DRM; FFmpeg cannot decrypt it |
| 2026-11-01 | `ram` | cut | a RealAudio metafile — text with URLs, i.e. a playlist (§3 of `next_steps.md`) |
| 2026-11-01 | Audio kind by extension or by content | extension | content sniffing is a per-file guess; a Theora `.ogg` still plays its sound |
| 2026-11-01 | A second player class for audio | no — `VideoPlayerState` without a picture | one seek/tempo/EOF path, already proven by VF-001…VF-103 |
| 2026-11-01 | PiP slot and Now Playing slot | one owner | two owners of the outliving player would disagree |
| 2026-11-01 | Dismissing a paused track | `×` in the capsule; stops and empties the slot | the author |
| 2026-11-01 | Build order | Now Playing (slot, row, bubble) before the audio player | the author; it already pays for PiP video, which is hard to find today |
| 2026-11-01 | Media keys with Woka unfocused | reopened as AP-007 | the author; music in a minimised window makes the old limitation matter |
| 2026-11-01 | Kept on measurement (AP-001) | `mp3`, `mpga`, `wav`, `wave`, `flac`, `aac`, `wma`, `m4a`, `m4b`, `m4r`, `aiff`, `aif`, `tta`, `w64`, `caf`, `ac3`, `a52`, `eac3`, `dts`, `opus`, `ogg`, `oga`, `spx`, `amr`, `3ga`, `voc` | each decodes to its end and seeks onto the target through the player's grabbers (§1a); `mpga`, `wave`, `m4b`, `m4r`, `aif`, `a52`, `oga` share a demuxer with a measured fixture |
| 2026-11-01 | `mlp` (TrueHD) | kept, length counted from audio packets | decodes and timestamps are right; only the stated length is missing (§1a ²) |
| 2026-11-01 | `attached_pic` | the cover, never the picture or the clock | measured as a one-picture video stream; treated as video it ends playback after one frame |
| 2026-11-01 | Audio clock source | samples handed to the line | chunk timestamps fail at the edges (WMA flush at 0); the count cannot |
| 2026-11-01 | `now-playing.html` review | accepted: progress ring, `×` in row and capsule, at-rest glyph; row loses its hover fill | the author; the row already sits in its own block |
| 2026-11-01 | `audio-player.html` review | side arrows only; codec/rate/length in the Info panel, not under the title; paused cover shrinks and dims slightly; a click on the cover toggles play | the author |
| 2026-11-01 | Audio clock source — revisited | a wall clock × speed, gated on the first fed chunk, EOF from the audio loop; **replaces** "samples handed to the line" | read against the code: the audio loop already follows an external clock (200 ms lead, 400 ms resync, seek gate); counting samples would rewrite the writer and its `atempo` accounting |
| 2026-11-01 | One mockup or two | two: `now-playing.html` now, `audio-player.html` before AP-003 | Now Playing is built first and is not audio-specific |
| 2026-11-01 | Who walks the folder for music | `AudioSession`, open viewer or not; the viewer follows the track it was on | the author: a track that ends with the viewer closed must not stop the music |
| 2026-11-01 | Repeat Off for music | plays the folder through to its end, then stops (films still stop after themselves) | the author |
| 2026-11-01 | Length on the grid tile | none; the caption reads `Audio · <size>` | the author; a length means opening every file in the folder |
| 2026-11-01 | A video opened over playing music | asks first, as a second video over PiP does ("Music Is Playing") | the author |
| 2026-11-01 | Clock arithmetic | nanoseconds carried between ticks | truncating to ms ran the clock 2.2 % slow; the lead throttle starved the line and a song broke up (measured: drift −336 ms over 15 s → −18 ms, line underruns 4 → 0) |
| 2026-11-01 | Now Playing block height | 48 dp: 4 dp above and below a 40 dp row (was 8 + 44 + 8) | the author: the block read too tall |
| 2026-11-01 | A track opened over a video in PiP | asks first, the PiP dialog's other half ("Close it and play this track?") | the author |
| 2026-11-01 | Fit/Fill and zoom for a track | hidden; Info's divider goes with them | the author: the cover is not a picture to size |
| 2026-11-01 | The line under a track's title | the artist alone; album, year and genre move to Info's `Track` section, beside codec, channels, sample rate, bitrate and length | the author: "artist — album" read as unfamiliar; Info's audio rows were pulled forward from AP-009 |
