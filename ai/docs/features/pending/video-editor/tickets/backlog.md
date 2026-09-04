# Video Editor — Backlog

**The single backlog for all seven folders** ([`../video-editor.md`](../video-editor.md) §1). One
section per folder, in dependency order; each block says what it is for and what shape it takes, and
**names no files** — those are written when a block is promoted into its own ticket, against the tree
as it is then (`ai/docs/conventions/tickets.md`).

A promoted ticket leaves this file and lands in its folder's `tickets/`, and its row appears in that
folder's status table. Nothing here records state.

---

## Bookkeeping — 2026-09-03

The four blocks this file held until today are gone, and not one of them was dropped for being
wrong — the feature was re-scoped around a brief four times their size.

- **ME-201 — Video engine** was promoted, rewritten against the tree, and is
  [`foundation/tickets/implemented/ME-201-a-video-canvas-that-folds.md`](../../../implemented/video-editor/foundation/tickets/ME-201-a-video-canvas-that-folds.md).
  Its shape changed: it draws the player's frame through the recipe rather than folding to "a
  timeline stage", because that is what makes the five reused tools work on day one.
- **ME-202 — Trim tool**, **ME-203 — Mute tool** and **ME-204 — Video save** are **retired: the ids
  are kept and never reused** (`feature-docs.md` §3). ME-202's keyframe-snapped cuts are reversed by
  [`timeline/`](../timeline/timeline.md) §3.3; ME-203's mute is one of four audio behaviours now;
  ME-204 named `SaveTarget` and `Save a Copy`, neither of which exists, and its work is split across
  [`save/`](../save/save.md) and [`render/`](../render/render.md).

---

## 201 — Foundation — **empty: every block promoted or dropped, 2026-09-03**

Four ticket files, all written against the tree of that day and all in
[`foundation/`](../../../implemented/video-editor/foundation/foundation.md)'s status table, which is the only place their state is
recorded:

- **ME-201** — a video canvas that folds · **ME-206** — the band, the seam and a scrubber ·
  **ME-207** — the canvas opens paused, and a drag holds it · **ME-205** — HDR on the video canvas.
- **ME-208 was dropped, id retained and never reused.** It was conditional on `save/` not being next,
  and the author chose `save/` — so `EditCapability.Preview` lasts one phase and ME-280 deletes it.

Three findings came out of writing them, and they belong here rather than only in a ticket because
the next folder will meet the same walls:

- **A shader cannot be prepended to `RasterRecipe.shaders`.** The chain attaches the colour filter to
  the image shader *first*, so anything in `shaders` runs after Adjust and Filters. HDR therefore
  needs its own slot **before** the filter, and ME-205 adds `RasterRecipe.source` for it. The word
  "prepend" in the design docs predates this reading.
- **Three shell files are at the mandate's ceiling** — `EditorBottomPanel.kt` 149,
  `EditorTokens.kt` 146, `EditorScreen.kt` 150, and `RasterImageDraw.kt` 142. Two of the four
  tickets carry a split as their own numbered step, and no ticket touches `EditorScreen.kt`.
- **There is no shortcuts popover in the tree**, though the image editor's design names one. Space
  joins `Esc` and `Ctrl+Z` as an unlisted shell key; building the popover is nobody's ticket yet.

---

## 210 — Timeline

### ME-210 — The clip list and a trim that only trims
`ToolDomain.Time`, prepended to the enum. One `Single` tool owning one `ObjectParam`: an ordered list
of clips, each with a stable minted id, a source range, and the two fields the strip's layout needs.
`stage()` returns a `ToolStage` that is not a `RasterStage`, which the raster fold already drops.
Trimming the ends only; everything else is a later block.
*Depends on ME-206.*

### ME-211 — The filmstrip is the seek preview's cells
Lay `SeekPreviewSource`'s 240 px keyframe-only cells along the strip, through `SeekPreviewCache`, one
run per clip in timeline order. Nothing new decodes, and nothing decodes on the UI thread. The
expanded (76 dp) state of the band.
*Depends on ME-210.*

### ME-212 — Split at the play head
An `ActionParam` in the inspector and a ✂ on the play head; one entry becomes two at the play head's
millisecond, with no keyframe consulted. Undo is one entry per split.
*Depends on ME-210.*

### ME-213 — Select, delete, duplicate
Selection is a clip and it lives in the strip; the inspector's actions read it through the same
channel markup's rows already use. Delete closes the hole, duplicate lands beside the original with a
new id. Both disabled with no selection, and the `Delete` key has to be shared with markup's own.
*Depends on ME-212.*

### ME-214 — Reorder by dragging
Drag a clip along the row; the others slide; the drop is one history entry. No gaps, ever, and no
position anybody can get wrong.
*Depends on ME-213.*

### ME-215 — The clip scope switch, and a layer that travels
`EditScope` unchanged, `EditLayers` keyed on the clip id, the switch reading **All Clips / This
Clip**. Deleting a clip removes its layer, duplicating copies it under the new id, re-ordering does
nothing to it. `EditorFrames.allowsPerFrame` answers true for a video with more than one clip.
*Depends on ME-214.*

### ME-216 — Speed per clip
A field on the clip entry, ¼×–4×, with a detent at 1×. It changes the clip's length on the strip and
therefore where every clip after it sits. The pacer and the audio tempo are
[`playback/`](../playback/playback.md)'s halves of it.
*Depends on ME-215.*

### ME-217 — Freeze-frame
A clip whose source range is a point, plus a hold. One entry in the list, one repeated thumbnail, and
one branch where duration is computed.
*Depends on ME-216.*

### ME-218 — The strip zooms
Thirty minutes in 600 px is three seconds per pixel and a two-second cut is unmakeable. Zoom around
the play head, or a magnifier over it — the choice is the author's and it should be made with the
strip in front of them.
*Depends on ME-211.*

### ME-219 — Frame-accurate stepping
`←` / `→` step one frame, a modifier steps one second, and the readout shows the frame as well as the
time. Cheapest item in the feature and the one that decides whether a cut lands where it was meant
to; a split is only as honest as the play head's position.
*Depends on ME-206.*

### ME-220 — Snapping
The play head, and a dragged clip or sound edge, snap within a few pixels to: clip joins, the ends of
the whole timeline, a markup shape's span, and a sound entry's edges. A snap draws a one-pixel accent
line so it is visible rather than merely felt. Held modifier suppresses it.
*Depends on ME-214, ME-242.*

---

## 230 — Playback

### ME-230 — Probe: two decoders, one file
Before any of §3.2's design: open a second `FFmpegFrameGrabber` on a file the player already has
open, after `tracksProbed`, and measure the seek-and-decode-to-position cost on 1080p and 2160p, and
whether both decode at all at 4K. Print a table, record it here with the date. This is the gate on
the whole folder.
*Depends on ME-201.*

### ME-231 — Timeline time and source time
Two pure functions over the clip list, in `commonMain`, unit-tested, with speed and freeze folded in.
Every off-by-one in the feature will live here. `currentPositionMs` stays a source position and the
timeline position is derived.
*Depends on ME-210.*

### ME-232 — The pre-rolling decoder and the hand-off
Start the second decoder a measured margin before a join, into a queue of two or three frames, switch
the presenter at the boundary, then re-point the idle decoder at the clip after next. `FrameRetention`
has to keep working with frames arriving from two sources.
*Depends on ME-230, ME-231.*

### ME-233 — Sound across a join
One `SourceDataLine` for the session, fed from whichever audio decoder is current — never closed and
reopened, because that is an audible click. The audio pre-roll has to be deeper than the video's, and
by how much is a measurement.
*Depends on ME-232.*

### ME-234 — Speed and freeze in the pacer
The presenter divides its pacing by the current clip's speed and the audio runs `atempo` at it; a
freeze presents one frame for its hold. Whether rebuilding the tempo filter at a join is audible is
the open question.
*Depends on ME-216, ME-233.*

---

## 240 — Audio

### ME-240 — Mute, volume and fades
One manifest and four `ParamSpec`s, with no UI code at all — the acceptance test of the tool
architecture. Under **All Clips** it is the whole video; under **This Clip** it is one clip's layer.
Fades are linear and stated in seconds.
*Depends on ME-215.*

### ME-241 — The waveform
One audio-decode pass per file producing min/max peaks per column, cached on path plus length plus
mtime the way the poster is. A row with no waveform cannot be cut usefully, which is why this comes
before the row itself.
*Depends on ME-206.*

### ME-242 — Detach audio
The clip's sound stops following its picture and becomes an entry on a second row, trimmable and
draggable on its own. The row exists only while it holds something. The clip's volume op has to
travel with the sound, and that rule has to be written down.
*Depends on ME-240, ME-241.*

### ME-243 — Add an external audio file
An AWT `FileDialog` through `jvmMain`, a path stored relative to the video's folder when they share
one, and an honest **missing audio** state when the file has moved. The first thing in the editor
that can break because of something outside the file.
*Depends on ME-242.*

### ME-244 — Two sources at once
Summing two decoded streams with a gain each before the one sound line. It lands in the same code
ME-232 and ME-233 change, so it is sequenced after them and never written in parallel.
*Depends on ME-233, ME-243.*

### ME-245 — Extract the audio to a file
An `Output` tool: the edited audio as `m4a`, stream-copied when the codec fits the container and
encoded when it does not. It respects the cuts, because everything does.
*Depends on ME-240.*

### ME-246 — Loudness in one switch
One `BoolParam`. In the preview it is a measured gain applied to the whole timeline; in a render it is
`loudnorm`. For clips shot on different things this is the difference between usable and not, and the
measurement is one audio pass — the same pass ME-241 already makes for the waveform.
*Depends on ME-240, ME-241.*

---

## 260 — Picture

### ME-260 — Turn the four free tools on, and prove it
Crop, Transform, Adjust and Filters need no tool code — they need a test each proving the fold, the
`drawnSize` letterboxing and the source-coordinate placement are right on a video, and a by-hand pass
recorded with a date. If ME-201's measurement said the shader chain cannot run per frame, this block
is re-planned instead of promoted.
*Depends on ME-201, ME-205.*

### ME-261 — Filter-strip thumbnails from one held frame
`PreviewChoiceParam` asks the engine for a `Thumbnail` per option — fourteen for Styles. They must all
draw the same held frame, with no decode and no chain rebuild per option per recomposition.
*Depends on ME-260.*

### ME-262 — Resize declares the render's output size
Keep the tool, its slider and its `Output` position; its stage is ignored by the canvas and read only
by the renderer, and its footnote says when it applies.
*Depends on ME-291.*

### ME-263 — Markup that appears, holds and fades
A sibling `ObjectParam` on `MarkupTool` keyed by shape id, holding `(atMs, holdMs, fadeMs)`; no
`MarkupShape` variant is touched and no existing wire format changes. A shape with no entry is on for
the whole video. One alpha function in `commonMain`, called by the canvas and by the renderer so the
two cannot disagree.
*Depends on ME-215.*

### ME-265 — Auto-enhance in one tap
`AdjustAuto` (ME-167) measures **the frame at the play head, once, when the button is pressed** — not
per frame, which would make the suggestion move while the video plays — and writes an All-Clips
Adjust op. It goes in the Adjust card's existing header-action slot, so there is no new control type
and no new rail entry. This is also the answer to `picture/` §4's open question about `stats`.
*Depends on ME-260.*

### ME-266 — 9:16 and 1:1 crop presets
Crop already carries an aspect list; this names the two ratios people actually export for and centres
the kept region on the frame. A list entry each, and no mechanism.
*Depends on ME-260.*

### ME-267 — Copy another clip's look
"Apply clip 2's adjustments here" is a copy of a few ops from one clip's layer into another's, so it
is `EditLayers` and nothing new. Two `ActionParam`s — copy, paste — reading the clip selection.
**Deliberately not presets:** nothing is named, stored or listed, because a named preset is a library
to manage and this is a clipboard.
*Depends on ME-215.*

---

## 280 — Save (`Done`)

### ME-280 — An in-place write, and mp4/mov wired in
Two extensions into `carrierFor`, and a tail-carrier write that seeks, `setLength`s back over the old
box and appends — instead of reading 4 GB and writing 4 GB back. Safe **only** because a tail box
sits after all media data, so a failed write cannot damage the picture; head carriers keep
`writeAtomically` untouched. Deletes `EditCapability.Preview`.
*Depends on ME-201.*

### ME-281 — Probe: does anything else tolerate the box?
An `mp4` and a `mov` with our `uuid` box appended, opened in mpv, VLC, Windows Explorer's preview and
a phone gallery. Print what each did, record it here with the date. If a common player refuses the
file, `Done` has nowhere to write and the whole `Done ▾` menu collapses permanently.
*Depends on ME-280.*

### ME-282 — A Matroska carrier
The library is mixed, so this is second and not later. Try an unknown top-level EBML element appended
after the `Segment` first — nothing moves and no size field is rewritten — then a `Tags`/`SimpleTag`
inside it, then existing `Void` padding. The probe that matters is what other players do afterwards,
not whether it round-trips through our own reader.
*Depends on ME-281.*

### ME-283 — A RIFF carrier for avi
A trailing chunk plus the four-byte `RIFF` size at offset 4. `WebpRecipe` already does RIFF surgery
and is the thing to read before writing this.
*Depends on ME-282.*

### ME-284 — Every reader folds
The viewer's frame, the grid poster, PiP and the seek preview all decode the original and fold the
recipe — **including the cuts**, by reusing ME-232's clip walk rather than growing a second one. A
viewer that applied the crop and ignored the cut would be worse than one that ignored both.
*Depends on ME-232, ME-280.*

### ME-285 — Duration, the slider and the watch record
A trim changes how long the file is. The slider and the readout must show the edited length; the
watch record stores `positionMs` against `durationMs` and can now point past the end; probed chapters
are in source time and may have been re-ordered or cut away.
*Depends on ME-284.*

### ME-286 — Revert, and the menu the capability drives
Revert removes the box and puts the file back as it was shot. `capability` then answers per container,
and `Done ▾` shows one entry or two accordingly, saying why when it shows one.
*Depends on ME-280, ME-290.*

### ME-287 — Choose the cover
One field in the recipe holding a **timeline** position, and the tile draws that frame **through the
fold** — so a frozen first frame with a title on it appears on the grid tile, which is the point
(§950). A tiny `Cover` tool in the `Output` group: one `ActionParam` for "use this frame", one to
clear it, and a footnote naming the time in use. Absent, `VideoThumbnailHeuristics` keeps guessing as
it does today. `SaveEviction`'s poster key already changes with the write, so nothing goes stale.
*Depends on ME-284.*

---

## 290 — Render

### ME-290 — Probe: can JavaCV copy packets?
`FFmpegFrameRecorder` writes frames; a stream copy is the packet-level API and nothing here has
exercised it. Read ME-181's KDoc first — it carries the trap it cost to find. If the answer is no,
either the copy path runs the ffmpeg binary as a process, reversing that decision for one path, or
every render re-encodes and the size promise is kept by encoding well.
*Depends on ME-201.*

### ME-291 — The all-copy render
A document holding only cuts, reorders and audio gain: copy every segment and concatenate, with a new
non-clobbering name beside the source and never an overwrite. Lossless, near-instant, and smaller
than the source by exactly what was cut. A cut boundary that is not on a keyframe demotes the whole
render to ME-292 and the banner says so.
*Depends on ME-290, ME-231.*

### ME-292 — The all-encode render
Any pixel work at all, so every segment is re-encoded: the source's container, codec and resolution,
the quality target measured off the input's own bitrate, and the same `RasterRecipe` replayed at scale
1.0 that a still's save already replays. Markup alpha comes from ME-263's shared function.
*Depends on ME-291, ME-263.*

### ME-293 — Progress, cancel, and outliving the editor
`FrameExportStatus`'s shape — a global, an `onProgress(done, total)`, `StatusPill` through
`StatusBanners` — plus the two things a frame export never needed: a cancel, and an answer for a
render still running when the window closes.
*Depends on ME-292.*

### ME-294 — This frame as a photo
The current frame at coded resolution, through the recipe, written beside the video. The still save
path already renders a recipe at native scale; this is that, given a frame instead of a bitmap.
*Depends on ME-260.*

### ME-295 — Per-segment hybrid render
The refinement ME-292 gives up: copy the segments with no pixel work and encode only the rest, so a
tweak on one clip out of five leaves the other four bit-identical. It needs a probe in front of it —
splicing a copied and an encoded segment means a parameter-set change some players refuse.
*Depends on ME-292.*

### ME-296 — Render GIF
A third entry in `Done ▾`, and **secondary to the video one on purpose**: it appears only when the
result is short — 30 s is the starting threshold, to be set when this is promoted — and it says what
it costs, because a muted `mp4` of the same clip is smaller and looks better. ffmpeg's
palettegen/paletteuse over the same document, so it is a render *target* and not a second mechanism.
*Depends on ME-292.*

---

## 300 — The shared shell

*Numbered outside the video bands on purpose: these change the shell **both** editors use, so a still
is affected by every one of them and the tests that prove them are the image editor's too.*

### ME-300 — Right-click resets any input
Right-click on a control the schema renders returns it to its default; right-click on the inspector
header resets the whole tool. Every `ParamSpec` type, in the one place rows are rendered — not eight
places. The double-click-to-detent on a slider stays, because a detent is not always a default. The
gesture is free: the canvas owns the context menu and no inspector row has one.
*Depends on nothing. Design: [`../video-editor.md`](../video-editor.md) §4.1b.*

### ME-302 — promoted 2026-09-04, and split in two on the way
The block named three surfaces. The **rail** left as
[`shell/tickets/ME-302-the-hdr-tool-leaves-an-sdr-rail.md`](../shell/tickets/ME-302-the-hdr-tool-leaves-an-sdr-rail.md),
whose status is in [`shell/`](../shell/shell.md)'s table — the part folder §300 grew when it needed
one. The **two pills** stayed here as ME-303 below.

### ME-303 — promoted 2026-09-04
[`shell/tickets/ME-303-the-hdr-dropdown-leaves-an-sdr-pill.md`](../shell/tickets/ME-303-the-hdr-dropdown-leaves-an-sdr-pill.md),
status in [`shell/`](../shell/shell.md)'s table. With it, the whole of the report of 2026-09-03 is
either shipped or specified, and §300 keeps only ME-300 and ME-301.

### ME-301 — The HDR control reaches a video frame
`showHdrMode` is false for video (ME-180) because the switch reached nothing: the tone map ran in
`HdrImagePainter` with an assumed white point. ME-205 puts it in the recipe chain, which is half the
answer; the other half is a white point that does not pump between frames. **Probe first:** whether
the container's content-light-level and mastering-display side data give a stable peak, which would
make it one number read once per file rather than a measurement per frame.
*Depends on ME-205.*

---

## 950 — Emergent: costs nothing, recorded so nobody builds it twice

**Nothing here is awaiting a decision.** Every proposal of 2026-09-03 was ruled on the same day: the
eight accepted ones are blocks with ids above, silence detection went to §900, and what is left is
the two things that need no work at all.

- **A title card.** Freeze the first frame for two seconds (ME-217) and put a text markup on that
  clip (ME-263). That *is* a title card, and it is also what the cover picker (ME-287) points at when
  the author wants the grid tile to show it. Nobody should build a Titles tool.
- **A watermark.** A markup shape with no timing entry is on for the whole video
  ([`../picture/picture.md`](../picture/picture.md) §3.3). That is the whole feature.

---

## 900 — Deliberately not planned

Recorded so none of it is re-proposed:

- **Reverse playback** — refused by the author 2026-09-03.
- **A project holding several files**, a clip library, tracks. Cutting within one file
  ([`../video-editor.md`](../video-editor.md) §3.2) covers the brief; a sequence across files has
  nowhere to live and every assumption in §3 stops holding.
- **Transitions, keyframed parameter animation, layers, masks, chroma key, stabilisation.**
- **Subtitle editing.** The viewer owns subtitles and the editor does not touch them.
- **Render presets, format choice, bitrate fields.** `Render video…` asks nothing, by decision.
- **More than one external audio track**, equalisation, compression, ducking.
- **Batch editing across a grid selection.** The grid's `Edit` entry is single-selection only, as it
  is for stills.
- **Splitting at silence, or at a scene change** — **declined by the author 2026-09-03**, and for the
  right reason: it is not a reliable recipe. There are too many clips where it would cut in the wrong
  places, and a proposal-with-confirmation still costs the user a review of every guess. It was the
  only automation proposed for this feature and it is the only one refused.
