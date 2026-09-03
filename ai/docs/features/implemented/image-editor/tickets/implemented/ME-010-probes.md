# ME-010 — Capability probes

| | |
|---|---|
| **Order** | 11 |
| **Depends on** | ME-001, ME-013 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Answer, on this machine, what the decoders and the caches actually do — instead of assuming
it in eight later tickets.

**Why now.** Every save-path decision (EXIF, formats, HDR, cache staleness, memory) is currently a
guess.

**Where the output goes.** Into this ticket, dated, per `ai/docs/conventions/feature-docs.md` §7.
There is no `ai/docs/testing/` tree to recreate — the earlier one was read, acted on and deleted
deliberately, and this ticket used to open by restoring it. It does not any more.

## Steps

1. `EditorFormatProbe.kt` (`jvmTest/probe/`, following `DecoderCapabilityProbe` + `CapabilityReport`):
   for every extension in `MediaKind`, decode through the existing `StillDecoders` path, re-encode via
   Skia to PNG and JPEG, re-decode, and print dimensions, max channel delta and round-trip time. This
   is the conversion matrix §7 needs.
2. `EditorExifProbe.kt`: (a) open a JPEG carrying `Orientation=6` through the viewer's decode path and
   record whether it displays upright; (b) dump the marker segments before and after a Skia re-encode
   and verify that splicing the source `APP1` with `Orientation` forced to 1 produces a file both Woka
   and Explorer read correctly.
3. `EditorCacheProbe.kt`: overwrite a file the grid has already drawn, then record which of
   `ImageBitmapCache`, `.thumbnails` and Coil still serve the stale picture. That is the exact
   invalidation list ME-104's overwrite path has to implement.
4. `EditorHdrProbe.kt`: the question §6.1 is gated on. Can the bundled ffmpeg **write** a PQ and an
   HLG file — AVIF via libaom, HEIF via libx265 — that (a) Woka's own `StillColorProbe` reads back
   with the right transfer, and (b) Windows Photos opens as HDR? Then the reverse: tone-map a PQ
   source to SDR through the existing `HdrShader` path and record the round-trip delta. The answer
   decides whether the HDR tool ships with `To HDR` or with `Keep` + `To SDR` only.
5. `EditorMemoryProbe.kt`: decode a 24 MP and a ~100 MP source, build the ≤ 2560 px proxy, render a
   full-size recipe into a Skia surface, and record peak native bytes and wall time against
   `ImageBitmapCache.retainedBytes`. This is the number the "very large sources" gap in §10 is waiting
   on — it decides whether the editor refuses, warns, or tiles.
6. Probes print a table and assert nothing about the machine (`code-style/test.md`). Paste the output
   into this ticket under a dated heading that names the ffmpeg version it came from, and write the
   decisions they settle into the feature's decision log (§9).

## Files

`jvmTest/probe/EditorFormatProbe.kt`, `probe/EditorExifProbe.kt`, `probe/EditorCacheProbe.kt`,
`probe/EditorHdrProbe.kt`, `probe/EditorMemoryProbe.kt` (new), this ticket,
`ai/docs/features/pending/image-editor/image-editor.md` (decision log)

## Done when

- the five probes run green and their output is recorded here with a date and the ffmpeg version it
  came from;
- §6 of the feature doc has no "probe first" left unanswered;
- the memory numbers are recorded even if the guard rail they imply is left to a later ticket.

## Result — 2026-08-17, ffmpeg 8.1.2-1.5.14, JavaCV 1.5.14

Five probes in `jvmTest/probe/`. They report and assert nothing, so they pass on any machine; the
output below is the deliverable.

    ./gradlew.bat :shared:jvmTest --tests "com.woka.probe.Editor*" -i

### Format round trip — decode, re-encode with skia, decode again

```
| source        | →    | size    | max Δ | encode | bytes |
|---------------|------|---------|-------|--------|-------|
| still.png     | png  | 640×480 | 0     | 19ms   | 36KB  |
| still.png     | jpg  | 640×480 | 112   | 1ms    | 25KB  |
| still.png     | webp | 640×480 | 112   | 11ms   | 13KB  |
| still.jpg     | png  | 640×480 | 0     | 36ms   | 93KB  |
| still.jpg     | jpg  | 640×480 | 37    | 1ms    | 24KB  |
| still.jpg     | webp | 640×480 | 44    | 10ms   | 14KB  |
| still.webp    | png  | 640×480 | 0     | 49ms   | 79KB  |
| still.avif    | png  | 640×480 | 0     | 9ms    | 4KB   |
| burst.heic    | png  | 320×240 | 0     | 5ms    | 4KB   |
| anim.gif      | png  | 320×240 | 0     | 2ms    | 1KB   |
| anim.webp     | png  | 320×240 | 0     | 7ms    | 11KB  |
| anim.avif     | png  | 320×240 | 0     | 2ms    | 0KB   |
| multipage.tif | png  | 320×240 | 0     | 2ms    | 1KB   |
```

**Every format Woka decodes re-encodes through Skia to all three targets**, dimensions preserved.
PNG is lossless at Δ0 as expected; JPEG and WebP reach Δ112 on the synthetic test pattern, which is
chroma subsampling on saturated edges and not a defect. §2's "Skia writes png/jpg/webp" holds, and
§7's conversion sheet can offer PNG as the always-faithful target.

### EXIF

```
| question                                       | answer                                |
|------------------------------------------------|---------------------------------------|
| `exif_rot90.jpg` decoded through `ImageLoader` | 480×640                               |
| the tag is applied for us                      | **yes**                               |

| bytes                       | segments                | carries APP1 |
|-----------------------------|-------------------------|--------------|
| `still.jpg` as shipped      | E0 FE DB C4 C0          | **no**       |
| `exif_rot90.jpg` as shipped | E1(APP1)                | **yes**      |
| after a skia JPEG re-encode | E0 DB DB C0 C4 C4 C4 C4 | **no**       |
```

Two answers, both decided:

1. **The decode path already applies `Orientation`** — a 640×480 source with `Orientation=6` arrives
   as 480×640. So the editor must **not** rotate again, and a save must write the tag as **1**;
   splicing the source `APP1` back unchanged would double-rotate on the next open. This is §6's first
   bullet, closed.
2. **A Skia JPEG re-encode drops `APP1` entirely.** EXIF preservation is therefore not automatic and
   never will be — camera, lens, date and GPS survive only if ME-104 splices the segment in
   deliberately, with `Orientation` forced to 1.

### Caches after an in-place overwrite

```
| cache                      | key                     | still serves the old picture |
|----------------------------|-------------------------|------------------------------|
| `ImageBitmapCache` native  | the absolute path alone | **yes**                      |
| `ImageBitmapCache` preview | `path#preview`          | **yes**                      |
| `.thumbnails` poster       | path + length + mtime   | **no**                       |
```

The invalidation list ME-104 has to implement is **two entries, both in `ImageBitmapCache`**:
`key(path, 0)` and `previewKey(path)`. The `.thumbnails` poster is keyed on length and mtime as well
as path, so an overwrite simply produces a different filename — the stale poster is never served, it
is orphaned. That is a disk-hygiene question, not a correctness one.

**`ImageBitmapCache` has no removal API at all** — only `get` and `set`. One has to be added before
ME-104 can do its job; that is a finding, not an oversight in this ticket.

Coil is deliberately unmeasured: the grid builds it inside a composition, so its staleness is a
question for the UI ticket rather than for a headless probe.

### HDR — §6.1's gate

```
| target   | encoder      | written                                            | read back as |
|----------|--------------|----------------------------------------------------|--------------|
| PQ AVIF  | `libaom-av1` | 3KB                                                | PQ + wide    |
| HLG AVIF | `libaom-av1` | 3KB                                                | PQ + wide    |
| PQ HEIF  | `libx265`    | **✗** Error opening output files: Invalid argument | —            |
| HLG HEIF | `libx265`    | **✗** Error opening output files: Invalid argument | —            |
```

- **HEIF output does not work at all** with the bundled build. HEIF is off the table for HDR writing.
- **AVIF via `libaom-av1` writes and reads back as PQ + wide gamut.** That is the `Keep` and `To HDR`
  path, if it survives the manual check.
- **HLG is never written at all**, and the reader is not at fault. The two outputs are **byte-identical**
  — same MD5, `ea6b16bf27168bea441a4c13d7beee9d` — so `-color_trc arib-std-b67` is silently ignored on
  this path and the source's PQ transfer is carried through instead. `readStillColor` reporting PQ is
  correct; there was no HLG file to read. **HLG is not an offerable target** until a way to actually
  write it is found, because §6.1 does not write a transfer it cannot verify.

### The visual check — done 2026-08-17, and it decides against `To HDR`

Looked at in Windows Photos, our written PQ AVIF beside the known-good reference. **They render
differently**: the reference briefly loads in correct HDR colour and then falls back to a dull
rendering; ours renders blown out — the colour bars burn to yellow, white and magenta.

That result stands even though the two files are equivalent by **every measurement available**:

| compared | result |
|---|---|
| pixel data | PSNR **52.0 dB** y/u/v against the source — lossy-codec noise, nothing more |
| stream format | both `yuv420p10le(tv, bt2020nc/bt2020/smpte2084)` |
| container boxes | both `colr`, `nclx`, `av1C`, `ispe`, `pixi`; neither has `clli`/`mdcv` |
| `colr` payload | **byte-identical** — `nclx 0009 0010 0009 00`: BT.2020 primaries, transfer 16 (PQ), BT.2020 NCL matrix, limited range |

Isolating the command line changed nothing: a plain re-encode, a `-pix_fmt`-only re-encode and the
full flagged one all produce the same PSNR and the same tags. The source was *already*
`yuv420p10le` PQ/BT.2020, so the colour flags were a no-op, and the damage is not ours to fix by
correcting them.

### The viewer drew HDR stills wrong — cause found, colour fixed here

**Observed 2026-08-17, in Woka, by the author.** Files in
`C:\Users\OldValencia\Pictures\woka-hdr-check`:

| file | how the viewer draws it |
|---|---|
| `1-reference-known-good.avif` (PQ) | blown out |
| `2-written-by-woka.avif` (PQ) | blown out |
| `3-reference-pq.png` (PQ) | blown out |
| **`4-reference-hlg.avif` (HLG)** | **correct — the only one** |
| `5-REAL-hdr-frame.avif` (real HDR master, PQ) | **too dark, as if brightness were at −50 %** |

Reported 2026-08-18 with the colours named: the six `testsrc2` bars are red, green, yellow, blue,
magenta, cyan in the grid, and in the viewer they are **yellow, white, white, magenta, white, white**
— *"yellow ended up where red was, magenta where blue was"*. On the HLG file, *"green draws yellow
and cyan draws white"*.

#### Cause 1 — the tone curve was fed negative light. This is the colour destruction.

`bt2020to709` ran **before** `hable`. Every saturated BT.2020 colour lies outside BT.709, so the
matrix hands the curve a negative component. `hable` is a rational function defined on non-negative
light; on a negative input its denominator changes sign and it returns a **large positive**, which
then clips to full. BT.2020 red arrives as `g = -12.5` and leaves the curve as `1.73` → 255.

Running the shader's own arithmetic in isolation reproduces the screenshot exactly, bar for bar —
including the HLG file's *"green draws yellow, cyan draws white"*, which appears there and not on the
PQ files only because HLG's lower absolute level keeps four of the six negatives small enough to
still clamp to zero:

| bar | PQ before | PQ after | HLG before | HLG after |
|---|---|---|---|---|
| red | **yellow** | red | red | red |
| green | **white** | green | **yellow** | green |
| yellow | **white** | yellow | yellow | yellow |
| blue | **magenta** | blue | blue | blue |
| magenta | **white** | magenta | magenta | magenta |
| cyan | **white** | cyan | **white** | cyan |

So the HLG file looking right was a coincidence of magnitude, not of branch. **Fixed**: the curve
runs first, its output is clamped to `[0,1]`, and the gamut matrix runs after with a second clamp —
a colour the display cannot make becomes the nearest one it can.

#### Cause 2 — the tone curve's white point is a constant. This is the darkness. **Still open.**

`W = 11.2` asserts the content peaks at 1120 nits. The real HDR frame peaks at **174**, so the curve
compresses a picture that barely exceeds SDR as though it had eleven times the range:

| scene luminance | drawn at, `W = 11.2` | drawn at, `W` = the frame's own 1.74 |
|---|---|---|
| 100 nits (SDR reference white) | **148 / 255** | 213 / 255 |
| 203 nits (graded reference white) | 186 / 255 | 255 / 255 |
| 400 nits | 219 / 255 | 255 / 255 |

SDR white landing at 148 is the *"brightness at −50 %"* that was reported. The fix is to drive the
white point from the content instead of a literal — a still is decoded in full, so its peak can
simply be measured — but **the corpus has no genuine HDR still to tune against**, and tuning against
`hdr_pq.avif` would mean tuning against SDR pixels wearing a PQ tag. Blocked on a real file, not on
the analysis.

Cause 1 is fixed in `HdrShader.kt` and compiles. Nothing about this is committed.

---

### ⚠ Everything in this HDR section was measured against a fixture that is not HDR

Found 2026-08-17, after the visual check: **`hdr_pq.avif`, `hdr_hlg.avif` and `hdr_pq.png` contain SDR
pixels wearing HDR tags.** The generator uses `setparams`, which writes colour tags and touches no
pixel data. Proof: `hdr_pq.avif` and `hdr_hlg.avif` are pixel-identical (`psnr` reports **inf**), and
two different transfer curves cannot encode one scene into identical numbers.

Measured peaks after decode, which settle it:

| file | decoded peak | as a PQ signal |
|---|---|---|
| `hdr_pq.avif` (fixture) | 255 | 10000 nits |
| our written `hdr_smpte2084.avif` | 255 | 10000 nits |
| a frame cut from a real HDR master | **144** | **174 nits** |

So the "reference" this probe compared against was never a reference. Our written file matched it
because both are the same lie. **The HDR half of this ticket has to be re-run against a genuine HDR
source before any of it counts**, and `ai/docs/.../media/README.md` now carries the warning so the
next person does not repeat it.

What survives unaffected: the HEIF failure (`libx265` cannot open the output at all) and the fact
that `-color_trc arib-std-b67` is silently ignored — neither depends on the source's pixels.

**This does not decide the gate, and the first version of this section wrongly said it did.**

Windows Photos mishandles the *reference* as well — it flashes correct HDR and falls back to dull.
A viewer that cannot hold a known-good file is not an instrument for judging ours; its verdict is
evidence about Photos. Killing `To HDR` on it would have been a decision taken on a broken oracle.

**The check that decides it has not been run: how Woka itself draws these files**, through the
`HdrShader` path it already has. That is the environment the feature lives in, and it is controlled.
Until then `To HDR` stays gated, neither shipped nor dropped.

`Keep` is unaffected either way: we can demonstrably write a file measurably identical to its source,
so passing a transfer through makes nothing worse than the original already was.

### The methodological finding, which matters more than the verdict

**A read-back that checks the transfer tag cannot certify an HDR file.** This probe reported
"PQ + wide" for a file that a person looked at and rejected. Every automated check agreed with every
other automated check, and all of them were beside the point. Any future attempt at HDR output needs
a pixel comparison against a known-good reference *and* a pair of eyes — not a metadata assertion.

### Memory — §10's "very large sources"

Test JVM heap cap 512MB. **The app runs at `-Xmx1536m`** (`desktopApp/build.gradle.kts`), so the
`peak MB` column is a floor measured under a tighter ceiling, not the app's behaviour.

```
| source | pixels     | decode | native MB | proxy            | full render | peak MB |
|--------|------------|--------|-----------|------------------|-------------|---------|
| 24 MP  | 6000×4000  | 142ms  | 91        | 2560×1706 in 6ms | 16ms        | 124     |
| 100 MP | 11600×8700 | 667ms  | 384       | 2560×1920 in 8ms | 120ms       | 133     |
```

The decisive number: **a single 100 MP native bitmap is 384 MB, against a 256 MB `ImageBitmapCache`
budget.** One such picture cannot be cached at all — it would evict the entire cache and still not
fit. At 24 MP the same bitmap is 91 MB, which is a third of the budget and fine.

The proxy is cheap at both sizes (6–8 ms) and is what the canvas actually draws, so **editing** a
100 MP photo is comfortable; it is **holding** it that is not. That points at "do not put the native
bitmap in the shared cache during an edit session" rather than at refusing the file — but the guard
rail itself belongs to a later ticket, as this ticket's done-when allows.
