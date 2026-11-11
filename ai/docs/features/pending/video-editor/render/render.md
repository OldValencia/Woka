# Feature: Video Editor — Render

The other half of `Done ▾`: a real file, that plays edited anywhere, produced by the bundled ffmpeg.
It exists because [`save/`](../save/save.md)'s recipe is only visible inside Woka, and a clip sent to
somebody must not depend on them having it.

Umbrella: [`../video-editor.md`](../video-editor.md) §3.5 and §4.5. Ticket ids: **ME-290…ME-299**.

---

## 1. Status

| Ticket | Title | State |
|---|---|---|
| ME-290 | Probe: can JavaCV copy packets? | **done 2026-11-01** — yes, in process; `libopenh264` for encoding. Measured at the end of this file; probe `jvmTest/probe/PacketCopyProbe.kt` |
| [ME-291](tickets/ME-291-render-video.md) | Render video… — folds in ME-292, ME-293's progress and cancel, and VF-104 | **done 2026-11-01**, build and tests green; **not yet looked at on screen** |

Every other block is in [the shared backlog](../backlog.md) §290.

---

## 2. Scope

**In.** `Render video…` over the whole document: cuts, per-clip speed, freezes, colour, crop, markup
with its timing, audio including a detached or external track. A progress banner that outlives the
editor. A new file, never an overwrite. Also **"this frame as a photo"** and
**`Render GIF…`** (§3.1a), both of which are outputs and have nowhere better to live.

**Out.** Choosing a format, a codec, a container or a bitrate. Presets. Uploading anywhere.

---

## 3. Architecture

### 3.1 It asks nothing

Author's decision, 2026-09-03. One click, then a progress bar. Concretely:

- **The container and codec are the source's**, so a `.mkv` renders to `.mkv` and an HEVC film stays
  HEVC. Nothing is transcoded for the sake of being modern.
- **The resolution is the source's**, unless Resize says otherwise — that is the one control that
  reaches this ([`picture/`](../picture/picture.md) §3.2).
- **The quality target is "as close to the source as the encoder can get"**, not a fixed CRF: the
  author asked for the best possible quality with the file no larger than the original after a light
  edit, and those two are only compatible if the target is measured off the input rather than chosen.
- **A new file beside the source, and never an overwrite.** Not taste — arithmetic. A re-encode is
  lossy, so writing over the source means a second edit encodes an already-encoded picture and the
  losses compound with the pristine original gone. Stills never had this problem because a recipe
  always unfolds from an untouched source, which is exactly why ME-132 deleted `Save a Copy`. **For
  video that reasoning inverts**, and the copy comes back — for this path alone.
  `copyNameBeside`-style non-clobbering naming is the existing behaviour to follow: the rule that a
  copy never silently replaces another file is already in the tree.

### 3.1a A GIF is a third entry, and deliberately the quieter one

Accepted 2026-09-03 with a condition attached: *only for short videos, and as an extra option rather
than the main one.* So `Done ▾` grows a third entry, `Render GIF…`, which

- **appears only when the result is short** — 30 s is the starting threshold and it is a number to
  set when ME-296 is promoted, not one to guess here;
- **says what it costs.** A GIF has no sound, is limited to 256 colours per frame, and for the same
  clip is usually *larger* than a muted `mp4` that also looks better. The honest framing of the
  choice is "a GIF, or just a video with no sound", and the menu should put it that way rather than
  presenting a GIF as the way to get a small file.

It is a render **target** and not a second mechanism: the same document, the same fold, ffmpeg's
palettegen and paletteuse at the end instead of a video encoder. It therefore inherits §3.3's parity
requirement unchanged.

### 3.2 Copy everything, or encode everything — decided by whether any pixel work exists

This is the mechanism behind the size promise, and it is deliberately binary.

| The document holds | What runs | Result |
|---|---|---|
| only cuts, reorders and audio gain | **stream copy** per segment, concatenated | lossless, near-instant, and *smaller* than the source by exactly what was cut |
| any pixel work at all — crop, adjust, filter, markup, speed, freeze | **re-encode**, every segment | larger or smaller depending on the edit, which is what the author expects |

**Why not per-segment hybrid**, which is the obvious better answer: a copied H.264 segment and an
encoded one carry different parameter sets, so splicing them into one track means either a mid-stream
SPS/PPS change or a second sample entry in `stsd`. Some players handle it and some produce a green
frame or refuse the file, and "some" is not a thing to ship. It is a **refinement with a probe in
front of it**, not a v1 mechanism, and it is worth taking later precisely because it would let a
colour tweak on one clip out of five leave the other four untouched.

**A copy needs its cut boundaries on keyframes**, and [`timeline/`](../timeline/timeline.md) §3.3
deliberately does not snap them. So the copy path either moves the boundary to the nearest keyframe —
changing the result the user approved, which is not acceptable — or re-encodes. **The rule is: a
boundary that is not on a keyframe demotes the render to the encode path**, and the banner says the
render is re-encoding. That keeps the strip honest, which was the point of not snapping.

### 3.3 Parity with the preview is a requirement, not an aspiration

The exported file must match what the canvas showed. Two places this is easy to lose:

- **markup timing** — the player's alpha comes from a draw-time computation and the renderer has to
  reproduce it frame by frame. One function in `commonMain`, called by both
  ([`picture/`](../picture/picture.md) §3.3);
- **the fold** — the renderer replays the same `RasterRecipe` at scale 1.0 against the decoded frame,
  which is what `renderAtNativeScale` already does for a still and the reason `drawRecipeImage` is
  shared between the preview and the save path rather than written twice.

### 3.4 In-process, following ME-181

`FfmpegStillEncoder` drives `FFmpegFrameRecorder` in-process against the bundled build, and that is
the precedent — the old plan's "run ffmpeg as a background process" was written when nothing here
encoded at all. **Read ME-181's KDoc before writing a line**: it carries the trap it cost to find,
a converter freed under a `Frame` still being read.

A whole video is not a still, so two things ME-181 never faced arrive here: a decode and an encode
running together for minutes, and the stream copy path, which is `FFmpegFrameRecorder`'s
packet-level API rather than its frame-level one. Whether JavaCV exposes enough of it is the first
measurement in this folder.

### 3.5 Progress, and outliving the editor

`FrameExportStatus` is the pattern: a global holding "what is running", a
`onProgress(done, total)` callback, and `StatusPill`/`StatusBanners` drawing it — so an export and a
save can be up at once without covering each other. A render is longer than a frame export, so it
also needs a **cancel**, which the frame export does not have, and a render still running when the
window closes has to be answered the way ME-125 answered a save in flight.

---

## 4. Open questions

- **Does JavaCV expose packet-level copy?** The gate on §3.2's fast path. If it does not, either the
  copy path uses the ffmpeg binary as a process after all — reversing §3.4 for one path — or every
  render re-encodes and the size promise is only kept by encoding well.
- **What quality target actually lands "no bigger"** on a light edit. Measured off the input's
  bitrate is the plan; whether that is per-stream, per-segment or a global average is a number
  somebody has to try on real files.
- ~~**HDR through a render.**~~ Answered 2026-11-04 — see the decision log: an encode tone maps
  through the canvas's own stage, a copy keeps the source's HDR.
- **Where the rendered file goes** — beside the source, or a folder of its own. Beside is consistent
  with everything else Woka writes.
- **Whether a render can be queued** while another is running. One at a time is the honest first
  answer.

---

## 5. Decision log

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-09-03 | Is a GIF an option, and how loud? | **A third `Done ▾` entry, short results only, saying what it costs** | Author's condition. A muted `mp4` of the same clip is smaller and looks better, so presenting a GIF as the way to a small file would be misleading (§3.1a) |
| 2026-09-03 | What does `Render video…` ask? | **Nothing. Source container, codec and resolution; quality measured off the input** | Author's call. A sheet of choices is the professional-editor interface the whole feature is defined against (§3.1) |
| 2026-09-03 | Overwrite the source? | **Never** | A lossy overwrite is irreversible and compounds on the second edit. This is the one path where a copy is right, and it is why ME-132's reasoning inverts here (§3.1) |
| 2026-09-03 | Per-segment hybrid copy/encode? | **No — all-copy or all-encode, for now** | Splicing a copied and an encoded segment into one track means a parameter-set change some players refuse. It is a refinement with a probe in front of it (§3.2) |
| 2026-09-03 | What happens when a cut is not on a keyframe? | **The whole render demotes to the encode path, and says so** | Moving the boundary would change the result the user approved, which is exactly what not snapping the cut was for (§3.2) |
| 2026-09-03 | Process or in-process ffmpeg? | **In-process, following ME-181 — unless the copy path cannot be reached that way** | ME-181 is a working precedent against the bundled build; the copy path is the one thing that may force the other answer (§3.4) |
| 2026-11-04 | HDR through a render? | **An encode tone maps to SDR with the canvas's own stage; a copy keeps the source's HDR** | Author's call, "as in Woka": `hdrStageFor` in `RasterRecipe.source`, the same shader and `ASSUMED_WHITE_POINT` as the viewer, so the file matches the preview (§3.3). A cuts-only copy carries the stream's colour tags with its packets and stays HDR, losslessly. Test: `VideoRenderHdrTest` |

---

## Measured — ME-290, 2026-11-01

`PacketCopyProbe` (`shared/src/jvmTest/kotlin/com/woka/probe/`, helpers `PacketCopy`, `PacketReadback`,
`EncoderTrial`, `GeneratedClip`), bundled `org.bytedeco:ffmpeg 8.1.2-1.5.14` / JavaCV 1.5.14, this
machine. Every output was reopened with a fresh `FFmpegFrameGrabber` and decoded to the last frame.
`range` = keyframe at or before ⅓ → ⅔; `concat` = [0, ⅓) then [keyframe ≤ ⅔, end), rebased.

**Every committed video has exactly one keyframe**, so on the corpus rows a seek to ⅓ lands back on 0.
The three `gop_*` files are generated by the probe — 640×360, 3 s, a keyframe every 0.5 s, AAC — and
they are the rows that show a mid-file cut.

| File | Keys / packets, scan | Full copy | Range, source stamps | Range, rebased | Concat A+B |
|---|---|---|---|---|---|
| `rot90.mp4` (H.264, rot 90) | 1 / 25, 4 ms | 25 fr, 1000 ms, rot 90 kept | 17 fr, start 0 | 17 fr, start 0 | 34 fr, 1360 ms, decodes |
| `noaudio.mkv` | 1 / 36, 2 ms | 36 fr, start 21 ms (= source) | 25 fr, start 21 ms | 25 fr, start 0 | 49 fr, 4083 ms, decodes |
| `chapters.mkv` (+audio) | 1 / 178, 3 ms | 178 pkts, 36 fr | 121 pkts, start 21 ms | start 0 | 238 pkts, 49 fr, 4112 ms |
| `cut.mkv` (truncated) | 1 / 146, 3 ms | 53 fr, dur 2159 ms (header said 4023) | 53 fr | start 0 | 86 fr, 3456 ms |
| `livephoto.mov` (HEVC, 17/23 pts≠dts) | 1 / 23, 2 ms | 23 fr | 14 fr, start 0 | 14 fr, start 0 | 28 fr, 1866 ms, decodes |
| `gop_openh264.mp4` | 6 / 232 at 0,500…2500 ms, 4 ms | 90 fr, 3000 ms | 30 fr, **first frame 1000 ms** | 30 fr, first 0 ms | **60 fr, 2000 ms**, decodes |
| `gop_mf.mp4` (h264_mf) | 6 / 232, 3 ms | 90 fr | 30 fr, first 1000 ms | 30 fr, first 0 ms | 60 fr, 2000 ms, decodes |
| `gop_openh264.mkv` | 6 / 232, 4 ms | 90 fr | 51 fr, first 1000 ms | 51 fr, first 0 ms | 56 fr, 1895 ms, decodes |

No copy took more than 8 ms, and no output dropped a packet for a non-monotonic dts.
`AV_PKT_FLAG_KEY` and `pts` read on every packet with nothing decoded, and no packet lacked a pts.

**Encoders** (`avcodec_find_encoder_by_name`): present — `libopenh264`, `h264_mf`, `h264_nvenc`,
`h264_qsv`, `hevc_mf`, `hevc_nvenc`, `mpeg4`, `libvpx`, `libvpx-vp9`, `libaom-av1`, `aac`,
`libmp3lame`, `libopus`, `flac`, `pcm_s16le`, `gif`. **Absent: `libx264`, `h264_amf`.**

| 1280×720 mp4 + AAC | 30 frames | 5 s, `videoBitrate` 1M | 5 s, 4M | 5 s, `b=4M` over 1M | 5 s, own mode |
|---|---|---|---|---|---|
| `libopenh264` | opens, 30 fr decode, 4000 kbit/s | 2343 | **3998** | 3998 | `rc_mode=bitrate`: 2350 |
| `h264_mf` | opens, 30 fr decode, 8854 kbit/s | 2121 | 4571 | 4571 | `rate_control=cbr`: 2121 |
| `h264_nvenc` | `avcodec_open2` −40: driver has NVENC API 13.0, build needs 13.1 (driver ≥ 610) | — | — | — | — |
| `h264_qsv` | −22 on yuv420p (wants nv12); −40 with nv12: "Error initializing a MFX session" | — | — | — | — |

The content is a moving diagonal under grain, so 1 Mbit/s is below what either encoder will go on it;
4 Mbit/s is the comparable column.

**Verdict.**

- **In-process packet copy is viable: yes.** `grabber.grabPacket()` into
  `recorder.recordPacket(packet)`, after `recorder.start(grabber.formatContext)`, copied every
  container tried (mp4, mkv, mov; H.264 and HEVC with B-frames). The outputs decode end to end, and
  the mp4 display rotation survives. §3.4 stands, with no process fallback.
- **Timestamps are the caller's job.** `recordPacket` only rescales from the input stream's time
  base to the output's. A range copied as-is keeps the source clock: its first frame sits at
  1000 ms and the container reports that offset as `start_time`. Concatenation works when every
  packet of a segment is shifted by one amount: the segment's first video keyframe **pts** moves to
  the presentation end of what was already written. Rebasing on dts instead left the HEVC clip
  starting at 133 ms.
- **Caveats.** `recordPacket` writes only one video and one audio stream: subtitle and data
  packets are dropped, and from the JavaCV 1.5.14 bytecode a second audio track would be folded into
  the first. That last point was not measured. A cross-container copy (mp4 → mkv) is refused with
  `Tag mp4a incompatible` unless `codec_tag` is cleared; the render keeps the source container, so
  it never hits this.
- **H.264 encoder: `libopenh264`.** It hit the 4 Mbit/s target to within 0.1% over 5 s,
  `videoBitrate` is the control (`b` as a video option overrides it to the same effect), and it
  needs no GPU. `h264_mf` works too, but overshot 4M by 14% and ignored `rate_control`, and it
  depends on the Windows Media Foundation driver. NVENC and QSV do not open on this machine. There is
  no `libx264`, so the CRF-style quality knob the plan imagined does not exist in the build: quality
  is a bitrate.

---

## Measured — ME-295, 2026-11-10

**Probe only; the application is unchanged.** `HybridRenderProbe`, `HybridSegments` and
`HybridReadback` in `shared/src/jvmTest/kotlin/com/woka/probe/`. Run:

```powershell
./gradlew.bat :shared:jvmTest --tests "*HybridRenderProbe" --info
```

Bundled JavaCV 1.5.14 / FFmpeg 8.1.2-1.5.14 performs the changed H.264 segment's encode,
direct packet mux and readback. System FFmpeg 8.0 (GPL build with `libx264` / `libx265`)
generates independent sources and provides reference muxing and frame hashes. It is a probe
dependency, **not a proposed application dependency**. The HEVC reference encoder is therefore
not evidence that the bundled renderer can encode HEVC.

### Method

Four generated six-second, 640×360, 30 fps sources: OpenH264, x264 Baseline without B-frames,
x264 High with three B-frames, and x265 Main 10. Each is split at closed-GOP keyframes into
three two-second segments. Each fixture is checked to decode to exactly 60 frames before use.
The initial segmenter missed the first boundary on B-frame fixtures; allowing 100 ms of timestamp
lead corrected the fixtures before the reported runs. This was not a hybrid failure.

The middle segment's pixels are inverted and encoded with the production renderer's
`libopenh264` settings. The first and third segments are copied. Every route also has an
unchanged copy/copy/copy control. Direct JavaCV mux uses the first stream's global parameters,
rescales timestamps and equalises decode lead across segments; it does not drop packets to
hide non-monotonic DTS.

The oracle is the concatenation of each segment's **independently decoded RGB24 frame hashes**.
The result is decoded sequentially by system FFmpeg and bundled JavaCV; fresh bundled grabbers
also seek to 1.9, 2.0, 2.1, 3.9, 4.0 and 4.1 seconds and compare pixels with independently
decoded segments. This checks both entering and leaving the changed segment.

### Results

All four unchanged controls decode to **180/180 frames, zero frame-hash mismatches** through
direct JavaCV MP4 mux and reference concat to MP4 and MKV.

| Source / changed middle | Direct JavaCV MP4 | Reference concat MP4 / MKV |
|---|---|---|
| OpenH264 / OpenH264 | 180/180, hashes match | 180/180, hashes match |
| x264 Baseline / OpenH264 | 167/180; picture errors and bad seeks | 180/180, hashes match |
| x264 High + B-frames / OpenH264 | 120/180; middle disappears | 180/180, hashes match |
| HEVC Main 10 / OpenH264 | 120/180; middle disappears | 120/180; middle disappears |
| HEVC Main 10 / newly encoded HEVC Main 10 | not tested | 120/180; middle disappears |
| HEVC Main 10 / newly encoded HEVC, each segment first remuxed through Annex B MPEG-TS | not tested | 180/180, hashes match |

H.264 concat uses FFmpeg's automatic `h264_mp4toannexb` conversion, which carries the changed
parameter sets into the stream. That conversion is not automatically applied for HEVC by this
concat route. Explicit `hevc_mp4toannexb` remuxing of **each** segment before concatenation makes
the same-codec HEVC reference pass. Successful reference routes also preserve the tested seek
positions and pixel content. The failed HEVC routes seek from 2.0 seconds straight to 4.0 seconds.

Raw measurements, files, frame hashes and command logs are retained in `shared/build/me295/`;
`results.txt` is the table from the most recent run. Small-fixture H.264 middle encodes take about
0.1–0.2 s and final muxes about 0.01–0.06 s. These are **not** an end-to-end performance comparison:
generation, splitting, reference preparation and verification are outside those timers.

### What this settles

**Hybrid rendering is possible, but adding per-clip branching to the current copy/encode API is
insufficient.** A muxer can accept the file and report six seconds while its entire edited middle
is missing. Parameter-set handling is an observed requirement, not just the hypothetical concern
in §3.2. The working reference paths show that blanket rejection as technically impossible would
also be wrong.

The current production encoder always writes H.264 (`RenderEncoder.kt`); it cannot supply a
same-codec HEVC middle for a copied HEVC track. The reference HEVC solution needs both an HEVC
encoder and stream preparation not implemented by the application. HDR-to-SDR rendering introduces
another incompatibility with an untouched HDR segment; that case was not measured here.

**Recommendation, not an author decision:** defer ME-295 from 1.0.8. If revisited, start with a
restricted same-size H.264 path, explicit parameter-set handling and a whole-file encode fallback.
The current all-copy/all-encode mechanism remains unchanged; ME-295 is not marked implemented.

**Author decision, 2026-11-10:** remove ME-295 from 1.0.8. It remains in the backlog without a
replacement release; this does not cancel the feature permanently.

Limits: generated SDR fixtures only (including 10-bit SDR HEVC), video only, keyframe boundaries,
fixed resolution and frame rate. No audio sync, arbitrary-frame cuts, HDR/Dolby Vision, resized
segments, real 4K films, hardware decoding, VLC or Windows system-player compatibility is established.
Passing FFmpeg-based readers is evidence for the mechanism, not the cross-player shipping gate.
