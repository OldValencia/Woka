# Feature: Video Editor — Picture tools

The author's requirement, verbatim: *"я бы хотел переиспользовать оттуда практически все плагины —
resize, crop, adjust, filters, markup"*. This folder is what "reuse" turns out to mean once it is
read against the code — which is **less work than expected in four cases and a real design decision
in two.**

Umbrella: [`../video-editor.md`](../video-editor.md) §3.1. Ticket ids: **ME-260…ME-279**.

---

## 1. Status

| Ticket | Title | State |
|---|---|---|
| [ME-264](tickets/ME-264-the-hdr-tool-stops-claiming-video.md) | The HDR tool stops claiming video | **done 2026-09-03** — build green |

Everything else is in [the shared backlog](../tickets/backlog.md) §260. ME-264 was taken out of band,
on the author's word, because ME-201 made the rail visibly wrong and the fix was one manifest line.

**Half the gate has been measured — 2026-09-03, `VideoFoldProbe`, recorded in
[ME-201](../foundation/tickets/ME-201-a-video-canvas-that-folds.md).** The finding splits this folder
in two:

- **A colour matrix costs 5× a plain draw**, at 1080p and at 2160p alike. So **Filters and the eight
  Adjust sliders that fold to a matrix are not in question** at any size.
- **The four sliders ME-162 made shaders are.** Highlights and Shadows cost 18×, Noise 22×, Sharpness
  **87×** — and all four together cost **443×**, not the 133× they would if the costs added. Each
  effect takes the previous as its *child*, so every tap of the sharpen convolution re-evaluates
  noise, the tone curve, the colour filter and the image beneath it. **A trailing convolution
  multiplies everything before it by its tap count.**

Those are CPU-raster figures and therefore a ceiling, not a frame budget — but a 443× multiplier is a
property of the work, and a hardware backend has the same texture-fetch count with more cores to
spend on it. **ME-201's step 9 is the by-hand half of the gate**, and until it is run this folder is
planned on the assumption that matrix work is free and shader work is not.

---

## 2. What is already true

Measured 2026-09-03, and it is the finding that made this folder small:

- **`ToolManifest.supports` defaults to `setOf(MediaKind.Image, MediaKind.Video)`**, so
  `registry.tools(MediaKind.Video)` returned the whole rail. **HDR is the one override**, added by
  ME-264; every other tool still takes the default, and that default is what makes the reuse free.
- **`RasterSurface` draws an `ImageBitmap`** and does not care where it came from, so a video frame
  goes through the same fold, the same colour filter, the same shader chain and the same paint list.
- **Every stage is expressed in source coordinates**, and the draw scales the bitmap up to source
  size as it draws — so a decoder that presents 960×540 for a 3840×2160 file changes nothing about
  where a crop rect or a markup arrow lands.
- `Hold to see the original` (ME-173), the zoom pill, undo, the scope switch, the skipped-op notice
  and every param row work with no knowledge of the medium.

So Crop, Transform, Adjust and Filters need **no tool code at all**. What they need is the canvas
[`foundation/`](../foundation/foundation.md) builds, and the measurement it takes.

---

## 3. The four that need nothing, and the two that need a decision

### 3.1 Crop, Transform, Adjust, Filters — wiring, not porting

The one thing to check per tool rather than assume: `drawnSize(document)` is the recipe's
`outputSize`, so a cropped video is laid out in a box of its own proportions and the canvas
letterboxes accordingly. That is the same code path as a cropped photograph.

**The filter strip is where this can go wrong.** `PreviewChoiceParam` asks the engine for one
`Thumbnail` per option — fourteen of them for Styles — each with a document holding only that
option's op. For a still, `ImageCanvas` hands them all the same remembered proxy. The video engine
must do the equivalent with **one held frame**, not one decode or one shader chain rebuild per
option per recomposition. Fourteen chains at 60 fps is the failure this sentence exists to prevent.

### 3.2 Resize means "the size the render writes"

For a still, Resize is an `Output` tool that scales the pixels the save encodes. For a video whose
default save writes no pixels at all, scaling has no meaning at playback — the canvas fits the window
regardless — and it has exactly one meaning at render time.

So on video, **Resize declares the output resolution and only the renderer reads it**. It keeps its
manifest, its slider and its position in the `Output` group; its stage is ignored by the timeline
fold and by the canvas, and its inspector footnote says when it applies. The alternative — hiding it
from video with a `supports` override — was rejected because the author asked for it by name, and
because "the size the file comes out at" is a genuinely useful control that has nowhere else to live
now that `Render video…` asks nothing (§0 of the umbrella).

### 3.3 Markup appears, holds and fades

The author's design, 2026-09-03: pick the point on the timeline, say how many seconds it lasts, let
it fade out. It belongs to the **document**, not to either save — the player's fold and the renderer
both read it, and if the two disagree the exported file will not match what was approved.

**Where the timing lives.** `MarkupTool` is `Single` with one op carrying the whole ordered list of
shapes, and `MarkupShape` is a sealed interface with six variants and an explicit wire format. Two
ways in:

1. **A sibling `ObjectParam` on the tool, keyed by shape id** — a map from `MarkupShape.id` to a
   `(atMs, holdMs, fadeMs)` triple. One new param, no shape class touched, no wire format changed,
   and a still simply never writes the map. **Chosen.**
2. A `timing` field on `MarkupShape`. Rejected: six data classes to edit, a new field in the
   serialised form of every arrow ever drawn on a photograph, and a nullable that means "not video"
   in a type that has no idea what video is.

**A shape with no timing entry is on for the whole video**, which is what a still means and what a
watermark wants, so the common case needs no interaction with the feature at all.

**The two halves must agree.** The player gets its fade from an alpha computed at draw time; the
renderer has to produce the same alpha frame by frame. One function, in `commonMain`, taking a
timeline position and returning an alpha per shape — called by both, never implemented twice.

**And it is not markup's function alone.** A sound entry on the audio row asks the same three
questions — when it starts, how long it lasts, how it fades — so the four numbers and the envelope
they produce are one shared `TimeWindow` group, described in
[`../audio/audio.md`](../audio/audio.md) §3.3a. Markup multiplies the envelope into an alpha and
audio into a gain; whichever of the two is built first builds it for both.

---

## 4. What does not come across

- **The HDR tool** — **done 2026-09-03 (ME-264)**, and it is the app's first `supports` override. For
  a still it decides what the save writes and can bake to SDR; on video that question is the
  renderer's, in a vocabulary the tool does not speak (a transfer characteristic on an encoded
  stream, not a pixel operation). It comes back — if it comes back — as part of
  [`render/`](../render/render.md).
- **Markup precision at 4K.** A still zooms to the native bitmap for exactly this reason (ME-116).
  A video canvas has whatever the decoder presented, which may be a quarter of the coded size, so a
  markup point placed on the canvas is placed against a coarser picture than the render will use.
  The arithmetic is unaffected — everything is in source coordinates — but what the user can *see*
  while placing it is. Whether that matters is a question for someone who has tried it.
- ~~**Auto adjust** and what it measures~~ — **answered 2026-09-03 (ME-265)**: the frame at the play
  head, measured **once, when the button is pressed**. A running measurement was the thing to avoid,
  because it would make the suggestion move while the video plays; measuring on demand needs no
  `stats` at all, so `EditorEngine.stats` can stay null for video.

---

## 5. Decision log

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-09-03 | How much work is reusing the five tools? | **Four need none; Resize and Markup need one decision each** | `supports` already includes video and `RasterSurface` already draws any bitmap in source coordinates (§2) |
| 2026-09-03 | What does Resize mean on a video? | **The render's output resolution, read only by the renderer** | The default save writes no pixels, so scaling has no other meaning — and it is the only home for "how big is the file I get" now that the render asks nothing (§3.2) |
| 2026-09-03 | Where does markup timing live? | **A sibling param keyed by shape id, not a field on the shape** | It touches no shape class and no existing wire format, and a still never writes it (§3.3) |
| 2026-09-03 | When does Auto adjust measure? | **Once, on the press, against the frame at the play head** | A running measurement would make the suggestion move while the video plays; on demand it needs no `stats` at all (ME-265) |
| 2026-09-03 | Does the HDR tool support video? | **No — the first `supports` override** | Its question on video is about an encoded stream, not about pixels, and that belongs to the renderer (§4) |
