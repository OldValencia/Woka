# Image Editor — Backlog

Everything not yet promoted into a ticket file. Each block says what it is for and what shape it
takes, and **names no files** — those are written when the block becomes its own ticket, against the
tree as it is then (`ai/docs/conventions/tickets.md`).

Design behind all of it: [the feature doc](../image-editor.md).

The **200 track — the video editor** — left this file on 2026-08-24 for
[`../../video-editor/tickets/backlog.md`](../../video-editor/tickets/backlog.md). Its blocks still
depend on ME-001…ME-014 and on ME-106, which are here; the numbers did not change.

**The 100 track emptied on 2026-09-02.** Its six remaining blocks were promoted into eight ticket
files — ME-162, ME-163, ME-165…ME-172 — after the author decided what ships. Two blocks were folded
rather than dropped: Auto adjust became ME-166 (the hook) plus ME-167 (the tool), and the scrim sweep
became three slices instead of two, because twenty in-scope files do not fit the ~8-file line
`tickets.md` §4 sets and the five files that mix a scrim with a `Theme.colors` plate are judgement
rather than substitution. **ME-117 was deleted, not promoted**: `roadmap.md`'s Phase 5 was corrected
in the same change, so there is nothing left for a ticket to do.

Order is dependency order.

---

## 500 — HDR

Gathered here on 2026-09-01 because the three read as one subject. **Two of the three left on
2026-09-03** — [ME-120](implemented/ME-120-the-tone-curve-asks-the-picture-how-bright-it-is.md) and
[ME-119](implemented/ME-119-the-hdr-view-mode.md) — along with
[ME-177](implemented/ME-177-one-tone-map-at-the-decode.md), which this file never predicted: the
editor had never tone mapped at all, and §6.1 said the opposite plainly enough that nobody looked.
ME-118 alone is left here.

**The blocker is gone as of 2026-09-03.** For a year it was *"the corpus holds no genuine HDR
still"* — `hdr_pq.avif`, `hdr_hlg.avif` and `hdr_pq.png` are SDR pixels wearing HDR tags, the first
two pixel-identical with `psnr` reporting `inf`. That is still true of those three, but the corpus
turned out to hold real HDR content all along: `WokaSample.DualVideo.mkv` stream `v:0` is ffv1
`yuv420p10le` PQ/BT.2020, measuring **YMAX 592/1023 = 0.579** against **YAVG 0.204**. ME-172 cut a
frame from it and encoded PQ AVIF, so **`hdr_real_pq.avif` is in the corpus** and measures 0.592 —
the source's own distribution plus what lossy AV1 adds.

**What it is not.** 320×180, one scene, one master. Enough to measure a white point and to show that
`W = 11.2` compresses a picture with a fraction of that range; not enough to tune a curve across a
variety of content. ME-120 was done against it; whether its result *looks* right on a photograph
somebody took is a separate question and a separate fixture.

**ME-120 shipped 2026-09-03**, and the measurement corrected two of its own numbers. **ME-118 is no
longer blocked by it**: the tone curve `To SDR` would bake is now normalised by what the picture
reaches, so the fixture's SDR reference white renders above 200 rather than at 148/255.

### ME-118 — HDR tool
`Output` domain, `Single`, one `ChoiceParam` — **`Keep` and `To SDR` only in v1** (§9, 2026-08-18).
`To SDR` bakes the existing tone map into the pixels and writes an SDR file; `Keep` carries the
source's transfer through, which needs the ffmpeg encode path since Skia cannot write PQ. **`To HDR` is
deferred** — no measurement blocks it (PQ AVIF via libaom writes correctly, ME-010) but it is inverse
tone mapping and does not recover clipped highlights, so it is not MVP.

**The ME-120 block is lifted (2026-09-03).** `To SDR` bakes the tone curve into pixels permanently,
and that curve was normalised by the literal `W = 11.2` — on real HDR content SDR white landed at
148/255, so shipping `To SDR` first would have written the defect into the user's files. It now
measures the picture. What ME-118 inherits instead is ME-120's stated limit: **a still measures, a
video frame still assumes**, so the tool must not offer `To SDR` on a video frame without saying what
divisor it used.
*Depends on ME-013, ME-132.*

---

## 600 — Adjustments beyond the twelve

### ME-6xx — The four ME-162 did not take
Split off when ME-162 was written (2026-09-02), which chose Highlights, Shadows, Sharpness and Noise
Reduction and stopped there. What is left: **Definition**, **Vignette**, **Brilliance**, and the
**per-pixel Vibrance** Photos actually means — the one that raises saturation more for pixels that are
already less saturated, which makes the multiplier a function of the input and the map quadratic.
ME-142 ships a *weighted linear* saturation under the name Vibrance and says so in its own KDoc.

**Nothing architectural is left to decide.** ME-165 built the shader stage and ME-162 proved it
against four adjustments, so each of these is a shader and a `FloatParam`. Twelve plus four is
sixteen; ME-161 measured the panel at fifteen, so **the panel is the open question here, not the
engine** — which is the reverse of how this block read before 2026-09-02.
*Depends on ME-162.*

---

## 900 — Follow-ons, explicitly not planned

- Straighten / arbitrary-angle rotation.
- ~~ME-143 — Crop that can pull one edge~~ — **dropped 2026-09-01**, the id retained and never reused.
  It held two readings and never chose between them. The **stretch** one — drag an edge and the pixels
  get taller — is a destructive operation Photos does not offer, and "change the dimensions" is already
  Resize. The **keystone** one is the valuable half and is a closer relative of straighten, directly
  above it in this list, than of Crop: `CropRect` is four integers and cannot describe a quadrilateral,
  `CropHandle` resizes a rectangle, so it would be its own tool doing an operation §2 has already put
  out of scope. Nothing had shipped toward either — checked 2026-09-01, Crop carries `Rect`, `Aspect`,
  `Portrait` and Resize is uniform-only.
- ~~Re-opening a saved edit to adjust a previous crop, as a sidecar beside the copy~~ — **shipped
  2026-08-19 (ME-134)**, and not that way: the recipe is in the file, so reopening replays it into a
  history Undo can walk back. No sidecar, and no copy to sit beside.
- ~~A user-configurable copy-name pattern~~ — **moot since ME-132**: nothing is copied and nothing is
  named. `copyNameFor` is gone and `-edited` appears nowhere in the tree.
- **Saving a flattened copy to a file.** The clipboard answers "get this picture out of Woka" without
  minting anything (`Copy`, 2026-08-19), and an export step is precisely what §5 removed. ME-136 was
  raised for this on 2026-08-19 and closed the same day by making `Copy` the picture; the number is
  retired rather than reused.
- Batch apply across a selection in the grid — the grid's `Edit` entry is single-selection only.
- RAW, layers, masks, healing — out of scope for this feature entirely.
