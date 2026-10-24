# Video formats — the rest of `next_steps.md` §2.2 (1.0.4)

**Goal.** Every container below plays in Woka's player, draws a grid poster and gets a file
association — in **one pass**, [VF-001](tickets/VF-001-all-video-formats.md), the way photo-formats
PF-003 landed. Total goes from 108 to **125** formats (35 → 52 video).

**This file carries the whole investigation** (2026-10-24): a new session implements VF-001 from it
without re-measuring what is recorded here. §5 is the set of facts about the current code it needs.

**Out of scope.**
- `r3d` (REDCODE) — FFmpeg reaches only the raw JPEG 2000 Bayer stream; a real decode needs RED's
  proprietary SDK.
- Raw HEVC (`hevc`/`h265`/`265`) — the bundle has no HEVC encoder for a fixture, the one sample came
  from an unknown source, and the author found it hard to find at all. Dropped.
- Bink 2 — no decoder outside RAD's SDK. `.bik` is Bink 1 only; a Bink 2 file fails to the placeholder.
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
| `nsv` | `nsv` / `vp6`, `aac` | `64vp6.nsv` 320×240 | ✅ | **N/A** |
| `nuv` | `nuv` / `nuv`, PCM | `sample.nuv` 640×512 | ✅ | 32.9 s |
| `mtv` | `mtv` / `rawvideo`, `mp3` | `comedian_auto.mtv` 96×64 | ✅ | **N/A** |
| `amv` | `avi` / `amv`, `adpcm_ima_amv` | `MTV_medium_res_…amv` 128×96 | ✅ | **N/A** |
| `rm` `rmvb` | `rm` / `rv10…rv60`, `cook`, `atrac3`, `sipr`, `ralf` | generated `realvideo.rm` (rv20) | ✅ | 2 s |
| `gxf` | `gxf` / `mpeg2video`, `dv` | generated `pal.gxf` | ✅ | 2 s |
| `ogv` `ogm` `ogx` | `ogg` / `theora`, `vorbis`, `opus`, `flac`, `speex` | **not opened yet** | — | — |
| `dav` | `dhav` (file starts `DAHUA`) / `h264`, `hevc` | the author's Dahua export, 1920×1080, 440 MB | ✅ | **N/A** |
| `h264` `264` | raw H.264 elementary stream | `D1M1.h264` 960×480 (Dahua channel dump), `raw-10bit.h264` | ✅ incl. 10-bit | **N/A**, no timestamps |
| `asf` | already listed in `MediaKind` | the author's 1080p H.264 `.asf` | ✅ | 625 s — nothing to do |

Known issues found:
- `thp` — `adpcm_thp` logs `invalid number of samples in packet`: audio is **unverified**.
- `mtv`, `amv`, `nsv`, `dav`, raw H.264 — no duration from the container (§3).
- `rmvb` — no `.rmvb` opened yet (same demuxer as `.rm`).

## 2. Samples

**Committed** (`shared/src/jvmTest/resources/media/`, commands in its `README.md`): `dahua.dav`
(300 KB head of the 440 MB export — 17 frames decode), `raw.h264` (libopenh264, 50 frames 160×120),
`raw-10bit.h264`, `blank.bik`, `realvideo.rm`, `pal.gxf`.

**Third-party, not committed** — download into a scratch folder for the probe (`WOKA_VIDEO_SAMPLES`):

```sh
B=https://samples.ffmpeg.org
curl -o s.xesc "$B/V-codecs/MTS2/ScreenCapture.xesc"        # 613 KB
curl -o s.rpl  "$B/game-formats/rpl/landing.rpl"            # 4.4 MB
curl -o s.bik  "$B/game-formats/bink/logo_legal.bik"        # 396 KB
curl -o s.thp  "$B/game-formats/thp/losegame.thp"           # 6.8 MB
curl -o s.nsv  "$B/nsv/64vp6.nsv"                           # 449 KB
curl -o s.nuv  "$B/nuv/sample.nuv"                          # 5.8 MB
curl -o s.mtv  "$B/mtv/comedian_auto.mtv"                   # 19.8 MB
curl -o s.amv  "$B/amv/MTV_medium_res_128x96_Over_The_Hedge_Trailer.amv"  # 4.9 MB
curl -o s.ogv  "$B/ogg/Theora/theora-a4_v6-k250-s0.ogg"     # Theora — the one container not opened yet
```

No `.rmvb` source is known yet; look under `$B/real/` before giving up on measuring it.

**CLI tools without installing anything:** the bundled `ffmpeg.exe`/`ffprobe.exe` are inside
`~/.gradle/caches/modules-2/files-2.1/org.bytedeco/ffmpeg/8.1.2-1.5.14/*/ffmpeg-8.1.2-1.5.14-windows-x86_64.jar`
— unzip it to a scratch dir, they sit in `org/bytedeco/ffmpeg/windows-x86_64/`.

## 3. Unknown duration — the one real piece of work

The player and both preview paths already open any container through `FFmpegFrameGrabber`; what
the corpus never had is **a file that does not know its length**. Today that means (§5): no seek bar
in `VideoPlayerDecoder`, posters taken from a zero-length candidate list in `VideoThumbnailExtractor`,
every hover-reel position seeking to 0 in `HoverExcerptReel`, and no length row in the info panel.

- **Counted, not estimated.** The last video packet's `pts + duration`, read by seeking to the file's
  tail by byte (`AVSEEK_FLAG_BYTE`) and demuxing to EOF without decoding. The 440 MB `.dav` must not
  be read whole to draw a seek bar; a full packet walk only where the byte seek fails. A
  bitrate × size guess is an unverifiable heuristic, which this project does not build.
- **Raw H.264 has no timestamps at all.** FFmpeg times it from the SPS frame rate (VUI), or 25 fps
  when absent; duration is frames ÷ that rate, and there is no index to seek with. Whether the player
  can seek it is measured in VF-001 step 1; if not, it plays forward only, no seek bar, and this file
  says so.
- One helper in `feature/viewer/video/engine/probe/`, used only when `lengthInTime <= 0`.

## 4. Status

| Ticket | Title | Status |
|---|---|---|
| [VF-001](tickets/VF-001-all-video-formats.md) | Every remaining video format, one pass | specified |
| [VF-002](tickets/VF-002-associate-without-settings.md) | "Associate Formats" stops opening Windows Settings | **done** 2026-10-24 |
| VF-101 | Duration for the existing formats too | backlog |
| VF-102 | Deinterlacing, with a setting | backlog — deferred until VF-001, investigation in `backlog.md` |
| VF-103 | Truncated and damaged files | backlog |
| VF-104 | "Save as MP4" for formats nothing else opens | backlog |
| VF-105 | Rotation from metadata | backlog |

## 5. Facts about the current code (2026-10-24)

- **Media list:** `core/data/MediaKind.kt`, `VIDEO_EXTENSIONS` (35 today). `ALL_SUPPORTED_MEDIA_EXTENSIONS`
  feeds the Windows registry associations; `WindowsFileAssociationsTest` asserts the total (108 → 125).
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
| 2026-10-24 | Raw `.hevc`/`.h265`/`.265` | dropped | no encoder in the bundle for a fixture, one sample of unknown origin, rare in practice |
| 2026-10-24 | "Associate Formats" opens Windows Default apps | removed, no replacement link (VF-002) | the user has to click Woka in for every format by hand in Windows anyway; the jump added a window and nothing else |
