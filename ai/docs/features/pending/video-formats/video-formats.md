# Video formats — the rest of `next_steps.md` §2.2 (1.0.4)

**Goal.** Every container below plays in Woka's player, draws a grid poster and gets a file
association — in **one pass**, [VF-001](tickets/VF-001-all-video-formats.md), the way photo-formats
PF-003 landed. Total went from 108 to **122** formats (35 → 49 video): 17 were planned, and
raw H.264 (`h264`, `264`) and `nuv` were cut on measurement (§6).

**This file carries the whole investigation** (2026-10-24): a new session implements VF-001 from it
without re-measuring what is recorded here. §5 is the set of facts about the current code it needs.

**Out of scope.**
- `r3d` (REDCODE) — FFmpeg reaches only the raw JPEG 2000 Bayer stream; a real decode needs RED's
  proprietary SDK.
- Raw HEVC (`hevc`/`h265`/`265`) — the bundle has no HEVC encoder for a fixture, the one sample came
  from an unknown source, and the author found it hard to find at all. Dropped.
- Bink 2 — no decoder outside RAD's SDK. `.bik` is Bink 1 only; a Bink 2 file fails to the placeholder.
- Raw H.264 (`h264`, `264`) — cut 2026-11-01: every frame reaches JavaCV with timestamp 0, so there
  is no timeline to pace, seek or show (§1).
- `nuv` — cut 2026-11-01: on both RTjpeg samples FFmpeg itself stops after 4–5 s of a 24–33 s file
  (§1); only an MPEG-4 `.nuv` plays through.
- Editing these in place — they reach the video editor through the existing "Convert first" path.

---

## 1. What the bundled FFmpeg does with each — measured

Bundled FFmpeg 8.1.2 (`org.bytedeco:ffmpeg 8.1.2-1.5.14`). Every container has a demuxer and every
stream a decoder: **no new dependency.** "Frame" = `ffmpeg -i f -frames:v 1 -f null -` succeeded.

| Extensions | Demuxer / codecs | Sample measured | Frame | Duration |
|---|---|---|---|---|
| `xesc` | mts2 (Expression Encoder Screen Capture); container not recorded | `ScreenCapture.xesc` 1172×852 | ✅ | 8.7 s |
| `rpl` | `rpl` ARMovie / `escape130`, PCM | `landing.rpl` 320×160 | ✅ | 23.9 s |
| `bik` | `bink` / `binkvideo`, `binkaudio_dct` | `logo_legal.bik` 640×480; `blank.bik` | ✅ Bink 1 | 5 s / 0.04 s |
| `thp` | `thp` / `thp` (MJPEG), `adpcm_thp` | `losegame.thp` 640×480 | ✅ video | 8.0 s |
| `nsv` | `nsv` / `vp6`, `aac` | `64vp6.nsv` 320×240 | ✅ | **N/A** → counted 60 s |
| `mtv` | `mtv` / `rawvideo`, `mp3` | `comedian_auto.mtv` 96×64 | ✅ | **N/A** → counted 93 s |
| `amv` | `avi` / `amv`, `adpcm_ima_amv` | `MTV_medium_res_…amv` 128×96 | ✅ | **N/A** → counted 129 s |
| `rm` `rmvb` | `rm` / `rv10…rv60`, `cook`, `atrac3`, `sipr`, `ralf` | generated `realvideo.rm` (rv20); `Sample.rmvb` (rv40 + cook) 640×272 | ✅ | 2 s / 150.5 s |
| `gxf` | `gxf` / `mpeg2video`, `dv` | generated `pal.gxf` | ✅ | 2 s |
| `ogv` `ogm` `ogx` | `ogg` / `theora`, `vorbis`, `opus`, `flac`, `speex` | Theora `.ogg` 640×256; `packetsize0.ogv` 720×576 (origin 59 s); `bgc.sub.dub.ogm` 640×480 + Vorbis; `intro_ogg.ogm` 352×240 + Vorbis. No `.ogx` sample — same demuxer | ✅ | 1.9 / 21.1 / 60.0 / 22.7 s |
| `dav` | `dhav` (file starts `DAHUA`) / `h264`, `hevc` | the author's Dahua export, 1920×1080, 440 MB; `dahua.dav` | ✅ | `dhav`'s own figure wrong on the fixture (1000 ms of 1939) → always counted |
| ~~`h264` `264`~~ | raw H.264 elementary stream | `D1M1.h264`, `raw.h264`, `raw-10bit.h264` | ✅ decode | **cut** — no timestamps reach JavaCV |
| ~~`nuv`~~ | `nuv` / `nuv` (RTjpeg), PCM | `sample.nuv`, `Today.nuv` (RTjpeg); `mythtvexample.nuv` (MPEG-4) | ✅ | **cut** — RTjpeg stops at 5.4 s of 32.9 / 3.9 s of 24.3, CLI too |
| `asf` | already listed in `MediaKind` | the author's 1080p H.264 `.asf` | ✅ | 625 s — nothing to do |

**Through the player's grabber — `VideoFormatProbe`, 2026-11-01** (seek = `grabImageAt` to 50 % of
the shown length; "right picture" = the same pixels as decoding from the start to that time):

| Sample | Shown length, ms | Origin | Seek 50 % → landed, ms | Right picture | Audio | Audio seek |
|---|---|---|---|---|---|---|
| `dahua.dav` | 1939 (counted; `dhav` says 1000) | 1455778979 s | 969 → 1000 | — ¹ | none | — |
| `blank.bik` | 40 | 0 | one frame | — | `binkaudio_dct` | ok |
| `realvideo.rm` | 2000 | 0 | 1000 → 1000 | ✅ | none | — |
| `pal.gxf` | 2000 | 0 | 1000 → 1480 | ✅ | none | — |
| `s.amv` | 128 999 (counted) | 0 | 64 499 → 64 499 | ✅ | `adpcm_ima_amv` | ok |
| `s.bik` | 5000 | 0 | 2500 → 2500 | ✅ | none | — |
| `s.mtv` | 93 000 (counted) | — | 46 500 → 46 500 | ✅ ² | `mp3float` | throws ² |
| `s.nsv` | 60 000 (counted) | 0 | 30 000 → 30 000 | ✅ ² | `aac` | throws ² |
| `s.ogm` / `s2.ogm` | 60 009 / 22 674 | 0 | 30 004 → 30 029 / 11 337 → 11 344 | ✅ | `vorbis` | ok |
| `s.ogv` / `packetsize0.ogv` | 1920 / 21 080 | 0 / 59 s | 960 → 960 / 10 540 → 10 560 | ✅ | none | — |
| `s.rmvb` | 150 464 | 0 | 75 232 → 75 265 | ✅ | `cook` | ok |
| `s.rpl` | 23 933 | 0 | 11 966 → 12 000 | ✅ ² | `pcm_s16le` | throws ² |
| `s.thp` | 8048 | — | 4024 → 4037 | ✅ ² | `adpcm_thp` ³ | throws ² |
| `s.xesc` | 8671 | 0 | 4335 → 4383 | ✅ | none | — |
| generated `.ts` (control) | 4000 | 1.44 s | 2000 → 2400 | ✅ | none | — |
| generated 387 MB `.amv` | 900 000 (counted in 82 ms) | 0 | 450 000 → 450 000 ⁴ | ✅ | `adpcm_ima_amv` | 40 000 ⁴ |

¹ The 300 KB head holds 17 frames with timestamps 0, 58, 1000, 1086… and probably one keyframe; a
seek lands on a frame decoded without its reference. Needs the full export to judge.
² `setTimestamp` throws and leaves the grabber at EOF; the picture comes from the restart-and-walk
fallback (§3).
³ `adpcm_thp` logs `invalid number of samples in packet` on all 241 packets yet yields 257 553
samples (8.05 s at 32 kHz). RMS −5.5 dBFS, zero-crossing rate 0.19 against 0.17 for a real PCM
track, sample-to-sample step 0.90 × RMS against 0.31. The spectrogram settles it: flat broadband
noise at full scale, where `landing.rpl` shows harmonics. **Broken in this sample**; `.thp` stays
(§6). The stream exists, so the player plays the noise with the ordinary speaker icon.
⁴ JavaCV's `setTimestamp` walks forward 500 frames at most: the `.amv` demuxer seeks to the start, so
the seek returns at 20 s (video) / 40 s (audio). The walk on to the target is ours (§3).

## 2. Samples

**Committed** (`shared/src/jvmTest/resources/media/`, commands in its `README.md`): `dahua.dav`
(300 KB head of the 440 MB export — 17 frames decode), `raw.h264` (libopenh264, 50 frames 160×120),
`raw-10bit.h264`, `blank.bik`, `realvideo.rm`, `pal.gxf`, and two stand-ins for behaviour rather than
formats: `noseek.roq` (a container whose seek throws) and `nolength.amv` (no stated length).

**Third-party, not committed** — download into a scratch folder for the probe (`WOKA_VIDEO_SAMPLES`):

```sh
B=https://samples.ffmpeg.org
curl -o s.xesc "$B/V-codecs/MTS2/ScreenCapture.xesc"        # 613 KB
curl -o s.rpl  "$B/game-formats/rpl/landing.rpl"            # 4.4 MB
curl -o s.bik  "$B/game-formats/bink/logo_legal.bik"        # 396 KB
curl -o s.thp  "$B/game-formats/thp/losegame.thp"           # 6.8 MB
curl -o s.nsv  "$B/nsv/64vp6.nsv"                           # 449 KB
curl -o s.mtv  "$B/mtv/comedian_auto.mtv"                   # 19.8 MB
curl -o s.amv  "$B/amv/MTV_medium_res_128x96_Over_The_Hedge_Trailer.amv"  # 4.9 MB
curl -o s.ogv  "$B/ogg/Theora/theora-a4_v6-k250-s0.ogg"     # 369 KB
curl -o s.ogv2.ogv "$B/ogg/packetsize0.ogv"                 # 5.7 MB, origin 59 s
curl -o s.ogm  "$B/ogg/bgc.sub.dub.ogm"                     # 12.2 MB
curl -o s2.ogm "$B/ogg/DShow-NEW/intro_ogg.ogm"             # 1.6 MB
curl -o s.rmvb "$B/real/VC-RV40/Sample.rmvb"                # 15 MB
```

**CLI tools without installing anything:** the bundled `ffmpeg.exe`/`ffprobe.exe` are inside
`~/.gradle/caches/modules-2/files-2.1/org.bytedeco/ffmpeg/8.1.2-1.5.14/*/ffmpeg-8.1.2-1.5.14-windows-x86_64.jar`
— unzip it to a scratch dir, they sit in `org/bytedeco/ffmpeg/windows-x86_64/`.

## 3. What VF-001 built — the timeline, not just the length

The plan was one duration helper. The probe (§1) found three faults the length alone would not
have fixed, and the author chose the fix for each (2026-11-01, §6):

- **Counted length** — `video/engine/probe/ContainerDuration.kt`, `measuredDurationMs`. The last
  video packet's `pts + duration` minus the stream's start, read by byte-seeking to the last 4 MB
  and demuxing without decoding. `avi` (`.amv`) and `mtv` number their packets as they read, so a
  byte seek gives the wrong end (93 s read as 19.7 s); they are walked whole — 82 ms for 387 MB.
  `dhav` declares no streams until its first packet, so the first video packet is read, not looked up.
- **One length for every path** — `sync/MediaTimeline.kt`, `lengthMs`: the container's figure,
  counted when there is none or the demuxer is `dhav`. Read by the player, the poster, the hover reel
  and the info panel.
- **The media timeline** — `originUs` / `mediaTimeUs`. JavaCV's `timestamp` is the raw presentation
  time and its `setTimestamp` adds `start_time`, so every position the player read and every seek it
  checked was off by the container's start — 46 years on a `.dav`, 1.44 s on a generated `.ts`,
  where each seek overshot, restarted the file and landed 1.44 s early. The player's video, audio and
  seek reads, and embedded subtitle cues, now subtract it. Resume points saved for such files before
  this are off by the same amount.
- **Seeking a container that cannot** — `grabImageAt` (poster, hover reel, seek preview) and
  `videoSeekTo`/`audioSeekTo` (player): when no picture follows the seek, restart and decode forward;
  when the seek stopped short (`.amv`, ⁴ in §1), walk on. A poster with every candidate past the end
  (`blank.bik`) takes the first frame.

## 4. Status

| Ticket | Title | Status |
|---|---|---|
| [VF-001](tickets/VF-001-all-video-formats.md) | Every remaining video format, one pass | **built** 2026-11-01 — open: the 440 MB `.dav` |
| [VF-002](tickets/VF-002-associate-without-settings.md) | "Associate Formats" stops opening Windows Settings | **done** 2026-10-24 |
| VF-101 | Duration for the existing formats too | **closed** 2026-11-01 — measured, nothing to build (§7) |
| [VF-102](tickets/VF-102-deinterlace-in-the-player.md) | Deinterlace interlaced video in the player | **built** 2026-11-01 |
| [VF-106](tickets/VF-106-deinterlace-row.md) | "Deinterlace: Auto / Off" in the player's ⚙ popover | **built** 2026-11-01 |
| [VF-103](tickets/VF-103-truncated-files.md) | Truncated and damaged files | **done** 2026-11-01 |
| VF-104 | "Save as MP4" for formats nothing else opens | **moved** 2026-11-01 to `video-editor/backlog.md` (§290 Render) |
| [VF-105](tickets/VF-105-rotation-from-metadata.md) | Rotation from metadata | **done** 2026-11-01 |

## 5. Facts about the current code (2026-10-24)

- **Media list:** `core/data/MediaKind.kt`, `VIDEO_EXTENSIONS` (49 after VF-001). `ALL_SUPPORTED_MEDIA_EXTENSIONS`
  feeds the Windows registry associations; `WindowsFileAssociationsTest` asserts the total (122).
- **Installer associations:** `desktopApp/build.gradle.kts`, `mediaAssociations` — one
  `Triple(mime, ext, description)` per extension; photo-formats kept it equal to `MediaKind` (108/108).
- **Duration reads** (`grabber.lengthInTime`, µs): `video/engine/decode/VideoPlayerDecoder.kt:38`
  (and the re-read on the first frame at `:106`), `video/engine/VideoThumbnailExtractor.kt:79`,
  `video/engine/preview/HoverExcerptReel.kt:60`, `FileInfoPanelData.kt:50` (all under
  `shared/src/jvmMain/kotlin/com/woka/feature/viewer/`). Lengths: 143, 135, 101, 114 lines —
  `VideoPlayerDecoder.kt` has 7 lines of room before the 150 cap.
- **Existing probes to copy:** `jvmTest/.../probe/FormatUnlockProbe.kt` (writes candidates with the
  bundled FFmpeg, reads them back), `probe/RawFormatProbe.kt` (reads a folder from an env var, prints a
  table, skips without it).
- **Native access:** the test task already passes `--enable-native-access=ALL-UNNAMED`, and the app's
  launcher does too.
- **Known flaky test:** `DirectoryWatchTest`'s sub-folder case fails intermittently on Windows; it is
  not a regression.

## 6. Decision log

| Date | Question | Choice | Why |
|---|---|---|---|
| 2026-10-24 | `r3d` | excluded | only the raw Bayer JPEG 2000 stream is reachable; RED SDK is proprietary |
| 2026-10-24 | One ticket or several? | one | the author wants a single pass; no new dependency, so nothing to stage |
| 2026-10-24 | Unknown duration: estimate or count? | count — byte-seek to the tail, last packet | an estimate is a guess the tests cannot pin; counting is exact and the tail read is cheap |
| 2026-10-24 | `dav` | in, from the author's own export | it is `dhav` (starts `DAHUA`) and decodes 1080p H.264; a 300 KB head is the fixture |
| 2026-10-24 | Raw `.h264`/`.264` | in | the author supplied samples; FFmpeg decodes them, 10-bit included; `libopenh264` makes a clean fixture |
| 2026-11-01 | Seeking where the container refuses | restart and decode forward; a format that still cannot seek loses its seek bar; one that cannot be read at all is cut | the author: exotic formats that cannot be read are dropped, not worked around. Every kept format seeks onto the right picture (§1) |
| 2026-11-01 | Raw `.h264`/`.264` | **cut** (reverses 2026-10-24) | every frame reaches JavaCV with timestamp 0 — nothing to pace, seek or show a length against |
| 2026-11-01 | `nuv` | **cut** | FFmpeg reads 4–5 s of the two RTjpeg samples and stops, CLI included; seeking lands at EOF |
| 2026-11-01 | A container's start offset | subtract it everywhere the player reads time, for every format | `.dav` is unplayable without it; `.ts`/`.m2ts`/`.vob` with a start had every seek overshoot and land early. The author chose all formats over `.dav` only |
| 2026-11-01 | `dhav`'s own length | ignored, always counted | 1000 ms on a fixture whose packets run 1939 ms |
| 2026-11-01 | `.thp` with noise for audio | kept, no special case | the one sample is broken, not the format; muting a stream that exists would be a heuristic proven on one file |
| 2026-11-01 | `BufferUnderflowException` opening `.thp` | `toPcmBytes` reads `remaining()`, not `capacity()` | JavaCV reuses one sample buffer; a shorter frame leaves `limit` below `capacity` — also hit `.amv` and `.rpl` (`PcmBytesTest`) |
| 2026-11-01 | VF-101: duration for `vob`/`ts`/`mpg`/`evo`/`mod` | closed, no code | 19 real samples: the stated length is right everywhere (§7); a missing one already falls to `lengthMs`'s count for every format |
| 2026-11-01 | Deinterlace: what decides | the decoded frame's interlaced flag, not the stream's `field_order` | `field_order` names the wrong parity on 2 of 16 samples, calls a film interlaced, and says `unknown` on an interlaced one (§7) |
| 2026-11-01 | Deinterlace: per frame or per field | per frame, 25i → 25p | the author; keeps one grab = one picture through pacer and seek. 50p is a later ticket if wanted |
| 2026-11-01 | VF-103: a cut `.mp4` with no index | a notice, no recovery | nothing to rebuild the index from without a reference file; every other cut file already ends cleanly (VF-103) |
| 2026-11-01 | VF-103: a cut `.mkv` keeps its stated length | left as is | the author: cosmetic — the bar jumps to the end at the cut |
| 2026-11-01 | VF-104 | moved to the video editor (1.0.6) | the author: it needs the encoder that work builds anyway |
| 2026-11-01 | VF-105: where the turn is applied | the player's picture graph; the previews turn their bitmap | upright from the decoder means zoom, pan and fit need no change |
| 2026-11-01 | Deinterlace: whose filter | our own libavfilter graph, not `FFmpegFrameFilter` | measured: JavaCV's drops the frame flags and crashes on RAW planar frames (§7) |
| 2026-11-01 | Deinterlace: setting | Auto / Off, global, **only in the player's ⚙ popover and only on a file whose frames are interlaced** | the author: not in the Settings screen, and no row where the filter would do nothing |
| 2026-10-24 | Raw `.hevc`/`.h265`/`.265` | dropped | no encoder in the bundle for a fixture, one sample of unknown origin, rare in practice |
| 2026-10-24 | "Associate Formats" opens Windows Default apps | removed, no replacement link (VF-002) | the user has to click Woka in for every format by hand in Windows anyway; the jump added a window and nothing else |

## 7. Existing MPEG-family formats — duration and interlacing (2026-11-01)

Measured with the bundled `ffprobe.exe` over 19 samples from `samples.ffmpeg.org` (`MPEG2/`,
`MPEG-VOB/`, `MPEG-VOB/interlaced/`, `MPEG2/interlaced/`, `HDTV/`, `evob/`, `ts/`, `camera-dvr/`), not
committed — re-download into `WOKA_VIDEO_SAMPLES`. "Span" = last − first video packet pts. "Interlaced"
= frames with `interlaced_frame=1` among the first 200 decoded.

| Sample | Demuxer | Stated s | Span s | `field_order` | Interlaced, TFF |
|---|---|---|---|---|---|
| `01c56b0dc1.ts` (H.264 1080i) | mpegts | 10.73 ¹ | 9.42 | tt | 73/73, TFF |
| `channel9hdtv_ac3.ts` (MPEG-2 1080i) | mpegts | 16.93 | 16.48 | tt | 198/198, TFF |
| `mpeg2_field_encoding.ts` | mpegts | 1.62 | 1.60 | bb | 31/31, BFF |
| `res_change_ffmpeg_aspect.ts` | mpegts | 3.99 ¹ | 3.44 | tt | 84/84, TFF |
| `vid_0x80.ts` | mpegts | 1.42 | 1.17 | bb | 28/28, BFF |
| `prev_seek_bug.ts` | mpegts | 8.84 | 8.88 | progressive | — |
| `TITLE01-ANGLE1.VOB` | mpeg | 8.02 | 7.84 | tt | 198/198, TFF |
| `V.VOB` | mpeg | 11.23 | 11.06 | **bb** | 181/181, **TFF** |
| `VTS_06_0.VOB` (NTSC) | mpeg | 38.74 | 38.71 | tt | 89/89, TFF |
| `lasvegas-vts_01_1.vob` | mpeg | 10.50 | 10.44 | **bb** | 200/200, **TFF** |
| `burosch1.mpg` | mpeg | 29.96 | 29.92 | bb | 200/200, BFF |
| `tv2-1_25.mpg` | mpeg | 17.96 ¹ | 17.36 | tt | 198/198, TFF |
| `impossible_seek.mpg` | mpeg | 23.88 | 23.76 | **unknown** | 192/198, TFF |
| `dallas.mpg` (MPEG-1) | mpeg | 119.61 | 119.54 | progressive | 0/198 |
| `eof.mpeg` | mpeg | 4.46 | 4.44 | progressive | 0/110 |
| `Example_JVC_MOD_VIDEO.MOD` | mpeg | 16.54 | 16.48 | tt | 200/200, TFF |
| `HV001T01.EVO` (H.264 1080i) | mpeg | 16.38 | 16.37 | tt | 100/100, TFF |
| `sample.evo` (VC-1 film) | mpeg | 10.69 | 10.63 | **tt** | **0/200**, 99 repeat-field |
| `zzz.mpg` | **iv8** | **N/A** | 6.04 | unknown | — |

¹ Audio runs past the last video packet; the stated length is the file's, and right.

**Duration (VF-101): nothing to fix.** Every stated length matches its span within a frame or the
audio tail. The one missing length (`zzz.mpg`, a DVR's `iv8` in an `.mpg`) already goes to
`measuredDurationMs` through `lengthMs`, which applies to every format. The case left — VOBs
concatenated across a timestamp reset — would fool the counter (last pts − start) the same way.

**Interlacing (VF-102).** Committed fixtures are all progressive frame by frame, `dahua.dav` included
(0/17 — the backlog's worry about forcing it is moot). The stream's `field_order` is wrong in bold on
four samples; the frame flags are right on all of them.

**JavaCV, from the 1.5.14 bytecode.** `FFmpegFrameGrabber.processImage`, COLOR mode:
`av_frame_copy_props(picture_rgb, picture)`, then `frame.opaque = picture_rgb` — the flags reach the
RGBA frame. RAW mode: `frame.opaque = picture`. `FFmpegFrameFilter.push(Frame)` → `pushImage`, which
fills a fresh `image_frame` with format/width/height only — **flags lost**, so `parity` and `deint`
are set by us.

**Through the player's grabber — `DeinterlaceProbe`, 2026-11-01.** (a) The RGBA frame's
`Frame.opaque` flags agree with ffprobe on all 21 files above plus the fixtures: `interlaced.ts` 25/25
TFF, `chapters.mkv` 0/36, `sample.evo` 0/200 interlaced with TFF set on 100 — so parity is never
read off a frame that is not interlaced. (b) **`FFmpegFrameFilter` crashes the JVM on RAW frames**
(`av_buffersrc_add_frame_flags` from `pushImage`: one buffer read as three planes) — the graph is our
own (`DeinterlaceGraph`), fed the decoder's `AVFrame` with its flags, so `parity=auto:deint=interlaced`
work as in the CLI. (c) Its pictures trail the push by exactly one frame; times must be read in the
**sink's** time base, `bwdif` halves the source's. (d) Comb score on RGBA G (`combScore`), frame 12 of
`interlaced.ts`: **2.48 raw → 0.92** through the graph.

**Fixture and comb score.** `interlaced.ts` (447 KB):
`ffmpeg -f lavfi -i testsrc2=s=720x288:r=50:d=1 -vf "weave=first_field=top,setfield=tff" -c:v
mpeg2video -b:v 2M -flags +ildct+ilme -top 1 -f mpegts interlaced.ts` — 25 frames 720×576, `tt`, all
flagged TFF. Comb score = mean over odd rows y and even x of `|p(y) − (p(y−1)+p(y+1))/2|` on the gray
plane, frame at 0.5 s: raw **1.95**, `bwdif=send_frame:parity=auto:deint=interlaced` (CLI) **0.48**,
wrong parity (`bff`) **0.75**.

**Cost — moved from the backlog (2026-10-24).**

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

## 8. Rotation (VF-105) — 2026-11-01

Fixtures made with the bundled FFmpeg: `-display_rotation N -i in.mp4 -c copy`. `ffprobe` then prints
the stream's `rotation` as **90, −180, −90** for N = 90, 180, 270 — counter-clockwise, as
`av_display_rotation_get` returns it. Which filter turns each upright, against the CLI's own
autorotate (`ffmpeg -i rotN.mp4` vs `ffmpeg -noautorotate -i rotN.mp4 -vf …`, PSNR):

| `rotation` | `transpose=clock` | `transpose=cclock` | `hflip,vflip` |
|---|---|---|---|
| 90 | 0.73 dB | **inf** | — |
| −180 | — | — | **inf** |
| −90 | **inf** | 0.73 dB | — |

So clockwise turns = `(−rotation) mod 360 / 90`.
