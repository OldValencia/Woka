# ME-291 — Render video…

| | |
|---|---|
| **Order** | second in `render/`, after the ME-290 probe; built 2026-11-01 with ME-210 |
| **Depends on** | ME-290 (packet copy measured), ME-231 (timeline time) |
| **Feature** | [Render](../render.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** `Done ▾` → `Render video…` writes the edited film beside the source as a real file.

**Why now.** The author chose, 2026-11-01, to ship 1.0.6 with cutting and Render before the
in-file `Done` is honoured by the viewer (ME-284): a render is visible everywhere by construction.

**Re-cut from three blocks**: ME-291 (all-copy), ME-292 (all-encode) and the progress and cancel of
ME-293. The copy-or-encode switch is one decision (`render.md` §3.2), and a render with no banner
is not a slice anybody can see; 292 and 293 are folded in and not reused. ME-293's *window closes
mid-render* question is answered by a scratch file deleted on JVM exit, not by the exit gate.

## What was built

1. `EditorEngine.exports` / `export()` and `EditorExport` (`commonMain`) — an engine offers entries
   beside `Done`, the shell draws them and names no medium. `DoneButton` becomes `Done ▾` when the
   list is not empty (`DoneMenu`, via `LocalDoneChoices` because the bar composes elsewhere).
2. `exportAction` — reads the layers, closes the editor, runs in `EditorRenderStatus.scope`.
   **The source is not written** (iOS *Save as New Clip*); one render at a time.
3. `EditorRenderStatus` + `EditorRenderBanner` in `StatusBanners`: name, bar, percent, ✕.
4. `engine/video/render/`: `renderVideo` decides — **copy** when the fold is the identity and
   every clip starts within 16 ms of a keyframe, **encode** otherwise.
   - Copy: `PacketSpans.copySpan`, ME-290's recipe (`av_seek_frame`, start at the first keyframe,
     one pts-anchored shift per segment), into the source's own container.
   - Encode: `FrameRender.encodeClips` — RGBA decode, `RecipePicture` through
     `renderAtNativeScale` when there is pixel work, `libopenh264` at the source's bitrate, AAC
     192k, display rotation carried; `mp4`/`mov`/`m4v`/`mkv` keep their container, others → `mp4`.
   - Written to `.<name>.woka-render` and moved over `clip edited.mp4` / `clip edited 2.mp4`.
5. **VF-104 on the same path**: `Save as MP4` in the grid's menu for `rm rmvb amv mtv dav bik thp
   rpl nsv xesc` — H.264/HEVC + AAC/MP3 copied, anything else encoded; `film.mp4` beside it.

## Known gaps

- **HDR is rendered untone-mapped** into SDR H.264 (`render.md` §4, unanswered).
- Copy keeps one video and one audio stream; subtitles and extra audio tracks are dropped (ME-290).
- `mkv`/`webm`/`avi` still cannot open the editor at all — no carrier means no engine (ME-282).
- The finished banner says "Rendered …", not "copied, not re-encoded".

## Verified

`jvmTest/integration/VideoRenderTest` (cuts on keyframes copy and shrink the file; a cut off a
keyframe encodes to the timeline's length; pixel work encodes and the second render takes the
next name; a cancel leaves nothing) and `integration/SaveAsMp4Test` (`.dav` copied, `.rm`
encoded, sources untouched). **Not yet by hand**: the menu, the banner and a render of a real
phone film.
