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

### ME-117 — Housekeeping
`roadmap.md` Phase 5 updated to "image half shipped, video half pending"; the editor exercised by
hand once and the result dated in this ticket; the `-edited` naming checked against a folder
that already holds a dozen of them.
*Depends on ME-108, ME-115.*

### ME-142 — The rest of the adjustments
Adjust ships six — Exposure, Brightness, Contrast, Saturation, Warmth, Tint. Photos offers around
fifteen. The missing ones are wanted, and they do **not** all cost the same.

**The ones that are still one matrix**, and therefore cost a slider each and nothing more: Vibrance
(saturation weighted away from skin tones), Black Point, and a second look at Brilliance.

**The ones that a `ColorMatrix` cannot express at all** — Highlights, Shadows, Sharpness, Definition,
Noise Reduction, Vignette. Every one of them is either per-luminance (a tone curve) or per-neighbourhood
(a convolution), and `RasterStage.Pixels` carries a `ColorMatrix`. A curve is not a linear map of the
channels; a blur reads the pixels around the one being written.

So this block is really two: the matrix ones, which are ME-111 again with more constants, and the rest,
which need a **fourth thing a stage can be** — a Skia `RuntimeEffect` shader carried through the fold and
run identically on the canvas, in `renderAtNativeScale` and in `RecipeStillEdits`. That is an engine
change, and §3.2's "three shapes and no more" is the sentence it argues with. Splitting the promotion
along that line is the whole point of writing this down now.

**Also a panel problem.** Fifteen sliders in one inspector is a wall. Photos groups them and scrolls;
the editor's inspector has never had to scroll.
*Depends on ME-111.*

### ME-143 — Crop that can pull one edge
Requested 2026-08-22; **the exact behaviour is the author's to describe when it is promoted**, and the
two readings are far apart, so this block deliberately records both rather than choosing.

- **Non-uniform stretch** — drag the bottom edge down and the picture gets taller, the pixels stretched
  rather than the frame recut. Free in the current model: `RasterStage.Geometry` carries a `Matrix`, a
  scale is a matrix, and the reader path already scales the recipe.
- **Perspective / keystone** — pull the top edge wider than the bottom to straighten a building. Also a
  matrix, but a projective one: Compose's `Matrix` is 4×4 and Skia honours the perspective row, so it
  fits `Geometry` too — what does *not* fit is `CropRect`, which is four integers and cannot describe a
  quadrilateral, and `CropHandle`, whose handles resize a rectangle.

Either way it is a **Geometry** stage in source pixels and needs no new stage kind. Whether it belongs
to Crop or becomes its own tool depends on which of the two it is.
*Depends on ME-109.*

### ME-144 — Icons where the panels currently use words
Requested 2026-08-22. Several controls say in text what a glyph says faster, and the rail already
proved the point from the other side — it carried icons *alone* until two undrawn glyphs became two
identical squares, and gained labels (ME-109, 2026-08-21). This is the same judgement applied per row
rather than per tool.

The candidates as the tree stands: Crop's `Portrait` switch (an orientation, drawn in a heartbeat),
Crop's aspect chips where a ratio has a shape, Resize's presets, and any adjustment whose name is
longer than its effect.

**The work is mostly SVGs, not Kotlin.** `composeResources/drawable` holds around ninety and none of
them is an orientation, a rectangle outline or an arrow; `Icons.Static` is a flat list of one-line
aliases. A row renderer that can show a glyph instead of a label is a small change to
`ParamRowChoice`/`ParamRowScalars`; drawing the glyphs is the ticket.

**Not everywhere.** A number stays a number, and a slider keeps its name — an inspector of unlabelled
pictograms is the submenu problem in a different costume.
*Depends on nothing.*

### ME-118 — HDR tool
`Output` domain, `Single`, one `ChoiceParam` — **`Keep` and `To SDR` only in v1** (§9, 2026-08-18).
`To SDR` bakes the existing tone map into the pixels and writes an SDR file; `Keep` carries the
source's transfer through, which needs the ffmpeg encode path since Skia cannot write PQ. **`To HDR` is
deferred** — no measurement blocks it (PQ AVIF via libaom writes correctly, ME-010) but it is inverse
tone mapping and does not recover clipped highlights, so it is not MVP.

**Blocked on ME-120, not just on ME-104.** `To SDR` bakes the tone curve into pixels permanently, and
that curve is currently normalised by the literal `W = 11.2` — on real HDR content SDR white lands at
148/255. Shipping `To SDR` first would write the defect into the user's files.
*Depends on ME-013, ME-104, ME-120.*

### ME-119 — HDR view mode
A tri-state — `Auto` / `Force HDR` / `Force SDR` — that changes how a still is *displayed* and no
pixels at all: a `WokaSettings` field, a control in the viewer, and the same toggle mirrored in the
editor's top bar so the user can see what the HDR tool is about to write. Belongs to the viewer as
much as to the editor; it is listed here because §6.1 is where the two halves are defined together.
*Depends on ME-106.*

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

**Blocked on a fixture, not on the analysis.** The corpus has no genuine HDR still — `hdr_pq.avif`
and friends are SDR pixels wearing a PQ tag (see the corpus README), and tuning a tone curve against
them would tune it against nothing. A real PQ BT.2020 still has to land in the corpus first, ideally
two or three with different peaks.

Belongs to the viewer, not to the editor; listed here because ME-010 is where it was measured and
because the HDR tool's `To SDR` bakes this very curve into pixels.
*Depends on a real HDR fixture reaching the corpus. Blocks nothing in the 100 track except honest
output from ME-118's `To SDR`.*

### ME-121 — Migrate the viewer's on-media chrome to the onScrim tokens
`ViewerChrome` gained the foreground half of its on-media pair in ME-012 (`onScrim`,
`onScrimSecondary`, `onScrimDisabled`, `onScrimBorder`, `onScrimHover`, `onScrimSelected`), light in
both themes for the same reason its scrims are dark in both. The editor reads them from its first
commit; **the viewer does not yet**. About 27 files under `feature/viewer/` still take `Theme.colors`
for text and icons drawn on a scrim, which in the light theme measures between 1.05 : 1 and 6.68 : 1
depending on the photograph behind it — see the decision log.

The change is mechanical but wide, and it is a *visible* change to a surface that already ships, so it
wants the author's eyes rather than a green build: the files to convert are the intersection of "reads
`Theme.viewer`" and "reads `Theme.colors`". Deliberately kept out of ME-012, whose job was the editor.
*Depends on nothing. Blocks nothing — the editor is already correct.*

### ME-123 — Keep a huge source out of the shared cache
The guard rail §10 measured and did not build. A 100 MP source is **384 MB** of native pixels against a
**256 MB** `ImageBitmapCache` budget, so one such picture evicts everything else and still does not fit.

Most of this falls out of ME-103's decision to build the proxy in the editor's own scope and decode
native only at save (§9) — what is left is the part that decision does not cover: the **viewer**
route already put the native bitmap in the cache before the editor opened, and ME-104's save decode
must not put a second one there. So: a decode path that returns a bitmap without caching it, and a
measured assertion that editing a 100 MP file leaves the cache no larger than it started.
**Also carries the decode off the composition thread.** ME-103 builds the proxy inside
`remember { EditorProxy.load(...) }`, which runs synchronously while composing — 667 ms for a 100 MP
source, ~150 ms for a 24 MP one, measured in ME-010. Opening the editor on a large photograph freezes
the UI for exactly that long. The fix needs a placeholder, an ordering against the frozen viewer
underneath, and an answer for `Cancel` pressed mid-decode, which is why it was not improvised in ME-103.

**Wider since 2026-08-19.** ME-133 makes every *read* of an edited file a decode plus a fold, so the
native decode is no longer something only a save does. The viewer's route is the one to watch: a
100 MP edited photograph is now 384 MB decoded, folded, and the fold's output cached on top of it.
*Depends on ME-133.*

### ME-125 — Save cancellation and job lifetime
What happens to an in-flight save when the app closes. No file is ever half-written — the atomic move
is the last step — so this is about the **job and the banner**, not corruption: whether closing the
window waits for the save, and what the banner says if the process goes away mid-write.

**Half answered by ME-130** (2026-08-30): the save runs in a scope owned by `EditorSaveStatus`, built
like `PipManager.pipScope`, so it survives the editor that started it and reports through the banner.
What that does *not* answer is the process going away — a `SupervisorJob` in an object is not a
shutdown hook, and nothing waits for it.

Smaller since 2026-08-19 but not gone: a recipe write is a few hundred bytes rather than a re-encode of
100 MP, so the window is short — for bucket B (§5.1) it is still a full render and encode.
*Depends on ME-130.*

### ME-1xx — An unsaved edit per frame
Raised by ME-158 (2026-08-30), which chose the discard dialog instead. Walking from frame 1 to frame 3
inside the editor rebuilds the engine, the stored document and the session, so work on frame 1 is
either lost or confirmed away. What is wanted is to carry it: `EditorSession` holding a document **and
a history** per frame, so undo does not walk out of the frame being looked at into one that is not, and
`Done` writing every frame it touched in one merge.

That is a redesign of a 145-line state holder already at the size mandate, plus a rule for what `Undo`
means when the answer is in another frame — which is why it was not improvised inside ME-158.
*Depends on ME-158.*

### ME-128 — Concurrent editor sessions, and the dock list that reaches them
Requested 2026-08-18. Three things that belong together:

- **The dock hides while an editor is open**, the way it already hides for the viewer
  (`FolderScreen` sets `dockAccessories.isVisible = viewerMediaId == null`).
- **The `Editor` dock item lists the open, unsaved editors** on hover — the same popup-on-hover shape
  `Library` already uses for `pinnedSessions`, and driven the same way. Picking one **returns to it with
  its document and history intact**, which is the whole point: an edit in progress is not lost by
  opening something else.
- **A badge dot when that list is non-empty**, using `DockItem`'s existing `hasBadge`. Explicitly *only*
  the dot — the item is **not** tinted the way `Library` tints itself when active. One is "you are here",
  the other is "something is waiting".

**The architectural consequence, which is the reason this is not a small ticket.** ME-004 made
`EditorSession` a Koin `factory` whose constructing composable **owns it and must `close()` it**. More
than one live session means a composable can no longer be that owner: something above the screen has to
hold them, key them by path plus frame index, and decide when a session is finally disposed — closing
the editor is no longer the same event as ending the session. That store is what this ticket really
adds; the dock list is its UI. **ME-106 must not create-and-dispose per open** once this lands, so
whichever ships second adjusts the other.

`EditorScreen` needs no change: ME-101 already takes the session as a parameter rather than resolving it.

Also settled while looking: **`Icons.Static.Editor` already exists** (`square_and_pencil`) and is the
right glyph. `FloatingDock` currently draws the Editor item with `Icons.Static.Settings` under a stale
`// Replace with Editor icon if available` comment, so it is the same icon as the Settings button beside
it. One-line fix, folded in here.
*Depends on ME-106.*

---

## 900 — Follow-ons, explicitly not planned

- Straighten / arbitrary-angle rotation.
- Re-opening a saved edit to adjust a previous crop — the document is already serialisable; this is
  where it starts being written to disk, as a sidecar beside the copy.
- A user-configurable copy-name pattern (`-edited` is hardcoded until someone wants otherwise).
- **Saving a flattened copy to a file.** The clipboard answers "get this picture out of Woka" without
  minting anything (`Copy`, 2026-08-19), and an export step is precisely what §5 removed. ME-136 was
  raised for this on 2026-08-19 and closed the same day by making `Copy` the picture; the number is
  retired rather than reused.
- Batch apply across a selection in the grid — the grid's `Edit` entry is single-selection only.
- RAW, layers, masks, healing — out of scope for this feature entirely.
