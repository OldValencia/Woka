# Video Editor — Backlog

**The single backlog for all seven folders** ([`../video-editor.md`](video-editor.md) §1). One
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
  [`foundation/tickets/implemented/ME-201-a-video-canvas-that-folds.md`](../../implemented/video-editor/foundation/tickets/ME-201-a-video-canvas-that-folds.md).
  Its shape changed: it draws the player's frame through the recipe rather than folding to "a
  timeline stage", because that is what makes the five reused tools work on day one.
- **ME-202 — Trim tool**, **ME-203 — Mute tool** and **ME-204 — Video save** are **retired: the ids
  are kept and never reused** (`feature-docs.md` §3). ME-202's keyframe-snapped cuts are reversed by
  [`timeline/`](timeline/timeline.md) §3.3; ME-203's mute is one of four audio behaviours now;
  ME-204 named `SaveTarget` and `Save a Copy`, neither of which exists, and its work is split across
  [`save/`](save/save.md) and [`render/`](render/render.md).

---

## 201 — Foundation — **empty: every block promoted or dropped, 2026-09-03**

Four ticket files, all written against the tree of that day and all in
[`foundation/`](../../implemented/video-editor/foundation/foundation.md)'s status table, which is the only place their state is
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

### ME-210…ME-214 — promoted 2026-11-01 as one ticket
[`timeline/tickets/ME-210-cut-the-film-on-the-strip.md`](timeline/tickets/ME-210-cut-the-film-on-the-strip.md),
status in [`timeline/`](timeline/timeline.md)'s table. ME-211…ME-214 are folded in and not reused.

### ME-215 — promoted 2026-11-02
[`timeline/tickets/ME-215-this-clip.md`](timeline/tickets/ME-215-this-clip.md).

### ME-216 — promoted and done 2026-11-04
[`timeline/tickets/ME-216-speed-per-clip.md`](timeline/tickets/ME-216-speed-per-clip.md) — with
ME-234's speed half (the pacer and `atempo`) folded in.

### ME-217 — promoted and done 2026-11-04
[`timeline/tickets/ME-217-freeze-frame.md`](timeline/tickets/ME-217-freeze-frame.md) — with
ME-234's freeze half folded in.

### ME-218 — promoted and done 2026-11-04
[`timeline/tickets/ME-218-the-strip-zooms.md`](timeline/tickets/ME-218-the-strip-zooms.md).

### ME-219 — done 2026-11-05 without a ticket, checked by the author
`←` / `→` step one second of the result and `Alt+←` / `→` one frame — YouTube's arrows, the
author's call the same day, after the first cut had them the other way round; `❙◀` `▶❙` flank
the band's transport, and the readout is `m:ss:ff` (`FrameSteps.kt`, `FrameStepsTest`). The
viewer moved with it: on a film the arrows seek ±5 s and `Alt` changes file, on a picture they
change file; the ±5 s and ‹ › buttons name their keys in a tooltip.

### ME-221 — new, promoted 2026-11-05
[`timeline/tickets/ME-221-the-clip-tabs-are-the-scope.md`](timeline/tickets/ME-221-the-clip-tabs-are-the-scope.md)
— the selected clip is the scope; the `All Clips` / `This Clip` switch goes for a film.

### ME-220 — Snapping
The play head, and a dragged clip or sound edge, snap within a few pixels to: clip joins, the ends of
the whole timeline, a markup shape's span, and a sound entry's edges. A snap draws a one-pixel accent
line so it is visible rather than merely felt. Held modifier suppresses it.
*Depends on ME-214, ME-242.*

---

## 230 — Playback

### ME-230 — done 2026-11-04
Measured; table and verdict at the end of [`playback/`](playback/playback.md). Two 4K decoders run at
~60 fps each; the pre-roll lead is the keyframe distance, up to ~2.2 s on a long-GOP stream.

### ME-230 — Probe: two decoders, one file (as written)
Before any of §3.2's design: open a second `FFmpegFrameGrabber` on a file the player already has
open, after `tracksProbed`, and measure the seek-and-decode-to-position cost on 1080p and 2160p, and
whether both decode at all at 4K. Print a table, record it here with the date. This is the gate on
the whole folder.
*Depends on ME-201.*

### ME-231 — done 2026-11-01 inside ME-210
Without speed and freeze; status in [`playback/`](playback/playback.md)'s table.

### ME-232 — promoted 2026-11-04
[`playback/tickets/ME-232-joins-without-a-stall.md`](playback/tickets/ME-232-joins-without-a-stall.md),
status in [`playback/`](playback/playback.md)'s table. Re-cut against ME-230's numbers: a pool of up
to four lanes at four threads, pre-rolling as soon as a lane is free, the engine owning the list.
ME-234's remainder — pace and holds in the engine rather than the walks — is folded into it.

### ME-233 — promoted and built 2026-11-05
[`playback/tickets/ME-233-sound-across-a-join.md`](playback/tickets/ME-233-sound-across-a-join.md),
status in [`playback/`](playback/playback.md)'s table. Re-cut by a measurement: an audio seek costs
1–11 ms, so no audio lanes — the one audio grabber splices to the sample with a 5 ms ramp.

### ME-235 — dropped 2026-11-05, id retained
Holding short ranges in memory does not lift the bound it was for: filling the memory takes the
same decode from the keyframe as a lane's pre-roll, and the author ruled out spending RAM on
frames. A cheaper pre-roll was measured instead (`playback.md`, `PrerollProbe`) and nothing in
software reaches it — the cost is decoding reference frames. What stands: four lanes cover runs
of ranges down to ~0.75 s on a long-GOP stream and ~0.15 s on an ordinary one, and a join past
that waits on a lane already part way there, never longer than a seek. Idle lanes now close after
3 s instead of 10.

### ME-236 — Hardware decoding for the lanes
The only thing measured to move the pre-roll bound. Needs a hardware device context JavaCV does
not set up for a grabber, and a way past the D3D11VA scaling bugs that made the player decode in
software (`openVideoGrabber`). Probe first: GPU HEVC pre-roll time and memory on S02E01, and
whether frames come back whole.
*Depends on ME-232.*

### ME-234 — folded, id retained
Speed half into ME-216, freeze half into ME-217, the rest — pace and holds owned by the engine — into
ME-232.

---

## 240 — Audio

### ME-240 — promoted and done 2026-11-04
[`audio/tickets/ME-240-mute-volume-fades.md`](audio/tickets/ME-240-mute-volume-fades.md).

### ME-241 — The waveform — done 2026-11-10 without a ticket
A sound row under the strip, opened by a speaker right of the readout (the author, 2026-11-10);
`audio.md` §3.6. Tests: `AudioPeaksTest`, `SoundRowUiTest`. As specified:
One audio-decode pass per file producing min/max peaks per column, cached on path plus length plus
mtime the way the poster is. A row with no waveform cannot be cut usefully, which is why this comes
before the row itself.
*Depends on ME-206.*

### ME-242 — Detach audio
The clip's sound stops following its picture and becomes an entry on a second row, trimmable and
draggable on its own. The row exists only while it holds something. The clip's volume op has to
travel with the sound, and that rule has to be written down.
*Depends on ME-240, ME-241.*

### ME-243 — folded into ME-244 2026-11-05, id retained
The author merged the two: adding a file and laying tracks over the film are one feature.

### ME-244 — Insert external audio tracks
Re-scoped by the author 2026-11-05, with ME-243 folded in: **one or several outside audio files
laid over the film**. Each comes in through an AWT `FileDialog` via `jvmMain`, is stored by a path
relative to the video's folder when they share one, and becomes an entry on a sound row —
trimmable and movable — mixed with the film's own sound at a gain each before the one sound line.
A file that has moved shows an honest **missing audio** state: the first thing in the editor that
can break because of something outside the file. It lands in the code ME-232 and ME-233 changed —
the spliced sound — so it is written after them.
*Depends on ME-233, ME-242.*

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

### ME-260 — done, checked by the author 2026-11-05
Crop, Transform, Adjust and Filters work on a video — on the canvas, per clip through This Clip
(ME-215) and in a render. Checked by hand rather than through the per-tool tests the block named.

### ME-261 — dropped 2026-11-05, id retained
The author does not want filter-strip thumbnails for video.

### ME-262 — done 2026-11-02, in ME-291
The author chose *render only*: the render reads Resize through the fold, the viewer ignores it, and
the inspector says "Applies when you render the video" (`EditorEngine.toolNote`).

### ME-263 — promoted 2026-11-02
[`picture/tickets/ME-263-markup-appears-holds-fades.md`](picture/tickets/ME-263-markup-appears-holds-fades.md).

### ME-268 — dropped 2026-11-10, id retained
[`picture/tickets/ME-268-an-area-for-a-look.md`](picture/tickets/ME-268-an-area-for-a-look.md) — rectangle and ellipse areas;
built, then reverted unshipped: a fixed area is of little use on a moving picture without tracking,
and it made the colour tools canvas tools (`video-editor.md` §6, 2026-11-10).

### ME-265 — dropped 2026-11-05, id retained
The editor already has auto-enhance, and the author judged a better one is not reachable without a
learned model.

### ME-266 — dropped 2026-11-05, id retained
Crop already offers 1:1, 4:3, 3:2 and 16:9 (`CropAspect`), and its Orientation switch turns any of
them upright — 16:9 + Portrait is 9:16 (`cropRatioIn`). A separate 9:16 chip would duplicate that and
read backwards under Portrait; the author kept it as it is.

### ME-267 — done 2026-11-05 without a ticket, checked by the author
Re-cut with the author: not two `ActionParam`s but **`Copy` and `Paste ▾` on the clip bar**, where
the selection already is. `Copy` takes the clip's own work whole; `Paste ▾` offers Look, Sound, or
Look and Sound, and **replaces** those kinds on the target (`ClipLook.kt`, `pasteOnClip`). A paste is
one undo step: a history entry now carries the off-screen clip layer it changed (`LayerChange`,
`stepLayers`), so `Ctrl+Z` puts it back (`ClipPasteTest`). Still a clipboard, not presets.
The same day the strip learned to pick clips in **every** tool once there are two — tabs, and a
bar with `Copy` · `Paste ▾` alone — while cutting stays in the tool, renamed **Clips** (id still
`trim`; `timeline.md` §6, `ClipLookUiTest`).

---

## 280 — Save (`Done`)

### ME-280 — promoted 2026-09-04, and split in two on the way
The block did the write *and* the wiring. The write left as
[`save/tickets/ME-280-a-tail-carrier-writes-in-place.md`](save/tickets/ME-280-a-tail-carrier-writes-in-place.md);
the wiring stayed here as ME-288 below, because together they are thirteen files and a mandated split
of a 150-line one.

### ME-288 — promoted 2026-09-04
[`save/tickets/ME-288-mp4-and-mov-carry-an-edit.md`](save/tickets/ME-288-mp4-and-mov-carry-an-edit.md),
status in [`save/`](save/save.md)'s table. It is where `EditCapability.Preview` dies, and where an
`mkv` loses `Edit` until ME-282 gives it a carrier.

### ME-281 — promoted 2026-09-04
[`save/tickets/ME-281-does-anything-else-tolerate-the-box.md`](save/tickets/ME-281-does-anything-else-tolerate-the-box.md),
status in [`save/`](save/save.md)'s table. The probe writes the files; the four programs have to
be opened by hand, so it stays open until its § *Measured* is filled in.

### ME-282 — promoted 2026-09-04, done 2026-11-04
[`save/tickets/ME-282-a-matroska-carrier.md`](save/tickets/ME-282-a-matroska-carrier.md), status
in [`save/`](save/save.md)'s table.

### ME-283 — promoted and done 2026-11-04
[`save/tickets/ME-283-a-riff-carrier-for-avi.md`](save/tickets/ME-283-a-riff-carrier-for-avi.md).

### ME-284 — promoted 2026-11-02
[`save/tickets/ME-284-every-reader-folds.md`](save/tickets/ME-284-every-reader-folds.md), status in
[`save/`](save/save.md)'s table. Built on ME-210's walk rules, not on ME-232, which does not exist yet.

### ME-285 — done 2026-11-05 without a ticket, checked by the author
The slider and the readout already showed the edited length (ME-284). **The watch record:** a
film's `Done` clears it **when it changes the result's length** — a cut, a speed, a freeze
(`VideoEditorOutputs.save`, `VideoSaveWatchTest`); a look keeps it (the author, same day). **Chapters:** re-decided once the tree was read — the slider already lays them on
the result (`chapterSegmentsOnResult`, ME-284), so only the ⚙ list goes, and only on a film that
is cut: its rows seek in source time. Editing chapters is planned for 1.0.9.

### ME-286 — already built, recorded 2026-11-05
Revert is in the top bar (`RevertDialog`), and a film whose container cannot carry an edit opens
render-only, its `Done ▾` saying so (ME-289, `EditCapability.RenderOnly`).

### ME-287 — done 2026-11-05 without a ticket, checked by the author
`CoverTool` in `Output`: `Use this frame` writes the play head's timeline time through the new
`EditorTool.atPlayHead` hook (`EditorEngineSurface.playHeadMs` → `PictureFacts`), `Pick
automatically` clears it. `RecipeVideoEdits` maps it to source time, clamped inside the result, and
`VideoThumbnailExtractor` takes the poster there instead of guessing; the poster is drawn through
the cover's own clip's look. Under This Clip the tool says the cover is the whole video's.

---

## 290 — Render

### ME-290…ME-293 — done 2026-11-01
ME-290 the probe, ME-291 the render with ME-292 and ME-293's progress and cancel folded in —
[`render/tickets/ME-291-render-video.md`](render/tickets/ME-291-render-video.md). ME-293's
*a render still running when the window closes* is answered by a scratch file deleted on exit.

### ME-294 — This frame as a photo — done 2026-11-10 without a ticket
`Done ▾` → **Save frame as photo** (`render/FramePhoto.kt`): the frame under the play head,
decoded exactly, through its clip's look with markup at that moment and the canvas's HDR mode, at
the coded size and without Resize, turned upright, as `name 1m05s.jpg` (quality 100, the author) beside the film —
numbered, never an overwrite. The play head is stamped on the export before the editor closes
(`EditorExport.atMs`). A film that cannot keep an edit gets it under the `Render video…` button's
caret, which lists the exports beside the button. Test: `FramePhotoTest`.

As specified:
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

### ME-300 — already built, recorded 2026-11-05
Right-click resets a control through the app's own `resetOnRightClick` — the editor's sliders, the
clip-speed slider, and on the strip the whole cut (`StripActions`).

### ME-302 — promoted 2026-09-04, and split in two on the way
The block named three surfaces. The **rail** left as
[`shell/tickets/ME-302-the-hdr-tool-leaves-an-sdr-rail.md`](shell/tickets/ME-302-the-hdr-tool-leaves-an-sdr-rail.md),
whose status is in [`shell/`](shell/shell.md)'s table — the part folder §300 grew when it needed
one. The **two pills** stayed here as ME-303 below.

### ME-303 — promoted 2026-09-04
[`shell/tickets/ME-303-the-hdr-dropdown-leaves-an-sdr-pill.md`](shell/tickets/ME-303-the-hdr-dropdown-leaves-an-sdr-pill.md),
status in [`shell/`](shell/shell.md)'s table. With it, the whole of the report of 2026-09-03 is
either shipped or specified, and §300 keeps only ME-300 and ME-301.

### ME-301 — promoted 2026-11-05, and cut in three on the way
The probe answered yes — a stable, truthful peak on every frame's side data — and found a native
leak in JavaCV's RGBA path on the way (ME-237, `playback/`). The block became
[ME-301](shell/tickets/ME-301-the-films-own-white-point.md) (the file's own white point),
[ME-304](shell/tickets/ME-304-the-hdr-menu-reaches-a-video.md) (the pill on a video) and
[ME-305](shell/tickets/ME-305-hdr10-plus-scene-peaks.md) (HDR10+ scene peaks), status in
[`shell/`](shell/shell.md)'s table. §300 keeps only ME-300.

---

## 950 — Emergent: costs nothing, recorded so nobody builds it twice

**Nothing here is awaiting a decision.** Every proposal of 2026-09-03 was ruled on the same day: the
eight accepted ones are blocks with ids above, silence detection went to §900, and what is left is
the two things that need no work at all.

- **A title card.** Freeze the first frame for two seconds (ME-217) and put a text markup on that
  clip (ME-263). That *is* a title card, and it is also what the cover picker (ME-287) points at when
  the author wants the grid tile to show it. Nobody should build a Titles tool.
- **A watermark.** A markup shape with no timing entry is on for the whole video
  ([`../picture/picture.md`](picture/picture.md) §3.3). That is the whole feature.

---

## 900 — Deliberately not planned

Recorded so none of it is re-proposed:

- **Reverse playback** — refused by the author 2026-09-03.
- **A project holding several files**, a clip library, tracks. Cutting within one file
  ([`../video-editor.md`](video-editor.md) §3.2) covers the brief; a sequence across files has
  nowhere to live and every assumption in §3 stops holding.
- **Transitions, keyframed parameter animation, layers, brush masks, chroma key, stabilisation.**
- **Subtitle editing.** The viewer owns subtitles and the editor does not touch them.
- **Render presets, format choice, bitrate fields.** `Render video…` asks nothing, by decision.
- **More than one external audio track**, equalisation, compression, ducking.
- **Batch editing across a grid selection.** The grid's `Edit` entry is single-selection only, as it
  is for stills.
- **Splitting at silence, or at a scene change** — **declined by the author 2026-09-03**, and for the
  right reason: it is not a reliable recipe. There are too many clips where it would cut in the wrong
  places, and a proposal-with-confirmation still costs the user a review of every guess. It was the
  only automation proposed for this feature and it is the only one refused.
