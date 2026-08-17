# Feature: Media Editor (Roadmap Phase 5)

Two editors, one shell. The image editor ships first (5a); the video editor (5b) adds a tool set and a
canvas to machinery that is media-agnostic from the first commit.

This file is **the feature document** — scope, architecture, UI, decisions, status. It is the only
durable document; it names no source files, because file lists are what rot first. Those live in the
tickets:

```
media-editor/
  media-editor.md   ← you are here: what the thing is and why. The feature file.
  tickets/          one file per specified ticket + backlog.md for everything after
  archive/          the two superseded plans, banner-marked, not a source of truth
```

Conventions: `ai/docs/conventions/feature-docs.md` · `tickets.md` · `code-style/README.md`.
UX model: the iOS Photos editor. Visual truth: `ai/mockups/design/FINAL_design_mockup.html`, plus
`ai/mockups/design/editor.html`.

---

## 1. Status

Build order is top to bottom. This table is the only place a ticket's state is recorded.

| Ticket | Title | State |
|---|---|---|
| [ME-001](tickets/ME-001-toolchain-refresh.md) | Toolchain and dependency refresh | **done** 2026-08-16 |
| [ME-013](tickets/ME-013-ffmpeg-8-1-2.md) | JavaCV 1.5.14 / FFmpeg 8.1.2 | **done** 2026-08-16 |
| [ME-002](tickets/ME-002-package-skeleton.md) | Editor package skeleton and DI seam | **done** 2026-08-16 |
| [ME-003](tickets/ME-003-param-schema.md) | Parameter schema | **done** 2026-08-16 |
| [ME-004](tickets/ME-004-document-history-session.md) | Document, history, session | **done** 2026-08-16 |
| [ME-005](tickets/ME-005-tool-contract.md) | Tool contract | **done** 2026-08-16 |
| [ME-006](tickets/ME-006-tool-registry.md) | Tool registry, enablement, settings | **done** 2026-08-16 |
| [ME-007](tickets/ME-007-raster-fold.md) | Raster stage and the fold | **done** 2026-08-16 |
| [ME-008](tickets/ME-008-engine-seam.md) | Editor engine seam | **done** 2026-08-16 |
| [ME-009](tickets/ME-009-save-targets.md) | Save targets and the atomic write | **done** 2026-08-17 |
| [ME-010](tickets/ME-010-probes.md) | Capability probes | **done** 2026-08-18 |
| [ME-011](tickets/ME-011-test-harness.md) | Test harness and the fixture tool | **done** 2026-08-18 |
| [ME-012](tickets/ME-012-mockup-tokens.md) | Editor mockup and tokens | **done** 2026-08-18 |
| [ME-014](tickets/ME-014-remove-material3.md) | Remove `material3` — before ME-101 | **done** 2026-08-18 |
| [ME-101…ME-121](tickets/backlog.md) | Image editor | backlog |
| ME-107 | ~~Revert to Original~~ | dropped — no backup store |
| [ME-201…ME-204](tickets/backlog.md) | Video editor | backlog |

**Why the foundation is fourteen tickets and no pixels.** Every one of ME-002…ME-011 is shared by both
editors and testable without a window. When the video editor arrives, the work is a tool set, an
engine and a saver — not a second application built beside the first. If any ME-2xx ticket needs the
shell changed, the foundation was wrong and the fix belongs in the foundation.

The order is also a risk order: the save targets and atomic write (ME-009) and the probes that decide
what re-encoding does to EXIF, colour and caches (ME-010) land before any code can write anything.

---

## 2. Scope

**In (image):** Rotate 90°, Flip H/V, Crop (aspect presets), Resize, Adjustments, Filter presets,
HDR on/off, Markup (arrow, rectangle, ellipse, freehand, text), Undo/Redo, **Save a Copy** (default)
and Overwrite (explicit).

**In (video, 5b):** Trim by keyframe-snapped stream copy, Mute. Same shell, same history, same save
model.

**Out:** layers, masks, healing, straighten/arbitrary-angle rotation, batch editing, RAW, re-encoding
video filters.

**Formats.** Decode is not the constraint — encode is. Skia writes `png`, `jpg/jpeg`, `webp`, so those
are edited and saved in place. Everything else Woka lists (`heic`, `heif`, `avif`, `tif`, `tiff`,
`gif`, `bmp`) and every file holding more than one frame is edited **after an explicit conversion the
user agrees to** (§7). The Edit button is never a dead button; it either edits or offers the
conversion.

---

## 3. Architecture

### 3.1 The document is data, not pixels

An edit session never mutates a bitmap. It builds an immutable `EditDocument` — an ordered list of
`ToolOp` — and the canvas *renders* it. The same renderer runs at screen scale for the preview and at
native scale on save, which is what makes "markup rasterised at source resolution" fall out of the
design instead of being a special case.

```
ToolOp        data class(toolId: String, params: ParamSet) — the ONLY op type in the app
EditDocument  data class(ops: List<ToolOp>) — the whole edit, ~1 KB, serialisable by construction
EditorHistory List<EditDocument> + index, capped at 50 — snapshots, not inverse commands
EditorSession preview(op) / commit() / undo() / redo() / dirty — the only mutator
```

One op type, not one per tool: the document then needs no polymorphic serialiser, history is a list
copy, and — the point of §3.4 — the shell can render a tool's panel without knowing the tool.

`preview(op)` replaces a *pending* op and pushes **no** history entry; `commit()` folds it in and
pushes one. Which of the two a control uses is decided by the tool's manifest, not by the tool's code,
so a slider cannot accidentally produce 200 undo steps.

`EditorSession` is a ViewModel in all but name: `StateFlow` out, no `androidx.compose.*` inside, built
by Koin as a factory taking the media it edits.

**Serialisation.** `ParamSet` is *not* `Map<String, Any>` — that cannot round-trip through
`kotlinx.serialization`, and "plainly serialisable" was the whole argument for one op type. Values are
a closed `ParamValue` hierarchy (float, int, bool, text, colour, and a `JsonElement` payload for the
structured shapes markup owns). Nothing is written to disk in the first release; the document is
serialisable so that re-opening a saved edit, and restoring an interrupted session, are later tickets
rather than a rewrite.

### 3.2 Rendering: a recipe, not a pipeline

Ops fold into three draw-time primitives, so the live canvas allocates **no bitmap at all** while
editing:

```
RasterStage.Geometry(matrix, outputSize)   crop, rotate, flip, resize
RasterStage.Pixels(colorFilter)            adjustments, filter presets
RasterStage.Paint(draw)                    markup
```

The fold walks the ops, asks each owning tool for its stage, and collapses them into **one** matrix,
**one** `ColorFilter` (colour matrices multiply) and an ordered paint list. The canvas draws: source →
matrix → colour filter → paints. A brightness slider at 60 fps costs one `ColorFilter` rebuild per
frame and zero pixel work. The save path replays the same recipe onto a Skia surface at scale 1.0
against the **native-resolution** bitmap.

The canvas draws a **proxy**: the source downscaled so its longest edge is ≤ 2560 px. A 24 MP photo is
edited against a ~7 MP texture; the native bitmap is touched once, on save. Above 100 % canvas zoom
the proxy is no longer enough — markup precision is the reason zoom exists — so the canvas swaps to
the native bitmap for the visible region while zoomed (ME-116, deliberately later).

### 3.3 One shell, two media: the engine seam

`EditorSession`, the history, the tool contract, the schema-driven inspector, the save target and the
banner know nothing about pixels. Everything that does sits behind one interface, resolved per
`MediaKind`:

```
EditorEngine
  sourceSize: IntSize                      what the ops address, in source coordinates
  fold(document): Recipe                   ops → whatever this medium draws with
  @Composable Surface(recipe, modifier)    the live canvas for this medium
  save(document, target): EditSaveResult   the background job that writes the file
```

The image engine folds to a raster recipe and draws a bitmap. The video engine folds to a timeline
edit and draws the player plus a trim strip; its saver runs the Bytedeco ffmpeg with `-c copy`. The
shell composes `engine.Surface(...)` and never branches on media kind.

This is the seam that makes "the video editor inherits the shell" true rather than aspirational, and
the reason `EditorTool.stage()` returns a `ToolStage` marker rather than a `RasterStage` directly: a
trim tool has a stage, and it is not a matrix.

### 3.4 Tools — the plugin architecture

Every editing element is the same shape: an icon, a name, a line saying what it does, a handful of
parameters, and a rule turning those parameters into output. **Nothing in a tool knows where it sits,
how its panel is laid out, in what order it applies, or how it reaches Settings** — the shell derives
all of that from the manifest. Adding a tool is one directory and one line in the Koin module. (Named
`EditorTool` rather than `Plugin`: nothing is loaded from a jar at runtime, and calling it a plugin
would promise that.)

```kotlin
interface EditorTool {
    val manifest: ToolManifest              // 1. what it is
    val params: List<ParamSpec<*>>          // 2. what it edits — and its whole UI
    fun stage(params: ParamSet): ToolStage? // 3. what it performs — pure, no Compose
}

interface CanvasTool : EditorTool {         // only when it needs direct manipulation
    @Composable fun Overlay(scope: EditorToolScope)
}
```

**The manifest**

| Field | Consumed by |
|---|---|
| `id` | op ownership, settings key, test ids |
| `title` | rail tooltip, inspector header, settings row, "disabled tool" warning |
| `summary` | rail tooltip second line, settings row subtitle |
| `icon: DrawableResource` | rail button, settings row |
| `domain: ToolDomain` | **rail group and position** *and* **op application order** |
| `order: Int` | position within its group |
| `supports: Set<MediaKind>` | whether the tool appears at all for this media |
| `cardinality: Single \| Repeating` | whether a new op replaces the tool's previous one or appends |
| `interaction: Instant \| Continuous \| Canvas` | commit on change, on release, or hand over to `Overlay` |
| `shortcut: EditorShortcut?` | the keyboard table and the shortcuts help popover |

`ToolDomain` is `Geometry | Color | Annotate | Output`, and it does two jobs at once:

- **Placement:** the rail draws the groups in that order with a 1.dp divider between them. Reordering
  the rail is reordering an enum.
- **Application order:** the fold sorts ops by domain rank, *not* by the order the user clicked.
  Geometry before colour before annotation before output is the only semantically correct order — a
  crop performed after an arrow was drawn must still clip the arrow, and a resize must be the last
  thing before encoding. This removes a whole class of "the result depends on what I clicked first"
  bugs.

**The parameter schema *is* the UI.** The panel is not written per tool — it is rendered from the
schema, one row renderer per spec type:

| `ParamSpec` | Rendered as |
|---|---|
| `FloatParam(key, label, range, default, detent, unit)` | `DetentSlider` row + signed readout; double-click resets to `detent` |
| `IntParam(key, label, range, default)` | numeric field with steppers |
| `BoolParam(key, label)` | label + `IosSwitch` |
| `ChoiceParam(key, label, options)` | `SegmentedControl` (≤ 4 options) or a chip row |
| `PreviewChoiceParam(key, options, preview)` | thumbnail strip, each drawn through that option's own stage |
| `ColorParam(key, label, swatches)` | swatch row + custom-colour popover |
| `ActionParam(key, label, icon)` | icon button; always `Instant` |
| `ObjectParam<T>(key, serializer)` | **nothing** — structured payload owned by a `CanvasTool`'s overlay |

```kotlin
object AdjustTool : EditorTool {
    val Brightness = FloatParam("brightness", "Brightness", -1f..1f, default = 0f, detent = 0f)
    val Contrast   = FloatParam("contrast",   "Contrast",   -1f..1f, default = 0f, detent = 0f)
    override val params = listOf(Brightness, Contrast, /* … */)
    override fun stage(p: ParamSet) = RasterStage.Pixels(adjustMatrix(p[Brightness], p[Contrast]))
}
```

`p[Brightness]` is a `Float` at the call site and a `ParamValue.FloatValue` in the document.
Adjustments, Filters, Transform, Resize, HDR and Mute are therefore **pure data plus one pure
function** — no Compose code at all. Only Crop, Markup and Trim implement `CanvasTool`, and even they
get their inspectors from the schema; the overlay is only the on-canvas dragging.

**What the shell derives, and the tool never writes:** rail button, icon, tooltip, group, divider,
position · inspector header, every control row, its spacing and typography · preview-vs-commit
behaviour · undo granularity · the keyboard table and its help popover · the Settings → Editor toggle
row · the "3 operations from a disabled tool are not applied" notice · the ordering of ops in the
recipe · test ids.

**Enabling, disabling, failing.** The registry is a `StateFlow<List<EditorTool>>`; rail, inspector,
shortcut table and settings list all derive from it, so a tool appearing or disappearing re-renders
the editor with no other wiring. `WokaSettings.editorDisabledTools` persists per-tool enablement. An
op whose tool is absent or disabled is **skipped** by the fold and reported in the inspector — the
document never crashes and never silently loses work.

**The test of the design:** Mute is a manifest and one `BoolParam`. If adding it to the video editor
takes more than a file and a Koin line, §3.4 is wrong and the shell has grown a branch it should not
have.

---

## 4. UI / UX

### 4.1 Entry and exit

Two entry points, one destination:

- **Viewer** — `Edit` in the control pill (currently a stub wired to close the viewer) opens the
  editor **over** the viewer. The viewer stays mounted and frozen underneath, so `Cancel` returns to
  the exact zoom/pan/frame the user left. The native bitmap is already in `ImageBitmapCache` — the
  editor takes it from there, so opening is instant and nothing is decoded twice.
- **Grid context menu** — `Edit` on a single item, beside Rename/Copy/Delete, opening the same overlay
  directly on that file. Greyed out with a reason on a multi-selection (batch editing is out of scope)
  and on anything not editable.

Both routes call the same entry function with one path plus a frame index; nothing decides editability
twice — the engine factory is the single answer.

Exit: `Done` saves a copy and returns to the viewer showing it; `Cancel`/`Esc` returns unchanged, via
a confirmation dialog if the document is dirty.

### 4.2 Layout

Full-bleed over the viewer's existing dark backdrop. The carousel, the nav arrows, the size control
and the dock accessories are hidden while editing — the editor is a mode, not a panel.

```
┌───────────────────────────────────────────────────────────────┐
│  Cancel        photo.jpg · Edited    HDR ▾   ↶  ↷     [Done ▾]│  top bar (accessory)
├───────────────────────────────────────────────────────────────┤
│                     ┌───────────────────┐                     │
│                     │  media + overlay  │                     │  canvas: engine.Surface
│                     └───────────────────┘                     │  through the recipe
│            ╭─────────────────────────────────────╮            │
│            │  Brightness  ──────●────────   +12   │            │  inspector — rendered from
│            │  Contrast    ─────●─────────    -4   │            │  the param schema
│            ╰─────────────────────────────────────╯            │
│                ╭───────────────────────────────╮              │
│                │  ⤢  ⟳  ⤡ │ ◐  ✦ │ ✎ │ ⇲       │              │  rail — groups from ToolDomain
│                ╰───────────────────────────────╯              │
└───────────────────────────────────────────────────────────────┘
```

Concrete geometry, colours and timings live in `ai/mockups/design/editor.html` and are mirrored by
`feature/editor/shell/EditorTokens.kt`. **The editor follows the app theme**; the one layer that does
not is chrome sitting on the photograph, which takes `ViewerChrome`'s dark scrims and its light
`onScrim*` foregrounds in both themes — legibility over a picture cannot depend on a preference. The rail reuses the viewer's controls container; row renderers reuse
`DetentSlider`, `SegmentedControl`, `IosSwitch`, `DropdownItem` — the existing components, wired once
in the shell instead of once per tool. The selected tool and the `Done` pill use
`Theme.colors.accentEditor` (#BF5AF2).

### 4.3 Canvas interaction

The canvas is `Fit` at all times; the size control is gone in edit mode.

- **Primary drag** goes to the active `CanvasTool`'s `Overlay` first. If it does not consume, nothing
  happens (no accidental pan).
- **Wheel** zooms 100–800 % about the cursor, reusing `ScaleMath`; **middle-drag** or **Space+drag**
  pans, clamped by `PanBounds`. Zoom exists for markup precision only and resets on tool change.
- Hovering a markup shape shows its handles; `Delete`/`Backspace` removes the selected one.

### 4.4 Keyboard

Registered through the viewer's existing shortcut plumbing and taken over while the editor is open, so
no viewer or app shortcut fires underneath.

| Key | Action |
|---|---|
| `Esc` | Cancel (confirm if dirty) |
| `Ctrl+S` / `Enter` | Done — save a copy |
| `Ctrl+Shift+S` | Overwrite (goes through the confirmation) |
| `Ctrl+Z` / `Ctrl+Shift+Z` / `Ctrl+Y` | Undo / Redo |
| `Delete` | Delete selected markup shape |
| `Tab` / `Shift+Tab` | Next / previous tool |
| `?` | Shortcuts popover — built from the manifests |
| tool-declared | `manifest.shortcut`; a disabled tool's key disappears with it |

### 4.5 Save UX

`Done` closes the editor immediately and runs the save in the background, with the same banner pattern
as `FrameExportBanner`: "Saving…" → "Saved" / "Save failed — original untouched". The folder listing
is refreshed when the job reports success, because a copy is a **new** file and nothing watches the
directory for it.

---

## 5. Saving — non-destructive by default

**There is no hidden backup store.** The original is preserved by not being written to.

- **Save a Copy (default, `Done`).** `photo.jpg` → `photo-edited.jpg`; a taken name gets `-2`, `-3`.
  The name is shown before the write, not after it. The source keeps its thumbnail, its watch record
  and its place in the folder; the copy is a new item the grid picks up.
- **Overwrite (explicit).** Its own menu entry, behind a confirmation that says plainly that Woka
  keeps no copy and the original cannot be recovered. The dialog carries a **"Don't ask again this
  session"** option: the consent lives in memory for the run of the app and is gone on restart. It is
  deliberately **not** a `WokaSettings` field — a persisted flag would quietly turn destructive
  overwrite into the permanent default, which is the one thing this section exists to prevent.
- **Why no backup store.** Git covers the repository, not the user's linked folders — a photo in
  `D:\Photos` is in no history. A hidden `.originals` directory would be Woka inventing private
  version control for files it does not own, with an index to keep consistent, orphans to sweep, and a
  "Revert to Original" promise that breaks the moment the folder moves to another machine. Not writing
  to the source gives the same guarantee with none of that.
- **Windows-safe write**: render → encode → write a temp file beside the target → atomic move with
  replace, retried a few times, because the viewer underneath may still hold the file. Both save modes:
  a half-written copy is as bad as a half-written original.
- **Stale-source guard**: if the file changed on disk since the editor opened it, the save stops and
  says so rather than overwriting someone else's change.
- **After save**: a copy is a new path and needs no invalidation. An overwrite drops the native and
  preview entries from `ImageBitmapCache`, deletes the stale poster, and evicts Coil's memory and disk
  entry for the path — a grid still showing the pre-edit tile is the bug that prevents.

---

## 6. Colour, EXIF and the things that get silently destroyed

Everything here is **probe first, decision second** (ME-010) — none of it is assumed.

- **EXIF orientation. Measured 2026-08-17 (ME-010): the decode path already applies the tag** — a
  640×480 source with `Orientation=6` arrives as 480×640. The rotation is therefore already in the
  pixels the editor holds, so a save must write the tag as **1** and must not rotate again. Splicing
  the source `APP1` back unchanged would double-rotate on the next open.
- **EXIF preservation.** Baseline decision: **preserve**. Camera, lens, date and GPS are not the
  editor's to throw away. **Measured 2026-08-17: a Skia JPEG re-encode drops `APP1` entirely**, so
  preservation is never automatic — the segment survives only if ME-104 splices it in deliberately.
  If the splice proves unreliable, the fallback is a one-time "metadata will be dropped" notice.
- **Colour profile.** An sRGB assumption is fine for the first release, and must be an explicit,
  logged assumption rather than an oversight: a Display-P3 photo re-encoded without its profile shifts
  visibly.

### 6.1 HDR is a tool, not a footnote

Woka already tone-maps HDR stills at draw time (`HdrShader`, `HdrImagePainter`, driven by
`StillColorInfo.transfer`). The editor exposes that as **two separate controls**, because they answer
two different questions and conflating them is how HDR features become confusing.

**The HDR tool** — `Output` domain, one `ChoiceParam`, part of the document:

| Value | What it does | Needs |
|---|---|---|
| `Keep` | the output carries the source's transfer function unchanged | an HDR-capable encoder — **ffmpeg**, not Skia |
| `To SDR` | bakes the tone map into the pixels and writes an SDR file | the existing shader, rendered into the surface |
| `To HDR` | expands an SDR source into a PQ output | inverse tone mapping + an HDR encoder — **still gated**, see below |

**Probe results, 2026-08-17 (ME-010, ffmpeg 8.1.2-1.5.14). The gate is not resolved yet.**

Settled:

- **HEIF output is impossible**: `libx265` cannot open a HEIF output at all. AVIF or nothing.
- **HLG is never actually written**: the PQ and HLG outputs come out byte-identical, so
  `-color_trc arib-std-b67` is silently ignored. **HLG is not an offerable target**; `To HDR` means
  PQ if it means anything.
- **PQ AVIF via `libaom-av1` writes correctly by every measurement available** — PSNR 52 dB against
  the source, identical stream tags, and a `colr`/`nclx` payload byte-identical to a known-good
  reference.

**Resolved 2026-08-18. The gate falls, and it was never the file.**

Windows Photos renders our written file blown out — but it does not render the *reference* correctly
either, flashing correct HDR and falling back to dull. **A viewer that mishandles a known-good file
cannot decide whether ours is good**, so its verdict was evidence about Photos, not about the file.

The check that mattered was the one never run: **how Woka itself draws these files**. Run, it found a
bug in `HdrShader` — the gamut matrix ran before the tone curve, feeding a rational function defined
on non-negative light the negatives that every out-of-709 colour produces, so saturated colours came
out full-scale (red drew yellow, blue drew magenta, the rest white). With the matrix moved after the
curve, Woka draws both the reference and **our own written PQ AVIF** correctly, confirmed on sight.

So `To HDR` is technically clear: **PQ AVIF via `libaom-av1`, and nothing else** — no HEIF, no HLG.
Whether it *ships* is still a product call for §6.1, but no measurement blocks it.

**Two lessons, recorded because both will recur:** a read-back that checks the transfer *tag* cannot
certify an HDR file — the probe reported "PQ + wide" for a file rejected on sight. And a visual check
is only as good as the viewer doing the looking, **including when that viewer is ours**.

**None of this touches HDR *display*.** Woka already tone-maps HDR stills at draw time, and the
`Auto` / `Force HDR` / `Force SDR` view mode below is unaffected. Everything above is about what a
save *writes*.

Skia is not the encoder for any HDR output: it does not even read a valid PQ `cICP` PNG correctly
(measured 2026-08-07, recorded in `StillColorInfo`). HDR output therefore goes through the bundled
ffmpeg, which is why the HDR tool depends on ME-013.

**The HDR view mode** — *not* part of the document. A tri-state (`Auto` / `Force HDR` / `Force SDR`)
changing how a still is *displayed*, in the viewer and mirrored as a preview toggle in the editor's
top bar. It changes no pixels and is persisted in `WokaSettings`. It exists so the user can see what a
tone-mapped file actually looks like before deciding what the tool should write.

---

## 7. Conversion on demand

Formats Skia cannot encode, and multi-frame files, are converted **after the user agrees** — the Edit
button is never dead. A sheet states the target name, that the source file stays exactly where it is,
and (for a frame set) which single frame survives. Photographic sources default to JPEG q95, graphics
and anything with alpha or multiple frames to PNG; an HDR source offers an HDR-capable target (§6.1)
when the probe says the encoder can write one.

The conversion writes the new file beside the source and opens the editor on it. The source is
**kept** — §5's rule holds here too, and a HEIC the user shot on a phone is not Woka's to delete.

---

## 8. What the earlier plan did not account for

Read against the tree on 2026-08-16. The earlier `image-editor-plan.md` (now in `archive/`) was strong
on the tool architecture and is carried forward almost intact; these are the holes that were closed,
listed because each would have cost a rewrite:

1. **`ParamSet` as `Map<String, Any>` cannot be serialised** — and serialisability was the entire
   argument for one op type. Closed by `ParamValue` (ME-003).
2. **`stage()` returned a `RasterStage`** — a raster type in the shared contract means the video editor
   cannot implement a tool without pretending a trim is a matrix. Closed by `ToolStage` (ME-005).
3. **Nothing named the seam the shell talks to** — leaving the save job, the fold and source loading
   media-specific. Closed by `EditorEngine` (ME-008).
4. **No session lifetime** — who constructs `EditorSession`, in which scope, what cancels it. Now a
   Koin factory with an owner (ME-004).
5. **No colour story** — editing a tone-mapped HDR still and saving it destroys the HDR. Now a
   first-class tool with a probe behind it (§6.1) rather than a warning label.
6. **The probes had nowhere to publish.** The earlier plan assumed `ai/docs/testing/`, which is gone —
   read, acted on and deleted deliberately, not lost. Closed by making the ticket the place: a probe's
   output is dated in the ticket that asked for it, and the decision it settles goes into §9
   (`conventions/feature-docs.md` §7, ME-010).
7. **No mockup.** Design values lived in prose, which drifts from the code silently. Closed by ME-012.
8. **A 2560 px proxy plus 800 % zoom is a blurred canvas** at the precision markup exists for.
   Acknowledged and deferred deliberately (ME-116) rather than discovered.
9. **Build debt was invisible** — a dependency nobody imports, a beta pinned where a stable exists,
   four libraries behind, and `material3` held for four symbols. ME-001, ME-013, ME-014.
10. **The whole backup apparatus was solving a problem that "don't overwrite" solves for free.**
    Replaced by Save a Copy, which deleted an index, a sweep, an orphan rule and a broken promise.

Surviving unchanged: one op type, the manifest deriving placement *and* application order, the
schema-as-UI rule, the recipe-not-pipeline rendering, the proxy, the atomic write, and the conversion
sheet instead of a dead Edit button.

---

## 9. Decision log

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-08-16 | One op type or one per tool? | One `ToolOp` + generic `ParamSet` | No polymorphic serialiser, history is a list copy, the shell renders panels it does not know |
| 2026-08-16 | `Map<String, Any>` for params? | No — closed `ParamValue` hierarchy | `Any` cannot round-trip through `kotlinx.serialization` |
| 2026-08-16 | Does `stage()` return `RasterStage`? | No — a `ToolStage` marker | A trim stage is not a matrix; without this the video editor cannot reuse the tool contract |
| 2026-08-16 | How does video reuse the shell? | An `EditorEngine` per `MediaKind` | The shell composes `engine.Surface(...)` and never branches on media |
| 2026-08-16 | Is the document persisted in v1? | No, but serialisable by construction | Re-opening a saved edit is a later ticket, not a rewrite |
| 2026-08-16 | Where is the editor mounted? | Over a frozen, still-mounted viewer | Cancel returns to the exact zoom/pan/frame; the bitmap is already cached |
| 2026-08-16 | Backups? | **None.** Save a Copy is the default; overwrite is explicit and confirmed | Git covers the repo, not `D:\Photos`. Not writing to the source beats inventing private version control for files Woka does not own |
| 2026-08-16 | Copy name? | `photo-edited.jpg`, collisions `-2`, `-3` | Reads as what it is; sorts next to the original; no space to break a shell command |
| 2026-08-16 | Overwrite consent? | Ask every time, plus a session-scoped "don't ask again" | Repetition is the point for a destructive action, but twenty in a row is not served by twenty dialogs. In memory only |
| 2026-08-16 | HDR: one control or two? | Two — an `Output` **tool** in the document and a **view mode** that changes no pixels | They answer different questions: what the file becomes, versus what the screen shows |
| 2026-08-16 | Who encodes HDR? | ffmpeg, never Skia | Skia misreads a valid PQ `cICP` PNG (2026-08-07, `StillColorInfo`); the bundled ffmpeg can write PQ AVIF, and only that (ME-010) |
| 2026-08-16 | Entry points? | Viewer control pill **and** grid context menu, one entry function | Two doors, one decision about editability |
| 2026-08-16 | `material3`? | Removed before the editor shell (ME-014) | 37 files hold a pre-release dependency for four symbols; 23 files already use the `BasicText` idiom |
| 2026-08-17 | Does the decode path apply EXIF `Orientation`? | **Yes** — measured, ME-010 | A 640×480 source with `Orientation=6` arrives 480×640, so the editor must not rotate again and must save the tag as 1 |
| 2026-08-17 | Does a Skia re-encode keep EXIF? | **No** — `APP1` is dropped entirely | Preservation is never automatic; ME-104 splices the segment in or the metadata is gone |
| 2026-08-17 | What must an overwrite invalidate? | `ImageBitmapCache.key(path, 0)` and `previewKey(path)`, and nothing else | The `.thumbnails` poster is keyed on length + mtime, so it orphans rather than going stale. `ImageBitmapCache` has no removal API yet — one is needed |
| 2026-08-17 | Can the bundled ffmpeg write HDR stills? | **AVIF yes (PQ), HEIF no, HLG never written** | `libx265` cannot open a HEIF output; the HLG request produces a byte-identical PQ file, so the flag is ignored |
| 2026-08-17 | Does `To HDR` ship? | **Still gated**, and the evidence so far is void | Every HDR measurement was taken against `hdr_pq.avif`, which turned out to be SDR pixels wearing HDR tags (`psnr` between the PQ and HLG fixtures is **inf** — `setparams` only tags). Re-run against a genuine source |
| 2026-08-18 | Why did the viewer burn out HDR stills while the grid did not? | **`HdrShader` ran the BT.2020→709 matrix before the tone curve** — fixed by swapping the order (ME-010) | `hable` is defined on non-negative light; every out-of-709 colour reaches it negative, its denominator flips sign, and it returns >1. Red drew yellow, blue drew magenta, the rest white. The HLG fixture looked right only because its lower absolute level kept four of six negatives small enough to clamp to zero — magnitude, not branch |
| 2026-08-18 | Does the editor follow the app theme? | **Yes** — like the rest of the app. Only the on-media layer is fixed | Measured while drawing the mockup: chrome over media took its background from a `ViewerChrome` scrim and its text from `Theme.colors.textPrimary`, which in the light theme is `#1C1C1E` — **1.05 : 1** over a dark photograph, 6.68 : 1 over a bright one. Legibility decided by the picture is not a theme |
| 2026-08-18 | Where do the on-media foreground colours live? | `ViewerChrome`, beside the scrims it already owns (`onScrim`, `onScrimSecondary`, `onScrimDisabled`, `onScrimBorder`, `onScrimHover`, `onScrimSelected`) | The pair had only one half, which is why ~27 viewer files reach for `Theme.colors` instead. Putting the other half in `EditorTokens` would have been a second source of truth. The editor reads them from its first commit; the viewer migrates in **ME-121** |
| 2026-08-18 | Is HDR *display* now correct? | **No — one defect left, and it is the viewer's, not the editor's** | The tone curve's white point is the literal `W = 11.2`, asserting 1120-nit content. A real 174-nit HDR frame therefore draws SDR white at 148/255 instead of 255. Needs a genuine HDR still to tune against, so it is **ME-120** in the backlog rather than a hold on ME-010 |
| 2026-08-17 | Is a transfer-tag read-back enough to certify HDR output? | **No** | The probe reported "PQ + wide" for the file rejected on sight. And a visual check is only as good as the viewer doing the looking |
| 2026-08-17 | What does a 100 MP source cost? | 384 MB native, 667 ms to decode — larger than the whole cache budget | Editing is fine because the canvas draws a 2560 px proxy built in 8 ms; caching the native is what has to be avoided |

---

## 10. Known gaps

Not oversights — things this document deliberately has no answer for yet. Each becomes a ticket when
it becomes the next question, per `ai/docs/conventions/feature-docs.md`.

- **Very large sources — measured 2026-08-17 (ME-010).** A 100 MP source (11600×8700) decodes in
  667 ms to **384 MB** of native pixels; a 24 MP one is 91 MB. The proxy is cheap at both — 2560 px
  in 6–8 ms — and it is what the canvas draws, so *editing* a huge photo is comfortable. *Holding* it
  is not: 384 MB exceeds the whole 256 MB `ImageBitmapCache` budget, so one such picture would evict
  everything else and still not fit. The guard rail this points at is "keep the native bitmap out of
  the shared cache for the duration of an edit", not "refuse the file" — but the rail itself is still
  a ticket to write.
- **Save cancellation.** What happens to an in-flight save when the app is closing. Nothing writes a
  partial file — the atomic move is the last step — but the banner and the job's lifetime are unstated.
- **Editor plus PiP.** The picture-in-picture window can be open while the editor is; who owns the
  keyboard and whether editing is even allowed then is undefined.
- **Frame sets.** Editing frame N of a burst is covered by conversion, but nothing says what the
  carousel shows afterwards.
- **Localisation.** Every string in this feature is English, like the rest of the app. If that ever
  changes, the manifests are where the strings live.
