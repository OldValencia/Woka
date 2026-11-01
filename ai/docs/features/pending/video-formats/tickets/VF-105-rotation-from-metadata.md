# VF-105 — Rotation from metadata

| | |
|---|---|
| **Order** | after VF-102 (reuses its picture graph) |
| **Depends on** | VF-102 |
| **Feature** | [Video formats](../video-formats.md) §8 |

**Goal.** A video whose display matrix says "turn me" — every portrait clip from a phone — shows
upright in the player, on its poster, in the hover reel and in the seek preview.
**Why now.** Measured 2026-11-01: nothing in Woka read the rotation. JavaCV 1.5.14 only reports it
(`FFmpegFrameGrabber.getDisplayRotation`, from the bytecode) and never applies it, so every path showed
the stored landscape picture on its side.

## Steps
1. `probe/DisplayRotation.kt` — `clockwiseQuarterTurns(displayRotation)` (FFmpeg reports the angle
   counter-clockwise; matched against the CLI's autorotate, §8), `FFmpegFrameGrabber.clockwiseTurns`,
   `swapsSides`, and `rotatedClockwise` for an `ImageBitmap` (Skia) and a `BufferedImage` (AWT).
2. **Player** — `Deinterlace`/`DeinterlaceGraph` become `PictureFilter`/`PictureGraph`: the graph is
   `[bwdif,] [transpose…,] [scale,] format=rgba`. `PictureFilter.open` reads the turns; a rotated
   stream on its first (RGBA) open asks for a RAW re-open before any picture is shown.
   `RenderScale.useNativeOf(track, turns)` and `ViewerMedia.Video.nativeSize` swap sides for odd turns,
   so fit, zoom and "Actual size" work on the upright picture.
3. **Graph size from the render-scale**, not the grabber: a RAW grabber's `imageWidth` reads back the
   stored width when none was set, and a turned picture was scaled flat again.
   `RenderScale.decodeHeight` becomes readable.
4. **Poster** (`VideoThumbnailExtractor`), **hover reel** (`HoverExcerptReel`), **seek preview**
   (`SeekPreviewSource`) — the picture they already produce, turned. Thumbnail cache schema
   `.keys-v4` → `.keys-v5`, so a poster cached on its side is redrawn once.
5. Fixtures `rot90.mp4`, `rot180.mp4` (160×96 H.264, tagged with `-display_rotation`). Tests:
   `unit/DisplayRotationTest`, `integration/RotationTest` (turns read, graph pixels against the
   counter-clockwise turn, the player's frame portrait, the poster portrait).

## Result — 2026-11-01
**Done** as above. The seek preview and the hover reel are covered by the shared helper, not by a test
of their own; the editor does not open video yet and is untouched. Not looked at on screen with a real
phone clip.
