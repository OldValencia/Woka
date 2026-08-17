# Media Editor — Backlog

Everything after the foundation (ME-001…ME-014, one file each in this directory). Each block says what
it is for and what shape it takes, and **names no files** — those are written when the block is
promoted into its own ticket file, against the tree as it is then
(`ai/docs/conventions/tickets.md`).

Design behind all of it: [the feature doc](../media-editor.md).

Order inside a track is dependency order. Between tracks, only the marked dependencies matter.

---

## 100 — Image editor

### ME-101 — Editor shell
The screen itself: top bar accessory (Cancel · name · Edited dot · Undo/Redo · Done), the canvas host
that measures itself and builds the canvas↔source matrices, the animated inspector container, and the
tool rail with its domain groups and dividers. Built against `FixtureTool` (ME-011) so it is provable
before a real tool exists. Owns `activeToolId` and nothing else.
*Depends on ME-008, ME-011, ME-012.*

### ME-102 — Param row renderers
One renderer per `ParamSpec` type, reusing `DetentSlider`, `SegmentedControl`, `IosSwitch`,
`DropdownItem`. This is the file set that makes every tool's panel identical, and the only place
`manifest.interaction` decides between `preview` and `commit`. A tool contributing UI code after this
ticket is a design failure, not a feature.
*Depends on ME-101.*

### ME-103 — Image surface
Native bitmap and ≤ 2560 px proxy out of `ImageBitmapCache`; the live canvas drawing the proxy through
the folded recipe — matrix, colour filter, paints — with no bitmap allocation per frame. Native size
comes from the decoder, never from the bitmap.
*Depends on ME-101.*

### ME-104 — Save pipeline
Replay the recipe onto a Skia surface at native scale, encode (PNG / JPEG q90 / WebP q90), splice EXIF
per the ME-010 decision, and write through ME-009's atomic write. `Done` saves a **copy** with the
generated name; `Overwrite` is a separate menu entry behind a confirmation that says no copy is kept
and carries the session-scoped "don't ask again" option (`OverwriteConsent`), and is the only path
that invalidates caches. Reports through `EditSaveResult`; the banner follows `FrameExportBanner`.
The source is never touched when the encode fails.
*Depends on ME-009, ME-103.*

### ME-105 — Transform tool
The first real tool and the proof that a tool is data: a manifest, three parameters, four actions and
one pure matrix function. No inspector code, no overlay. If this needs anything the shell does not
already give it, the shell is wrong.
*Depends on ME-102, ME-103.*

### ME-106 — Entry points
The Edit button stops being a stub, and the grid context menu grows an `Edit` entry beside
Rename/Copy/Delete — both routes call one entry function with a path and a frame index, both greyed
out with a reason when the media is not editable, neither deciding editability on its own. Plus: the
editor mounted over a frozen viewer, the keyboard scope taken over and restored on dispose (`Ctrl+S`
copy, `Ctrl+Shift+S` overwrite), the discard-changes dialog, and an explicit `FolderViewModel.refresh()`
after a successful save — nothing watches the directory, so a saved copy is invisible until the
listing is re-read. After this the editor is usable end to end on PNG/JPEG/WebP.
*Depends on ME-104, ME-105.*

### ~~ME-107 — Revert to Original~~ — dropped
There is no backup store to revert from: `Save a Copy` is the default, so the original is still the
original (decision log, 2026-08-16). The id is retired, not reused.

### ME-108 — Conversion on demand
Formats Skia cannot encode, and multi-frame files, become editable through an explicit sheet: target
format defaulted from the ME-010 matrix, the new filename shown live, the surviving frame named when
the source is a frame set, and the HDR target offered when the probe says the encoder can write one.
The source file is kept exactly where it is; the converted file is a new item beside it.
*Depends on ME-106.*

### ME-109 — Crop tool
`Geometry`, `Single`, `Canvas`. A rect in source coordinates plus aspect presets from the schema; an
overlay with dimming, thirds grid, eight handles, hover cursors and a minimum size. Drags preview,
pointer-up commits. The aspect and hit-test math is a separate pure file, unit-tested.
*Depends on ME-105.*

### ME-110 — Resize tool
`Output`, `Single`, `Continuous`, pure data: width, height, aspect lock, percentage presets, and a
footnote row showing source → target dimensions. The footnote is a shell addition every later tool
inherits.
*Depends on ME-105.*

### ME-111 — Adjustments tool
`Color`, `Single`, `Continuous`, pure data: six sliders composing into one colour matrix, neutral at
all-zero, plus an auto action. No Compose code in the tool at all.
*Depends on ME-105.*

### ME-112 — Filter presets
`Color`, `Single`, `Continuous`, pure data: a handful of colour-matrix constants, an intensity that
lerps toward identity, and the thumbnail strip rendered by the schema's preview-choice row — one
texture, one filter per thumbnail, no extra decode.
*Depends on ME-111.*

### ME-113 — Markup engine
`Annotate`, `Repeating`, `Canvas`. The shape hierarchy in source coordinates, one draw routine shared
by the overlay, the recipe and the save path — which is what makes native-resolution rasterisation
automatic — and hit-testing with handle positions. Style parameters come from the schema.
*Depends on ME-105.*

### ME-114 — Markup interaction
The overlay: draw, select, move, resize, bend an arrow, inline text editing committed on blur or Enter
and discarded when empty, Delete removes. Selection state lives outside the history — selecting is not
an edit.
*Depends on ME-113.*

### ME-115 — Editor settings panel
Settings → Editor, with one row per manifest: icon, title, summary, switch. No per-tool settings code
now or ever; a tool disabled here disappears from the rail, the shortcut table and the fold, and its
ops are reported as skipped rather than dropped.
*Depends on ME-106.*

### ME-116 — Zoomed canvas fidelity
Above 100 % the proxy is not enough for markup precision: the canvas draws the native bitmap for the
visible region while zoomed, and drops back to the proxy when it is not.
*Depends on ME-114.*

### ME-117 — Housekeeping
`roadmap.md` Phase 5 updated to "image half shipped, video half pending"; the editor exercised by
hand once and the result dated in this ticket; the `-edited` naming checked against a folder
that already holds a dozen of them.
*Depends on ME-108, ME-115.*

### ME-118 — HDR tool
`Output` domain, `Single`, one `ChoiceParam`: `Keep` / `To SDR` / `To HDR` (§6.1 of the feature doc). `To SDR` bakes the
existing tone map into the pixels and writes an SDR file — that part is implementable the day the
save pipeline exists. `Keep` and `To HDR` need an HDR encode path through ffmpeg, and ship only if
ME-010's HDR probe says the bundled build writes a PQ/HLG file that reads back correctly. `To HDR` is
labelled an expansion in the UI; it does not pretend to recover clipped highlights.
*Depends on ME-013, ME-104, and the ME-010 HDR probe.*

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

---

## 200 — Video editor

*Every ticket here is a claim on ME-001…ME-014: if one of them needs a change to the shell, the
foundation was wrong and the fix belongs there, not in a branch.*

### ME-201 — Video engine
An `EditorEngine` for `MediaKind.Video`: the existing player as the live surface, a timeline stage
folded from the document, and a saver that runs the Bytedeco-bundled ffmpeg as a background process.
The shell is not touched.
*Depends on ME-106.*

### ME-202 — Trim tool
`Geometry`, `Single`, `Canvas`: dual handles on the timeline, snapping to real keyframes read from the
container so the preview never lies about where the cut lands. Stream copy only — precise, re-encoding
trim is out of scope.
*Depends on ME-201.*

### ME-203 — Mute tool
One manifest, one boolean, no UI code. This is the acceptance test of the whole tool architecture: if it
takes more than a file and a Koin line, §3.4 of the feature doc is wrong.
*Depends on ME-201.*

### ME-204 — Video save
The same save targets, atomic write, cache invalidation and banner as images, over the ffmpeg job —
sharing the implementation, not a copy of it. `Save a Copy` is the default here too; an overwrite
invalidates the poster frame and the watch record with the file.
*Depends on ME-202, ME-203.*

---

## 900 — Follow-ons, explicitly not planned

- Straighten / arbitrary-angle rotation.
- Re-opening a saved edit to adjust a previous crop — the document is already serialisable; this is
  where it starts being written to disk, as a sidecar beside the copy.
- A user-configurable copy-name pattern (`-edited` is hardcoded until someone wants otherwise).
- Batch apply across a selection in the grid — the grid's `Edit` entry is single-selection only.
- RAW, layers, masks, healing — out of scope for this feature entirely.
