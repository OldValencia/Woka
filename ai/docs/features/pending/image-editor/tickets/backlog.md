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

**Every ticket from that batch has shipped**, the last two — ME-170 and ME-171, the remaining slices
of the scrim sweep — on 2026-09-03. `feature/viewer/` now holds five `Color.White` literals, each
documented where it sits, against the 58 undocumented ones ME-121 counted.

Order is dependency order.

---

## 500 — HDR

Gathered here on 2026-09-01 because the three read as one subject. **The track emptied on 2026-09-03
except for ME-118**, and it did not empty the way this file predicted.

Two blocks became tickets and shipped —
[ME-120](implemented/ME-120-the-tone-curve-asks-the-picture-how-bright-it-is.md), the tone curve's
white point, and [ME-119](implemented/ME-119-the-hdr-view-mode.md), the view mode. Three more
tickets were written the same day that **this file never anticipated**, and each came from looking at
the app rather than at the plan:

- [ME-177](implemented/ME-177-one-tone-map-at-the-decode.md) — **the editor had never tone mapped at
  all.** It draws through `RasterRecipe` and never read `StillColorInfo`, so a PQ file's codes were
  painted as sRGB for as long as the editor has existed. Nothing recorded it because §6.1 opened by
  asserting the opposite, which was true of the viewer only. Found by hand, minutes after ME-120 made
  the viewer correct and the same file stayed dull in the editor. The fix moved the map under the
  decode, so no surface owns a tone curve any more.
- [ME-179](implemented/ME-179-the-hdr-mode-lives-in-the-pill.md) — ME-119's settings row lived one
  day. The mode is a question about *this picture*, and `Force SDR` is a probe; a probe three screens
  from what it probes compares against nothing. It moved into the top bar's pill.
- [ME-180](implemented/ME-180-the-switch-reaches-the-picture.md) — flipping the mode redrew nothing.
  ME-119 put the mode in both **cache** keys and called invalidation unnecessary, which was right and
  beside the point: nobody asked for the new key, because neither decode was keyed on the mode. A
  cache key and a recomposition key are different mechanisms and that change needed both.

**ME-178 is retired, not deferred.** It was raised on 2026-09-03 to mirror the mode into the editor's
top bar and closed the same day without a line of code: ME-177 had already put the tone map under
both surfaces, so there was nothing editor-specific left to mirror and one control in the shared pill
serves both. The id is retired rather than reused.

**The fixture blocker is gone as of 2026-09-03.** For a year it was *"the corpus holds no genuine HDR
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

### ME-118 — HDR tool
`Output` domain, `Single`, one `ChoiceParam` — **`Keep` and `To SDR` only in v1** (§9, 2026-08-18).
**`To HDR` is deferred** — no measurement blocks it (PQ AVIF via libaom writes correctly, ME-010) but
it is inverse tone mapping and does not recover clipped highlights, so it is not MVP.

**Both halves changed shape on 2026-09-03 and the block is smaller than it was.**

`Keep` was specified as *"carries the source's transfer through, which needs the ffmpeg encode path
since Skia cannot write PQ"*. **That path is not needed, because nothing re-encodes.** Every format
Woka lists resolves to `EditCapability.Recorded` — checked 2026-09-03, `EditCapabilityTest` asserts it
for JPEG, PNG, WebP, HEIC and AVIF — and `Recorded` splices a recipe into the file and leaves the
pixels untouched. Bucket B has been empty since ME-137. So an HDR file already keeps its transfer,
its primaries and its PQ codes through an edit, and `Keep` is **the behaviour today** rather than
something to build. What v1 owes it is a name in the UI, not an encoder.

`To SDR` is the half that writes pixels, and it is the one that got cheaper: since ME-177 the canvas
already holds tone-mapped sRGB, so baking is no longer "run the shader into a surface" but "write out
what is being drawn". It still needs a real write path, because it is the first thing in the editor
that would take a file out of `Recorded`.

**The ME-120 block is lifted.** `To SDR` bakes the tone curve into pixels permanently, and that curve
was normalised by the literal `W = 11.2` — on real HDR content SDR white landed at 148/255, so
shipping it first would have written the defect into the user's files. It now measures the picture.

**What ME-118 inherits instead is ME-120's stated limit: a still measures its white point, a video
frame still assumes 11.2.** Not a problem for the image editor, which never opens a video at all —
`EditorEngineFactoryImpl` answers `Unavailable` for anything that is not `MediaKind.Image`. It is a
problem for **the 200 track**, whose editor reuses these tools on frames that cannot be measured: a
`To SDR` there would bake a divisor nobody chose. ME-180 acts on the same fact from the other end, by
hiding the view mode over video in the viewer.
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
- ~~ME-178 — the editor's HDR top-bar mirror~~ — **retired 2026-09-03**, the id kept and never reused.
  Raised and closed the same day: ME-177 put the tone map under the decode, so the editor and the
  viewer read one mode through one pill and there was nothing to mirror.
- Batch apply across a selection in the grid — the grid's `Edit` entry is single-selection only.
- RAW, layers, masks, healing — out of scope for this feature entirely.
