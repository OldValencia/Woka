# ME-172 — A genuine PQ still enters the corpus

| | |
|---|---|
| **Order** | last of the 100 track; unblocks the whole 500 track |
| **Depends on** | ME-011 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Add one real PQ BT.2020 still to `jvmTest/resources/media/`, cut from HDR content the corpus
already holds.

**Why now.** Every block in `backlog.md`'s 500 section is blocked on the same sentence: *"the corpus
holds no genuine HDR still."* `hdr_pq.avif`, `hdr_hlg.avif` and `hdr_pq.png` are SDR pixels wearing
HDR tags — `setparams` wrote the tags and touched no pixel, and the first two are pixel-identical
(`psnr` reports `inf`).

**Measured 2026-09-02: the source is already committed.** `WokaSample.DualVideo.mkv` stream `v:0` is
ffv1 `yuv420p10le`, PQ/BT.2020, and `signalstats` over its first frames reports **YMAX 592/1023 =
0.579**, **YAVG 0.204** — a real PQ distribution, matching the corpus README's own note that a frame
from a real HDR master peaks at 0.565 → 174 nits. It is genuine HDR content. Nothing needs to be
sourced; a frame needs to be cut.

**The caveat, stated so nobody over-reads the fixture:** 320×180, one scene, one master. Enough to
measure a white point and to prove a tone curve is not eleven times too aggressive. Not enough to
judge how a curve behaves across a range of content.

## Steps
1. Cut one frame from `WokaSample.DualVideo.mkv` stream `v:0` and encode it PQ AVIF via
   `libaom-av1` — the path ME-010 measured as writing correctly (PSNR 52 dB, `colr`/`nclx` payload
   byte-identical to a known-good reference). No HEIF: `libx265` cannot open a HEIF output at all. No
   HLG: `-color_trc arib-std-b67` is silently ignored and comes out byte-identical to PQ.
2. Name it `hdr_real_pq.avif`, so no reader confuses it with the three tags-only files whose names it
   would otherwise sit beside.
3. Record the ffmpeg invocation in the corpus README's Provenance section, as every other derived
   fixture there does, with `$FF` as the bundled Bytedeco binary.
4. Add it to `com.woka.fixtures.SampleMedia` — the corpus is reached from Kotlin through that object
   and never by absolute path.
5. Rewrite the README's warning block. It currently ends *"Anything about how HDR pixels are decoded,
   tone mapped or drawn needs a genuine HDR source, and the corpus does not have one yet."* That
   sentence becomes: which three files are tags-only and what they are still good for, which one is
   real, and what the real one's limits are (§ the caveat above).
6. Add one assertion proving the fixture is what it claims: its decoded peak luma is in the
   neighbourhood of the source's 0.579 and **not** 1.0. A fixture that silently degrades into an SDR
   file is exactly the failure that produced the three useless ones.

## Files
`shared/src/jvmTest/resources/media/hdr_real_pq.avif` (new)
`shared/src/jvmTest/resources/media/README.md` (edit — the warning block and Provenance)
`shared/src/jvmTest/.../fixtures/SampleMedia.kt` (edit)
`shared/src/jvmTest/unit/.../StillColorInfoTest.kt` (edit — the peak-luma assertion)

## Done when
- `hdr_real_pq.avif` decodes with `transfer` PQ and `wideGamut` true, and its peak luma is nowhere
  near full scale;
- the README no longer claims the corpus has no genuine HDR source;
- `./gradlew.bat :shared:build` green.

## What this unblocks
`backlog.md`'s **ME-120** (the tone curve's white point — `HdrShader.kt:76`'s `const float W = 11.2`
asserts every HDR source peaks at 1120 nits, so SDR white lands at 148/255), and behind it **ME-118**
(the HDR tool) and **ME-119** (the HDR view mode). ME-120 still gates ME-118: `To SDR` bakes the curve
into the user's file permanently, and shipping it first would write the defect into their pictures.
