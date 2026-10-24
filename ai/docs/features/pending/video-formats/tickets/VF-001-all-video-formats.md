# VF-001 — Every remaining video format, one pass

| | |
|---|---|
| **Order** | 1 (the only one) |
| **Depends on** | nothing |
| **Feature** | [Video formats](../video-formats.md) — read §1–§5 first; the investigation is done |

**Goal.** `xesc`, `rpl`, `bik`, `thp`, `nsv`, `nuv`, `mtv`, `amv`, `rm`, `rmvb`, `gxf`, `ogv`, `ogm`, `ogx`,
`dav`, `h264`, `264` play, seek where the stream allows, get a poster and a file association.
**Why now.** 1.0.4 in `next_steps.md`; the author asked for one pass.

## Steps
1. **Probe — record the table in this ticket, dated.** New `jvmTest/.../probe/VideoFormatProbe.kt` on
   the `RawFormatProbe` pattern: env `WOKA_VIDEO_SAMPLES`, skips without it. Per file, through the
   player's own grabber: `lengthInTime`, first frame size, `setTimestamp` to 50 % and the timestamp
   actually reached, audio codec and whether `grabSamples()` yields data. Run it over the committed
   fixtures plus the downloads in the feature doc §2. Only open questions to settle: Theora `.ogg`,
   `thp` audio, whether raw H.264 seeks. A format that cannot decode a frame leaves the list.
2. **Duration helper** — `feature/viewer/video/engine/probe/ContainerDuration.kt` (new):
   `fun measuredDurationMs(file: File, videoStream: Int): Long?` — open with raw `avformat` (the
   `readStillLayout` style, no decoder), `avformat_seek_file` with `AVSEEK_FLAG_BYTE` to
   `max(0, size − 4 MB)`, `av_read_frame` to EOF, keep the largest video `pts + duration` in stream
   time base → ms; if the byte seek fails, walk from the start. Raw H.264: count video packets ÷
   `avg_frame_rate`. Returns null when nothing usable is found.
3. Call it **only where `lengthInTime <= 0`**, at the four reads listed in the feature doc §5
   (`VideoPlayerDecoder.kt:38`/`:106`, `VideoThumbnailExtractor.kt:79`, `HoverExcerptReel.kt:60`,
   `FileInfoPanelData.kt:50`). `VideoPlayerDecoder.kt` is 143 lines — if the call pushes it past 150,
   split first (the duration read and the first-frame re-read into a small helper file).
4. `core/data/MediaKind.kt` — the 17 extensions into `VIDEO_EXTENSIONS` (35 → 52).
5. `desktopApp/build.gradle.kts` — one `mediaAssociations` row each: `video/ogg` (`ogv`, `ogm`),
   `application/ogg` (`ogx`), `application/vnd.rn-realmedia` (`rm`), `application/vnd.rn-realmedia-vbr`
   (`rmvb`), `video/x-nsv`, `video/x-nuv`, `video/x-bink`, `video/x-thp`, `video/x-rpl`, `video/x-amv`,
   `video/x-mtv`, `video/x-gxf`, `video/x-xesc`, `video/x-dav`, `video/h264` (`h264`, `264`).
   Keep it equal to `MediaKind` (a script compared them 108/108 in photo-formats).
6. Tests:
   - `integration/VideoFormatsTest.kt` (new) — each committed fixture (`dahua.dav`, `raw.h264`,
     `raw-10bit.h264`, `blank.bik`, `realvideo.rm`, `pal.gxf`) opens through the player's grabber
     path, first frame has the size in the fixtures README, and the duration the app would show is
     > 0 (`dahua.dav` and `raw.h264` exercise the helper; `raw.h264` = 50 frames → 2000 ms at 25 fps).
   - `unit/ContainerDurationTest.kt` (new) — the helper on `realvideo.rm` agrees with `lengthInTime`
     within one frame; on `dahua.dav` returns a positive value.
   - `unit/WindowsFileAssociationsTest.kt` — 108 → 125.
7. Docs: `README.md` video table (35 → 52, total 125), `next_steps.md` §1 / §2.2 (empty) / §3 (`r3d`,
   raw HEVC, Bink 2), this feature's status row; then move the folder to `implemented/`.

## Files
`shared/src/commonMain/kotlin/com/woka/core/data/MediaKind.kt`,
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/video/engine/probe/ContainerDuration.kt` (new),
`.../feature/viewer/video/engine/decode/VideoPlayerDecoder.kt`, `.../video/engine/VideoThumbnailExtractor.kt`,
`.../video/engine/preview/HoverExcerptReel.kt`, `.../feature/viewer/FileInfoPanelData.kt`,
`shared/src/jvmTest/kotlin/com/woka/probe/VideoFormatProbe.kt` (new),
`shared/src/jvmTest/kotlin/com/woka/integration/VideoFormatsTest.kt` (new),
`shared/src/jvmTest/kotlin/com/woka/unit/ContainerDurationTest.kt` (new),
`shared/src/jvmTest/kotlin/com/woka/unit/WindowsFileAssociationsTest.kt`,
`desktopApp/build.gradle.kts`, `README.md`, `next_steps.md`. Fixtures are already committed.

## Done when
- the probe table is recorded here, dated, one row per sample;
- `.nsv`, `.mtv`, `.amv`, `.dav` show a seek bar and a non-black poster (or the table says why not);
- `thp` audio is confirmed or recorded as broken with the log line;
- the duration helper takes < 1 s on a 440 MB `.dav` (the author's export — ask for it if needed);
- `./gradlew.bat :shared:build` green (`DirectoryWatchTest`'s sub-folder case is a known flake);
- anti-FQN grep clean on every touched `.kt`; no `.kt` over 150 lines.

## Still to measure — cheap, do these first (left open on 2026-10-24)
Each is one CLI run with the bundled `ffmpeg.exe`/`ffprobe.exe` (feature doc §2) and its answer goes
into the feature doc's §1 table before code is written:
1. **Theora `.ogg`** — download `ogg/Theora/theora-a4_v6-k250-s0.ogg`; first frame, duration. The only
   container never opened.
2. **`thp` audio** — `ffmpeg -i s.thp -map 0:a -f null -` and count decoded samples vs. duration ×
   sample rate; the `adpcm_thp` warning may be per-packet noise or a real loss.
3. **Raw H.264 seeking** — `ffmpeg -ss 1 -i raw.h264 -frames:v 1 -f null -` on the committed fixture:
   does it land near frame 25, or decode from 0? Decides whether raw streams get a seek bar.
4. **Tail-read duration prototype** — on `dahua.dav` (and a full export if the author still has
   one): `ffprobe -read_intervals 99%% -show_packets` or a byte-offset demux; check that the last
   packet's pts gives the real length, and how long it takes.
5. **`.rmvb` sample** — search `samples.ffmpeg.org/real/`; open one (RealVideo 4 + cook expected).
6. **`ogm`/`ogx`** — whether either needs anything beyond `.ogv` (both are Ogg; `ogm` may carry old
   DirectShow-style video headers the `ogg` demuxer reads as `ogm` streams).
