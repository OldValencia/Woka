# Deinterlace samples — for looking at, not for the build

Hand-check material for VF-102 / VF-106 (`ai/docs/features/pending/video-formats/`). Nothing here is
read by Gradle or the tests; the committed test fixture is `shared/src/jvmTest/resources/media/interlaced.ts`.

Each file is the **head** of a public sample from `https://samples.ffmpeg.org`, cut with `head -c` on a
packet boundary (188 B for TS, 2048 B for PS) — the original bytes, only shorter. The files therefore
end mid-stream, the way a cut recording does. "Interlaced" = decoded frames flagged so, by the bundled
`ffprobe` (2026-11-01).

## Interlaced — the ⚙ popover should show "Deinterlace", Auto should remove the comb

| File | Source | What it shows | Interlaced |
|---|---|---|---|
| `burosch1.mpg` | `MPEG2/interlaced/burosch1.mpg` | PAL test chart, bottom field first — the strongest comb | 40/40 |
| `mpeg2_field_encoding.ts` | `MPEG2/mpeg2_field_encoding.ts` (whole) | PAL, bottom field first | 31/31 |
| `tv2-1_25.mpg` | `MPEG-VOB/interlaced/tv2-1_25.mpg` | PAL TV recording | 60/60 |
| `V.VOB` | `MPEG-VOB/interlaced/V.VOB` | DVD; the stream claims `bb`, every frame is top field first | 19/19 |
| `impossible_seek.mpg` | `MPEG-VOB/impossible_seek.mpg` | `field_order=unknown`, mixed stream | 85/91 |
| `jvc-camcorder-mod.mpg` | `camera-dvr/Example_JVC_MOD_VIDEO.MOD` | NTSC camcorder; renamed — `.mod` is not a Woka format | 62/62 |
| `channel9hdtv_ac3.ts` | `HDTV/channel9hdtv_ac3.ts` | MPEG-2 1080i — CPU cost | 20/20 |
| `h264-1080i.ts` | `ts/01c56b0dc1.ts` | H.264 1080i | 41/41 |

## Progressive — no "Deinterlace" row

| File | Source | Why it is here |
|---|---|---|
| `sample.evo` | `evob/sample.evo` | film whose stream claims `tt`; every frame is progressive |
| `dallas.mpg` | `MPEG-VOB/dallas.mpg` | MPEG-1 |
| `prev_seek_bug.ts` | `HDTV/prev_seek_bug.ts` | progressive transport stream |
