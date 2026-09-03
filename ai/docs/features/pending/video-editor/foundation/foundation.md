# Feature: Video Editor — Foundation

The thing the other six folders stand on: an `EditorEngine` for `MediaKind.Video` that draws the
player's frame **through the folded document**, and the one seam the shell grows to make room for a
timeline.

Umbrella and the architecture this implements: [`../video-editor.md`](../video-editor.md) §3.1 and
§3.4. Ticket ids: **ME-201…ME-209**.

---

## 1. Status

| Ticket | Title | State |
|---|---|---|
| [ME-201](tickets/ME-201-a-video-canvas-that-folds.md) | A video canvas that folds | **written** — not started |
| [ME-206](tickets/ME-206-the-band-the-seam-and-a-scrubber.md) | The band, the seam, and a scrubber | **written** — not started |
| [ME-207](tickets/ME-207-the-canvas-opens-paused.md) | The canvas opens paused, and a drag holds it | **written** — not started |
| [ME-205](tickets/ME-205-hdr-on-the-video-canvas.md) | HDR on the video canvas | **written** — not started |
| ME-208 | Say up front that a video cannot be kept yet | **dropped 2026-09-03** — id retained, never reused |

**The folder is fully specified and the backlog's §201 is empty.** All four are written against the
tree of 2026-09-03; each names the file sizes it has to respect, because three of the files they
touch are within a few lines of the 150-line mandate.

**ME-208 was dropped rather than written**: the author chose [`save/`](../save/save.md) as the next
folder, so `EditCapability.Preview` lives for the length of this phase and ME-280 deletes it. A
session-long notice for a state that ends with the following ticket is work spent on the interval
rather than on the feature.

---

## 2. Scope

**In.** A video opens in the editor. The canvas is the running player, drawn through the recipe, so
Crop, Resize, Adjust, Filters and Markup work live on a moving picture. The band under the canvas is
a scrubber. Playback is togglable. HDR is correct. Frames are freed.

**Out, and each has its own folder.** Cutting anything ([`timeline/`](../timeline/timeline.md)),
seamless playback across a join ([`playback/`](../playback/playback.md)), audio beyond what the player
already does ([`audio/`](../audio/audio.md)), and **both saves** ([`save/`](../save/save.md),
[`render/`](../render/render.md)).

**Phase 0 deliberately cannot save.** The author's call, 2026-09-03. It is the fastest route to the
one measurement everything else is priced against — whether a document folds at frame rate — and a
save built before that answer is known is a save that may have to be rebuilt.

---

## 3. Architecture

### 3.1 The canvas is two existing things composed

```
VideoPlayerState.currentFrame : ImageBitmap        already published, 24–60 times a second
RasterSurface(source, sourceSize, recipe)          already draws an ImageBitmap through a document
```

The video engine's `Surface` owns a `VideoPlayerState` for the file, folds the document once per
document change, and hands the current frame to `RasterSurface`. Every reused tool then works with no
code of its own, because the fold and the draw are the image editor's.

**Three things this must not get wrong**, all three found by reading the player rather than guessed:

- **`frameRetention.acknowledge(currentFrameId)` from the draw scope.** The id must be read in the
  same composition as the bitmap, exactly as `MediaViewerFrame` does. Skipping it holds every frame
  the file has.
- **The id and the bitmap are one pair.** Reading them in two places gives a frame reported against
  the wrong id, which makes the retention rule either too eager or silently useless.
- **The fold is per document, not per frame.** A `RasterRecipe` rebuilt 60 times a second for a
  document that has not changed is pure waste; the recipe is remembered on the document and the frame
  is the only thing that moves.

### 3.2 `sourceSize` is the video track's, and it is not the frame's

`sourceSize` is what operations address — a crop rect, a markup point. It must be the **coded** frame
size of the video track, read once, and never the size of the bitmap that happens to have arrived:
the decoder scales what it decodes (`RenderScale`, `VideoPlayerRenderScale`), so the same file can
present 1920×1080 and 960×540 bitmaps within one session. `RasterSurface` already reasons in source
coordinates and rescales the bitmap as it draws, which is exactly what makes a varying decode size
harmless — provided nothing measures the bitmap.

### 3.3 HDR is the chain's first stage

`RasterSurface` does not go through `HdrImagePainter`, so an HDR video drawn naively arrives
untone-mapped and blown out. The fix is not a second draw path: `HDR_SHADER`'s uniform block is
already in the `RasterStage.Shader` convention (declaration order, little-endian, no
`resolutionScale` — it samples one pixel), so the engine prepends one shader stage to the recipe when
`currentFrameHdrInfo.needsColorManagement` says the frame needs it.

**Its `scale` uniform means something different in the chain, and getting that wrong is a picture
blown up from one corner.** In `HdrImagePainter` it is `intrinsicSize / drawSize`, because that
painter's image shader has no local matrix and the effect is handed a *device* coordinate. In the
recipe chain, `imageShaderFor` has already installed the source→bitmap matrix and every stage
evaluates its child in **source** coordinates — so the stage passes `scale = (1, 1)` and the mapping
is the chain's, once, where it already lives.

**Prepended, not appended** — the tone map converts the signal into ordinary sRGB light, and every
adjustment in the chain after it was written for ordinary sRGB light. Running it last would grade a
PQ signal and then map the result, which is two wrong pictures rather than one.

**`ASSUMED_WHITE_POINT`, not a measured one.** ME-120 measures a still's own peak; a frame that will
be gone in 40 ms is the one thing that cannot be measured, and a running peak would make the curve
move while the picture plays. The viewer already made this decision; the editor inherits it rather
than re-taking it.

**And it is why the `HDR` control in the top bar is still hidden for video** (ME-180). The stage makes
the tone map reachable; the switch also needs a peak that is stable for the whole file, which is
ME-301's probe — the container's content-light-level and mastering-display side data, read once
rather than measured per frame.

### 3.4 The seam the shell grows

One defaulted composable on `EditorEngineSurface`:

```
@Composable fun Timeline(document: EditDocument, modifier: Modifier) = Unit
```

`Thumbnail` is the precedent — same file, same reasoning, same default. The shell places it in the
column `EditorBottomPanel` already builds, **between the inspector and the rail** (§4.1 of the
umbrella), and learns nothing about what it holds. An image engine does not override it and draws
nothing, so no still changes.

### 3.5 What the factory says about a video

`EditorEngineFactoryImpl.capability` answers `Unavailable` for everything that is not
`MediaKind.Image`, and that one function is read by both doors. Phase 0 has no save, so a third
answer is needed — a video *opens* and cannot be *kept*:

```
EditCapability.Preview   the editor opens; neither save exists yet
```

**A temporary case with a named removal.** It is deleted by [`save/`](../save/save.md)'s first
ticket, at which point `mp4` and `mov` answer `Recorded` and the rest answer whatever their carrier
says. It exists rather than being faked with `Recorded` because a `Done` button that silently does
nothing is the failure mode §4.5 of the image editor's design was written to prevent — the cost of a
save is stated **before** the work, never after it. The shell already draws a notice strip for
exactly this shape of statement (`EditorNoHistoryNotice`), so the sentence costs no new UI.

---

## 4. Open questions

- **Does the fold survive at frame rate?** ME-201's first step, and the gate on
  [`picture/`](../picture/picture.md). Two numbers are wanted, not one: a document with no shader
  (one `drawImage` plus a colour filter) and a document with the Adjust chain, both against a 4K
  file.
- ~~Does the canvas play by default, or start paused?~~ **Answered by the author 2026-09-03: paused,
  and a drag holds it** — ME-207 carries the three answers and their wiring.
- **What the zoom pill means over a video.** `PublishCanvasZoom` drives the still canvas from the top
  bar. A video frame has no proxy and no native bitmap to swap to, so the ME-116 fidelity question
  does not exist here — but whether zooming a video canvas is wanted at all has not been asked.

---

## 5. Decision log

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-09-03 | How does the video canvas draw? | **The player's frame through `RasterSurface`** | It is the composition of two shipped things, and it makes the five reused tools work with no code of their own (§3.1) |
| 2026-09-03 | Where does HDR happen? | **A `RasterStage.Shader` prepended to the chain** | A second draw path would mean two implementations of the tone map; prepending is also the only correct order (§3.3) |
| 2026-09-03 | What does the factory answer in Phase 0? | **A third capability, `Preview`, deleted by `save/`** | A `Done` that silently does nothing breaks the rule that a save's cost is stated before the work (§3.5) |
| 2026-09-03 | Does Phase 0 include a save? | **No** | The frame-rate measurement prices everything else, and a save built before it is known may have to be rebuilt |
