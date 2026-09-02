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

Gathered here on 2026-09-01 because the three read as one subject.

**The blocker changed on 2026-09-02, and shrank.** It was *"the corpus holds no genuine HDR still"* —
`hdr_pq.avif`, `hdr_hlg.avif` and `hdr_pq.png` are SDR pixels wearing HDR tags, the first two
pixel-identical with `psnr` reporting `inf`. That is still true of those three files, but the corpus
turned out to hold real HDR content all along: `WokaSample.DualVideo.mkv` stream `v:0` is ffv1
`yuv420p10le` PQ/BT.2020 and measures **YMAX 592/1023 = 0.579**, **YAVG 0.204** — a genuine PQ
distribution, matching the README's own "a real HDR master peaks at 0.565 → 174 nits". So the blocker
is now [ME-172](ME-172-a-genuine-pq-still-enters-the-corpus.md), which cuts a frame from it and
encodes PQ AVIF, and not the absence of a source.

**ME-120 must still land before ME-118**, or the defect gets written into the user's files.

### ME-120 — The tone curve's white point must come from the content
`HdrShader` normalises Hable by the literal `W = 11.2`, which asserts every HDR source peaks at 1120
nits. Measured in ME-010: a real HDR frame peaks at **174**, so the curve compresses a picture that
barely exceeds SDR as though it had eleven times the range, and SDR reference white lands at
**148/255** instead of 255. That is the "brightness at −50 %" the author reported — the second of two
defects found on 2026-08-18, the first (gamut matrix before the curve, wrecking every saturated
colour) being fixed in ME-010.

The shape: the white point becomes a uniform beside `transfer` and `wideGamut`. A still is decoded in
full, so its peak can simply be measured; video should prefer the stream's mastering-display or
MaxCLL metadata and fall back to a constant when a file carries neither.

Confirmed still open 2026-09-02: `HdrShader.kt:76` is unchanged.

Belongs to the viewer, not to the editor; listed with the feature because ME-010 is where it was
measured and because the HDR tool's `To SDR` bakes this very curve into pixels.
*Depends on ME-172.*

### ME-118 — HDR tool
`Output` domain, `Single`, one `ChoiceParam` — **`Keep` and `To SDR` only in v1** (§9, 2026-08-18).
`To SDR` bakes the existing tone map into the pixels and writes an SDR file; `Keep` carries the
source's transfer through, which needs the ffmpeg encode path since Skia cannot write PQ. **`To HDR` is
deferred** — no measurement blocks it (PQ AVIF via libaom writes correctly, ME-010) but it is inverse
tone mapping and does not recover clipped highlights, so it is not MVP.

**Blocked on ME-120, not just on ME-104.** `To SDR` bakes the tone curve into pixels permanently, and
that curve is currently normalised by the literal `W = 11.2` — on real HDR content SDR white lands at
148/255. Shipping `To SDR` first would write the defect into the user's files.
*Depends on ME-013, ME-132, ME-120.*

### ME-119 — HDR view mode
A tri-state — `Auto` / `Force HDR` / `Force SDR` — that changes how a still is *displayed* and no
pixels at all: a `WokaSettings` field, a control in the viewer, and the same toggle mirrored in the
editor's top bar so the user can see what the HDR tool is about to write. Belongs to the viewer as
much as to the editor; it is listed here because §6.1 is where the two halves are defined together.
*Depends on ME-106.*

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
