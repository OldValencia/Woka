# Feature: Video Editor — Render

The other half of `Done ▾`: a real file, that plays edited anywhere, produced by the bundled ffmpeg.
It exists because [`save/`](../save/save.md)'s recipe is only visible inside Woka, and a clip sent to
somebody must not depend on them having it.

Umbrella: [`../video-editor.md`](../video-editor.md) §3.5 and §4.5. Ticket ids: **ME-290…ME-299**.

---

## 1. Status

No tickets written. Blocks in [the shared backlog](../tickets/backlog.md) §290.

---

## 2. Scope

**In.** `Render video…` over the whole document: cuts, per-clip speed, freezes, colour, crop, markup
with its timing, audio including a detached or external track. A progress banner that outlives the
editor. A new file, never an overwrite. Also **"this frame as a photo"** and
**`Render GIF…`** (§3.1a), both of which are outputs and have nowhere better to live.

**Out.** Choosing a format, a codec, a container or a bitrate. Presets. Uploading anywhere.

---

## 3. Architecture

### 3.1 It asks nothing

Author's decision, 2026-09-03. One click, then a progress bar. Concretely:

- **The container and codec are the source's**, so a `.mkv` renders to `.mkv` and an HEVC film stays
  HEVC. Nothing is transcoded for the sake of being modern.
- **The resolution is the source's**, unless Resize says otherwise — that is the one control that
  reaches this ([`picture/`](../picture/picture.md) §3.2).
- **The quality target is "as close to the source as the encoder can get"**, not a fixed CRF: the
  author asked for the best possible quality with the file no larger than the original after a light
  edit, and those two are only compatible if the target is measured off the input rather than chosen.
- **A new file beside the source, and never an overwrite.** Not taste — arithmetic. A re-encode is
  lossy, so writing over the source means a second edit encodes an already-encoded picture and the
  losses compound with the pristine original gone. Stills never had this problem because a recipe
  always unfolds from an untouched source, which is exactly why ME-132 deleted `Save a Copy`. **For
  video that reasoning inverts**, and the copy comes back — for this path alone.
  `copyNameBeside`-style non-clobbering naming is the existing behaviour to follow: the rule that a
  copy never silently replaces another file is already in the tree.

### 3.1a A GIF is a third entry, and deliberately the quieter one

Accepted 2026-09-03 with a condition attached: *only for short videos, and as an extra option rather
than the main one.* So `Done ▾` grows a third entry, `Render GIF…`, which

- **appears only when the result is short** — 30 s is the starting threshold and it is a number to
  set when ME-296 is promoted, not one to guess here;
- **says what it costs.** A GIF has no sound, is limited to 256 colours per frame, and for the same
  clip is usually *larger* than a muted `mp4` that also looks better. The honest framing of the
  choice is "a GIF, or just a video with no sound", and the menu should put it that way rather than
  presenting a GIF as the way to get a small file.

It is a render **target** and not a second mechanism: the same document, the same fold, ffmpeg's
palettegen and paletteuse at the end instead of a video encoder. It therefore inherits §3.3's parity
requirement unchanged.

### 3.2 Copy everything, or encode everything — decided by whether any pixel work exists

This is the mechanism behind the size promise, and it is deliberately binary.

| The document holds | What runs | Result |
|---|---|---|
| only cuts, reorders and audio gain | **stream copy** per segment, concatenated | lossless, near-instant, and *smaller* than the source by exactly what was cut |
| any pixel work at all — crop, adjust, filter, markup, speed, freeze | **re-encode**, every segment | larger or smaller depending on the edit, which is what the author expects |

**Why not per-segment hybrid**, which is the obvious better answer: a copied H.264 segment and an
encoded one carry different parameter sets, so splicing them into one track means either a mid-stream
SPS/PPS change or a second sample entry in `stsd`. Some players handle it and some produce a green
frame or refuse the file, and "some" is not a thing to ship. It is a **refinement with a probe in
front of it**, not a v1 mechanism, and it is worth taking later precisely because it would let a
colour tweak on one clip out of five leave the other four untouched.

**A copy needs its cut boundaries on keyframes**, and [`timeline/`](../timeline/timeline.md) §3.3
deliberately does not snap them. So the copy path either moves the boundary to the nearest keyframe —
changing the result the user approved, which is not acceptable — or re-encodes. **The rule is: a
boundary that is not on a keyframe demotes the render to the encode path**, and the banner says the
render is re-encoding. That keeps the strip honest, which was the point of not snapping.

### 3.3 Parity with the preview is a requirement, not an aspiration

The exported file must match what the canvas showed. Two places this is easy to lose:

- **markup timing** — the player's alpha comes from a draw-time computation and the renderer has to
  reproduce it frame by frame. One function in `commonMain`, called by both
  ([`picture/`](../picture/picture.md) §3.3);
- **the fold** — the renderer replays the same `RasterRecipe` at scale 1.0 against the decoded frame,
  which is what `renderAtNativeScale` already does for a still and the reason `drawRecipeImage` is
  shared between the preview and the save path rather than written twice.

### 3.4 In-process, following ME-181

`FfmpegStillEncoder` drives `FFmpegFrameRecorder` in-process against the bundled build, and that is
the precedent — the old plan's "run ffmpeg as a background process" was written when nothing here
encoded at all. **Read ME-181's KDoc before writing a line**: it carries the trap it cost to find,
a converter freed under a `Frame` still being read.

A whole video is not a still, so two things ME-181 never faced arrive here: a decode and an encode
running together for minutes, and the stream copy path, which is `FFmpegFrameRecorder`'s
packet-level API rather than its frame-level one. Whether JavaCV exposes enough of it is the first
measurement in this folder.

### 3.5 Progress, and outliving the editor

`FrameExportStatus` is the pattern: a global holding "what is running", a
`onProgress(done, total)` callback, and `StatusPill`/`StatusBanners` drawing it — so an export and a
save can be up at once without covering each other. A render is longer than a frame export, so it
also needs a **cancel**, which the frame export does not have, and a render still running when the
window closes has to be answered the way ME-125 answered a save in flight.

---

## 4. Open questions

- **Does JavaCV expose packet-level copy?** The gate on §3.2's fast path. If it does not, either the
  copy path uses the ffmpeg binary as a process after all — reversing §3.4 for one path — or every
  render re-encodes and the size promise is only kept by encoding well.
- **What quality target actually lands "no bigger"** on a light edit. Measured off the input's
  bitrate is the plan; whether that is per-stream, per-segment or a global average is a number
  somebody has to try on real files.
- **HDR through a render.** A PQ source re-encoded without its transfer characteristics tagged is a
  visibly wrong file. This is where the HDR tool may come back ([`picture/`](../picture/picture.md)
  §4), and it is unanswered.
- **Where the rendered file goes** — beside the source, or a folder of its own. Beside is consistent
  with everything else Woka writes.
- **Whether a render can be queued** while another is running. One at a time is the honest first
  answer.

---

## 5. Decision log

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-09-03 | Is a GIF an option, and how loud? | **A third `Done ▾` entry, short results only, saying what it costs** | Author's condition. A muted `mp4` of the same clip is smaller and looks better, so presenting a GIF as the way to a small file would be misleading (§3.1a) |
| 2026-09-03 | What does `Render video…` ask? | **Nothing. Source container, codec and resolution; quality measured off the input** | Author's call. A sheet of choices is the professional-editor interface the whole feature is defined against (§3.1) |
| 2026-09-03 | Overwrite the source? | **Never** | A lossy overwrite is irreversible and compounds on the second edit. This is the one path where a copy is right, and it is why ME-132's reasoning inverts here (§3.1) |
| 2026-09-03 | Per-segment hybrid copy/encode? | **No — all-copy or all-encode, for now** | Splicing a copied and an encoded segment into one track means a parameter-set change some players refuse. It is a refinement with a probe in front of it (§3.2) |
| 2026-09-03 | What happens when a cut is not on a keyframe? | **The whole render demotes to the encode path, and says so** | Moving the boundary would change the result the user approved, which is exactly what not snapping the cut was for (§3.2) |
| 2026-09-03 | Process or in-process ffmpeg? | **In-process, following ME-181 — unless the copy path cannot be reached that way** | ME-181 is a working precedent against the bundled build; the copy path is the one thing that may force the other answer (§3.4) |
