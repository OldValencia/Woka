# ME-181 — A still encoder that is not Skia

| | |
|---|---|
| **Order** | before ME-118, which has nothing to write with until this exists |
| **Depends on** | ME-013 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Write a single picture as AVIF, so `To SDR` has somewhere to put the pixels it bakes.

**Why now.** ME-118's `To SDR` has to put SDR pixels back into the user's own `.avif`, and **nothing
in this repository has ever encoded with anything but Skia.** `EncodedImageFormat` has no AVIF entry
and `formatFor` answers null for the extension, so the one container genuine HDR photographs arrive
in is one Woka could read and never write.

## The probe came first, and it was the gate

`FfmpegFrameRecorder` being on the classpath and the bundled ffmpeg having an `avif` muxer are two
facts that do not add up to "this works from Java". `FfmpegEncodeProbe` asked the question before a
line of the encoder was written — 64×48 grey out, read back at 64×48, tagged SDR, centre 124 against
128, which is lossy AV1's own cost. **Verdict YES**, and only then did the rest follow.

## Steps
1. `FfmpegEncodeProbe` in `jvmTest/probe/`. It reports, it does not assert — "the recorder cannot do
   this" would have been an input to a decision, not a regression.
2. `encodeSdrAvif(bitmap): ByteArray` — **bytes out, not a file written**, so it drops into
   `writeAtomically` exactly where `encodeToData` does and the atomic replace stays the only step
   that touches a real path.
3. BT.709 on every axis. The bitmap arriving has already been tone mapped at the decode (ME-177), so
   what is written is ordinary SDR light; tagging it PQ would describe pixels that no longer exist
   and hand the next reader a second tone map.
4. The frame is repainted `TYPE_3BYTE_BGR` before the converter sees it. `toAwtImage` gives
   `TYPE_INT_ARGB`, whose int-packed raster became a four-channel frame the recorder wrote out black.

## What the test had to be, and why

A **four-quadrant** source, not a flat fill. A flat colour survives a sheared write, a channel swap
and a vertical flip without any of them showing — and the probe's flat grey did exactly that: it
passed while the picture was being written wrong. Four colours in four known corners fail loudly.

That test then failed for a reason that was **not the encoder's**, and four attempts were spent
fixing the wrong component before an independent ffmpeg decode showed the file was correct all along.
That is ME-182, and it must land with this.

## Files
`shared/src/jvmTest/kotlin/com/woka/probe/FfmpegEncodeProbe.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/save/FfmpegStillEncoder.kt` (new)
`shared/src/jvmTest/kotlin/com/woka/integration/FfmpegStillEncoderTest.kt` (new)

## Done when
- the four quadrants come back in the four corners at 96×64, and the file reads as `TransferKind.SDR`
  and not wide-gamut;
- `./gradlew.bat :shared:build` green.

## Known cost, stated

`toAwtImage` copies the picture onto the JVM heap — 138 MB for a 36-megapixel source, the figure
`EditorProxy` already records. Affordable exactly here: a save happens once, on an IO thread, and the
bitmap is dropped immediately, which is the trade `ImageClipboard` already makes.
