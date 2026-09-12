# Image Editor — Backlog

**Nothing here is planned work any more (2026-09-03).** Every track has emptied: what shipped is
recorded below in one paragraph each, and §900 is the list of things deliberately not done. The
feature's status table is [the feature doc](../image-editor.md).

What follows was written as "everything not yet promoted into a ticket file". Each block says what it is for and what shape it
takes, and **names no files** — those are written when the block becomes its own ticket, against the
tree as it is then (`ai/docs/conventions/tickets.md`).

Design behind all of it: [the feature doc](../image-editor.md).

The **200 track — the video editor** — left this file on 2026-08-24 for
[`../../../pending/video-editor/tickets/backlog.md`](../../../pending/video-editor/backlog.md). Its blocks still
depend on ME-001…ME-014 and on ME-106, which are here; the numbers did not change.

**The 100 track emptied on 2026-09-02.** Its six remaining blocks were promoted into eight ticket
files — ME-162, ME-163, ME-165…ME-172 — after the author decided what ships. Two blocks were folded
rather than dropped: Auto adjust became ME-166 (the hook) plus ME-167 (the tool), and the scrim sweep
became three slices instead of two, because twenty in-scope files do not fit the ~8-file line
`tickets.md` §4 sets and the five files that mix a scrim with a `Theme.colors` plate are judgement
rather than substitution. **ME-117 was deleted, not promoted**: `roadmap.md`'s Phase 5 was corrected
in the same change, so there is nothing left for a ticket to do.

**Every ticket from that batch has shipped**, the last two — ME-170 and ME-171, the remaining slices
of the scrim sweep — on 2026-09-03. `feature/viewer/` now holds five `Color.White` literals, each
documented where it sits, against the 58 undocumented ones ME-121 counted.

## 500 — HDR — **shipped, removed 2026-09-03**

Six tickets: ME-120, ME-119, ME-118, and three this file never predicted — ME-177 (the editor had
never tone mapped at all), ME-181 (the first encoder here that is not Skia) and ME-182 (a decode that
inferred its channel count from row padding). ME-178 was raised and retired the same day.

For a year this section said no genuine HDR still existed. `WokaSample.DualVideo.mkv` had held real
PQ content all along and one `ffprobe` found it; everything above followed from that measurement.

## 600 — Adjustments beyond the twelve — **dropped 2026-09-03**

Definition, Vignette, Brilliance and the per-pixel Vibrance. **The author decided against them**, not
the design: ME-165 built the shader stage and ME-162 proved it four times over, so each was a shader
and a `FloatParam` with nothing left to work out. Twelve is where the panel stops. The ME-6xx numbers
were never assigned and are not reserved.

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
- ~~ME-178 — the editor's HDR top-bar mirror~~ — **retired 2026-09-03**, the id kept and never reused.
  Raised and closed the same day: ME-177 put the tone map under the decode, so the editor and the
  viewer read one mode through one pill and there was nothing to mirror.
- Batch apply across a selection in the grid — the grid's `Edit` entry is single-selection only.
- RAW, layers, masks, healing — out of scope for this feature entirely.
