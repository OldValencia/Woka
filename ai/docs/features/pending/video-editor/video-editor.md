# Feature: Video Editor (Roadmap Phase 5b)

The second half of Phase 5, and the umbrella over seven features that ship separately. Everything
structural already exists: the document, the history, the tool contract, the schema-driven inspector,
the shell, the shortcut table, the save banner — [the image editor](../../implemented/image-editor/image-editor.md)
built all of it as ME-001…ME-182, deliberately media-agnostic. What is missing is a canvas that draws
a moving picture, a strip that cuts one, an audio model, and two ways out to a file.

**Restructured 2026-09-03**, from one folder with four backlog blocks into seven feature folders with
one shared backlog. The reason is §1: the author's brief is several times the size the old scope
described, and a single status table covering "an engine", "a trim tool" and "an audio mixer" says
nothing useful about any of them.

```
video-editor/
  video-editor.md   ← you are here. The vision, the architecture every folder rests on, the UI
                      grammar, and the decision log. It holds no ticket rows.
  tickets/
    backlog.md      THE backlog — every unpromoted block for every folder below, one section each
  foundation/ timeline/ playback/ audio/ picture/ save/ render/
                    one feature each: <name>/<name>.md plus tickets/ for promoted tickets only
```

Conventions: `ai/docs/conventions/feature-docs.md` · `tickets.md` · `plans.md` · `code-style/README.md`.
The shell this inherits unchanged, and the architecture it extends:
[`image-editor.md`](../../implemented/image-editor/image-editor.md) §3 and §4.

---

## 0. What the author asked for — 2026-09-03

Quoted rather than paraphrased, because every decision below is a consequence of it:

> Я хочу максимально простой, но одновременно крайне функциональный видеоредактор. Мой референс —
> это iOS Photos… без перегруженных интерфейсов профессиональных редакторов, без многослойных
> автоматизаций. Однако я бы хотел ещё возможность нарезать видосы (например вырезать из центра
> часть и удалить её либо вставить её в другое место) и аудио в видео. UI я бы хотел сделать таким,
> чтобы любой без особой трудности мог сразу же разобраться.

And, in the same session, that the image editor's tools are to be **reused, not rewritten**: Resize,
Crop, Adjust, Filters, Markup.

Answered the same day, and these answers are what the seven folders are drawn around:

| Question | Answer |
|---|---|
| What does "audio in video" mean? | **All four**: audio follows the cuts, audio can be detached onto its own row, an external audio file can be laid over the video, and the audio can be extracted to a file of its own |
| Playback across a cut | **Must be seamless.** A 0.1–0.3 s stall at every join was offered and refused, so the editor needs a decoder that pre-rolls the next segment |
| Containers in the library | **Mixed.** So the Matroska carrier is the second piece of work in `save/`, not a someday |
| Time effects | **Speed per clip, freeze-frame, and "this frame as a photo".** **Reverse was refused** |
| What `Render video…` asks | **Nothing.** The best quality it can manage, sized against the original — and, in the author's words, a re-render after few edits should come out *no larger* than the source |
| The second row | **Appears when asked**, never by default |
| Phase 0 | **The foundation alone, with no save**, so the frame-rate question is answered before anything is built on top of it |

---

## 1. Seven features, and why not one

The old document treated the video editor as four tickets on the image editor's shell. That was true
of the scope it described — a keyframe-snapped trim and a mute switch — and it is not true of the
brief above. Cutting, seamless playback, four audio behaviours, five reused tools, two containers'
carriers and a hybrid renderer are not four tickets; they are seven bodies of work that fail
independently and ship independently.

| Folder | What it owns | State |
|---|---|---|
| [`foundation/`](foundation/foundation.md) | An `EditorEngine` for `MediaKind.Video`: the player's frame drawn through the recipe, HDR in the chain, frame retention, playback on the canvas, the timeline seam | **fully specified** — ME-201, ME-206, ME-207, ME-205 written; nothing built |
| [`timeline/`](timeline/timeline.md) | The strip and the document behind it: trim, split, select, delete, reorder, per-clip scope, speed, freeze-frame | not started |
| [`playback/`](playback/playback.md) | Playing an *edited* timeline seamlessly — the pre-rolling second decoder, and the audio that follows it | not started |
| [`audio/`](audio/audio.md) | Mute, volume and fades per clip; detach; an external track; extract to a file | not started |
| [`picture/`](picture/picture.md) | Making Crop, Resize, Adjust, Filters and Markup *true* on a moving picture, and markup that appears, holds and fades | not started |
| [`save/`](save/save.md) | `Done`: a recipe inside a video container, written without copying the file, folded by every reader | not started |
| [`render/`](render/render.md) | `Render video…`: a new file, copying every segment it can and re-encoding only what it must | not started |

**This table is a feature roll-up and deliberately carries no ticket rows.** Each folder's own
document holds the only status table for its own tickets (`feature-docs.md` §4). A ticket state
recorded in two files is a ticket state that will disagree with itself.

**Numbering.** The `ME-` prefix stands for **Media Editor** and covers all of Phase 5 — which is why
the video work was numbered into it rather than given a prefix of its own (decision 2026-08-24, and
it stands). Bands inside the 200s, one per folder: foundation **201–209**, timeline **210–229**,
playback **230–239**, audio **240–259**, picture **260–279**, save **280–289**, render **290–299**.
ME-202, ME-203 and ME-204 are **retired**, their ids kept and never reused (`feature-docs.md` §3):
the work they named is redistributed across four folders whose blocks are written fresh.

Two sections of the backlog sit **outside** those bands, and deliberately. **§300 — the shared
shell** holds work that changes the shell *both* editors use, so a still is affected by every one of
it; §4.1b's right-click reset is the first. **§950** holds what needs no work at all — two things that
fall out of features already planned, recorded so nobody builds them twice. Everything proposed on
2026-09-03 was ruled on the same day: eight accepted with ids, one declined into §900.

---

## 2. Scope

**In:** everything in §0's table, in the shape §3 and §4 give it.

**Out, and decided rather than deferred:**

- **Reverse playback.** Refused by the author 2026-09-03. Decoding backwards is not something a
  decoder does — it means buffering every frame between two keyframes — and it buys one effect.
- **A project holding several files.** An `EditDocument` describes edits to *one* file and lives in
  that file's own metadata. A sequence of clips from different files has nowhere to live, and needs a
  project, a clip library and a timeline of tracks. That is the line between this and iMovie, and it
  is where the feature stops. **Cutting within one file is not that** — see §3.2.
- **Transitions, keyframed parameter animation, layers, masks, chroma key, stabilisation.**
- **Precise (non-keyframe) cuts in the copy path.** A cut that must land mid-GOP re-encodes, and
  `render/` says what that costs.
- **Subtitles.** The viewer owns them; the editor does not touch them.

---

## 3. The five loads

Everything in the seven folders rests on these. They are stated once, here, and no folder restates
them.

### 3.1 The recipe already reaches a video frame

`VideoPlayerState` publishes `currentFrame: ImageBitmap`, replaced 24–60 times a second.
`RasterSurface(source, sourceSize, recipe)` draws *an* `ImageBitmap` through a folded document. The
video canvas is therefore the composition of two things that already exist — and **the five reused
tools arrive by wiring rather than by porting.**

Measured against the tree, 2026-09-03, and this is the finding that shrinks the whole feature:
**every tool already declares support for video.** `ToolManifest.supports` defaults to
`setOf(MediaKind.Image, MediaKind.Video)` and not one tool in `tools/` overrides it, so
`registry.tools(MediaKind.Video)` returns the full rail today. Nothing has to be marked, moved or
generalised for Crop, Adjust, Filters, Markup and Resize to appear.

Two consequences, both real work and both shaped:

- **HDR.** A frame that needs colour management is drawn today by `HdrImagePainter`, which
  `RasterSurface` does not go through. `RasterStage.Shader` is the seam: `HDR_SHADER` declares
  `vec2 scale, float transfer, float wideGamut, float whitePoint, shader image`, which is exactly the
  chain's packing convention — declaration order, little-endian, and no appended `resolutionScale`
  because it samples one pixel. So the HDR pass becomes the chain's first stage rather than a second
  draw path.
- **Frame retention.** `FrameRetention.acknowledge(currentFrameId)` is what frees a 15 MB frame, and
  it is called today from `MediaViewerFrame`'s draw scope. A canvas that draws frames and does not
  call it does not leak slowly — it holds every frame of the file.

**The unmeasured gate.** The image canvas folds once per bitmap; this one folds per presented frame.
A document with no shader costs one `drawImage` with a colour filter attached, which is what the
viewer does today; a document *with* one builds a Skia shader chain per draw and closes it again.
Whether that survives at 60 fps on 4K is the first step of ME-201, and it is why Phase 0 carries no
save: if the answer is no, [`picture/`](picture/picture.md) changes shape and nothing built on top of
the foundation is wasted.

### 3.2 One file, so cutting fits the document

The old document declined cutting outright: *"an `EditDocument` describes edits to one file… a
sequence is a document about several files and has nowhere to live."* **That is true of a sequence and
false of the brief.** Cutting a piece out of the middle of a clip and moving it elsewhere in the
*same* clip is a re-ordering of ranges of **one** source file — one op, on one file, in that file's
own metadata, with no project and no clip library.

```
source   0 ─────────────────────────────────────────────── 40s
clips    [ 0.0 – 8.4 ] [ 22.1 – 29.6 ] [ 8.4 – 22.1 ]
                        ↑ moved forward   ↑ what used to be the middle, now last
```

An ordered list of `(fromMs, toMs)` over one source, plus a per-clip payload, is a few hundred bytes
of the same JSON the stills already carry. Delete-the-middle is dropping an entry; move-it-elsewhere
is re-ordering the list; trim is the first and last entry's bounds. **Nothing in §5 of the image
editor's design stops holding.**

What it does cost is [`playback/`](playback/playback.md): a list of ranges is trivial to *store*, and
is the whole reason the editor needs a decoder that can cross a join without stalling.

### 3.3 A clip is a frame — the scope machinery exists

"Adjust seconds 10–20 rather than the whole clip" was listed as a someday extra. It is not an
extension at all: **split at 10 and at 20, select that clip, edit it.** A clip is a time range, and
the file's edit already has exactly this shape:

| Stills (shipped: ME-156, ME-169, ME-175, ME-176) | Video |
|---|---|
| `EditScope.AllFrames` / `SingleFrame` | All Clips / This Clip — the same enum, the same switch |
| `EditLayers(base, frames: Map<Int, EditDocument>, touched)` | the same type, keyed on clip index |
| `EditorFrames(count, overridden, storedScope)` | the same type, `count` = clips |
| `storedDocumentIn(frameIndex)`, `storedBase()` | the same two methods |
| the strip's ● marking a frame that carries its own edit | a clip that carries its own edit, marked the same way |

So per-clip editing is a **binding**, not a mechanism, and the words on the switch are the only thing
that changes. `EditorFrames.allowsPerFrame` excludes an animation because an edit on one frame of
eight is a flicker; a clip is seconds long, so video wants the opposite answer — and that rule
already lives in exactly one place.

### 3.4 The timeline is an engine surface, not a tool overlay

A `CanvasTool.Overlay` draws over the **canvas**. The strip is not the canvas, and a trim handle
dragged on the picture is not a design anybody wants. Three ways out were considered:

1. **The shell grows a timeline slot and asks the engine to fill it** — one
   `@Composable fun Timeline(document, modifier) = Unit` on `EditorEngineSurface`, defaulted to
   nothing, exactly as `Thumbnail` already is. The shell places it in the column it already builds
   and names no `MediaKind`. **Chosen.**
2. The shell grows a timeline of its own, reading a timeline model. Rejected: it puts time into
   `commonMain` shell code for the sake of one medium, which is the branch §3.3 of the image editor's
   design exists to prevent.
3. The trim tool's overlay is re-pointed at a second surface. Rejected: `EditorToolScope` would have
   to describe two canvases, and every existing overlay would have to say which one it wants.

The band is drawn by the video engine and writes through `EditorSession` exactly as an overlay does,
so undo, preview-vs-commit and history granularity stay the shell's and never become the strip's.

### 3.5 Two saves, and a render that copies what it can

`Done` is the only save for a still (§5, image editor): the document goes into the file's private
metadata and pixels are never rewritten. For video that is worth **more**, not less — a re-encode is
slow *and* lossy — but it cannot be the only save, because a clip sent to somebody must not require
them to have Woka. So `Done` becomes a split button (§4.5), and both halves are real work:

- **`Done`** → [`save/`](save/save.md). The recipe goes into the container. `IsoBmffRecipe` already
  writes the `uuid` box that `mp4` and `mov` use; Matroska needs a carrier of its own; and the write
  path has to stop copying the file (§5).
- **`Render video…`** → [`render/`](render/render.md). A new file, never an overwrite — a lossy
  overwrite is irreversible, and a second edit would then encode an already-encoded picture with the
  pristine original gone.

**The renderer copies before it encodes, and that is the answer to the author's size requirement.** A
document holding only cuts touches no pixel, so each segment is a stream copy and the output carries
the input's own frames — instant, lossless, and *smaller* than the source by exactly what was cut.
Only a segment carrying pixel work — crop, adjust, filter, markup — is re-encoded, and only that
segment. "Few edits, no bigger" therefore falls out of the design rather than being a quality knob.

---

## 4. UI

### 4.1 Layout

The image editor's layout with one band added. Nothing else moves.

```
┌───────────────────────────────────────────────────────────────────┐
│ < Revert   [−  Fit·42% ▾  +│HDR│ⓘ] [◉]   ↶ ↷ [Done ▾]  — ❐ ✕ │  the SHELL's bar (§4.1a)
├───────────────────────────────────────────────────────────────────┤
│                                                                   │
│                    ┌───────────────────────┐                      │
│                    │  the player's frame,  │                      │  canvas: engine.Surface —
│                    │  through the recipe   │                      │  the frame folded per draw
│                    └───────────────────────┘                      │
│                                                                   │
│              ╭──────────────┬──────────────╮                      │  clip scope — only once
│              │  All Clips   │  This Clip   │                      │  there is more than one clip
│              ╰──────────────┴──────────────╯                      │
│         ╭─────────────────────────────────────────╮               │
│         │  Brightness  ──────●────────      +12   │               │  inspector — unchanged,
│         │  Contrast    ─────●─────────       −4   │               │  schema-driven
│         ╰─────────────────────────────────────────╯               │
│    ╭──────────────────────────────────────────────────────╮       │
│    │ ▶  ▓▓▓▓▓▓▓▓▓▓│▓▓▓▓▓▓▓▓▓▓▓▓▓▓│▓▓▓▓▓▓   0:12 / 0:34   │       │  THE BAND — always there
│    ╰──────────────────────────────────────────────────────╯       │  for a video, never moves
│         ╭───────────────────────────────────────────╮             │
│         │ ✂ Trim │ ⛶ Crop │ ◐ Adjust │ ♪ Audio │ ✎ … │             │  rail — unchanged
│         ╰───────────────────────────────────────────╯             │
└───────────────────────────────────────────────────────────────────┘
```

**The band sits between the inspector and the rail**, so it and the rail are the two fixed things at
the bottom edge and the inspector opens *above* it. The strip therefore never moves when a tool is
picked — which matters, because the strip is where the selection lives, and a selection that jumps
under the cursor is the opposite of "anyone can work it out immediately".

Geometry, colour and timing: [`ai/mockups/design/video-editor.html`](../../../../mockups/design/video-editor.html),
which is the spec and not a proposal — a disagreement is settled by changing it first. It reuses
`editor.html`'s tokens verbatim.

### 4.1a The top bar is the shell's, and the video editor changes nothing about it

**The editor draws no top bar** — read against the code 2026-09-03, and worth stating because the
first draft of this document drew one. `EditorTopBarSetup` fills `GlobalTopBar`'s accessory slots and
restores every one of them on dispose; it used to draw its own bar, which put two on screen at once
with the folder path above an editor that had already said which file it was editing.

| Slot | While the editor is up |
|---|---|
| Back | `<`. **Back *is* cancel** — one control, one meaning, which is what the viewer already does. There is no `Cancel` button |
| Leading (the breadcrumb's slot) | **`Revert`**, red, always drawn and disabled when there is nothing to remove. Beside Back rather than beside undo/redo: those two give work back and this one throws it away |
| Centre | **the viewer's own pill, the very same lambda** — `[− Fit·42% ▾ +]` then `HDR` then `ⓘ`. Wrapping it would replay its enter animation, so it is not touched; the carousel eye and `Edit` retract as one group. Opened from the grid there is no pill to borrow and the editor brings one |
| Centre-trailing | the **peek** button — hold to see the original. Its own container beside the pill, for the reason above |
| Right | `↶` `↷` `[Done ▾]` |
| After them | `WinControls` — minimise, maximise, close. The shell's, untouched |

Two consequences the video half must not get wrong:

- **The bar is an ordinary themed surface, not a scrim.** Its controls read `Theme.colors`; reading
  the `onScrim*` set there would put near-white text on a light background. Only what sits *on the
  picture* — the band, the inspector, the rail — takes `ViewerChrome`.
- **The `HDR` control is hidden for video today** (ME-180): everything ME-177 built runs at the still
  decode, so the switch reaches nothing on a moving frame, and a control that moves nothing is worse
  than no control. **§3.1's HDR stage is what makes it reachable** — once the tone map is in the
  recipe chain the switch has something to switch. It needs one more thing than the stage: a white
  point that does not pump between frames, which is the `save/`-adjacent probe named in the backlog.

There is **no file name in the bar**. It left with the second bar, and the "edited" mark went to the
Information panel — the thing that already answers *what is this file*.

### 4.1b Right-click resets — every input, one gesture

**Right-clicking any control the schema renders returns it to its default**, and right-clicking the
inspector's header resets the whole tool. It holds for every `ParamSpec` type: slider, stepper,
switch, segmented control, chip row, preview strip, swatch row, pad.

Why it is worth a rule of its own rather than a per-control affordance: the schema renders eight row
types today and a double-click reset only ever meant something on the two that have a track. One
gesture that works everywhere is one thing to learn; eight controls with three reset conventions
between them is what the schema-driven inspector exists to avoid. The existing double-click-to-detent
on a slider stays — it is a *detent*, which is not always the default.

The gesture is free: the canvas owns the right-click context menu, and no inspector row has one.
Applies to the **image** editor identically, so it is a shared-shell ticket
([backlog](tickets/backlog.md) §300) rather than a video one.

### 4.2 The strip, in two states

One control, two states, and the second is reached only by picking Trim:

- **Resting (44 dp).** Thumbnails, a play head, `0:12 / 0:34`, a play/pause affordance. It reads as
  the viewer's scrubber, because for every tool other than Trim that is what it is for. Clicking
  scrubs; clicking a clip selects it.
- **Trim selected (76 dp).** The outer handles appear on the first and last clip, the area outside
  them dims, a **✂** sits on the play head, and each join grows a grip. Clips become draggable.

Thumbnails come from `SeekPreviewSource` and `SeekPreviewCache`, which already decode 240 px stills
keyframes-only on one dedicated thread with an LRU in front — the filmstrip is a row of the cells
that machinery already produces, and a second decoder for it would be the mistake the seek preview
was written to avoid.

### 4.3 The grammar — one row, no gaps

Four rules, and they are what keep this out of iMovie:

1. **There is exactly one picture row.** No layers, no tracks, no free positioning.
2. **Clips are butt-joined and there is never a gap.** Deleting a clip closes the hole; dragging one
   pushes the others aside. Nothing has an "x position" a user can get wrong.
3. **Selection is a clip.** Every clip-scoped operation — delete, duplicate, speed, freeze, mute, and
   any of the five picture tools under *This Clip* — acts on the selected clip and on nothing else.
4. **Splitting is the only way to make a boundary.** ✂ at the play head. That one verb produces
   trim-from-the-middle, delete-a-piece and move-a-piece, which is the whole of the brief.

The four gestures the brief asks for, in full:

| Wanted | How |
|---|---|
| Trim the ends | drag the outer handles |
| Cut a piece out of the middle and delete it | ✂, ✂, select the middle, `Delete` |
| Move that piece elsewhere | ✂, ✂, drag the middle clip to where it goes |
| Edit only part of the video | ✂, ✂, select it, **This Clip**, then any tool |

### 4.3a One drag vocabulary, on both rows: **the body moves, the edge trims**

The author asked whether a sound entry's start and end could be dragged on the strip rather than only
typed in the panel (2026-09-03). Yes — and it is the same two gestures the picture row already has,
which is the reason to answer it here rather than in `audio/`:

| Gesture | On a picture clip | On a sound entry |
|---|---|---|
| **Drag the body** | reorder it in the row (§4.3 rule 2 — no gaps) | move where it starts on the timeline |
| **Drag an edge** | the outer handles trim the whole timeline; a join's grip moves the cut | trim that end of the sound, content staying where it is |

**With the left button, not the right.** The author's first phrasing was *"ПКМ и тянуть"*, and it
collides with a rule recorded the same day at the author's own request: §4.1b makes **right-click a
reset** on every control the schema renders. Two meanings for one button, one panel apart, is exactly
the class of bug §4.1b exists to remove — and every editor that does this uses a left-drag on the
edge anyway. So the right button resets, everywhere, and dragging is the left one.

**The panel and the strip are two views of the same numbers.** `Starts at` in the inspector is the
number a body-drag writes; the entry's length is the number an edge-drag writes. Neither is
authoritative — they read and write one `TimeWindow` ([`audio/`](audio/audio.md) §3.3a).

**Slip is out of scope**: dragging the body while holding a modifier, to change *which part of the
source plays* without moving the placement, is a professional gesture nobody asked for and a third
meaning for the same drag.

### 4.4 The second row appears when asked

Author's decision, 2026-09-03. The default is one row with the sound inside it; a second row exists
only after **Detach audio** or **Add audio…**, and disappears again when it is emptied. Someone who
never asks for it never sees it; someone who needs a reply to carry across a join gets it. Its own
design is [`audio/`](audio/audio.md).

### 4.5 `Done ▾`

```
[ Done ▾ ]
     Done             instant, lossless — the recipe goes into the file
     Render video…    a new file, with a progress bar
```

The mockup has drawn `[Done ▾]` since 5a and the shipped control is a plain button, so this realises
a shape the design already assumed rather than inventing one. **The menu is capability-driven**: on a
container with no carrier it collapses to `Render video…` alone and says why, in the notice strip the
shell already has for a format that keeps no history.

On the naming: the author's first phrasing was *"Save (visible only in Woka)"*. The meaning is right
and the words are a disclaimer rather than a name — and a menu entry that needs a parenthetical is a
tax on the goal this whole feature rests on. `Done` stays the word 5a uses for the same act; the
ellipsis on the other one is the convention for "this opens something" rather than "this happens
now".

---

## 5. Read against the tree — 2026-09-03

The image editor shipped in full that day, so the old document's claims were checked against the code
rather than against the plan they came from. **Four were wrong**, and each one moved work between
folders.

- **"`EditRecipe` reads whole files, so `carries(file)` is an immediate OOM on a 4 GB video."**
  **False for reading.** `RecipeSlices` was extracted at ME-126: a tail carrier reads the last 64 KB
  with a `RandomAccessFile`, a head carrier the first 512 KB, and the whole-file read is a fallback
  for a walk that actually ran out. The grid's per-tile question is therefore already cheap for an
  `mp4`. **True for writing**, and worse than the old text stated: `EditRecipe.write` and `clear`
  call `file.readBytes()` and hand the result to `writeAtomically`, which builds a second whole copy
  and moves it over the target. For a 4 GB film that is an OOM *and* 4 GB of writes to change 300
  bytes. The fix is shaped by the format — our box is the last thing in the file, so a write is
  `setLength` back over the old box plus an append, O(payload) and touching nothing else. That is
  `save/`'s first ticket, not a side note.
- **"The saver runs the bundled ffmpeg as a background process."** ME-181 changed the precedent:
  `FfmpegStillEncoder` drives `FFmpegFrameRecorder` **in-process** against the bundled build. So
  `render/` has a working in-process precedent on one side and a real question on the other — and
  ME-181's own KDoc carries the trap it cost to find, a converter freed under a `Frame` still being
  read.
- **"ME-204: the same save targets as images."** `SaveTarget` **does not exist** — ME-132 deleted it,
  and a grep finds no reference anywhere. What is genuinely shared in `save/` is `AtomicWrite`,
  `SaveEviction`, `SourceStamp` and `SaveFormat`. `Save a Copy`, in the same retired block, described
  nothing in the app.
- **"Enabling video is one line in the factory."** **Still true**, and the line is
  `EditorEngineFactoryImpl.capability`, which answers `Unavailable` for anything that is not
  `MediaKind.Image`. Both doors — the viewer's control pill and the grid's context menu — read that
  one function.

Two further findings, neither of them corrections:

- **A timeline stage and a raster fold coexist with no branch.** `orderedStages` ends in
  `tool.stage(op.params) as? RasterStage`, so a stage that is not a raster one is dropped silently
  rather than crashing. A video-only tool can return its own `ToolStage`, be folded by a second
  timeline fold, and the raster fold needs no knowledge of it.
- **`ToolDomain` wants a `Time` case, first in the enum.** Its own KDoc warns against reordering the
  enum to fix a rail layout, because the order is *also* application rank — but *prepending*
  preserves every existing relative rank, so no image comes out differently. Time before Geometry is
  also the only correct rank: a cut decides which frames exist before anything decides what they look
  like.

**What is measured and what is not.** The frame-rate fold (§3.1) and the seamless join
([`playback/`](playback/playback.md)) are unmeasured, and they are the two gates on the feature.
Whether other players tolerate a `uuid` box appended to an `mp4`'s tail is a third, and it belongs to
[`save/`](save/save.md). ME-181's probe took four wrong turns before it read correctly, so each of
these wants a fresh head rather than an estimate.

---

## 6. Decision log

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-09-03 | One folder or several? | **Seven feature folders, one shared backlog under `tickets/`** | The brief is seven bodies of work that fail and ship independently. The backlog stays single because a block's track is a section heading, and one file is where "what is left" can be read in one go |
| 2026-09-03 | Does cutting break the data model? | **No — clips are ranges of one source** | The old objection was about a sequence of *several files*. Re-ordering ranges of one file is one op on one file, so the recipe, the carrier and the history all keep working (§3.2) |
| 2026-09-03 | How are effects scoped to part of the video? | **A clip is the scope. `EditScope` and `EditLayers` unchanged, keyed on clip index** | ME-156…ME-176 built exactly this for frame sets. A second, time-range notion of scope would be two answers to one question (§3.3) |
| 2026-09-03 | Where does the strip live in the contract? | **A defaulted `Timeline` composable on `EditorEngineSurface`** | It follows `Thumbnail`'s precedent, keeps the shell free of `MediaKind`, and leaves undo granularity with the session (§3.4) |
| 2026-09-03 | Is `Done` enough on its own? | **No — `Done ▾` offers `Render video…` too** | Both cases are real: a quick trim must not wait on a render, and a clip being sent to somebody must not need Woka to look edited |
| 2026-09-03 | Does a render re-encode everything? | **No — copy every segment with no pixel work, encode only the rest** | It is what makes "few edits, no bigger than the source" true, and a copied segment is lossless as well as instant (§3.5) |
| 2026-09-03 | Reverse playback? | **Declined** | Author's call. It needs a keyframe-to-keyframe frame buffer to exist at all, and buys one effect |
| 2026-09-03 | A project across several files? | **Declined outright** | Every assumption in §3 and §5 stops holding, and this stops being iOS Photos. Cutting within one file (§3.2) already covers the brief |
| 2026-09-03 | Does the video editor draw a top bar? | **No — it fills the shell's slots, exactly as the image editor does. `Back` is cancel and there is no `Cancel` button** | Read against `EditorTopBarSetup` after the first draft of this document invented one. The shipped arrangement is already right, and the bar is a themed surface rather than a scrim (§4.1a) |
| 2026-09-03 | How is a control reset? | **Right-click, on every input, plus the header for the whole tool** | The schema renders eight row types and double-click-to-detent only ever meant something on the two with a track. One gesture everywhere is one thing to learn (§4.1b). It changes the image editor too, so it is a §300 ticket |
| 2026-09-03 | What settings does a sound entry get? | **`TimeWindow` — markup's own group — plus volume and repeat** | The two ask the same three questions, and one envelope function read by the canvas *and* the renderer is what keeps an export matching the preview |
| 2026-09-03 | Can a sound entry be dragged on the strip, with the right button? | **Dragged yes, with the **left** button: the body moves, an edge trims** | The right button was spent on reset the same day (§4.1b), and two meanings for one button one panel apart is the class of bug that rule removes. Left-drag on an edge is what every editor does anyway (§4.3a) |
| 2026-09-03 | Is a GIF the main way out? | **No — a third `Done ▾` entry, only for a short result, saying what it costs** | A muted `mp4` of the same clip is smaller and looks better, so a GIF has to be the secondary offer rather than the headline (ME-296) |
| 2026-09-03 | What frame is the cover? | **One the author picks, in timeline time, drawn through the fold** | Folding it is the whole point: a frozen first frame with a title on it is what should appear on the grid tile, and an unfolded poster would show the bare source frame (ME-287) |
| 2026-09-03 | Split at silence or scene change? | **Declined** | Author's call, and the reason is the right one: too many clips where it cuts in the wrong place, and confirming every guess costs more than making the cuts |
| 2026-09-03 | Prefix and numbering | **`ME-`, bands inside the 200s; ME-202…204 retired; §300 for the shared shell, §950 for proposals with no ids** | `ME` is *Media Editor* and spans Phase 5, so the shared foundation and the video work stay in one namespace. Bands give each folder room without a second prefix; a proposal gets no number until it is accepted |
| 2026-08-24 | Own folder, or stay under `media-editor/`? | **Own folder** | A feature folder is a shipping unit *(carried)* |
| 2026-08-16 | Does `stage()` return `RasterStage`? | No — a `ToolStage` marker | A trim stage is not a matrix; this is the decision the whole feature rests on *(carried from the image editor's log)* |
| 2026-08-16 | How does video reuse the shell? | An `EditorEngine` per `MediaKind` | The shell composes `engine.Surface(...)` and never branches on media *(carried)* |
