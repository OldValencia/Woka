# Image Editor — Backlog

Everything after the foundation (ME-001…ME-014, one file each in this directory). Each block says what
it is for and what shape it takes, and **names no files** — those are written when the block is
promoted into its own ticket file, against the tree as it is then
(`ai/docs/conventions/tickets.md`).

Design behind all of it: [the feature doc](../image-editor.md).

The **200 track — the video editor** — left this file on 2026-08-24 for
[`../../video-editor/tickets/backlog.md`](../../video-editor/tickets/backlog.md). Its blocks still
depend on ME-001…ME-014 and on ME-106, which are here; the numbers did not change.

Order is dependency order.

---

## 100 — Image editor

### ME-1xx — Auto adjust
Lifted out of ME-111 when that ticket was written (2026-08-22), because it cannot be a tool as tools
are defined: an automatic correction reads the **picture** — a histogram, a black point — and
`stage()` receives parameters and nothing else (§3.4). `EditorTool.resolve` hands over the frame, not
the pixels. It needs a hook that does not exist, and inventing one before something asks for it is
the kind of engine change that should follow a want rather than precede it.
*Depends on ME-111.*

### ME-117 — Housekeeping: make `roadmap.md` describe the editor that exists
Phase 5 still reads as it did on 2026-08-18 — *"No UI yet by design… **ME-101** is the first ticket
that puts pixels on screen"* — while what ships has a shell, eight tools, a recipe carrier per
container family, a save banner and thirty-odd closed tickets behind it. The same paragraph lists
"save targets and the atomic write" among the foundation, and `SaveTarget` was deleted by ME-132.

So: Phase 5 rewritten to "image half shipped, video half pending", pointing at the feature doc's status
table rather than restating it, and the foundation sentence corrected where ME-132 removed something.

**The `-edited` half of this block died 2026-08-30** — there is no copy to name. `copyNameFor` went
with ME-132 and the tree holds no `-edited` anywhere; checked before deleting the sentence.
*Depends on nothing.*

### ME-162 — Adjustments a colour matrix cannot express
Split from ME-142 when the matrix half was promoted (2026-09-01). Highlights, Shadows, Sharpness,
Definition, Noise Reduction, Vignette — **and Brilliance, and the real Vibrance**, both moved here by
ME-142 when its arithmetic was checked. A `ColorMatrix` is a 4×5 **affine** map: Brilliance is
region-specific by definition, and Vibrance raises saturation more for pixels that are already less
saturated, which makes the multiplier a function of the input and the map quadratic. ME-142 ships a
*weighted linear* saturation under the name Vibrance and says so in its own KDoc; the per-pixel one is
here.

The rest are each either per-luminance (a tone curve) or per-neighbourhood (a convolution): a curve is
not a linear map of the channels, and a blur reads the pixels around the one being written.

So this needs a **fourth thing a stage can be** — a Skia `RuntimeEffect` carried through the fold and
run identically in three places: the live canvas, the native-scale replay on save, and the reader path
where every grid tile folds the recipe. §3.2's "three shapes and no more" is the sentence it argues
with, and that argument belongs in §9 before any code is written.

**The panel stopped being the blocker** — ME-161 answers it — but the count is the thing ME-161 has to
have been measured against: seven more on top of ME-142's eight is fifteen.
*Depends on ME-142, ME-161.*


### ME-163 — The rest of the `Color.White` literals on scrims
ME-121 converted twelve sites in four files and set the mapping in `ViewerChrome`'s KDoc: `Color.White`
→ `onScrim`, `0.72f` → `onScrimSecondary`, `0.45f`/`0.6f` → `onScrimDisabled`, a `0.10f` hover fill →
`onScrimHover`, a `0.18f` one → `onScrimSelected`, a hairline → `onScrimBorder`. **46 sites across 21
files are left**, and every one is on a `ViewerChrome` scrim, so this is substitution rather than
judgement.

The bulk is `feature/viewer/video/` — the player settings and sync rows, the speed slider and its
parts, the subtitle customise panel and rows, the volume overlay, the sound-boost row, the playback
notices — plus `MediaViewerAudioExtension` and `PlayerResetRow`. Two sittings by size, split wherever
eight files fall.

**Not everything is in scope, and ME-121 wrote down which.** The 28 files reading `Theme.colors` sit
on `Theme.colors` plates and must not be touched. A fill describing a track — `MediaSliderTrack`'s
lightening constant, `MediaVerticalSlider`'s 20 % default — is not a foreground and stays a literal.

Each slice is a *visible* change on a shipped surface, small per site, so each wants the author's eyes
rather than a green build.
*Depends on ME-121.*

### ME-1xx — Opening a large photograph must not freeze the window
Split out of ME-123 on 2026-08-30, when the cache half of that block was promoted and this half was
found to be a different question. `EditorAnimatedProxy.kt` builds the canvas proxy inside
`remember(file, frameIndex) { EditorProxy.load(...) }`, which runs **synchronously while composing** —
667 ms for a 100 MP source, ~150 ms for a 24 MP one, measured in ME-010. Opening the editor on a large
photograph freezes the UI for exactly that long.

The fix needs three things the cache ticket did not: a placeholder for the frame the proxy is not there
yet, an ordering against the frozen viewer still composed underneath (§4.1), and an answer for `Cancel`
pressed mid-decode. That is why it was not improvised inside ME-103 and not folded into ME-123.
*Depends on ME-123.*

### ME-1xx — An unsaved edit per frame
Raised by ME-158 (2026-08-30), which chose the discard dialog instead. Walking from frame 1 to frame 3
inside the editor rebuilds the engine, the stored document and the session, so work on frame 1 is
either lost or confirmed away. What is wanted is to carry it: `EditorSession` holding a document **and
a history** per frame, so undo does not walk out of the frame being looked at into one that is not, and
`Done` writing every frame it touched in one merge.

That is a redesign of a 145-line state holder already at the size mandate, plus a rule for what `Undo`
means when the answer is in another frame — which is why it was not improvised inside ME-158.
*Depends on ME-158.*

---

## 500 — HDR

Gathered here on 2026-09-01 because the three read as one subject and were previously scattered
through the 100 track. **All three are blocked by the same missing thing**: the corpus holds no
genuine HDR still. `hdr_pq.avif`, `hdr_hlg.avif` and `hdr_pq.png` are SDR pixels wearing HDR tags —
the first two are pixel-identical, `psnr` reports `inf` — so they test that a transfer tag is *read*
and nothing else (`jvmTest/resources/media/README.md`). Nothing in this section can be tuned, measured
or confirmed until one or two real PQ BT.2020 stills land in the corpus, and ME-120 must land before
ME-118 or the defect gets written into the user's files.

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

**Blocked on a fixture, not on the analysis** — see the section note above. Confirmed still open
2026-09-01: `HdrShader.kt:76` is unchanged, and the corpus README still says in as many words that no
genuine HDR source is in it.

Belongs to the viewer, not to the editor; listed with the feature because ME-010 is where it was
measured and because the HDR tool's `To SDR` bakes this very curve into pixels.
*Depends on a real HDR fixture reaching the corpus.*

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
