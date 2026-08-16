# ME-013 — JavaCV 1.5.14 / FFmpeg 8.1.2

| | |
|---|---|
| **Order** | 2 — id is out of sequence; promoted from "deferred" on 2026-08-16 |
| **Depends on** | ME-001 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Move the video stack one release forward, deliberately, with the affected paths re-checked.

**Why now.** Before any editor package exists, so a decode regression cannot be blamed on the editor.
Everything ffmpeg-touching that the editor adds — HDR encode (§6.1), trim, conversion — would
otherwise be written against a version we intend to leave.

## Steps

1. `libs.versions.toml`: `javacv` `1.5.13` → `1.5.14` **and** `ffmpeg` `8.0.1-1.5.13` →
   `8.1.2-1.5.14` in the same change. Bytedeco pairs them; a mismatched pair fails at native load
   time, not at compile time.
2. **Regenerate nothing.** The test corpus is committed under `jvmTest/resources/media/` (3.3 MB,
   reached only through `SampleMedia`) precisely so an encoder that disappears from a future ffmpeg
   cannot silently empty the suite — `resources/media/README.md` carries the provenance and the
   commands, and `tools/*.py` built the exotic ones. What the bump has to prove is therefore the
   *decode* side: the suite that reads that corpus stays green. An earlier generate-at-test-time
   layer existed and was deliberately replaced by this; do not resurrect it.
3. Walk the paths that read ffmpeg's own output, not just the ones that compile: `probe/HdrInfo`, the
   chapters probe, `StillColorProbe`, `FfmpegStillDecoder` and the HEIF tile path, the decode loop,
   `FramePacer`, `VideoPlayerSeek`, subtitle decode and ASS rendering.
4. Record whether the distribution size changed. `ffmpeg-platform` pulls natives for every OS and Woka
   builds Msi/Dmg/Deb, so that stays — but the number belongs in the result, because a per-platform
   `javacpp.platform` build is the obvious follow-on if it has grown.

## Files

`gradle/libs.versions.toml`, `shared/build.gradle.kts`, `desktopApp/build.gradle.kts`
(the last two from the platform narrowing recorded in the result)

## Done when

- `./gradlew.bat :shared:build` green with the same test count;
- HDR, chapters, subtitles, seek and a long playback session each confirmed by hand, and the result
  written into this ticket with a date. There is no `ai/docs/testing/manual/` to re-run: the manual
  pass documents were checked and deleted deliberately, so the ticket is where a by-hand result is
  recorded;
- the distribution size before/after written into this ticket too.

## Result — 2026-08-16

Bumped as specified: `javacv 1.5.13 → 1.5.14`, `ffmpeg 8.0.1-1.5.13 → 8.1.2-1.5.14`, one change.
Both resolved on the first try; the distributable carries `ffmpeg-8.1.2-1.5.14` for every platform.

`./gradlew.bat :shared:build --rerun-tasks` green — **483 tests in 62 classes, 0 failures, 0 errors,
0 skipped**, identical to the count before the bump and to ME-001's.

**Step 3 by machine.** The decode paths the step names are covered by the suite, and it is green on
the new build: `HdrInfoTest` + `MediaProbeTest` (`probe/HdrInfo`), `MediaChapterProbeTest` (chapters),
`StillColorTest` (`StillColorProbe`), `StillDecoderRoutingTest` + `FrameSetDecodeTest`
(`FfmpegStillDecoder`, HEIF tiles), `VideoPlayerEngineTest` + `VideoTransportTest` (decode loop,
pacer), `VideoSeekTest` (`VideoPlayerSeek`), `SubtitleStreamTest` + `SubtitleFileTest` +
`SubtitleCharsetTest` + `BitmapSubtitleTest` (subtitles, ASS).

**Step 4 — distribution size.** Measured on `:desktopApp:createDistributable`, the app image:

| | before bump | after bump | after narrowing |
|---|---|---|---|
| whole image | 304 MB | 305 MB | **163 MB** |
| all jars | 207 MB / 103 | 207 MB / 103 | **64.8 MB / 84** |
| Bytedeco natives | 168 MB, 7 platforms | 168 MB, 7 platforms | **31 MB, `windows-x86_64`** |

The bump itself costs +1 MB — nothing worth acting on. What the measurement surfaced was older and
much larger: `ffmpeg-platform` shipped natives for all seven platforms Bytedeco builds, so a
desktop-only app carried `linux-x86_64` 25.7 MB, `linux-arm64` 25.1 MB, `android-x86_64` 23.7 MB and
`android-arm64` 22.3 MB — **97 MB for platforms Woka neither targets nor can run on**.

This step called that narrowing "the obvious follow-on". It was done here rather than deferred,
because the number only exists while the measurement is open: **−142 MB**, and jpackage builds only
for the OS it runs on, so a distribution could never have used the other six sets anyway.

How, since the sanctioned mechanism does not work here: the `org.bytedeco.gradle-javacpp-platform`
plugin is not published at 1.5.14 — neither the Gradle Plugin Portal nor Maven Central resolves its
marker. So `ffmpeg-platform` is replaced by catalogue entries for `ffmpeg` and `javacpp` without a
classifier, and `shared/build.gradle.kts` appends the host's. `variantOf` is a `DependencyHandler`
extension and does not exist inside a KMP `jvmMain.dependencies { }` block, so the coordinate is
spelled out — the version still comes from the catalogue. Host detection knows `os.arch` is `amd64`
on Windows and `x86_64` on macOS, and fails loudly on anything else. `-PjavacppPlatform=macosx-arm64`
overrides it. `TargetFormat.Deb` is dropped: Woka targets Windows and macOS only.

**Still open:** the by-hand pass — HDR, chapters, subtitles, seek and a long playback session. Not
run yet; this section is not complete until it is.
