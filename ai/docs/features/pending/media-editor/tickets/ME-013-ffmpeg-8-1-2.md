# ME-013 — JavaCV 1.5.14 / FFmpeg 8.1.2

| | |
|---|---|
| **Order** | 2 — id is out of sequence; promoted from "deferred" on 2026-08-16 |
| **Depends on** | ME-001 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Move the video stack one release forward, deliberately, with the manual pass re-run.

**Why now.** Before any editor package exists, so a decode regression cannot be blamed on the editor.
Everything ffmpeg-touching that the editor adds — HDR encode (§6.1), trim, conversion — would
otherwise be written against a version we intend to leave.

## Steps

1. `libs.versions.toml`: `javacv` `1.5.13` → `1.5.14` **and** `ffmpeg` `8.0.1-1.5.13` →
   `8.1.2-1.5.14` in the same change. Bytedeco pairs them; a mismatched pair fails at native load
   time, not at compile time.
2. Re-run the fixture generators (`jvmTest/fixtures/`) and `FfmpegCapabilities`. A new ffmpeg can
   change which encoders are present, and a fixture that silently stops being generated becomes a test
   that silently stops testing.
3. Walk the paths that read ffmpeg's own output, not just the ones that compile: `probe/HdrInfo`, the
   chapters probe, `StillColorProbe`, `FfmpegStillDecoder` and the HEIF tile path, the decode loop,
   `FramePacer`, `VideoPlayerSeek`, subtitle decode and ASS rendering.
4. Record whether the distribution size changed. `ffmpeg-platform` pulls natives for every OS and Woka
   builds Msi/Dmg/Deb, so that stays — but the number belongs in the result, because a per-platform
   `javacpp.platform` build is the obvious follow-on if it has grown.

## Files

`gradle/libs.versions.toml`

## Done when

- `./gradlew.bat :shared:build` green with the same test count;
- the manual video pass in `ai/docs/testing/manual/` re-run and its result committed with a date;
- HDR, chapters, subtitles, seek and a long playback session each confirmed by hand;
- the distribution size before/after written into the result file.
