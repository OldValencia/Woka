# Video formats — backlog

Added by the author for the same 1.0.4 pass (2026-10-24). Each block is promoted to a ticket against
the tree as it is then (`ai/docs/conventions/tickets.md`); none was measured yet — the first step of
each is the measurement named in it.

### VF-101 — Duration for the existing formats too
VF-001's duration helper applied to everything already listed: `.vob`, `.ts`/`.m2ts`/`.mts`, `.mpg`,
`.evo`, `.vro`, `.tod` often carry a missing or wrong container duration. First: run VF-001's probe
over samples of these and record `lengthInTime` against the real length. Only the formats that are
wrong get the helper; the call site stays the single `lengthInTime <= 0` (or "known-wrong") check.

### VF-102 — Deinterlacing, with a setting
Interlaced sources — PAL/NTSC `gxf`, `dv`, `vob`, `ts`, CCTV `dav` — comb on motion. The bundled FFmpeg
has `yadif` and `bwdif`. First: whether the player deinterlaces today, and what `field_order` the
samples report. Shape: a `WokaSettings` field (Auto / Off; Auto = only when the stream's
`field_order` is interlaced), a row in the player settings, the filter in the decode path. Measure
the cost at 1080i before choosing `bwdif` over `yadif`.

**Deferred 2026-10-24 by the author** — picked up after VF-001 lands the interlaced containers
(`gxf`, `dav`). Investigated up to the ticket; what is known:

- **The player does not deinterlace today.** No video filter anywhere: `FFmpegFrameFilter` is used
  only for `atempo` (`engine/audio/VideoPlayerAudioTempo.kt`). JavaCV 1.5.14's
  `FrameGrabber.setDeinterlace(true)` is dead — `FFmpegFrameGrabber.processImage` throws *"Cannot
  deinterlace: Functionality moved to FFmpegFrameFilter"* (read from the bytecode). The filter has to
  be ours.
- **It must run on YUV, before scale and RGBA.** `openVideoGrabber` (`decode/VideoPlayerGrabber.kt`)
  asks for `AV_PIX_FMT_RGBA` and `RenderScale.applyTo` sets a downscaled size, so frames arrive
  already scaled — deinterlacing after a vertical downscale mixes the fields first. Shape: grabber on
  the stream's native pixel format, one graph `bwdif=…,scale=W:H,format=rgba` doing deinterlace +
  render-scale + RGBA. Not verified yet: that JavaCV hands planar YUV through untouched when
  `pixelFormat` equals the stream's.
- **Cost, measured** — 1080i25 MPEG-2, 10 s (250 frames / 500 fields), bundled `ffmpeg.exe -threads
  auto -benchmark -f null`, rtime:

  | `-vf` | rtime |
  |---|---|
  | none | 58 ms |
  | `format=rgba` | 68 ms |
  | `yadif` (send_frame, 250 out) | 108 ms |
  | `bwdif` (send_field, 500 out) | 85 ms |
  | `yadif,format=rgba` / `bwdif,format=rgba` | 128 / 122 ms |
  | `format=rgba,yadif,format=rgba` / same with `bwdif` | 327 / 339 ms |

  → **`bwdif`**: faster than `yadif` while emitting twice the frames, and better quality. On RGBA
  input both cost ~3× — the second reason the graph sits before the RGBA conversion.
- **Field order.** `bwdif`/`yadif` `parity=auto` read the frame's TFF flag, and with none fall back to
  bottom-first. `FFmpegFrameFilter` pushes through `av_buffersrc_add_frame_flags` and looks at
  `Frame.opaque`; whether the decoder's per-frame interlaced/TFF flags survive that is **not
  verified**. Until it is, pass `parity` from the stream's `field_order` (`tt`/`tb` → tff, `bb`/`bt` →
  bff).
- **Committed fixtures are all progressive or unknown:** `pal.gxf` progressive (generated so),
  `raw.h264` progressive, `dahua.dav` and `realvideo.rm` `unknown`. A pure `field_order` Auto would
  therefore **never** deinterlace the Dahua export.
- **Making an interlaced fixture.** `interlace`/`tinterlace` are GPL and absent from the LGPL bundle;
  present: `yadif`, `bwdif`, `w3fdif`, `estdif`, `idet`, `weave`, `separatefields`, `setfield`,
  `fieldorder`. This gives `field_order=tt`, 1080i25 (28 MB for 10 s — cut it down for a committed one):
  `ffmpeg -f lavfi -i testsrc2=s=1920x540:r=50:d=10 -vf "weave=first_field=top,setfield=tff"
  -c:v mpeg2video -b:v 25M -flags +ildct+ilme -top 1 i1080.ts`
- **Touch points (lines, 2026-10-24):** `decode/VideoPlayerDecoder.kt` 143 — `grabFrame` `:96`,
  `pacer.convert` `:117`; `sync/VideoPlayerSeek.kt` 78 — `grabFrame` `:30`, `:51`, the landed frame
  bypasses the pacer and needs the filter too, and the graph must be reset on every seek (it holds
  neighbouring fields); `decode/VideoPlayerFramePacer.kt` 142 (`convert` assumes RGBA);
  `decode/VideoPlayerRenderScale.kt` 132; settings row beside `ValueRow` in
  `video/PlayerSettingsPlayback.kt` (97). **`core/data/WokaSettings.kt` 148 and
  `SettingsMutations.kt` 143 need a split before a field goes in.**
- **Proposed scope:** the player only; posters, hover reel and seek preview stay as they are.

**Open, for the author:** Auto/Off as written, or Auto/On/Off so a stream with `unknown` field order
(the `.dav`) can be forced; and `bwdif` per field (50p from 25i — smoother, twice the RGBA
conversions) or per frame (25p).

### VF-103 — Truncated and damaged files
CCTV and camera exports are often cut mid-write — `dahua.dav` is exactly that (17 frames, then a
broken one). The player must play to the cut and stop as at a normal end: no crash, no hang, no
endless spinner. First: open `dahua.dav` and the `broken.mkv` fixture in the player and record what
happens; then fix only what fails. A test on both fixtures.

### VF-104 — "Save as MP4" for formats nothing else opens
A context-menu action on `rm`, `rmvb`, `amv`, `mtv`, `dav`, `bik`, `thp`, `rpl`, `nsv`, `nuv`, `xesc`,
`h264`…: a copy beside the original as MP4 through the bundled FFmpeg — stream copy where MP4 can hold
the codec (H.264 from `dav`/raw `h264`), re-encode to H.264 with `libopenh264` + AAC otherwise. First:
whether the video editor (1.0.6, `pending/video-editor/`) already has a save/encode path to reuse
rather than a second one; the LGPL build has no x264, so openh264 is the only H.264 encoder.

### VF-105 — Rotation from metadata
Phone video stores its orientation as a `rotate` tag or a display matrix. First: whether the player,
the poster and the hover reel honour it today — record per path on a 90° and a 180° sample (generate
with the bundled FFmpeg `-display_rotation`). Fix only the paths that ignore it.
