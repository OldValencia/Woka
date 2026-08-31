# Feature: Image Editor (Roadmap Phase 5a)

Two editors, one shell. This is the first one, and it carries **the shell both of them use**:
ME-001…ME-014 are the foundation, media-agnostic from the first commit, and they live here because
here is where they were built. The video editor (5b) is a sibling folder —
[`../video-editor/`](../video-editor/video-editor.md) — that adds a tool set, an engine and a saver to
this machinery and changes none of it. Split out of one `media-editor/` folder on 2026-08-24; the
ticket IDs are unchanged, because an ID is never reused (`conventions/feature-docs.md` §3).

This file is **the feature document** — scope, architecture, UI, decisions, status. It is the only
durable document; it names no source files, because file lists are what rot first. Those live in the
tickets:

```
image-editor/
  image-editor.md   ← you are here: what the thing is and why. The feature file.
  tickets/          one file per specified ticket + backlog.md for everything after
  archive/          the two superseded plans, banner-marked, not a source of truth
```

Conventions: `ai/docs/conventions/feature-docs.md` · `tickets.md` · `code-style/README.md`.
UX model: the iOS Photos editor. Visual truth: `ai/mockups/design/FINAL_design_mockup.html`, plus
`ai/mockups/design/editor.html`.

---

## 1. Status

Build order is top to bottom. This table is the only place a ticket's state is recorded.

| Ticket                                                                     | Title | State |
|----------------------------------------------------------------------------|---|---|
| [ME-001](tickets/implemented/ME-001-toolchain-refresh.md)                  | Toolchain and dependency refresh | **done** 2026-08-16 |
| [ME-013](tickets/implemented/ME-013-ffmpeg-8-1-2.md)                       | JavaCV 1.5.14 / FFmpeg 8.1.2 | **done** 2026-08-16 |
| [ME-002](tickets/implemented/ME-002-package-skeleton.md)                   | Editor package skeleton and DI seam | **done** 2026-08-16 |
| [ME-003](tickets/implemented/ME-003-param-schema.md)                       | Parameter schema | **done** 2026-08-16 |
| [ME-004](tickets/implemented/ME-004-document-history-session.md)           | Document, history, session | **done** 2026-08-16 |
| [ME-005](tickets/implemented/ME-005-tool-contract.md)                      | Tool contract | **done** 2026-08-16 |
| [ME-006](tickets/implemented/ME-006-tool-registry.md)                      | Tool registry, enablement, settings | **done** 2026-08-16 |
| [ME-007](tickets/implemented/ME-007-raster-fold.md)                        | Raster stage and the fold | **done** 2026-08-16 |
| [ME-008](tickets/implemented/ME-008-engine-seam.md)                        | Editor engine seam | **done** 2026-08-16 |
| [ME-009](tickets/implemented/ME-009-save-targets.md)                       | Save targets and the atomic write | **done** 2026-08-17 |
| [ME-010](tickets/implemented/ME-010-probes.md)                             | Capability probes | **done** 2026-08-18 |
| [ME-011](tickets/implemented/ME-011-test-harness.md)                       | Test harness and the fixture tool | **done** 2026-08-18 |
| [ME-012](tickets/implemented/ME-012-mockup-tokens.md)                      | Editor mockup and tokens | **done** 2026-08-18 |
| [ME-014](tickets/implemented/ME-014-remove-material3.md)                   | Remove `material3` — before ME-101 | **done** 2026-08-18 |
| [ME-101](tickets/implemented/ME-101-editor-shell.md)                       | Editor shell | **done** 2026-08-18 |
| [ME-102](tickets/implemented/ME-102-param-row-renderers.md)                | Parameter row renderers | **done** 2026-08-18 |
| [ME-103](tickets/implemented/ME-103-image-surface.md)                      | Image surface | **done** 2026-08-18 |
| [ME-104](tickets/implemented/ME-104-save-pipeline.md)                      | Save pipeline | **done** 2026-08-18, ~~superseded~~ by ME-132 |
| [ME-105](tickets/implemented/ME-105-transform-tool.md)                     | Transform tool | **done** 2026-08-18 |
| [ME-106](tickets/implemented/ME-106-entry-points.md)                       | Entry points | **done** 2026-08-18 |
| [ME-131](tickets/implemented/ME-131-recipe-carrier.md)                     | Recipe carrier — JPEG and PNG | **done** 2026-08-19 |
| [ME-132](tickets/implemented/ME-132-done-writes-the-recipe.md)             | `Done` writes it; Export removed | **done** 2026-08-19 |
| [ME-133](tickets/implemented/ME-133-readers-apply-the-recipe.md)           | Every reader applies the recipe | **done** 2026-08-19 |
| [ME-134](tickets/implemented/ME-134-reopening-an-edited-file.md)           | Reopening an edited file | **done** 2026-08-19 |
| [ME-135](tickets/implemented/ME-135-readers-notice-a-save.md)              | Readers notice a save | **done** 2026-08-19 |
| [ME-107](tickets/implemented/ME-107-revert-to-original.md)                 | Revert to Original — **un-dropped** | **done** 2026-08-19 |
| [ME-108](tickets/implemented/ME-108-isobmff-carrier.md)                    | ISOBMFF carrier — HEIC, HEIF, AVIF | **done** 2026-08-19 — by hand outstanding |
| [ME-137](tickets/implemented/ME-137-webp-carrier.md)                       | RIFF carrier — WebP, and bucket B empties | **done** 2026-08-19 — by hand outstanding |
| [ME-138](tickets/implemented/ME-138-gif-carrier.md)                        | GIF carrier — Application Extension, one recipe for every frame | **done** 2026-08-21 — confirmed by hand |
| [ME-139](tickets/implemented/ME-139-tiff-carrier.md)                       | TIFF carrier — private IFD tag | **done** 2026-08-20 — by hand outstanding |
| [ME-140](tickets/implemented/ME-140-the-canvas-plays.md)                   | The editing canvas plays what the viewer plays | **done** 2026-08-21 — confirmed by hand |
| [ME-109](tickets/implemented/ME-109-crop-tool.md)                          | Crop tool, and the fold at preview scale | **done** 2026-08-22 — confirmed by hand; the shape every later tool takes |
| [ME-110](tickets/implemented/ME-110-resize-tool.md)                        | Resize tool | **done** 2026-08-22 — by hand outstanding |
| [ME-111](tickets/implemented/ME-111-adjustments-tool.md)                   | Adjustments — six sliders, one colour matrix | **done** 2026-08-22 — confirmed by hand |
| [ME-112](tickets/implemented/ME-112-filter-presets.md)                     | Filter presets, and the preview-choice row | **done** 2026-08-22 — by hand outstanding |
| [ME-113](tickets/implemented/ME-113-markup-engine.md)                      | Markup engine — four shapes, one draw routine | **done** 2026-08-24 |
| [ME-114](tickets/implemented/ME-114-markup-interaction.md)                 | Markup interaction — the overlay and `Delete` | **done** 2026-08-24 — by hand outstanding |
| [ME-122](tickets/implemented/ME-122-markup-text.md)                        | Markup text — the fifth shape | **done** 2026-08-26 — confirmed by hand with ME-145 |
| [ME-145](tickets/implemented/ME-145-markup-text-editing.md)                | Markup text — editing on the canvas | **done** 2026-08-27 — confirmed by hand after four fixes |
| [ME-146](tickets/implemented/ME-146-markup-text-box-and-background.md)     | Markup text — a box and a background | **done** 2026-08-27 — confirmed by hand after three fixes |
| [ME-115](tickets/implemented/ME-115-editor-settings-panel.md)              | Editor settings panel | **done** 2026-08-28 — by hand outstanding |
| [ME-116](tickets/implemented/ME-116-zoomed-canvas-fidelity.md)             | Zoomed canvas fidelity — the zoom itself shipped with ME-109 | **done** 2026-08-28 — by hand outstanding |
| [ME-141](tickets/implemented/ME-141-styles-pad-and-palette.md)             | Styles: two carousels, a pad and a palette slider | **done** 2026-08-28 — panel reshaped by hand over three passes; the last one not yet seen on screen. What the pad *means* is [ME-150](tickets/implemented/ME-150-the-pad-shapes-the-look.md) |
| [ME-147](tickets/implemented/ME-147-multi-select-chip-row.md)              | A chip row that lights more than one — `ToggleSetParam` | **done** 2026-08-29 — against `FixtureTool`; no tool touched |
| [ME-151](tickets/implemented/ME-151-markup-text-faces.md)                  | Markup text — the four faces | **done** 2026-08-29 — confirmed by hand after one fix, the glyphs rendering as dots |
| [ME-148](tickets/implemented/ME-148-markup-text-frame.md)                  | Markup text — the frame you can grab | **done** 2026-08-29 |
| [ME-149](tickets/implemented/ME-149-moving-a-mark-with-no-tool-open.md)    | Moving a mark with no tool open | **done** 2026-08-29 |
| [ME-152](tickets/implemented/ME-152-the-caption-box-is-the-type.md)        | The caption's box is the type, not the font's line box | **done** 2026-08-29 — the caret half measured impossible and recorded, not built |
| [ME-153](tickets/implemented/ME-153-paired-rows-and-a-wide-markup-card.md) | Two controls on one line, and a wide Markup card | **done** 2026-08-29, ~~superseded~~ by ME-155 — buttons beside a slider was refused on screen |
| [ME-155](tickets/implemented/ME-155-a-compact-control-bar.md)              | A compact control bar: every value behind its own button | **done** 2026-08-29 — confirmed by hand after five passes; the two-drawer shape was dropped the same day |
| [ME-154](tickets/implemented/ME-154-the-overlay-lives-in-the-picture.md)   | The overlay lives in the picture's space, not the canvas's | **done** 2026-08-29 — the drift reported by hand |
| [ME-150](tickets/implemented/ME-150-the-pad-shapes-the-look.md)            | The pad shapes the look, not the photograph | **done** 2026-08-29 — confirmed by hand, constants included. The pad's `Reset` and two right-clicks were asked for on top of it |
| [ME-127](tickets/implemented/ME-127-output-quality-is-q100.md)             | Output quality is q100, and there is nothing to configure | **done** 2026-08-30 — a policy written into the constant that would decide it; bucket B is empty, so nothing on screen moved |
| [ME-130](tickets/implemented/ME-130-the-save-banner.md)                    | The save banner, and the save that outlives the editor | **done** 2026-08-30 — confirmed by hand |
| [ME-124](tickets/implemented/ME-124-editor-and-picture-in-picture.md)      | Returning from the video may not destroy an open editor | **done** 2026-08-30 — rewritten the same day it was specified: the keyboard rule was dropped for a save on the way out. Confirmed by hand, along with a PiP bug it uncovered — a photograph opened over a playing video was refused with "Video Already Playing" |
| [ME-126](tickets/implemented/ME-126-a-recipe-keyed-by-frame.md)            | A recipe keyed by frame | **done** 2026-08-30 — no observable change by design; the tests are the deliverable. `EditRecipe.read` now answers an `EditPayload`, so eleven assertions moved to `documentIn(file, 0)`. Confirmed by hand |
| [ME-156](tickets/implemented/ME-156-all-frames-or-this-one.md)             | All Frames or This Frame: the switch that says who the edit is for | **done** 2026-08-30 — confirmed by hand |
| [ME-157](tickets/implemented/ME-157-frames-that-carry-their-own-edit.md)   | The frame strip becomes shared, and says which frames carry their own edit | **done** 2026-08-30 — confirmed by hand |
| [ME-158](tickets/implemented/ME-158-the-editors-frame-strip.md)            | The editor's frame strip | **done** 2026-08-30 — confirmed by hand |
| [ME-123](tickets/implemented/ME-123-a-huge-source-never-enters-the-cache.md) | A huge source never enters the shared cache | **done** 2026-08-30 — it reverses a 2026-08-17 decision and the test that asserted it; `byteSize` and the gate split into `CacheAdmission.kt`. Confirmed by hand |
| [ME-125](tickets/implemented/ME-125-closing-the-window-waits-for-the-save.md) | Closing the window waits for the save | **done** 2026-08-31 — by hand outstanding |
| [ME-160](tickets/implemented/ME-160-the-close-button-asks-about-unsaved-work.md) | The close button asks about unsaved work | **done** 2026-08-31 — found by hand the same day ME-125 shipped; by hand outstanding |
| [ME-128](archive/ME-128-more-than-one-editor-at-once.md)                   | ~~More than one editor at once, and the dock list that reaches them~~ | **dropped** 2026-09-01 — its own "Why now" had stopped being true; the id is retained and never reused |
| [ME-159](tickets/implemented/ME-159-markup-is-sized-in-absolute-pixels.md) | Markup defaults do not adapt to the picture that was opened | **done** 2026-08-31 |
| [ME-117…ME-1xx](tickets/backlog.md)                                        | Image editor | backlog |

ME-201…ME-204, the video editor's own tickets, moved to
[`../video-editor/tickets/backlog.md`](../video-editor/tickets/backlog.md) on 2026-08-24 and are
tracked by that feature's status table. The fourteen foundation tickets above are shared, and stay
here.

**Why the foundation is fourteen tickets and no pixels.** Every one of ME-002…ME-011 is shared by both
editors and testable without a window. When the video editor arrives, the work is a tool set, an
engine and a saver — not a second application built beside the first. If any ME-2xx ticket needs the
shell changed, the foundation was wrong and the fix belongs in the foundation.

The order is also a risk order: the save targets and atomic write (ME-009) and the probes that decide
what re-encoding does to EXIF, colour and caches (ME-010) land before any code can write anything.

---

## 2. Scope

**In (image):** Rotate 90°, Flip H/V, Crop (aspect presets), Resize, Adjustments, Filter presets,
HDR on/off, Markup (arrow, rectangle, ellipse, freehand, text), Undo/Redo, and **`Done`** — the only
save there is (§5).

**In (video, 5b):** nothing here — Trim and Mute belong to
[the video editor](../video-editor/video-editor.md), on this shell and this save model.

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
| `PreviewChoiceParam(key, options, preview, section)` | thumbnail strip, each drawn through that option's own stage; `section` heads it when a tool stacks two |
| `ColorParam(key, label, swatches)` | swatch row + custom-colour popover |
| `ActionParam(key, label, icon)` | icon button; always `Instant` |
| `ObjectParam<T>(key, serializer)` | **nothing** — structured payload owned by a `CanvasTool`'s overlay |
| `PadParam(key, label, xLabel, yLabel, xRange, yRange)` | 120.dp square with cross-hairs; one drag writes a `ParamPoint`, y inverted, double-click resets |

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

Exit: `Done` writes the recipe into the file and returns to the viewer, which now draws the edit
(§5); `Cancel`/`Esc` returns unchanged, via a confirmation dialog if the document is dirty.
**The window's own close button is a third exit** (ME-160): over a dirty editor it asks
**Save / Don't save / Cancel** — three answers, because on quit "keep editing" is not one of them —
and `Save` holds the window open until the write has landed (ME-125). A clean editor, and no editor
at all, quit with no question. Beside the
back control sits **Revert** — red, confirmed, and the one action that removes everything Woka wrote
into the file (ME-107). A file
that already carries a recipe opens with it loaded, so editing an edit continues where it left off
rather than starting again — and never degrades, because every render starts from the original pixels.

### 4.2 Layout

Full-bleed over the viewer's own backdrop — `Theme.colors.background`, the same surface the
viewer draws a picture on, and **not** a black plate of the editor's (2026-08-21). The carousel, the nav arrows, the size control
and the dock accessories are hidden while editing — the editor is a mode, not a panel.

```
┌───────────────────────────────────────────────────────────────┐
│  Cancel        photo.jpg · Edited    HDR ▾   ↶  ↷     [Done ▾]│  top bar (accessory)
├───────────────────────────────────────────────────────────────┤
│              ╭──────────────┬──────────────╮                  │  frame scope (ME-156) —
│              │  All Frames  │  This Frame  │                  │  multi-frame stills only
│              ╰──────────────┴──────────────╯                  │
│   ▤       Replaces the separate edits on 2 frames             │
│   ▤ ●               ┌───────────────────┐                     │
│   ▤                 │  media + overlay  │                     │  canvas: engine.Surface
│   ▤                 └───────────────────┘                     │  through the recipe
│   ▤        ╭─────────────────────────────────────╮            │  frame strip (ME-158) —
│   ▤        │  Brightness  ──────●────────   +12   │            │  same rule as the switch;
│            │  Contrast    ─────●─────────    -4   │            │  ● marks a frame carrying
│            ╰─────────────────────────────────────╯            │  its own edit (ME-157)
│         ╭───────────────────────────────────────╮             │
│         │ ⤢ Transform │ ⇲ Crop │ ◐ Adjust │ ✎ … │             │  rail — icon **and** name,
│         ╰───────────────────────────────────────╯             │  one row, groups from ToolDomain
└───────────────────────────────────────────────────────────────┘
```

**The switch and the strip appear together and never apart**, for a file that holds more than one
picture and is not an animation (`EditorFrames.allowsPerFrame`). A photograph — the overwhelmingly
common case — draws neither and is laid out exactly as it was before ME-156. The canvas is *inset* by
the strip's width rather than drawn under it, the same measurement `MediaViewerPlayer` makes.

**Every tool from here on takes the shape Crop settled on** (ME-109, confirmed by hand 2026-08-22).
That shape, in one line: **a rail entry of icon plus name, all tools in a single row, no submenus;
the tool's own controls in the schema-driven inspector directly above it; anything that has to be
pointed at on the picture drawn as a `CanvasTool` overlay.** A tool contributes a manifest, a param
schema and — where it needs one — an overlay, and gets that layout without writing any of it.

The name beside the icon is not decoration: with names in tooltips, two tools whose glyphs had not
been drawn yet were two identical squares nobody could tell apart (2026-08-22).

Concrete geometry, colours and timings live in `ai/mockups/design/editor.html` and are mirrored by
`feature/editor/shell/EditorTokens.kt`. **The editor follows the app theme**; the one layer that does
not is chrome sitting on the photograph, which takes `ViewerChrome`'s dark scrims and its light
`onScrim*` foregrounds in both themes — legibility over a picture cannot depend on a preference. The rail reuses the viewer's controls container; row renderers reuse
`DetentSlider`, `SegmentedControl`, `IosSwitch`, `DropdownItem` — the existing components, wired once
in the shell instead of once per tool. The selected tool and the `Done` pill use
`Theme.colors.accentEditor` (#BF5AF2).

### 4.3 Canvas interaction

The canvas rests at `Fit` and the viewer's size control is gone in edit mode — the top bar's zoom
pill drives the canvas instead (`PublishCanvasZoom`). **The zoom itself shipped with ME-109**,
revising the "`Fit` only in v1" decision twice (§9, 2026-08-18 and 2026-08-21). What is still
outstanding is the *fidelity*: above the proxy's own resolution the canvas draws the ≤2560 px copy
and the picture is soft, which is ME-116 and all that is left of it.

- **Primary drag** goes to the active `CanvasTool`'s `Overlay` first. If it does not consume, the
  canvas pans.
- **Ctrl+wheel** zooms about the cursor — as in the viewer, and at the viewer's own notch — from the
  floor `zoomBounds` gives to 1000 % of native; a bare wheel does nothing, because a scroll over a
  picture must not mean two different things one panel apart. Pan is clamped by
  `EditorCanvasGeometry`, and both reset to Fit on tool change.
- Hovering a markup shape shows its handles; `Delete`/`Backspace` removes the selected one.

### 4.4 Keyboard

Registered through the viewer's existing shortcut plumbing and taken over while the editor is open, so
no viewer or app shortcut fires underneath.

| Key | Action |
|---|---|
| `Esc` | Cancel (confirm if dirty) |
| `Ctrl+S` / `Enter` | Done — write the recipe into the file |
| `Ctrl+Z` / `Ctrl+Shift+Z` / `Ctrl+Y` | Undo / Redo |
| `Delete` | Delete selected markup shape |
| `Tab` / `Shift+Tab` | Next / previous tool |
| `?` | Shortcuts popover — built from the manifests |
| tool-declared | `manifest.shortcut`; a disabled tool's key disappears with it |

### 4.5 Save UX

`Done` closes the editor immediately and runs the save behind it, in a scope that outlives the
composition (ME-130). It is reported by a pill at the bottom of the window — `StatusPill`, the shape
lifted out of `FrameExportBanner` so an export and a save can be up at once without covering each
other, stacked by `app/shell/StatusBanners`.

Four things it can say, and the fourth is why the wording is not one template:

- **"Saving photo.jpg…"** while the write runs, named because the editor is already gone by then;
- **"Saved photo.jpg"** after a `Done`;
- **"Saved photo.jpg before returning to the video"** when the save was made on the way out of an
  editor the video window asked to leave (ME-124) — read in the film's folder, where a bare "Saved"
  has no visible cause;
- **the failure's own sentence, verbatim** — "The original is untouched.", "Nothing was written." —
  because `ImageEditWrites` and `ImageSaveJob` already write for the user, and a second vocabulary
  here would be two names for one failure.

Nothing at all is shown for `NothingToDo`: an empty document over a file that carries nothing is not
an event.

**Nothing is chosen.** No target, no name, no confirmation — the recipe goes into the file that was
opened (§5). The folder listing needs no refresh for a new item, because there is no new item; what
changes is how the existing one draws, which is a cache eviction rather than a re-listing.

A **bucket B** file (§5.1) carries one extra thing: a notice inside the editor saying this format keeps
no history and `Done` will re-encode it. A strip in the shell, following `EditorSkippedNotice` — never
a modal, because a warning that costs a click has been turned into a step.

---

## 5. Saving — the recipe lives inside the file

**Rewritten 2026-08-19**, reversing the model below it. The first hands-on use asked for one file
carrying its own history — edit repeatedly without breeding copies, return to the original whenever —
and every `Done` was minting `photo-edited.jpg`, then `-2`, then `-3`. The working through is in
[`archive/review-2026-08-18.md`](archive/review-2026-08-18.md), moved there 2026-08-30 once every item
in it had shipped or been answered here.

**The `EditDocument` is written into the file's own private metadata slot. Pixels on disk are never
rewritten.** Every reader in the app decodes the original and folds the recipe over it. Revert is
deleting the segment. There is no index, no sidecar, no store in `Documents` and no duplicate of
anything, because the original pixels never left the file they were always in.

`Done` is the **only** save. There is no copy, no overwrite, no export and no name to choose.

### 5.1 Three buckets, decided by the container

| | Condition | Behaviour |
|---|---|---|
| **A** | a carrier exists for the format | `Done` writes the recipe. Pixels untouched, history kept, revert forever. |
| **B** | no carrier, but Skia can encode it | a notice in the editor says there will be no history; `Done` re-encodes the file in place. |
| **C** | neither | `Edit` is unavailable, with the reason in the menu. |

At the time of writing: **A** = `jpg`, `jpeg`, `png`, `heic`, `heif`, `avif`, `webp`, `tif`, `tiff`,
`gif` · **B** = *nothing* · **C** = `bmp`, alone and permanently.

C is not a permanent state. Almost every image container has a **sanctioned** private extension point,
and a new carrier moves formats straight from C to A, never through B:

| Format | Slot | |
|---|---|---|
| `jpg`/`jpeg` | private `APPn` segment | done first |
| `png` | ancillary private chunk | done first |
| `heic`/`heif`/`avif` | ISOBMFF `uuid` box — the slot the spec provides for exactly this | done (ME-108) |
| `webp` | RIFF chunk, needs promotion to the extended `VP8X` container | done (ME-137) |
| `gif` | Application Extension block | done (ME-138) |
| `tif`/`tiff` | private IFD tag (numbers ≥ 32768 are reserved for it) | done (ME-139) |
| `bmp` | none | never |

**A carrier needs no encoder, which is what makes this reach formats a save never could.** `libx265`
cannot open a HEIF output at all and Skia writes three formats in total (ME-010) — so today the editor
opens a HEIC and then refuses at save time. Under a recipe there is nothing to encode: HEIC, AVIF and
TIFF become editable for the first time, and the refusal moves from *after* the work (a failed save) to
*before* it (a greyed menu item).

### 5.2 Rules

- **Only the serialised `EditDocument` is written** — no paths, no timestamps, no identifiers. "What
  does Woka put in my files" has a one-line answer, and that is deliberate.
- **Windows-safe write**: build the new bytes → temp file beside the target → atomic move with replace,
  retried a few times, because the viewer underneath may still hold the file. A half-written recipe is
  a corrupted photograph, so this is not optional for the small write either.
- **Never throws.** A malformed container, a truncated file, a segment that is not what it claims —
  every one reads as "no recipe". Losing an edit is smaller than refusing to open a picture, and
  `ExifSplice` already holds this line for the same reason.
- **Stale-source guard**: if the file changed on disk since the editor opened it, the save stops and
  says so rather than writing over someone else's change.
- **Saving an emptied document removes the recipe** rather than reporting nothing to do — that is the
  same operation as Revert, reached from the other direction.
- **After save**: drop the native and preview entries from `ImageBitmapCache` and evict Coil's memory
  and disk entry for the path. Not conditional any more — every save changes what the file draws as.
  The `.thumbnails` poster is keyed on path, size and mtime, all three of which a write changes, so it
  re-derives itself. Then **raise the signal** (ME-135): a cache is consulted only when something asks
  it, and until a reader is told to ask, an evicted entry and a fresh one look identical on screen.

### 5.3 The two costs, accepted knowingly

- **An edit is invisible outside Woka.** The file opens everywhere and shows the unedited photograph.
- **Another program re-saving the file may strip unknown metadata**, silently returning the picture to
  the original. The pixels are never at risk; only the edit is.

### 5.4 What was rejected, and why

Recorded so it is not re-proposed:

- **Embedding the whole original.** A JPEG `APP` segment caps at ~64 KB, so a 10 MB original needs
  ~160 chained segments with framing of our own — a container inside a container — and doubles every
  file.
- **Wrapping the picture in an archive.** Whatever the name, the file stops being a picture to every
  program that is not Woka, and every grid tile pays a full unpack where a segment read costs ~4 KB.
- **Converting the library to JPEG XL.** It does carry everything — HDR, boxes, lossless JPEG
  transcode — and nothing in the dependency set writes it, Windows cannot open it, and "convert back
  when you need to" is the export step this model exists to remove.
- **A hidden per-folder index or a central store keyed on path.** Universal and safe, and it loses
  portability: the edit stops travelling with the file. Unnecessary once the containers carry it.
- **NTFS alternate data streams.** Invisible, universal, zero surgery — and silently destroyed by any
  copy to exFAT, a network share or a stripping antivirus. A promise that stops working without saying
  so is worse than none.
- **A hidden `.originals` directory** — the model this replaces already rejected it: an index to keep
  consistent, orphans to sweep, and a promise that breaks the moment the folder moves.

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

**Narrowed 2026-08-19.** This section existed because Skia encodes three formats and the other eight
had to become one of them before they could be edited at all. A carrier needs no encoder (§5.1), so
that reason is gone: the road to editing a HEIC is its `uuid` box, not a converted copy of it beside
the original. Conversion mints a file, and the model no longer mints files.

What survived was a **multi-frame** question, which is a different one — and it was **answered
2026-08-30 by ME-126** rather than by a conversion: editing frame N of a burst says what happens to
the other frames through a payload keyed by frame, in the same slot, with no file minted.

Should a format ever want a target anyway, the old terms hold: a sheet states the name, that the source
stays exactly where it is, and which single frame survives; photographic sources default to JPEG
**q100** (§9) — the ceiling, with no setting anywhere that changes it — graphics and anything with
alpha to PNG; the source is **kept**, because a HEIC the user shot on a phone is not Woka's to delete.

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
| 2026-08-16 | ~~Backups? **None.** Save a Copy is the default~~ | **Superseded 2026-08-19** — see the four rows dated that day | |
| 2026-08-16 | ~~Copy name? `photo-edited.jpg`, collisions `-2`, `-3`~~ | **Superseded 2026-08-19** — nothing writes a copy | |
| 2026-08-16 | ~~Overwrite consent? Ask every time + session-scoped~~ | **Superseded 2026-08-19** — there is no overwrite | |
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
| 2026-08-18 | Which way does `concat` compose? | **`into` first, then `next`** — the implementation was the reverse of its own doc | Compose's `timesAssign` is `this = other * this`, so the receiver acts first. The fold had composed geometry stages backwards since ME-007 and nothing caught it: every geometry stage ever written was a *translation*, and translations commute. ME-105's rotation was the first non-commuting pair (ME-105) |
| 2026-08-18 | Does `nativeSize` describe the picture as stored or as decoded? | **As decoded** — it swaps the axes for a quarter-turn EXIF origin | `Codec.width/height` gives the stored raster while `Image.makeFromEncoded` applies `Orientation`, so the two disagreed for every portrait photograph. The editor stretched them on save; the viewer's zoom percentages were wrong about them too. Found by `ExifSpliceTest`, fixed in `SkiaFrameDecoder` (ME-104) |
| 2026-08-18 | Control palette in the inspector | Sliders **white** (as the zoom slider); switches **green**, as everywhere else in Settings; buttons, checkboxes and the markup shape colour on the editor accent | A switch that means "on" must not change colour by screen. Components are Woka's own and carry no vendor name in their type names |
| ~~2026-08-18~~ | More than one editor open at once? | ~~**Yes** — the dock lists open unsaved sessions and returning to one keeps its document and history (ME-128)~~ — **superseded 2026-09-01 by the row below** | An edit in progress is not lost by opening something else. Consequence: a composable can no longer own the session, so ME-106 must resolve from a store rather than construct per open |
| 2026-08-18 | Where does an edit live? | **A recipe in the file's own metadata. The pixels on disk are never rewritten.** | Embedding the whole original needs ~160 chained `APP` segments on a 10 MB JPEG — a container inside a container — and unknown metadata is silently stripped by uploads, messengers and sync, so revert would break with no message. `EditDocument` is a few hundred bytes and fits in one segment: no store, no index, no orphans, no generational loss, and no new file per save. Cost: outside Woka the photograph looks unedited |
| 2026-08-19 | Which containers can carry it? | **Nine of eleven, in four writer families.** `jpg`/`png` first; `heic`/`heif`/`avif` (ISOBMFF `uuid` box), `webp` (RIFF, needs `VP8X`), `gif` (Application Extension), `tif` (private IFD tag) follow one ticket each. Only `bmp` never | The first reading — "JPEG and PNG can, the rest cannot" — was wrong. Almost every image container has a **sanctioned** private extension slot; the difference between them is implementation cost, not possibility. So this is a staged rollout, not an architectural limit |
| 2026-08-19 | A format with no carrier **yet**? | **Three buckets** (§5.1). Carrier → record it. No carrier but Skia encodes it → a notice, then re-encode in place. Neither → `Edit` greyed, with the reason | A new carrier moves a format from C straight to A, so B is a transitional state and today holds one format. It also moves the refusal from *after* the work — today a HEIC edits happily and then fails to save — to *before* it |
| 2026-08-19 | Does `Export` / "save a flattened copy" survive? | **No. `Done` is the only save.** `SaveTarget`, `OverwriteConsent` and `SaveFileNaming` are deleted | The flow is edit → `Done`, with nothing to choose. The cost is real and accepted: the edit cannot leave Woka, and a file sent to someone arrives unedited. A menu entry was offered and declined in favour of the simpler editor |
| 2026-08-19 | Wrap the picture in an archive / convert to JPEG XL? | **No** to both | An archive stops the file being a picture to everything that is not Woka and costs a full unpack per grid tile against a ~4 KB header read. JXL genuinely carries everything and nothing in the build writes it, Windows cannot open it, and "convert back when needed" is the export step this removes |
| 2026-08-19 | Can a recipe be edited again? | **Yes** — the editor loads what the file carries, and `isDirty` means "differs from the file", not "the document is non-empty" | Otherwise reopening an edited photograph is dirty before it is touched, and a second edit either starts blank or re-encodes an encode. Every render starts from the original pixels, so there is no generational loss however many times it is edited |
| 2026-08-19 | Where does Revert sit, and what colour? | **Leading group beside `Cancel`, red** (ME-107) — overriding "beside Undo/Redo" | Asked for directly. It is the one control in the editor that throws work away, and grouping it with two that give work back read it as a third step of the same kind. The confirmation stops being a courtesy at that position and becomes the thing that makes it safe: beside `Cancel` is where the hand goes to leave |
| 2026-08-19 | Does evicting a cache redraw anything? | **No — eviction is not invalidation** (ME-135) | A cache is consulted only when something asks it, and the viewer's decode is a `LaunchedEffect` keyed on the item and the frame, neither of which changes while the editor is mounted over it. Found by hand: `Done` from the viewer changed nothing until the viewer was left and re-entered. The fix is a signal in `core` that every save raises, not a fifth thing for the save to clear |
| 2026-08-19 | Where does the reader seam live? | **`commonMain`, not `jvmMain`** — `core/ui/image/StillEdits.kt`, an interface plus a process-wide holder defaulting to the identity (ME-133) | ME-133's own file list said `jvmMain` and its step 5 said `StillCoverImage` asks it — and that composable is `commonMain`, which cannot see a `jvmMain` type. One JVM target means `ImageBitmap` and `File` are both nameable in `commonMain` (`core/utils/StillCover.kt` already imports `java.io.File`), so the interface moves and the implementation stays in the editor |
| 2026-08-19 | Does the grid wait for the recipe check before drawing? | **No — Coil draws while the answer is unknown** | The check is a few kilobytes off the file's head and costs nothing at all for a format with no carrier, which answers from its extension. Holding every tile blank until it returns would make an unedited folder pay for an edited one; the price is that an edited tile can show its original for a frame or two |
| ~~2026-08-19~~ | What size does a reader fold against? | ~~**The bitmap it was handed**~~ — **superseded 2026-08-21 by ME-109**, which folds against native and scales the recipe | `StillPreview` may be given a picture its decoder already scaled down, and folding against native would upscale that to full resolution and hand back a blurred preview. Rotate and flip are scale-free so this is exact today; the first tool that addresses pixels is what has to scale the recipe, and that is its ticket |
| 2026-08-19 | What does a save have to evict from Coil? | **Its memory entries for the path, matched by substring. Its disk cache is never touched** (ME-132) | Measured against coil 3.5.0: `addLastModifiedToFileCacheKey` defaults to **off**, so the memory key is the bare file URI and a write does not change it — the tile would keep the pre-edit picture. The disk cache is written by network fetchers only, `coil-network-*` is not on the classpath, and a local file is read straight off disk by `FileUriFetcher`: there is no entry to remove and no key that would name one. One file also yields one entry per requested size, so a key cannot be rebuilt — hence substring |
| 2026-08-19 | Does the stale-source stamp survive a save? | **No — it is re-read after every successful write** (ME-132) | The save now lands on the file the editor is still holding open, so the stamp taken at open no longer describes it. Without the re-read the second `Done` of a sitting reports the user's own first one as somebody else's change |
| 2026-08-19 | How does a Coil tile re-request a file that has not moved? | **`key(reloadKey) { AsyncImage(…) }`** — a new composition identity, not a rebuilt `ImageRequest` (ME-135) | The memory key is the bare file URI and the `File` model compares equal across a write, so Coil keeps the painter it already resolved and never asks again — evicting its entry alone changes nothing. A new key is a new `AsyncImagePainter`, which then misses the evicted entry and reads the file. The recipe case does not even reach here: `carries` goes true and the tile moves to Woka's road. What is left is the bucket-B re-encode and a revert coming back |
| 2026-08-19 | Does undo step back **past** what the file carries? | ~~No — the history is seeded with it and the first undo lands *on* it~~ **Superseded the same day by the row below** | The reasoning was that stepping past the file's own state is Revert's job (ME-107), not undo's. The by-hand run disagreed, and it was the user's call to make |
| 2026-08-20 | How does a TIFF gain a tag without moving anything? | **The payload and a rebuilt IFD0 are appended, and the header's pointer is repointed at the copy** (ME-139) | An IFD entry is 12 bytes and every value in a TIFF is addressed by an absolute file offset, so adding one in place moves the picture out from under every offset that names it. Appending moves nothing. The block carries **the offset it replaced**, so a clear puts the pointer back and cuts the file — byte-identical, which is what ME-107 promises and what the fixture test measures. The rebuilt IFD keeps the link to the next page, or a three-page scan would show one |
| 2026-08-22 | Is markup `Repeating`, one operation per shape? | **No — `Single`, one operation carrying the ordered shape list** (ME-113), reversing the backlog, `ToolCardinality` and `EditorToolScopeImpl`, all three written before anything had to move a shape | Under `Repeating` a drawn arrow is *committed*, and `EditorSession` has no way to change a committed operation — only `preview`, `commit`, `undo`, `redo`, `reset`. Worse, `preview` would put the moved copy in `pending` while the original sat in `ops`, and the fold applies both: the arrow would be drawn **twice** for the length of every drag. With one operation holding the list, adding, moving, resizing and deleting are all "preview a different list, commit on release" — the mechanism Crop already uses, with no session API added and undo still one entry per gesture. Three arrows are still three arrows; they are three entries and one `RasterStage.Paint` |
| 2026-08-24 | Does an inspector edit reach the shape that is **selected**? | **Yes, for `Color` and `Width` — `CanvasTool.restyled`, and the selection is hoisted out of the overlay's scope into `EditorScreen`** (ME-114, found by hand) | The pen is per-shape by design: each shape copies the style it was drawn with, so restyling the pen changed nothing already on the canvas — correct with nothing selected and plainly wrong with an arrow selected. The selection could not be read by the inspector at all, because `rememberToolScope` built it *inside* the overlay. One `ToolSelection` owned by the screen, passed to both, and a defaulted hook the writer applies after `resolve`. `Kind` is excluded: a rectangle cannot become an arrow |
| 2026-08-24 | Where does a colour picker keep its hue? | **In the picker, not in the colour** (ME-114, found by hand) | Deriving hue/saturation/brightness from the stored ARGB on every frame looked clean and was broken: black, white and every grey have all three channels equal, so hue has nothing to return. Dragging into the pad's dark or pale corner therefore discarded the hue, `hsvOf` reported 0°, and the panel snapped to red. The three numbers are the panel's state, seeded from the swatch when it opens. `MarkupRestyleTest` asserts the loss so the reason cannot be forgotten. The panel also gained the readout it was missing — a swatch and the hex — because a marker on a gradient states no result |
| 2026-08-24 | How does `Delete` reach the open canvas tool? | **One `@Stable` slot, `CanvasKeys`, built in `EditorHost` and written by `ActiveToolOverlay`** (ME-114) | `GlobalShortcutState.onPreviewKey` is a single mutable handler, and the active tool lives two composables below it — so the overlay cannot register its own, and a second claimant is how the viewer's arrow keys died. A slot rather than a lookup because "which tool is open" is `EditorScreen`'s state and the key handler must not learn it. `CanvasTool.onDelete` is defaulted to false so Crop says nothing, and the key is consumed either way |
| 2026-08-24 | Does the markup overlay draw its own shapes? | **No — only the selection. The fold draws the shapes, including the one in flight** (ME-114, departing from ME-114 step 2 as written) | `previewsThroughOverlay` stays false for markup, and the fold folds `pending` in exactly like a committed operation. Drawing the list in the overlay as well would put a second copy of every stroke on top of the first and double every translucent overlap. What the overlay draws is what the fold cannot know: handles and the dashed box, which live in the tool's scope and never reach the document |
| 2026-08-24 | Can a colour be picked outside the seven swatches? | **Yes — a rainbow circle opens a saturation/value pad over a hue strip** (ME-114, asked for by hand) | The swatches cover what a mark normally wants; "not quite that red" is seen rather than known, so it is a pad and not a hex field. Picking writes unsettled on every pointer event and settles once on dismiss, so sliding through fifty colours is one undo step — `ParamWriter`'s existing slider rule, reused. A value matching no swatch rings the rainbow circle, so a hand-picked colour never leaves the row looking unselected |
| 2026-08-30 | Editor open, and the video window asks to go back? | **The return happens — the editor is saved and closed on the way, and the pill says so** (ME-124) | Focus decides who *hears* a key, not what it acts on: PiP is a separate `Window` whose keys never touch `GlobalShortcutState`, while `Escape` there acts on the main one by design, and `replaceTop` with a fresh nonce rebuilt `FolderScreen` with `editorTarget` inside it. Three readings were rejected. **Refusing to open the editor** answers a data-loss bug by deleting a feature, and needs guards on four doors plus a fact `isEditable` — a question about a *file*, in `commonMain` — may not name. **Asking first** was designed and thrown away: a Compose `Dialog` is modal to its own window only, so the question cannot freeze the other one; the user can keep editing, press `Done`, and answer "save" ten minutes later, at which point the captured action writes a **stale document over a newer one**. **Saving without asking** is safe precisely here — the edit is a recipe, Revert removes it, and ME-134 replays it into a history Undo walks back — and it is what `Done` does anyway, there being no target to choose (§5). A clean session still writes nothing: its document is not empty, it holds last time's recipe |
| 2026-08-30 | Which scope does a save that outlives its editor run in? | **An object's — `EditorSaveStatus.scope`, `Main + SupervisorJob`** (ME-130) | `Done` closes first and writes second, so the composition's own `rememberCoroutineScope()` would cancel the write it had just started: nothing is half-written, because the atomic move is the last step (§5.2), but a `.tmp` is left beside the photograph and the edit is quietly lost. `Main` matches `EditorSession` — `EditorEngine.save` moves itself to IO, so what stays on the main thread is the status write and the `onSaved` callback, both of which touch Compose state. **This is not ME-125**: what an in-flight save does when the *application* closes is still open, and a `SupervisorJob` in an object does not answer it. Written down so that ticket starts from a stated scope rather than from whatever a composable happened to have |
| 2026-08-30 | Two things with something to say, one corner of the window | **The pill is a component, each producer keeps its own status and settle timer, and `app/shell/StatusBanners` stacks them** (ME-130) | `FrameExportBanner` aligned *itself* to the bottom centre, so a second copy was one pill drawn under the other — and an export from the grid and a `Done` on a photograph really can be live together. `core/` may not import a feature, which is what puts the editor's banner in `feature/editor/` and the host that names both in `app/` — the layout's rule, not a preference. `Motion.BANNER_MS` died here: ME-012 declared it for this ticket, nothing ever read it, and the duration now lives beside the animation it times |
| 2026-08-30 | Configurable output quality? | **No. q100, fixed** — every `SaveFormat` entry, and no `WokaSettings` field to argue with (ME-127) | Quality is not a preference, it is a promise: the file is a photograph already compressed once, and a re-encode spends a second generation of it that no setting can give back. A row in Settings would put that cost behind a number most people never touch, against a §5 that is already dense — and the day somebody left it at 90 would be invisible until the picture was looked at. The ceiling is the one value that never has to be defended, so the assertion is a loop over `SaveFormat.entries` rather than three constants. **Reaches almost nothing today**, and that is the point: bucket B is empty, `ImageSaveJob.encode` runs for no shipped format, and this is the policy waiting in the constant that would decide it. WebP's 100 is lossy-at-maximum — Skia's `encodeToData` has no lossless road — so the doc says ceiling, not round trip |
| 2026-08-31 | An in-flight save when the **application** closes? | **The exit is deferred, never blocked, and taken anyway after 5 s** — one `inFlight` count, both doors writing one flag, `app/shell/AppExit.kt` gating them (ME-125) | This is the half ME-130 left open: its scope outlives every composable and no part of the process, and `Done` closes the editor *first*, so the window is back on the grid while the write is running — the moment a hand reaches for the close button. Blocking would be worse than the loss, because `writeAtomically` sleeps on IO by design and holding the EDT through it is a frozen window with an unpainted pill on it; deferring keeps the composition running and the banner drawing. A **count** rather than a flag, for `EditorPresence`'s reason: ME-128 makes two saves reachable and a boolean clears on the first to finish. **Bounded**, because a save gets no veto over quitting — the original photograph is intact whatever happens, and an application that will not close is the worse defect. `.tmp` sweeping stays out: a leftover temp is a file no reader lists (`LocalMediaRepository` filters by extension), and a sweeper for a case this makes rare is work with no measurement behind it. **The cap is not a `withTimeoutOrNull`**, departing from the ticket as written: measured 2026-08-31, a Compose test's `mainClock.advanceTimeBy` resumes a `delay` inside a `LaunchedEffect` and does not expire a `withTimeoutOrNull` in the same effect — the timeout schedules through `Delay.invokeOnTimeout`, which falls back to real time — so the spelling the ticket named left the five-second path testable only by waiting five real seconds. It is a `merge` of the count and a one-tick `delay` flow, which says the same thing and is driven by the clock |
| 2026-09-01 | More than one editor open at once? | **No — ME-128 is dropped**, and one editor is the shape (`archive/ME-128-…`) | Its justification was written the day before §5 was rewritten, and did not survive it. "An edit in progress is lost by opening something else" is no longer true of any door: `Cancel`/`Esc` and a frame change confirm (ME-158), the video return saves (ME-124), the close button asks (ME-160), and the carousel and dock are hidden while editing, so there is no fifth way out. What was left was convenience, priced at moving session ownership out of the composition — the ticket's own step 4 calls it "the step that can leak a scope if it is done by halves" — ~20 MB of resident proxy per open editor, and reopening two decisions made that same week, since `EditorPresence.saveAndClose` and the exit dialog both speak about *the* editor. Two defects settled it: the ticket contradicts itself on whether `Cancel` ends a session, and with the dock hidden while editing **no door suspends an editor at all**, so the store could never hold a second entry. Making it work needs a "minimise" control beside `Cancel`, against §4.2's "the editor is a mode, not a panel". **`Done` is not a free bookmark either**, and that is the one thing that would have argued for it: `EditorSession` opens its history at the recipe the file carries, so `Done` → reopen makes the *previous* sitting's recipe unreachable by Undo and removable only by a Revert that takes the original with it. The cheap answer, if the itch returns, is one suspended slot keyed by `EditorTarget` — not a store, a list and a popup |
| 2026-08-31 | The close button over an editor with unsaved work? | **It asks — `Save` / `Don't save` / `Cancel`** — and the editor answers, never the door (ME-160) | Found by hand the same day ME-125 shipped: draw a rectangle, press the close button, Woka quits with no question and the edit is gone. ME-125 gates a save that is *already running* and none had started, so the sequence exposed a door with no opinion — and the most destructive one, since the others lose an edit and this one loses the process with it. **Three buttons, departing from `DiscardChangesDialog`'s pair**: `Discard` / `Keep editing` is right while the editor stays up, because keeping means *carrying on editing*; on quit there is a third answer, and folding it into "keep editing" makes the user cancel, press `Done`, watch for a pill and press close again. Asked by hand, over the silent save ME-124's row would otherwise have licensed. **The editor answers** because dirtiness is not readable from `app/shell` — a file opened and never touched still holds the recipe it carried (ME-134), which is why `EditorPresence` publishes a lambda and not an `isDirty`. So `AppExitRequest` carries the question and `EditorExitEffect` the answer, and `Save` starts the write *before* answering, which is what puts `inFlight` at 1 before the gate resumes. The five-second cap bounds the write and never the question: a dialog nobody answers does not quit the application |
| 2026-08-29 | Bold / italic / underline / strikethrough — four fields or one set? | **One `Set<TextFace>` on the shape** (ME-151) | The row that writes them is a `ToggleSetParam` whose *value* is a set, so four booleans would be converted to and from one at every boundary — the shape, the pen, the restyle and the factory — and each conversion is a place two spellings can disagree. One field is also one default, one wire key and one `copy`. It is what let `TextPen` stay deferred: the factory takes eight parameters rather than eleven |
| 2026-08-29 | May a face apply to part of a caption? | **No — to the whole caption; a second style is a second markup** (ME-151, asked by hand) | Per-range styling is a list of `(range, faces)` spans on the wire that every edit must remap, which is a text engine rather than a markup tool. There is also nowhere to read the range from: `restyleSelected` finds a shape by id, and the selection inside a line lives in `BasicTextField`'s own state, which ME-145 deliberately does not own — reclaiming it is exactly what broke the style rows the first time. iOS Photos styles the whole block too |
| 2026-08-29 | Why did three of four new glyphs render as dots? | **An SVG carrying `width`/`height` reports those, an SVG carrying only a `viewBox` reports `100×100`** (ME-151, measured) | `ParamChipRow` scales a row by the widest intrinsic size in it. All ninety existing icons omit the attributes and measure 100×100; three of the four new exports carried them and measured ~15–26, so the row's unit came from the one that did not and the other three were divided by 150. Fixed by stripping the attributes, which is what makes an export match the family. **A consequence worth knowing: since every icon measures 100×100, the per-glyph scaling in `ParamChipRow` never fires — the row looks even because Skia's `xMidYMid meet` preserves each viewBox's aspect** |
| 2026-08-27 | What does dragging a caption's frame do? | **iOS Photos: a corner scales the type**, and the frame's width only decides where words wrap (ME-146) | The alternative — the frame sets the width and the size is untouched — makes the corner a rectangle edit and the size row the only way to resize type. Under this reading a corner drag is a *ratio* applied to the font size and the wrap width together, which is the size row reached with a pointer, and the four corners are the only handles |
| 2026-08-27 | Where does a caption live while it is being typed? | **In the document, previewed from the moment the caret is placed** | It shipped the other way — the string in the field until it was finished — and the style rows could not reach it: `restyleSelected` looks a shape up by id, and clicking a row is what *blurs* the field, so the write landed before the commit with nothing selected. The row silently moved the pen instead. A preview is not an undo step, so a hundred keystrokes still cost one entry, and the canvas draws the caption through the fold at the size it will be saved with |
| 2026-08-27 | May a tool hide one of its own rows? | **Yes — `EditorTool.rows(params)`**, defaulted to all of them | A stroke width does nothing to a caption and a font size nothing to an arrow, and a row that changes nothing when dragged reads as broken. The schema is still the UI; which parameters *currently apply* is the one question only the tool can answer. Markup keys it on the pen rather than on the selection, so a row cannot appear and vanish under a click on the picture |
| 2026-08-27 | Why did a row labelled `Text size` write `width`? | **Rows had no identity.** `key(spec.key)` per row, and `gestureKey = spec.key` on the slider | Swapping one spec for another in the same position let Compose reuse the node: label and value followed the new spec while `DetentSlider`'s `pointerInput` — keyed only on the track's width — kept the coroutine closed over the old one. Found by a log from the running app; **three UI tests failed to reproduce it**, which is the note worth keeping |
| 2026-08-27 | What is the caret measured from? | The caption **as the document has it now** (`liveEdit`) | `MarkupTextEdit.shape` is frozen on purpose — the commit compares against it to tell an emptied caption from an abandoned caret — so a field drawing from it kept the size it opened at while the text under it changed |
| 2026-08-27 | Does `Enter` finish a caption? | **No — it breaks a line. Only a click elsewhere commits** (ME-146, asked for by hand) | It committed under ME-145, which was right while a caption was one unwrapped line with nowhere to break to. A box and a wrap width make the key that ends a paragraph have to end a paragraph. `Esc` is still the abandon, and it is still the only key the field consumes — the commit is the blur, plus `MarkupPointer` ending any open edit before it decides what else a press means, because the canvas beneath is not focusable |
| 2026-08-27 | Why was the plate not centred on its type? | **`TextLayoutResult.size.width` reports the constraint, not the text** — the box is the longest `getLineRight` (ME-146, measured by hand) | A block wrapped at 300 px whose longest line measured 266 px still reported 300, so the plate ran 34 px past the glyphs and every one of them landed on the right. The *vertical* offset is not a defect and was left alone: the probe read ascent 103 against descent 24 at a 95 px size, so a caption sits low in its box because the font's metrics say so |
| 2026-08-27 | Is the plate's corner radius a control? | **Yes — `Corner`, 0–100 % of the type's size** (ME-146, asked for by hand) | A per cent rather than a count of pixels, so ME-148's corner-drag ratio scales it for free and one field fewer takes part in the drag; it is also the number that stays meaningful from a 12 px caption to a 512 px one. Clamped to half the shorter side, which is what makes 100 % a capsule rather than a radius larger than the box it rounds. Shown only once a fill is chosen — the "a row that changes nothing reads as broken" rule, one level below the pen |
| 2026-08-27 | Where does a caption's wrap width come from? | **The picture, at the moment the caret is placed** — `(sourceWidth - at.x)` floored at a quarter of the width, stored on the shape and never set by the user (ME-146) | Wrapping needs a number and the user must not have to supply one; taken from where the caption *starts* it wraps at the picture's right edge rather than a quarter-picture past it. Moving a caption does not re-wrap it — the number is the box's width, which ME-148 makes visible and draggable. `0f` means unconstrained, which is exactly what every document written by ME-122 and ME-145 already means, so the field needs no migration |
| 2026-08-27 | How are bold / italic / underline / strikethrough drawn? | **A multi-select chip row — a ninth `ParamSpec`, `ToggleSetParam`** (ME-147) | They are four *independent* booleans, so `ChoiceParam` cannot express them: it picks one of a set. Four `IosSwitch` rows can, and would put a wall of four full-width rows under `Shape`, `Color`, `Fill` and `Text size`. The chip row is what Photos draws and what ME-144 argues for; it costs a spec, a renderer, the chip extracted out of `ParamRowChoice` so the two renderers cannot drift, the fixture's ninth spec, and four SVGs — which is why it is its own ticket rather than a quarter of ME-146 |
| 2026-08-27 | Does a caption's background need a nullable colour spec? | **No — `ColorParam` with a fully transparent first swatch** (ME-146) | A caption without a plate is the common case, so "none" must be one press away; `0L` is already a legal `ColorValue` and already the field's default, and `ColorSwatch` draws a zero-alpha value as a diagonal slash. The alternative — a `BoolParam` beside a colour row that silently does nothing when it is off — is the defect ME-122 fixed once already for the size rows |
| 2026-08-26 | What do Styles' two pad axes mean? | **Tone = exposure, Colour = warmth** (ME-141) | Both matrices already exist in `AdjustMatrix`, so the ninth `ParamSpec` costs a renderer and no engine change — the pad trims a chosen style rather than becoming a second Adjust |
| 2026-08-26 | Undertone and look at once, or one or the other? | **One of each**, two parameters whose matrices multiply | The fold already multiplies an adjustment by a filter; refusing the combination would be a rule the engine does not need. The cost is two strips rather than one, which is why `PreviewChoiceParam` gains a section label |
| 2026-08-26 | Are Palette and Intensity the same number? | **No — two.** Palette is the undertone's strength, Intensity the look's | Follows from the row above: two independent families need two strengths, or one slider means different things depending on which strip was touched last |
| 2026-08-26 | How is markup text sized? | **Its own `Text size` slider** (12–512 source px); the shape stores it in `width` | A stroke is 1–64 px and type on a 6000 px photograph starts around 64, so one range cannot serve both. One field rather than two keeps `styled()` and every `when` in `MarkupGeometry` honest; two controls route by the selected shape's class |
| 2026-08-26 | Is ME-122 one ticket? | **No — ME-122 is the shape, ME-145 is typing into it** | Fourteen files together. The split is ME-113/ME-114's, at the same seam: the model and the raster first, the gesture and the keyboard after |
| 2026-08-26 | What is left of ME-116? | **Only the fidelity.** The zoom itself shipped with ME-109 | Checked against the tree: Ctrl+wheel, clamped pan, reset on tool change and the top-bar pill are all in `EditorCanvasGeometry`/`Host`/`Gestures`. §4.3 still described the deferral and was corrected |
| 2026-08-22 | How does the inspector show a picture, when the shell may not name one? | **A second `@Composable` on the engine — `Thumbnail(document, modifier)`, defaulted to drawing nothing** (ME-112) | The preview strip has to render the photograph eight ways, and §3.3 forbids `ParamRows` from learning what a bitmap is. So it asks the same question it asks for the canvas, in miniature: here is a document, draw it. Each option is handed a document containing **only that option's operation**, or every thumbnail would look like the canvas behind it. The cost is a filter rebuild each and no pixel work — the strip draws the proxy `Surface` already holds, never a second decode, which is the property §3.2 was designed for and the one a counting fixture engine now guards, because it regresses invisibly |
| 2026-08-22 | What does the canvas draw while a slider is moving? | **Exactly what committing would produce — the fold asks `commitPending` rather than appending `pending` to `ops`** (ME-111) | `ops + pending` applied a `Single` tool's committed operation *and* its pending one at once: dragging Tint from −100 to +100 previewed as neutral and snapped to green on release. §3.1 promises a preview renders its own result, and one implementation is the only way to keep that true. The same drag also proved `ParamWriter` must read the document live: `DetentSlider` holds its gesture lambda across recompositions, so a captured document wiped whichever slider had been moved before |
| 2026-08-22 | How does a control that needs the picture's size work? | **Two defaulted hooks on `EditorTool`, both handed the frame — `resolve` before a value is stored, `footnote` for what the inspector prints** (ME-110) | `stage()` must never see a frame or the fold stops being a pure function of parameters — but "long edge 1280" and "4000 × 3000 → 2000 × 1500" are UI-time questions, asked where the size is known. The alternative was the shell branching on tool ids, which is exactly what the schema exists to prevent |
| 2026-08-22 | What shape does a tool take in the editor? | **The one Crop settled on** — icon and name in a single rail row, controls in the schema inspector above it, anything pointed at on the picture in a `CanvasTool` overlay (ME-109) | Settled by building it and using it. The rail carried icons alone until two tools shared a placeholder glyph and became two identical squares; submenus were never on the table. A tool contributes a manifest, a schema and optionally an overlay, and receives the layout — which is §3.4's claim made concrete rather than restated |
| 2026-08-21 | What does a reader fold a crop against? | **The file's native size, then `scaledBy` into the bitmap it holds** (ME-109) | Reversed the 2026-08-19 row above, and had to be: that decision was right only while every tool was scale-free. A crop rect is in source pixels, so folded against a 400 px grid cover it would cut a region twenty times too large. A reader handed a native bitmap scales by 1.0 and is bit-for-bit what it was |
| 2026-08-21 | How does a tool say "nothing chosen yet"? | **In its own value — `CropRect.Unset` — never by asking about the frame** (ME-109) | `stage()` receives parameters and never the frame (§3.4), the same limit that stops a rotation reporting its own output size. So an untouched crop is one whose rect has no area, and the overlay writes a real one only once a handle moves |
| 2026-08-21 | Does `manifest.order` decide application order? | **No — it orders the rail. The fold ranks by domain and keeps the user's order** (ME-109) | Written down because the crop ticket said "order = 1, after Transform" as though it did. Turning after a crop turns the cropped picture; cropping after a turn cuts the turned one. Both are asserted, in both directions |
| 2026-08-21 | What is behind the picture on the canvas? | **`Theme.colors.background`** — the viewer's backdrop, not black | §4.2 always said "full-bleed over the viewer's existing backdrop" and the screen was painting `Color.Black` instead. Nobody noticed while every test picture was opaque; a transparent PNG and an animated GIF both show what is behind them, and what was behind them was a black plate that the same file did not sit on one panel below |
| 2026-08-21 | How far out does the canvas zoom? | **To the viewer's floor, not to Fit** | Fit had been both the resting place and the floor, which made the editor the one panel where a picture could be enlarged and never shrunk. `zoomBounds` is reused for the number — it takes two sizes and answers a range of percentages, naming no `SizeMode` — so a tenth of Fit, or 1:1 for a picture the canvas hugely enlarges, exactly as one layer below. The wheel notch is the viewer's `ZOOM_STEP_RATIO` too. Fit stays where `Reset` returns to; the ceiling settled on 2026-08-18 is untouched |
| 2026-08-21 | Does the editing canvas animate? | **Yes, and a click pauses it — `EditorEngine.togglePlayback()`** (ME-140) | `Edit` was the one place in the app where a moving picture stopped dead: the canvas held `EditorProxy.load(file, frameIndex)` for the whole session. The toggle is on the engine and defaulted to nothing, so the shell asks the same question of a frame clock, of the video editor's player at 5b, and of a photograph — and still names no bitmap (§3.3). The frames are held for the session rather than decoded when due, because `EditorProxy` is deliberately outside `ImageBitmapCache` and a per-frame decode would re-read the file ten times a second; a 96 MB budget refuses an animation too large to hold, which then draws its still frame as before |
| 2026-08-21 | Where in a GIF does the recipe go? | **After the leading run of application extensions, not immediately after the Global Color Table** (ME-138) | The ticket said the table; the corpus said otherwise. `anim.gif` puts `NETSCAPE2.0` in the very first position and decoders exist that honour the loop count only when its block sits there, so inserting in front of it is a carrier having an opinion about how the file loops. Ours goes after that run, which is still the head window. The same choice makes the read cheap: the walk stops at the first non-application block and *that is the answer* — nothing of ours is ever written past it, so reaching the picture data decides "no recipe" rather than reporting a window that ran out, and the grid asks this per tile |
| 2026-08-21 | Does an edited GIF animate edited? | **Yes, and it cost this ticket nothing** (ME-138) | The clock advances `frameIndex` and the decode keyed on it goes through `ImageLoader`, which folds the recipe like every other reader — `RecipeStillEdits.apply` is handed a frame index and does not read it, so one document covers every frame. That was the bet behind deferring the carrier until something played the format, and it paid. The day HEIC wants a *different* edit per frame, the seam is that ignored parameter and a per-frame key in `EditDocument`; no carrier changes for it |
| ~~2026-08-20~~ | Does GIF get a carrier now? | ~~**No — deferred until an animation plays**~~ **Superseded 2026-08-21, one day later** | Measured before writing it: nothing in Woka animated a GIF. The viewer shows a frame set — `SkiaFrameDecoder` collapses animation to one frame by design — and the grid draws frame 0 through a Coil with no animated decoder on its classpath. An edit would apply to every frame the app draws, and the app draws one, so the result is invisible where the format is normally *watched*. Animated **WebP** keeps the carrier ME-137 gave it: a WebP is overwhelmingly a still photograph, and the carrier earns its place on that case |
| 2026-08-19 | What happens to a **simple** WebP that has to carry a recipe? | **It is promoted to the extended `VP8X` container on the way in** (ME-137) | The simple form allows nothing beside its one bitstream chunk, so there is no slot to write into — the choice is promote it or keep re-encoding the picture. Promotion copies the canvas out of the bitstream and touches no pixel, which the fixture tests assert. **Bucket B is now empty**: `Destructive`, the re-encode path and its notice are unreachable, kept as the fallback for a format that ever gains an encoder before a carrier. One cost, stated: a clear leaves the `VP8X` in place, so for WebP alone Revert restores the picture exactly rather than the file exactly |
| 2026-08-19 | What does an edit mean for a **frame set**? | **Every frame of it** — one recipe per file, and that is the MVP answer, not a placeholder (ME-126) | Confirmed by hand the day HEIC became editable: turning one frame of a burst turns the burst, and that reads as the file being edited rather than as a bug. Per-frame is a keyed payload in the same segment and is wanted, not needed. The **consequence that was a bug**: a save evicted only the edited frame's cache entries, so the other frames' thumbnails kept the pre-edit picture until something remounted the strip — `ImageBitmapCache.removeAllFor` now forgets the whole file, and the strip subscribes to the save signal like every other reader |
| 2026-08-30 | …and what does it mean now that per-frame exists? | **The payload is keyed by frame; the un-keyed form still means every frame; Revert stays whole-file** (ME-126) | `EditPayload` is `ops` plus an optional `frames: Map<Int, EditDocument>`, a **sibling field rather than a wrapper**, which is what makes compatibility free in both directions: `{"ops":[…]}` decodes with no keys and therefore means all-frames, and an older build decoding a keyed payload as an `EditDocument` drops the unknown key (`ignoreUnknownKeys`, ME-003) and draws the picture unedited rather than refusing the file. No carrier changed — six formats carry an opaque string and none of them was touched. `pending` is deliberately absent from the payload: an operation in flight is a drag, not a record. **Revert stays the whole file** — it is the one action that removes everything Woka wrote into this photograph (ME-107), it is red and confirmed, and a red confirmed action meaning two different things depending on a segmented control is a worse control than a second menu item would be |
| 2026-08-30 | Where does the frame scope live, and for which files? | **Its own strip under the top bar; multi-frame stills only, never an animation** (ME-156) | An animation is *watched*: the clock advances `frameIndex` and every frame goes through the same fold (ME-138, ME-140), so an edit applied to one frame of eight is a flicker rather than an edit — `allowsPerFrame` is `count > 1 && !playsAsAnimation`, and what is left is a burst, a Live Photo's still and a TIFF page chain, every one of which is a *set of pictures*. Two placements were rejected: **in the top bar**, which already carries Cancel, the title, Revert, HDR, undo, redo and Done and where a two-segment control would be the seventh thing in a row of six; and **at the head of a frame strip**, which reads well but did not exist until ME-158 and would have made the switch depend on it. The strip carries a secondary line stating the consequence *before* the click, because `All Frames` clears every override by construction and that is work the user may have done frames ago |
| 2026-08-30 | Is a bitmap too large to cache still admitted, so it stays drawable? | **No — an entry over half the budget is refused** (ME-123), reversing the 2026-08-17 reading written into `evictDownToBudget` | It was never `set` that kept a picture drawable: it answers with nothing, every caller already holds the bitmap it passed in, and the one reader that consults the cache first (`ViewerThumbnails.cachedFull`) falls through to a preview and a full decode on a miss. So admitting a 100 MP photograph — **384 MB** of native pixels against a 256 MB budget (ME-010) — evicted every thumbnail and poster the app held *and still left the cache over budget*: the eviction storm, with no cache entry bought by it. Refusing costs one re-decode on a later re-open. The gate is a **fraction** (half) rather than a constant, so raising `woka.imageCacheBytes` raises it too, and it lives in `set` rather than in `ImageLoader` because `StillPreview`, `MediaThumbnails` and the frame strip all write entries and a caller that must remember not to cache is a rule somebody forgets. A refused write still **drops what it was replacing**, or a picture rewritten on disk would go on serving its pre-edit pixels |
| 2026-08-30 | What happens to unsaved work when the edited frame changes? | **The discard dialog, reworded to name the frame** (ME-158) | `EditorHost` keys the engine, the stored document and the session on the frame, so changing it rebuilds all three and would discard the previous frame's work **silently** — the same loss ME-124 closed for the router, arrived at from inside the editor. Two better answers were written down rather than half-built. **Save on switch** is elegant on paper — `Done` merges into the payload (ME-126), so each frame's work would accumulate — but an autosave triggered by a click is a surprise and there is no undo across it. **Holding an unsaved edit per frame** is the real shape: `EditorSession` with a document *and a history* per frame, so undo does not walk out of the frame being looked at into one that is not. That is a redesign of a 145-line state holder already at the size mandate, so it went to `backlog.md` as its own ticket. A clean document switches with no dialog at all, which is the ordinary case |
| 2026-08-19 | Where in an ISOBMFF file does the recipe go? | **A `uuid` box appended after the last box**, and readers read the **tail** (ME-108) | HEIF stores item positions in `iloc` as absolute file offsets, so inserting anywhere earlier silently rewrites every one of them and the picture stops decoding. Appending disturbs nothing — an unrecognised top-level box is skipped by its size. The cost is that the head window `carries` was built around cannot see it, so `RecipeCarrier` now says which end it lives at and `EditRecipe` reads 64 KB from there. A payload that would not fit that window is **refused at write time**, which is what makes "not in the tail" a real answer rather than a reason to read 4 MB per grid tile |
| 2026-08-19 | Where does a grid Revert get its dialog? | **The dialog chrome moved to `commonMain`** — `core/ui/dialogs/Dialogs.kt`, whole and unchanged (ME-107) | The folder feature is `commonMain` to the bottom and the chrome sat in `jvmMain`, so the confirmation could not see it. Nothing in that file is JVM-specific — the AWT `FileDialog` pickers are in `ui/components/dialogs/`, a different file — and the alternative was a second dialog chrome for one dialog. Every caller is in the same package and none changed |
| 2026-08-19 | Does `Reset` stay in the inspector? | **No — removed** (ME-107) | It cleared the *whole* working document from a header naming one tool, next to an Undo that takes work back a step at a time, and what people reached for it to do was "put this photograph back as it was". That is Revert, and one control now says so. `EditorSession.reset()` survives with no caller: undo covers it, and the next tool that wants a per-panel reset should re-earn the button |
| 2026-08-19 | What does Revert actually remove? | **The recipe from the file, and the session's `openedWith` with it** (ME-107) | Two halves in one order: the engine writes, then the session mirrors it. Clearing only the document would leave the editor dirty against an edit the file no longer holds, and clearing only the file would leave Undo pointing at operations nobody can reach. A failed write moves neither, so the editor never claims a revert the disk refused |
| 2026-08-19 | Where does the `· Edited` mark come from in the **viewer**? | **The file itself** — `AppliedStillEdits.carries`, keyed on the reload signal | Nothing sets the shell's override outside the editor, so an edited photograph read as an untouched one in Information (found by hand). The override still answers for unsaved work; the file answers for everything else, and the two are `or`-ed |
| 2026-08-19 | Do **Duplicate** and **Copy** carry the edit? | **Duplicate: yes, untouched. Copy: it is now the picture, drawn** | Duplicate copies bytes, so the recipe travels and each copy is independently editable and revertible — the property §5.4 chose over a central index. `Copy` used to do the same thing, which made it a second Duplicate: pasting into a message gave nothing. It now puts the **rendered** frame on the clipboard at native resolution, through the same fold every reader uses, and that is the one way an edit leaves Woka at all (§5.3). A video has no single picture, so there `Copy` still means the file. Saving a *flattened file* stays declined — it is the export step §5 removed |
| 2026-08-19 | What is the history when an edited file opens? | **The file's recipe, replayed one operation per entry** — `EditorHistory.startingAt`, ending on the document as the file holds it, beginning on the original (ME-134) | Decided at the workbench: an edited photograph reopened with nothing to undo reads as an unedited one, whatever the canvas draws. The recipe already *is* an ordered list of operations, so a history is a list of its prefixes and costs nothing to build. Undo therefore crosses sittings, and undoing to the beginning reaches the original — `Done` after that clears the recipe, which is Revert reached from the other direction (§5.2). It is a **reconstruction**, not a recording: a `Single` tool's op has absorbed every drag that made it, so these are the steps the file can still describe |
| 2026-08-19 | What does the `· Edited` mark answer? | **"This photograph carries an edit"** — the document, not `isDirty` (ME-134) | Since a reopened session is *clean* by definition, marking only unsaved work left an edited picture presenting itself as an original. The exit dialog keeps the other meaning and asks only about work not yet in the file: two questions, two sources, one each |
| 2026-08-19 | "Remove Woka's data from this file"? | **It is Revert** — the segment holds the edit and nothing else. ME-107, un-dropped | Two names for one operation would be two buttons doing the same thing. A **bulk** removal across a folder is hygiene rather than editing and belongs in Settings, as its own later ticket |
| 2026-08-18 | Does the editor draw its own top bar? | **No** — it fills the shell's accessory slots, as the viewer does, and restores them on dispose | Two stacked bars left the folder path up there doing nothing, the carousel eye and a live `Edit` button running during an edit, and a `Cancel` duplicating a Back that still exited the folder |
| 2026-08-18 | Zoom in v1, revisited | **Yes, capped at 100 %** | A wheel that does nothing reads as broken. At 100 % the ≤2560 px proxy is still sharp; above it is ME-116's, and the cap is the line it removes |
| 2026-08-18 | Where does the editor mount when opened from the **grid**? | Its own overlay in `FolderScreen`, beside `viewerMediaId` | The viewer is not a `Screen` either — it is an overlay too. Cancel then returns where the user actually was: the grid from the grid, the viewer with its exact zoom/pan from the viewer |
| 2026-08-18 | Canvas zoom in v1? | **No — `Fit` only.** Zoom arrives with ME-116 | §4.3 asked for both "`Fit` at all times" and "wheel zooms 100–800 %". Above 100 % the canvas draws a 2560 px proxy, so zoom without ME-116's native-region draw ships a feature that looks broken on arrival |
| 2026-08-18 | Does the native bitmap stay in `ImageBitmapCache` during an edit? | **No.** The canvas draws an editor-scoped ≤2560 px proxy; native is decoded once, at save, and released | §4.1 wanted it from the cache, §10 wanted it out. 384 MB against a 256 MB budget settles it — and the guard rail §10 asked for falls out for free. Cost is 6–8 ms of proxy build per open |
| 2026-08-18 | HDR tool scope in v1? | `Keep` + `To SDR`, and **not before ME-120** | `To SDR` bakes the tone curve into pixels, and that curve is currently wrong for real content (SDR white at 148/255). Baking it would put the defect in the file permanently. `To HDR` is deferred, not blocked |
| 2026-08-18 | Markup scope in v1? | All five shapes, **three** tickets — ME-113, ME-114, ME-122 | Text is the only shape with focus, a caret and IME. Splitting it keeps each ticket to one sitting instead of cutting a feature |
| 2026-08-18 | Which six adjustments? | Exposure, Brightness, Contrast, Saturation, Warmth, Tint | All six compose into **one** `ColorMatrix`, which is what §3.2 promises. Highlights/Shadows need a per-luminance curve — a fourth fold primitive, and §3.2 says three |
| 2026-08-18 | Which filter presets? | Original, Vivid, Dramatic, Warm, Cool, Mono, Noir, Silvertone | Eight, iOS Photos naming, every one expressible as a colour matrix |
| 2026-08-18 | Which crop aspects? | Free, Original, 1:1, 4:3, 3:2, 16:9, plus an `ActionParam` flipping the ratio | Six exceeds `SegmentedControl`'s four, so a chip row (§3.4). The flip replaces doubling the list with portrait variants |
| 2026-08-18 | Which resize presets? | 100/75/50/25 % **and** a long-edge chip row — 4K, 2560, 1920, 1280 | Percentages for "a bit smaller", long edge for the target the web actually asks for |
| ~~2026-08-18~~ | JPEG quality on save? | ~~**q95** everywhere~~ **Superseded 2026-08-30 by the row above** | §7 said q95 and ME-104's block said q90. Editing is a second compression over a first, and q90 stacks visible artefacts. The "configurable quality is ME-127, not MVP" clause is what ME-127 answered, with a no |
| 2026-08-18 | Do §10's four open gaps become tickets? | **All four** — ME-123 large sources, ME-124 editor + PiP, ME-125 save cancellation, ME-126 frame sets | Each was measured or found, not guessed; none is a "later maybe" |
| 2026-08-18 | HDR view mode in MVP? | **No** — stays a backlog block | It writes no pixels, and its whole purpose — previewing what `To SDR` will bake — depends on the curve being right, i.e. on ME-120 |
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
- **Editor plus PiP.** ~~Who owns the keyboard, and whether editing is allowed at all then~~ —
  **answered 2026-08-30 (ME-124)**: the keyboard was never the question, because the video window is a
  separate `Window` whose keys never reach `GlobalShortcutState` — the OS decides who *hears* a key by
  focus. What a key *acts on* is the hazard: `Escape`, `F` and the two exit controls call
  `closePip(returnToMain = true)`, whose `replaceTop` with a fresh nonce rebuilds `FolderScreen` and
  took `editorTarget` with it, unsaved work included. The return now saves and closes the editor on the
  way through and says so in the pill. The **fullscreen** window shares that path, so it is covered too.
- **Frame sets.** ~~Editing frame N of a burst is covered by conversion~~ — ~~**answered 2026-08-19**:
  one recipe per file, so an edit applies to every frame in it~~ — **closed 2026-08-30** (ME-126,
  ME-156, ME-157, ME-158): the payload is keyed by frame, a switch under the top bar chooses which
  slot `Done` writes into, the strip marks the frames that carry their own edit, and the editor walks
  between them. The un-keyed form still means every frame, so nothing written before it changed.
  What is left is holding an *unsaved* edit per frame, which is a `backlog.md` block.
- **Localisation.** Every string in this feature is English, like the rest of the app. If that ever
  changes, the manifests are where the strings live.
