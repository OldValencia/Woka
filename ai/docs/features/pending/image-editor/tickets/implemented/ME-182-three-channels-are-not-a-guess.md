# ME-182 — Three channels are not a guess

| | |
|---|---|
| **Order** | found inside ME-181; must land with it |
| **Depends on** | — |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Stop the AVIF/HEIF decode inferring how many channels a frame has.

**Why now.** ME-181's round-trip test failed and the first four attempts to fix it were aimed at the
encoder. They were aimed wrong: an independent ffmpeg decode of the same file was **correct**, and a
file Woka never wrote — the same picture encoded by the ffmpeg CLI — came back broken in exactly the
same way. The fault was in reading, not writing.

## What was actually wrong

`Java2DFrameConverter` infers the channel count as `imageStride / imageWidth`. ffmpeg pads every row
to an alignment, so that division is a guess about padding. Measured 2026-09-03 on frames this
decoder produces:

| width | row bytes | padded stride | inferred | real |
|---|---|---|---|---|
| 96 | 288 | 384 | **4** | 3 |
| 128 | 384 | 384 | 3 | 3 |
| 160 | 480 | 576 | 3 (3.6, not exact) | 3 |
| 320 | 960 | 960 | 3 | 3 |

Where the padding happens to add exactly one byte per pixel the inference lands on four, and the
picture is read four bytes at a time out of three-byte data: the right of every row runs into the
next, the last quarter is empty. The symptom was the right half black with the rows walking.

**It went unseen because the corpus is 320 and 640 wide** — both exact at three — and because no test
had ever decoded a small multi-coloured picture and checked *where* the colours landed. A flat
fixture passes this bug without noticing it, which is precisely what the first probe did.

## Steps
1. `grabAt` builds the `BufferedImage` itself: `TYPE_3BYTE_BGR`, rows read at `frame.imageStride`,
   `width * 3` bytes taken from each. The stride is still honoured — the padding is real — but the
   channel count is no longer derived from it.
2. Three is not an assumption here: `onGrabber` asks for `AV_PIX_FMT_BGR24` two functions up. The old
   code discarded that knowledge and re-derived it from arithmetic.

## Files
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/image/FfmpegStillDecoder.kt`

## Done when
- `FfmpegStillEncoderTest` runs at **96×64** — the width that exposed this — and its four quadrants
  land in the four corners;
- the existing decode suite is untouched and green, which is what says the corpus's 320- and 640-wide
  files were never affected;
- `./gradlew.bat :shared:build` green.

## What this does not claim

Only widths whose padded stride divides exactly by four were ever wrong. Whether any AVIF a person
actually owns has such a width is unmeasured — every one in this corpus does not. So this is a
correctness fix with a known trigger, not a report that the viewer has been mangling photographs.
