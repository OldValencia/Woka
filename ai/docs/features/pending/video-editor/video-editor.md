# Feature: Video Editor (Roadmap Phase 5b)

The second half of Phase 5. Everything structural already exists: the document, the history, the tool
contract, the schema-driven inspector, the shell, the shortcut table, the save banner —
[the image editor](../image-editor/image-editor.md) built all of it as ME-001…ME-014, deliberately
media-agnostic, and this feature is a tool set, an engine and a saver plugged into it.

Split out of `pending/media-editor/` on 2026-08-24, so that a folder holds one shipping unit. **The
ticket IDs are unchanged** — ME-201…ME-204, on the ME prefix the shared foundation is numbered in —
because an ID is stable and never reused (`conventions/feature-docs.md` §3).

```
video-editor/
  video-editor.md   ← you are here: what the thing is and why. The feature file.
  tickets/          backlog.md, plus one file per ticket once one is promoted
```

Conventions: `ai/docs/conventions/feature-docs.md` · `tickets.md` · `code-style/README.md`.
Architecture, in full and not repeated here: [`../image-editor/image-editor.md`](../image-editor/image-editor.md)
§3 (the document, the fold, the engine seam, the tool contract) and §4 (the shell this inherits
unchanged).

---

## 1. Status

Nothing is built. This table is the only place a ticket's state is recorded.

| Ticket | Title | State |
|---|---|---|
| [ME-201](tickets/backlog.md) | Video engine | backlog |
| [ME-202](tickets/backlog.md) | Trim tool | backlog |
| [ME-203](tickets/backlog.md) | Mute tool | backlog |
| [ME-204](tickets/backlog.md) | Video save | backlog |

The foundation these claim is done: ME-001…ME-014, and ME-106 for the entry points, all in the image
editor's table. Nothing here is blocked by an unshipped image ticket — 5a's remaining work is tools
and polish, not shell.

---

## 2. Scope

**In:** Trim by keyframe-snapped stream copy, and Mute. Same shell, same history, same undo, same
`Done`.

**Out:** re-encoding filters of any kind, precise (non-keyframe) trim, speed changes, concatenation,
audio editing beyond the mute switch, subtitles, and anything that touches the picture's pixels.

**Formats:** whatever the player already opens and ffmpeg can stream-copy. The list is written when
ME-201 is promoted, against the probe results of the day, not guessed here.

---

## 2a. The shape the author wants — 2026-09-03

**"Как в iOS Photos."** The same short rail the image editor has — Resize, Crop, Trim, Filters,
Adjust, Markup — and nothing behind it. Five of those six are already built and media-agnostic; Trim
is the one this feature adds. The stated test is the one 5a passed: *"Из image редактора я же не
сделал photoshop"* — simple and useful, not capable and heavy.

**This contradicts §2 above, and the contradiction is the decision ME-201 has to make.** §2 puts
"re-encoding filters of any kind" and "anything that touches the picture's pixels" out of scope, and
draws the line at stream copy precisely so a save stays instant and lossless. Crop, Adjust, Filters
and Markup on video are all pixel work. One of the two sections has to give.

### The way out, and it is the one 5a already took

**Do not re-encode. Write a recipe and let the player fold it.** That is not a new idea here — it is
§5 of the image editor, shipped as ME-132: the document goes into the file's private metadata and the
pixels are never rewritten. Every reader folds it at draw time.

For video the reader is the player, and the player is **already a folding surface**: it runs
`HdrShader` per frame today, and draws subtitle and markup-shaped overlays per frame. A colour matrix,
a crop matrix and a set of paints are the same road it is already on. So:

| Tool | As a recipe | Cost at save |
|---|---|---|
| Adjust, Filters | a matrix and shaders the player already runs per frame | none |
| Crop, Resize | a matrix, applied where the frame is drawn | none |
| Markup | paints over the frame, as the subtitle layer already does | none |
| Trim | a range the player opens into and stops at | none |
| Mute | one boolean the audio path reads | none |

**Every one of them becomes free**, the save stays instant, and the source file is never degraded —
which for video, where a re-encode is both slow and lossy, matters more than it did for stills.

**What it costs is honest and already precedented:** the file only looks edited *in Woka*. That is
exactly true of an edited JPEG today, and 5a answered it twice — `Copy` hands over the drawn picture,
and ME-118's HDR tool bakes when a real file is wanted. The video answer has the same shape: an
`Output` tool whose second option runs ffmpeg and writes a new file, with a warning, once.

**So the ordering inverts.** §2 treats stream copy as the foundation and pixels as out of scope; this
reads the recipe as the foundation and the ffmpeg export as one `Output` tool at the end. ME-201 is
then "a player that folds a document", not "a saver that runs a process".

**What has to be measured before believing any of it:** whether the fold survives at frame rate. The
image canvas folds once per bitmap; a player would fold 24–60 times a second, and `RasterRecipe`'s
shader chain has never been asked for that. This is a probe, and it is the real gate on the section —
not the ffmpeg question §4a names.

### `Done` offers both — the author's call, 2026-09-03

Neither save is the right one, because both cases are real: a quick trim to watch later must not wait
on a render, and a clip being sent to somebody must not depend on them having Woka. So `Done` becomes
a split button and the user picks.

```
[ Done ▾ ]
     Done             instant, lossless — the recipe goes in the file
     Render video…    a new file, with a progress bar
```

The mockup has drawn `[Done ▾]` since 5a and the shipped control is a plain button, so this realises
a shape the design already assumed rather than inventing one.

**On the naming.** The author's first phrasing was *"Save (visible only in Woka)"*. The meaning is
right and the words are a disclaimer rather than a name — and a menu entry that needs a parenthetical
is a tax on the "simple, like iOS Photos" goal this whole section rests on. `Done` stays the word 5a
uses for the same act; `Render video…` says what the other one does, and the ellipsis is the
convention for "this opens something" rather than "this happens now".

**Render must mint a new file, never overwrite.** Not taste — arithmetic. A re-encode is lossy, so
writing over the source means a second edit encodes an already-encoded picture and the losses
compound with the pristine original gone. Stills never had this because the recipe always unfolds
from an untouched source, which is exactly why ME-132 deleted `Save a Copy`. **For video that
reasoning inverts**, and the copy comes back — for the render path alone, and because a lossy
overwrite is irreversible.

**The recipe half is not free yet, and it is the gate on the menu.** §4a found `carrierFor` routes
only `heic`, `heif` and `avif` to `IsoBmffRecipe`; `mp4` and `mov` are not wired. The `uuid` box is
structurally available in ISOBMFF, but what other players do when they meet an unknown box in a `moov`
is a measurement nobody here has taken. **If that probe fails, `Done` has nowhere to write and the
menu collapses to one entry** — which would make render the only save and change this section's
conclusion, not just its detail.

**Markup that appears, holds and fades** — the author's design, same session: pick the point on the
timeline, say how many seconds it lasts, let it fade out. It belongs to the document rather than to
either save: an op carrying a time range is read by the player's fold *and* by the render, and the
two must agree or the exported file will not match the preview. Worth noting the asymmetry — the
player gets the fade from Compose's own animation, while a render has to produce it frame by frame.

---

### Where a recipe would live in a video file — checked 2026-09-03

Asked directly: do video files have the same slot images do? **Two of the five formats Woka opens do,
one is a different problem, and there is a blocker underneath all of them that matters more than
either.**

`MediaKind` lists `mp4`, `mkv`, `avi`, `mov`, `webm`. They are three container families:

| Format | Container | The slot |
|---|---|---|
| `mp4`, `mov` | ISOBMFF | **The same one.** `IsoBmffRecipe` appends a top-level `uuid` box after the last box and never inserts; an unknown top-level box is skipped by its size. Wiring them into `carrierFor` is close to free |
| `mkv`, `webm` | Matroska / EBML | **No `uuid` box at all.** A different mechanism and a second carrier to write |
| `avi` | RIFF | Not ISOBMFF either — but the same family as WebP, so ME-108's unbuilt RIFF carrier would serve both |

So the split-button `Done` above is only offerable where a carrier exists. **A library that is mostly
`mkv` would see the menu collapse to one entry on most of its files**, which changes that section's
conclusion rather than a detail of it.

### …and the blocker: `EditRecipe` reads whole files

`EditRecipe` calls `file.readBytes()` in four places — `read`, both writes and the scan. For a 4 MB
photograph that is nothing. **For a 4 GB video it is an immediate OOM**, and not only on save: the
cheap-sounding question `carries(file)` — asked once per visible grid tile — reads the entire file to
answer it.

The video recipe path therefore is **not** "add two extensions to `carrierFor`". It needs the carrier
rewritten to work without holding the file: seek from the end with a `RandomAccessFile` and read the
last few kilobytes.

**The trailing box makes that easy rather than hard** — it is at a known end and its size prefix
points backwards — so this is a real piece of work with a shape, not an unknown. But it must be
costed into ME-201 rather than discovered inside it, and it is a prerequisite for the recipe half of
`Done` on any format, ISOBMFF or not.

---

### The two extras — **not in a release, and not equally far away**

The author raised both as wanted-someday and unnecessary now, and was right to separate them from the
list above. They are not the same size.

- **Effects over a time range** — Adjust on seconds 10–20 rather than the whole clip. **The
  architecture already has this shape.** ME-156 and ME-169 built exactly it for frame sets: an
  `EditScope` switch, a shared base layer and a per-frame layer over it, with ME-175 and ME-176
  settling how they compose and what Revert takes. A time range is the continuous form of a frame
  index. Not free, but not new thinking.
- **Cutting and splicing clips onto a timeline.** **This one breaks the data model.** An
  `EditDocument` describes edits to *one* file and lives in *that* file's metadata; a sequence is a
  document about several files and has nowhere to live. It needs a project, a library of clips and a
  timeline UI — and that is the point at which this stops being iOS Photos and starts being iMovie.
  Its cost is not the timeline widget; it is that every assumption in §3 and §5 stops holding.

The first could arrive later without disturbing anything. The second should be declined outright the
way the 600 track was, or it will quietly redefine the feature.

---

## 3. What this feature actually adds

Three things, and no fourth:

- **An `EditorEngine` for `MediaKind.Video`.** The existing player is the live surface; the fold
  produces a timeline edit rather than a raster recipe; the saver runs the Bytedeco-bundled ffmpeg as
  a background process. This is the seam §3.3 of the image editor's doc exists for — the shell
  composes `engine.Surface(...)` and never branches on media kind.
- **Two tools**, both on the ordinary contract: Trim is a `CanvasTool` (dual handles over the
  timeline), Mute is a manifest and one `BoolParam` with no UI code at all.
- **A saver** that reuses the image save targets, the atomic write, the cache invalidation and the
  banner — sharing the implementation, not copying it.

**Mute is the acceptance test of the whole design.** If adding it takes more than a file and a Koin
line, §3.4 of the image editor's doc is wrong. Likewise the reverse: if any ticket here needs the
shell changed, the foundation was wrong and the fix belongs in the foundation, not in a branch.

---

## 4. Open questions — answered when a ticket is promoted, not before

These are the things this document deliberately has no answer for. Each is a real fork, and guessing
one now would put a wrong sentence into a doc that is supposed to be true.

- **Does the recipe model reach video at all?** §5 of the image editor is "the `EditDocument` goes
  into the file's private metadata slot and pixels are never rewritten". MP4 and MOV are ISOBMFF, so
  the `uuid` box carrier ME-108 built for HEIC and AVIF is *structurally* available — but a recipe is
  only worth writing if every reader folds it, and the reader here is a decoding player, not a
  bitmap. A trim that is a recipe means the player skips a range on every open; a trim that is a save
  means ffmpeg writes a new file. **The backlog blocks were written before §5 was rewritten
  (2026-08-19) and still say `Save a Copy` is the default — that phrase no longer describes anything
  in the app**, and ME-204 has to be re-read against §5 when it is promoted rather than implemented
  as written.
- **What a keyframe-snapped trim tells the user** when the cut they asked for is 400 ms from the cut
  they get. Snapping to real keyframes is what keeps the preview honest; showing the snap is a UI
  decision nobody has made.
- **Whether the trim strip is the player's scrubber or a second timeline.** The viewer already has a
  slider; the editor is a mode over the viewer, and two timelines stacked would be the submenu problem
  in a different costume.
- **What an overwrite invalidates.** For an image it is `ImageBitmapCache` plus the preview key
  (2026-08-17). A video also has a poster frame and a watch record, both keyed on the file.
- **Playback while editing.** `EditorEngine.togglePlayback()` already exists (ME-140, added for
  animated stills on the image canvas) and the video engine is its obvious second implementation —
  but "the canvas plays" and "the canvas plays *the trimmed range*" are not the same feature.

---

## 4a. Reconciliation against the tree — 2026-09-03

The image editor shipped in full that day, so this document was read line by line against the code
rather than against the plan it was written from. **Nothing here is a decision** — it is what is
true, so that whoever promotes a ticket starts from the tree instead of from a year-old sentence.

### Still true

- **The shell names no media kind.** `EditorScreen` asks `engine.kind`, `EditorBottomPanel` takes the
  engine as an interface, and the only `MediaKind` under `shell/` is `EditorSkippedOps`' parameter.
  §3's claim that the video engine plugs in without touching the shell holds.
- **`togglePlayback()` is on `EditorEngineSurface`** with a `Unit` default, implemented by
  `ImageEditorEngine` and driven from `EditorCanvasGestures`. The video engine is its second
  implementation, exactly as §4 says.
- **The factory is the single gate.** `EditorEngineFactoryImpl:43` answers `Unavailable` for anything
  that is not `MediaKind.Image`, so enabling video is one line there and nothing else opens.

### No longer true, and load-bearing

- **ME-204 claims "the same save targets".** `SaveTarget` **does not exist** — ME-132 deleted it, and
  a grep finds no reference anywhere. What is actually shared in `save/` is `AtomicWrite`,
  `SaveEviction`, `SourceStamp` and `SaveFormat`; the block must be re-read against those.
  `Save a Copy` in the same block describes nothing in the app, which §4 already flagged.
- **ME-201 says the saver "runs the Bytedeco-bundled ffmpeg as a background process".** That was
  written when nothing here wrote with ffmpeg at all. **ME-181 changed it**: `FfmpegStillEncoder`
  drives `FFmpegFrameRecorder` in-process against the bundled build. Whether a trim wants the same
  road or a real process is now a live question with a working precedent on one side — and ME-181's
  own KDoc carries the trap it cost to find (a converter freed under a `Frame` still being read).

### Two of §4's open questions are narrower than they read

- **"What an overwrite invalidates."** Half answered in code: `SaveEviction`'s KDoc records that the
  `.thumbnails` poster is keyed on path, length *and* mtime, so a write orphans it rather than
  leaving it stale — that covers the video poster too. **The watch record is the real half**, and
  worse than the block states: it holds `positionMs` against `durationMs`, and a trim changes the
  duration, so a resume position can point past the end of the file it belongs to.
- **"Does the recipe model reach video at all?"** The structural half is settled: `carrierFor` routes
  `heic`, `heif` and `avif` to `IsoBmffRecipe` and **not** `mp4` or `mov`, so the carrier exists but
  the extensions are not wired. The open half is unchanged and is the one that matters — whether a
  player should fold a recipe on every open.

### Unmeasured, and the gate on ME-201

Whether a keyframe-snapped stream-copy trim can be driven from Java at all. `FfmpegFrameRecorder`
writes a still today; a stream copy is a different API path and nothing here has exercised it. **This
is a probe, not a judgement** — and ME-181's probe took four wrong turns before it was read
correctly, so it wants a fresh head rather than an estimate.

---

## 5. Decision log

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-08-24 | Own folder, or stay under `media-editor/`? | **Own folder, `pending/video-editor/`** | A feature folder is a shipping unit; the image half is nearly done and the video half is unstarted, and one status table covering both said "backlog" for a quarter of its rows indefinitely |
| 2026-08-24 | Renumber the tickets to a `VE-` prefix? | **No — ME-201…ME-204 stand** | IDs are stable and never reused (`feature-docs.md` §3). The foundation these depend on is ME-numbered and shared; a rename would break every citation to buy a nicer prefix |
| 2026-08-16 | Does `stage()` return `RasterStage`? | No — a `ToolStage` marker | A trim stage is not a matrix; without this the video editor cannot reuse the tool contract *(carried from the image editor's log — it is the decision this feature rests on)* |
| 2026-08-16 | How does video reuse the shell? | An `EditorEngine` per `MediaKind` | The shell composes `engine.Surface(...)` and never branches on media *(carried, same reason)* |
