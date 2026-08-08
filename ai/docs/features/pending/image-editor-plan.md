# Plan: Image Editor (Phase 5a)

The first half of the Media Editor (`media-editor-plan.md`). Everything here that is **not** about
pixels is built media-agnostic, so the Video Editor (Phase 5b) adds a tool set and a canvas and
inherits the session, the history, the tool system, the whole shell UI, the backup model and the
save job unchanged.

Reference: `ai/mockups/design/FINAL_design_mockup.html` · iOS Photos editor is the UX model.

---

## 0. Scope

**In:** Rotate 90°, Flip H/V, Crop (aspect presets), Resize, Adjustments, Filter presets, Markup
(arrow, rectangle, ellipse, freehand, text), Undo/Redo, Save (overwrite + hidden original), Revert.

**Out:** layers, masks, healing, straighten/arbitrary-angle rotation (follow-on), batch editing, RAW,
any video operation.

**Formats.** Decode is not the constraint — encode is. Skia writes `png`, `jpg/jpeg`, `webp`, so
those are edited and saved in place. Everything else Woka lists (`heic`, `heif`, `avif`, `tif`,
`tiff`, `gif`, `bmp`) and every file holding more than one frame is edited **after an explicit
conversion the user agrees to** — see Phase 8. The Edit button is never a dead button; it either
edits or offers the conversion.

**Videos** are not editable in this plan — the Edit button is disabled for `MediaItem.Video`.

---

## 1. Architecture

### 1.1 The document is data, not pixels

An edit session never mutates a bitmap. It builds an immutable `EditDocument` — an ordered list of
`ToolOp` — and the canvas *renders* it. The same renderer runs at screen scale for the preview and
at native scale on save, which is what makes "markup rasterised at source resolution" fall out of
the design instead of being a special case.

```
ToolOp        data class(toolId: String, params: ParamSet) — the ONLY op type in the app
EditDocument  data class(ops: List<ToolOp>) — the whole edit, ~1 KB, plainly serialisable
EditorHistory List<EditDocument> + index, capped at 50 — snapshots, not inverse commands
EditorSession preview(op) / commit() / undo() / redo() / dirty — the only mutator
```

One op type, not one per tool: the document then needs no polymorphic serialiser, history is a list
copy, and — the point of §1.3 — the shell can render a tool's panel without knowing the tool.

`preview(op)` replaces a *pending* op and pushes **no** history entry; `commit()` folds it in and
pushes one. Which of the two a control uses is decided by the tool's manifest, not by the tool's
code, so a slider cannot accidentally produce 200 undo steps.

`EditorSession` is a ViewModel in all but name: `StateFlow` out, no `androidx.compose.*` inside.

### 1.2 Rendering: a recipe, not a pipeline

Ops fold into three draw-time primitives, so the live canvas allocates **no bitmap at all** while
editing:

```
RasterStage.Geometry(matrix: Matrix, outputSize: IntSize)   crop, rotate, flip, resize
RasterStage.Pixels(colorFilter: ColorFilter)                adjustments, filter presets
RasterStage.Paint(draw: DrawScope.() -> Unit)               markup
```

`RasterRecipe.fold(document, registry)` walks the ops, asks each owning tool for its stage, and
collapses them into **one** matrix, **one** `ColorFilter` (colour matrices multiply) and an ordered
paint list. The canvas draws: source → matrix → colour filter → paints. A brightness slider at 60 fps
costs one `ColorFilter` rebuild per frame and zero pixel work.

`RasterRenderer.render(source, recipe, scale)` replays the same recipe onto a Skia `Surface` — used
only by the save job, at `scale = 1.0`, against the **native-resolution** bitmap.

The canvas draws a **proxy**: the source downscaled so its longest edge is ≤ 2560 px. A 24 MP photo
is edited against a ~7 MP texture; the native bitmap is touched once, on save.

### 1.3 Tools — the plugin architecture

Every editing element is the same shape: an icon, a name, a line saying what it does, a handful of
parameters, and a rule turning those parameters into pixels. **Nothing in a tool knows where it
sits, how its panel is laid out, in what order it applies, or how it reaches Settings** — the shell
derives all of that from the manifest. Adding a tool is one directory and one line in
`EditorToolModule`. (Named `EditorTool` rather than `Plugin`: nothing is loaded from a jar at
runtime, and calling it a plugin would promise that.)

A tool is three things:

```kotlin
interface EditorTool {
    val manifest: ToolManifest              // 1. what it is
    val params: List<ParamSpec<*>>          // 2. what it edits — and its whole UI
    fun stage(params: ParamSet): RasterStage?   // 3. what it performs — pure, no Compose
}
```

and, only when it needs direct manipulation on the image, a fourth:

```kotlin
interface CanvasTool : EditorTool {
    @Composable fun Overlay(scope: EditorToolScope)   // crop box, markup handles
}
```

#### The manifest

| Field | Consumed by |
|---|---|
| `id` | op ownership, settings key, test ids |
| `title` | rail tooltip, inspector header, settings row, "disabled tool" warning |
| `summary` | rail tooltip second line, settings row subtitle ("Rotate and mirror the picture") |
| `icon: DrawableResource` | rail button, settings row |
| `domain: ToolDomain` | **rail group and position** *and* **op application order** |
| `order: Int` | position within its group |
| `supports: Set<MediaKind>` | whether the tool appears at all for this media |
| `cardinality: Single \| Repeating` | whether a new op replaces the tool's previous one or appends |
| `interaction: Instant \| Continuous \| Canvas` | whether the shell commits on change, on release, or hands over to `Overlay` |
| `shortcut: EditorShortcut?` | the keyboard table and the shortcuts help popover |

`ToolDomain` is `Geometry | Color | Annotate | Output`. It does two jobs at once:

- **Placement:** the rail draws the groups in that order with a 1.dp divider between them. No tool
  positions itself; reordering the rail is reordering an enum.
- **Application order:** `RasterRecipe.fold` sorts ops by domain rank, *not* by the order the user
  clicked. Geometry before colour before annotation before output is the only order that is
  semantically correct — a crop performed after an arrow was drawn must still clip the arrow, and a
  resize must be the last thing before encoding. Getting this from the manifest removes a whole
  class of "the result depends on what I clicked first" bugs.

#### The parameter schema *is* the UI

Because every editing element really is the same shape, the panel is not written per tool — it is
rendered from the schema, one row renderer per spec type:

| `ParamSpec` | Rendered as |
|---|---|
| `FloatParam(key, label, range, default, detent, unit)` | `DetentSlider` row + signed value readout; double-click resets to `detent` |
| `IntParam(key, label, range, default)` | numeric field with steppers |
| `BoolParam(key, label)` | label + `IosSwitch` |
| `ChoiceParam(key, label, options)` | `SegmentedControl` (≤ 4 options) or a chip row |
| `PreviewChoiceParam(key, options, preview)` | horizontal thumbnail strip, each drawn through that option's own stage — how Filters gets its strip for free |
| `ColorParam(key, label, swatches)` | swatch row + custom-colour popover |
| `ActionParam(key, label, icon)` | icon button; always `Instant` |
| `ObjectParam<T>(key, serializer)` | **nothing** — structured payload owned by a `CanvasTool`'s overlay (markup shapes) |

Values are stored generically and read typed, so no tool works with strings:

```kotlin
object AdjustTool : EditorTool {
    val Brightness = FloatParam("brightness", "Brightness", -1f..1f, default = 0f, detent = 0f)
    val Contrast   = FloatParam("contrast",   "Contrast",   -1f..1f, default = 0f, detent = 0f)
    override val params = listOf(Brightness, Contrast, /* … */)
    override fun stage(p: ParamSet) = RasterStage.Pixels(adjustMatrix(p[Brightness], p[Contrast]))
}
```

`p[Brightness]` is a `Float` at the call site and an entry in a `Map<String, Any>` in the document.
Adjustments, Filters, Transform and Resize are therefore **pure data plus one pure function** — no
Compose code at all. Only Crop and Markup implement `CanvasTool`, and even they get their inspectors
from the schema; the overlay is only the on-image dragging.

An `EditorTool.CustomInspector` hook exists as an escape hatch, and every use of it is a sign the
schema is missing a row type.

#### What the shell derives, and the tool never writes

Rail button, icon, tooltip, group, divider, position · inspector header, every control row, its
spacing and typography · preview-vs-commit behaviour · undo granularity · the keyboard table and its
help popover · the Settings → Editor toggle row · the "3 operations from a disabled tool are not
applied" notice · the ordering of ops in the recipe · test ids.

#### Enabling, disabling, failing

- The registry is a `StateFlow<List<EditorTool>>`; rail, inspector, shortcut table and settings list
  are all derived from it, so a tool appearing or disappearing re-renders the editor with no other
  wiring.
- `WokaSettings.editorDisabledTools: Set<String>` persists per-tool enablement; Settings → Editor
  builds its rows straight from the manifests.
- An op whose tool is absent or disabled is **skipped** by the fold and reported in the inspector —
  the document never crashes and never silently loses work.

### 1.4 What the Video Editor inherits

Untouched: `core/` (op, document, history, session, manifest, param schema, registry, prefs), the
whole `shell/` including the schema-driven inspector, `EditBackup`, `EditSaveJob`'s structure, the
keyboard scope, the unsaved-changes dialog. Video adds an `EditorMediaSurface` drawing the player
plus a trim timeline, a `VideoOpApplier` (ffmpeg `-c copy`) in place of `RasterRenderer`, and two
tools — Trim (`Geometry`, `Canvas`) and Mute (`Output`, `Instant`, one `BoolParam`). Mute is
literally a manifest and a boolean; that is the test of whether §1.3 is right.

---

## 2. UI / UX

### 2.1 Entry and exit

`Edit` in the viewer's control pill (`GlobalControlButtons.kt:108`, currently wired to
`editAction = { onClose(state.currentId) }` — a stub) opens the editor **over** the viewer. The
viewer stays mounted and frozen underneath, so `Cancel` returns to the exact zoom/pan/frame the user
left. The native bitmap is already in `ImageBitmapCache` under `key(path, 0)` — the editor takes it
from there, so opening is instant and nothing is decoded twice.

Exit: `Done` saves and returns to the viewer showing the edited file; `Cancel`/`Esc` returns
unchanged, via a confirmation dialog if the document is dirty.

### 2.2 Layout

Full-bleed over the viewer's existing dark backdrop. The carousel, the nav arrows, the size control
and the dock accessories are hidden while editing — the editor is a mode, not a panel.

```
┌───────────────────────────────────────────────────────────────┐
│  Cancel            photo.jpg · Edited        ↶  ↷      [Done] │  top bar (accessory)
├───────────────────────────────────────────────────────────────┤
│                                                               │
│                     ┌───────────────────┐                     │
│                     │                   │                     │  canvas: proxy drawn
│                     │   image + tool    │                     │  through the recipe;
│                     │     overlay       │                     │  CanvasTool overlay on top
│                     └───────────────────┘                     │
│                                                               │
│            ╭─────────────────────────────────────╮            │
│            │  Brightness  ──────●────────   +12   │            │  inspector — rendered from
│            │  Contrast    ─────●─────────    -4   │            │  the param schema, identical
│            ╰─────────────────────────────────────╯            │  for every tool by construction
│                                                               │
│                ╭───────────────────────────────╮              │
│                │  ⤢  ⟳  ⤡ │ ◐  ✦ │ ✎ │ ⇲       │              │  rail — groups from ToolDomain,
│                ╰───────────────────────────────╯              │  dividers drawn by the shell
└───────────────────────────────────────────────────────────────┘
```

Concrete values: rail = `MediaViewerControlsContainer`'s shape (30.dp radius, `Theme.colors.surface`,
1.dp `Theme.colors.border`, 6.dp padding), 32.dp icon buttons, 8.dp spacing, 16.dp between groups
with a 1.dp × 16.dp divider, 32.dp from the bottom. Inspector = 12.dp radius, same surface/border,
`PaddingValues(horizontal = 14.dp, vertical = 12.dp)`, max width 520.dp, centred, 12.dp above the
rail, enter/exit `fadeIn + slideInVertically(24.dp)` at 180 ms. Selected tool =
`Theme.colors.accentEditor` (#BF5AF2) tint over `accentEditor.copy(alpha = 0.14f)` at 10.dp radius.
`Done` = filled `accentEditor` pill, `onAccent` label, 40 % alpha when not dirty.

Row renderers reuse `DetentSlider`, `SegmentedControl`, `IosSwitch`, `DropdownItem` — the existing
components, wired once in the shell instead of once per tool.

### 2.3 Canvas interaction

The canvas is `Fit` at all times; the size control is gone in edit mode.

- **Primary drag** goes to the active `CanvasTool`'s `Overlay` first. If it does not consume,
  nothing happens (no accidental pan).
- **Wheel** zooms 100–800 % about the cursor, reusing `ScaleMath`; **middle-drag** or **Space+drag**
  pans, clamped by `PanBounds`. Zoom exists for markup precision only and resets on tool change.
- Hovering a markup shape shows its handles; `Delete`/`Backspace` removes the selected one.

### 2.4 Keyboard

Registered through `LocalGlobalShortcuts.onPreviewKey` exactly as the viewer does, and taken over
while the editor is open, so no viewer or app shortcut fires underneath.

| Key | Action |
|---|---|
| `Esc` | Cancel (confirm if dirty) |
| `Ctrl+S` / `Enter` | Done |
| `Ctrl+Z` / `Ctrl+Shift+Z` / `Ctrl+Y` | Undo / Redo |
| `Delete` | Delete selected markup shape |
| `Tab` / `Shift+Tab` | Next / previous tool |
| `?` | Shortcuts popover — built from the manifests |
| tool-declared | `manifest.shortcut`; a disabled tool's key disappears with it |

### 2.5 Save UX

`Done` closes the editor immediately and runs the save in the background, with the same banner
pattern as `FrameExportBanner`: "Saving…" → "Saved" / "Save failed — original untouched". The viewer
underneath reloads the file when the job reports success.

---

## Phase 0: Probes and decisions

Nothing is assumed about the decoders; each probe is a throwaway test that prints a table.

- `EditorProbeTest`: for every listed image format, decode via `stillDecoderFor`, re-encode via Skia
  to PNG and JPEG, re-decode, and report dimensions, pixel delta and round-trip time. This produces
  the conversion matrix Phase 8 needs (which source formats map to which target).
- Probe **EXIF orientation**: open a JPEG carrying `Orientation=6` through the current viewer path
  and record whether it displays upright. Decides whether the save path must bake the rotation and
  reset the tag, or inherits correct behaviour.
- Probe **EXIF preservation**: dump the JPEG marker segments before and after a Skia re-encode, and
  verify that splicing the source `APP1` segment into the output (with `Orientation` forced to 1)
  produces a file that both Woka and Windows Explorer read correctly. Baseline decision: **preserve**
  — camera, lens, date and GPS are not the editor's to throw away. If the splice proves unreliable,
  fall back to a one-time "metadata will be dropped" notice on first save.
- Probe **Coil staleness**: overwrite a file the grid has already drawn and record whether the tile
  updates, so Phase 7 invalidates the right cache.
- Record all four in `ai/docs/testing/manual/results/`.

**Files:** `jvmTest/.../probe/EditorProbeTest.kt`, `jvmTest/.../probe/EditorRoundTrip.kt`

## Phase 1: Core document, history, session

- `ParamSpec.kt`: the sealed spec hierarchy from §1.3 plus `ParamSet` with typed
  `operator fun <T> get(spec: ParamSpec<T>)` and `with(spec, value)`; defaults come from the spec, so
  an absent key is never an error.
- `ToolOp.kt`: `data class ToolOp(val toolId: String, val params: ParamSet)`.
- `EditDocument.kt`: `data class EditDocument(val ops: List<ToolOp> = emptyList())` with
  `withPending(op)`, `commitPending()`, `opsOf(toolId)` — `Single` cardinality replaces, `Repeating`
  appends. Every mutator returns a `.copy()`.
- `EditorHistory.kt`: `push`, `undo`, `redo`, `canUndo`, `canRedo`, cap 50, redo tail dropped on push.
- `EditorSession.kt`: `MutableStateFlow<EditDocument>` + pending op + history; exposes `document`,
  `canUndo`, `canRedo`, `isDirty` as `StateFlow`. No Compose imports.

**Files:** `feature/editor/core/ParamSpec.kt`, `core/ParamSet.kt`, `core/ToolOp.kt`,
`core/EditDocument.kt`, `core/EditorHistory.kt`, `core/EditorSession.kt` (all `commonMain`)

## Phase 2: Tool contract and registry

- `ToolManifest.kt`: the data class and the three enums (`ToolDomain`, `ToolCardinality`,
  `ToolInteraction`), plus `EditorShortcut`.
- `EditorTool.kt` / `CanvasTool.kt`: the interfaces from §1.3.
- `RasterStage.kt`: the three-case sealed interface.
- `EditorToolScope.kt`: the only thing a tool sees — document, `preview`, `commit`, `sourceSize`,
  `canvasToSource` / `sourceToCanvas` matrices, selection slot.
- `EditorToolRegistry.kt`: takes the tool list + a `StateFlow<Set<String>>` of disabled ids; exposes
  `tools(kind): StateFlow<List<EditorTool>>` grouped by `domain` then `order`, and
  `toolFor(op): EditorTool?`.
- `EditorToolModule.kt` (jvm): the Koin module — the single place a tool is declared.
- `WokaSettings.editorDisabledTools: Set<String>`, a `setEditorToolEnabled(id, enabled)` mutation,
  and the `SettingsProjection` field.
- `EditorSettingsPanel.kt`: rows generated from the manifests (icon, title, summary, `IosSwitch`) —
  no per-tool settings code, ever.

**Files:** `feature/editor/core/ToolManifest.kt`, `core/EditorTool.kt`, `core/CanvasTool.kt`,
`core/RasterStage.kt`, `core/EditorToolScope.kt`, `core/EditorToolRegistry.kt`,
`jvmMain/.../editor/di/EditorToolModule.kt`, `core/data/WokaSettings.kt`,
`core/data/SettingsMutations.kt`, `app/SettingsProjection.kt`,
`feature/settings/EditorSettingsPanel.kt`, `feature/settings/SettingsPanels.kt`, `app/di/Koin.kt`

## Phase 3: Editor shell UI

Built against one throwaway tool whose schema uses every row type, so the shell is provable before
any real tool exists.

- `EditorScreen.kt`: composes `rememberEditorSession`, the registry, the surface slot, the top bar,
  the canvas host, the inspector and the rail. Owns `activeToolId`.
- `EditorTopBar.kt`: registers into `LocalTopBarAccessories` (the pattern in
  `MediaViewerEffects.kt:13`) — Cancel, filename + "Edited" dot, Undo/Redo bound to
  `canUndo`/`canRedo`, `Done`.
- `EditorToolRail.kt` + `EditorToolButton.kt`: groups and dividers straight from `ToolDomain`.
- `EditorInspector.kt`: the animated container; header from `manifest.title`.
- `ParamRows.kt` + `ParamRowSlider.kt` / `ParamRowChoice.kt` / `ParamRowColor.kt` /
  `ParamRowPreviewStrip.kt`: one renderer per `ParamSpec` type. **This is the file set that makes
  every tool's panel identical**, and the only place the preview-vs-commit rule from
  `manifest.interaction` is implemented.
- `EditorCanvasHost.kt`: measures itself, builds the canvas↔source matrices, renders
  `surface.Canvas(...)`, then the active `CanvasTool`'s `Overlay`.
- `EditorShortcutsPopover.kt`: the `?` sheet, built from the manifests.
- `EditorTokens.kt`: the dp/ms values from §2.2 in one place.
- `EditorMediaSurface.kt`: `interface EditorMediaSurface { val sourceSize: IntSize;
  @Composable fun Canvas(recipe: RasterRecipe, modifier: Modifier) }` — the seam the video editor
  fills.

**Files:** `feature/editor/shell/EditorScreen.kt`, `shell/EditorTopBar.kt`, `shell/EditorToolRail.kt`,
`shell/EditorToolButton.kt`, `shell/EditorInspector.kt`, `shell/params/ParamRows.kt`,
`shell/params/ParamRowSlider.kt`, `shell/params/ParamRowChoice.kt`, `shell/params/ParamRowColor.kt`,
`shell/params/ParamRowPreviewStrip.kt`, `shell/EditorCanvasHost.kt`,
`shell/EditorShortcutsPopover.kt`, `shell/EditorTokens.kt`, `feature/editor/core/EditorMediaSurface.kt`

## Phase 4: Image surface and render recipe

- `ImageEditorSource.kt` (jvm): from a `File`, produce the native `ImageBitmap` (from
  `ImageBitmapCache`, else `ImageLoader.load`), the native `IntSize` (from `StillDecoder.nativeSize`,
  never from the bitmap) and the ≤ 2560 px proxy cached under `ImageBitmapCache.previewKey`.
- `RasterRecipe.kt` / `RasterFold.kt`: `fold(document, registry) → RasterRecipe(matrix, outputSize,
  colorFilter, paints, skipped)`, ops sorted by `ToolDomain` rank; colour matrices multiplied,
  geometry matrices concatenated.
- `ImageEditorSurface.kt`: draws the proxy with `drawImage(colorFilter = …)` inside
  `withTransform { transform(recipe.matrix) }`, then runs `recipe.paints`.
- `RasterRenderer.kt`: Skia `Surface` of `recipe.outputSize`, replays the recipe against the
  **native** bitmap at scale 1.0, returns the snapshot.

**Files:** `jvmMain/.../editor/image/ImageEditorSource.kt`, `image/RasterRecipe.kt`,
`image/RasterFold.kt`, `image/ImageEditorSurface.kt`, `image/RasterRenderer.kt`

## Phase 5: Transform tool (rotate / flip)

The first tool, and the proof that a tool is data. `Geometry` domain, `Single`, `Instant`.

- `TransformTool.kt`: manifest + three params — `IntParam("quarterTurns")`, `BoolParam("flipH")`,
  `BoolParam("flipV")` — plus four `ActionParam`s (rotate left/right, flip H/V) that mutate them.
- `TransformMatrix.kt`: pure `(quarterTurns, flipH, flipV, sourceSize) → Matrix + outputSize`, with
  the size swap on odd quarter turns. Unit-tested.
- No inspector file: the four `ActionParam`s render as an icon button row from the schema.

**Files:** `jvmMain/.../editor/tools/image/TransformTool.kt`, `.../TransformMatrix.kt`

## Phase 6: Save, backup, revert

- `WokaStorage`: `originalsDir` (`root/.originals`) and `originalFile(source, ext)` keyed on the
  **absolute path only** — the existing `thumbnailFile` key includes length and mtime, which change
  on save and would mint a new backup per edit.
- `EditBackupIndex.kt`: `originals/index.json` — `key → { sourcePath, ext, savedAt }`.
- `EditBackup.kt`: `backupOnce(source)` copies to `originalsDir` **only if no entry exists**;
  `restore(source)` copies back, deletes backup and entry; `drop(source)`.
- `ImageSaveJob.kt`: `backupOnce` → `RasterRenderer.render(native, recipe, 1f)` → encode (PNG via
  `encodePng`, JPEG q90, WebP q90) → splice the source `APP1` EXIF segment with `Orientation = 1`
  (Phase 0 decision) → write `<name>.woka-tmp` beside the target → `Files.move(ATOMIC_MOVE,
  REPLACE_EXISTING)` with 3 retries at 120 ms (the viewer may hold the file on Windows) →
  invalidate caches.
- `JpegExifSplice.kt`: read `APP1` from the source, insert after `SOI`/`APP0` in the output. Pure
  byte work, unit-tested against the Phase 0 fixtures.
- `EditCacheInvalidation.kt`: drop `ImageBitmapCache` `key(path, 0)` and `previewKey(path)`, delete
  the stale `WokaStorage.thumbnailFile`, evict Coil's memory and disk entries for the path.
- `EditSaveBanner.kt`: the `FrameExportBanner` pattern for Saving / Saved / Failed.
- Revert: a `Revert to Original` entry in the viewer's frame context menu (`MediaViewerFrame.kt:64`,
  via `ViewerActions.extra`), shown only when a backup exists; confirms, restores, invalidates.

**Files:** `core/data/WokaStorage.kt`, `jvmMain/.../editor/save/EditBackupIndex.kt`,
`save/EditBackup.kt`, `save/ImageSaveJob.kt`, `save/JpegExifSplice.kt`,
`save/EditCacheInvalidation.kt`, `save/EditSaveBanner.kt`, `feature/viewer/shell/MediaViewerFrame.kt`

## Phase 7: Wiring into the viewer

- `EditButton` gains `enabled: Boolean` (false only for `MediaItem.Video`) and a tooltip.
- `NativeMediaViewer` (both `expect` and `actual`) gains `onEdit: (String) -> Unit`;
  `MediaViewerScreen.editAction` raises it instead of closing.
- `FolderScreen` hosts `EditorScreen` as a sibling overlay above `NativeMediaViewer`, keyed on the
  path being edited; the viewer stays mounted underneath. On save success it re-reads the item.
- `EditorKeys.kt`: takes over `LocalGlobalShortcuts.onPreviewKey` in a `DisposableEffect` and
  restores the previous handler on dispose (verify the viewer's handler survives the nesting).
- `EditorConfirmDialog.kt`: "Discard changes?" — Cancel / Discard, in the `RenameDialog` idiom.

**Files:** `feature/viewer/shell/GlobalControlButtons.kt`,
`feature/viewer/shell/MediaViewerGlobalControls.kt`, `feature/viewer/shell/MediaViewerScreen.kt`,
`feature/viewer/shell/MediaViewerLayoutWrapper.kt`,
`commonMain + jvmMain feature/viewer/NativeMediaViewer.kt`, `feature/folder/FolderScreen.kt`,
`feature/editor/shell/EditorKeys.kt`, `shell/EditorConfirmDialog.kt`

*After this phase the editor is usable end to end on PNG/JPEG/WebP: open, rotate, save, revert.*

## Phase 8: Conversion on demand

Formats Skia cannot encode, and multi-frame files, are converted **after the user agrees** — the
Edit button is never dead.

- `EditableFormats.kt`: `File.editPlan(frameIndex, frameCount): EditPlan` returning
  `EditInPlace`, `NeedsConversion(target: ConvertTarget, reason: ConvertReason)`, or `NotEditable`
  (video only). Target defaults follow the Phase 0 matrix: photographic sources (`heic`, `heif`,
  `avif`) → JPEG q95; graphics and anything with alpha or multiple frames (`tif`, `gif`, `bmp`) →
  PNG.
- `ConvertSheet.kt`: a centred card in the `RenameDialog` idiom, opened by the Edit button.

  > **Convert before editing**
  > `photo.heic` can be opened but not saved back in its own format.
  > Woka will convert it to **photo.jpg** and edit that.
  > The original is kept and `Revert to Original` brings it back.
  > *(when multi-frame)* Only frame 3 of 12 is kept.
  >
  > `[ JPEG · smaller ] [ PNG · lossless ]`   `Cancel`   **`Convert & Edit`**

  Target chosen with a `SegmentedControl`, defaulted from `EditPlan`; the filename line updates live.
- `ConvertJob.kt`: decode frame `frameIndex` at native resolution → encode to the target → back the
  source up through `EditBackup` → atomic-write the new file beside it → **delete the source** (it
  lives in `.originals/`, so nothing is lost and Revert restores it) → invalidate caches → open the
  editor on the new path. One `MediaItem` in, one out; no duplicate clutter in the folder.
- `FolderViewModel` refreshes the listing so the grid shows the converted name.
- Conversion is one undoable-by-Revert step, never silent, and never triggered by anything but a
  press on `Convert & Edit`.

**Files:** `jvmMain/.../editor/convert/EditableFormats.kt`, `convert/EditPlan.kt`,
`convert/ConvertJob.kt`, `convert/ConvertSheet.kt`, `feature/viewer/shell/GlobalControlButtons.kt`,
`feature/folder/FolderViewModel.kt`

## Phase 9: Crop tool

`Geometry` domain, `Single`, `Canvas`.

- `CropTool.kt`: manifest + `ObjectParam<IntRect>("rect")` in **source** coordinates +
  `ChoiceParam("aspect")` (Free, Original, Square, 4:3, 3:2, 16:9 and inverses) +
  `ActionParam("reset")`. The inspector is entirely schema-rendered.
- `CropOverlay.kt`: dimmed outside, rule-of-thirds grid, eight handles (corners 20.dp, edges 12.dp),
  drag inside to move, `pointerHoverIcon` per handle. Drags call `preview`, pointer-up `commit`.
  Minimum crop 32 source px.
- `CropGeometry.kt`: hit-testing and aspect-constrained resize math, kept separate so the overlay
  stays under the line limit. Unit-tested.

**Files:** `.../editor/tools/image/crop/CropTool.kt`, `crop/CropOverlay.kt`, `crop/CropGeometry.kt`

## Phase 10: Resize tool

`Output` domain, `Single`, `Continuous`. Pure data.

- `ResizeTool.kt`: `IntParam("width")`, `IntParam("height")` (1…16384), `BoolParam("lockAspect")`,
  `ChoiceParam("preset")` (25 / 50 / 75 / 100 %); `stage()` returns `Geometry` with a scale matrix.
- The "3024 × 4032 → 1512 × 2016" readout comes from a `ParamRowFootnote` the schema can attach to a
  group — one shell addition, available to every future tool.

**Files:** `.../editor/tools/image/ResizeTool.kt`, `shell/params/ParamRowFootnote.kt`

## Phase 11: Adjustments tool

`Color` domain, `Single`, `Continuous`. Pure data.

- `AdjustTool.kt`: six `FloatParam`s (exposure, brightness, contrast, saturation, warmth, tint),
  each −1f…1f with detent 0f, plus an `ActionParam("auto")` using `Icons.Static.MagicWand`.
- `AdjustMatrix.kt`: composes the six into one 4×5 `ColorMatrix`. Pure, unit-tested for neutrality
  at all-zero.

**Files:** `.../editor/tools/image/AdjustTool.kt`, `.../AdjustMatrix.kt`

## Phase 12: Filters tool

`Color` domain, `Single`, `Continuous`. Pure data.

- `FilterPresets.kt`: `Original`, `Vivid`, `Dramatic`, `Mono`, `Noir`, `Warm`, `Cool`, `Fade` — one
  `ColorMatrix` constant each.
- `FilterTool.kt`: `PreviewChoiceParam("preset", presets)` + `FloatParam("intensity", 0f..1f,
  detent = 1f)`; `stage()` lerps the preset matrix toward identity by intensity. The 64.dp thumbnail
  strip is rendered by `ParamRowPreviewStrip` from Phase 3 — each thumbnail is the same proxy texture
  under a different `ColorFilter`, so the strip costs one decode and no extra memory.

**Files:** `.../editor/tools/image/FilterPresets.kt`, `.../FilterTool.kt`

## Phase 13: Markup — engine

`Annotate` domain, `Repeating`, `Canvas`.

- `MarkupShape.kt`: sealed — `Arrow(from, to, curve)`, `Rect(bounds)`, `Ellipse(bounds)`,
  `Freehand(points)`, `Text(anchor, text, size)`. All coordinates in **source** space.
- `MarkupDraw.kt`: `DrawScope.draw(shape, style)` — the one renderer, used by the overlay, the recipe
  and the save path, so shapes rasterise at native resolution with no separate code path. The arrow
  head is computed from the curve tangent.
- `MarkupHitTest.kt`: point→shape resolution with 8.dp source-space tolerance, plus handle positions
  per shape type. Unit-tested.
- `MarkupTool.kt`: `ObjectParam<MarkupShape>("shape")` + `ChoiceParam("kind")` +
  `ColorParam("stroke")` + `BoolParam("fill")` + `FloatParam("width", 1f..24f)` +
  `FloatParam("fontSize", 12f..96f)`; `stage()` returns `Paint`. The style rows are schema-rendered —
  Markup writes no inspector code.

**Files:** `.../editor/tools/image/markup/MarkupShape.kt`, `markup/MarkupDraw.kt`,
`markup/MarkupHitTest.kt`, `markup/MarkupTool.kt`

## Phase 14: Markup — canvas interaction

- `MarkupOverlay.kt`: press-drag-release creates the active shape via `preview` then `commit`; click
  selects; drag moves; handles resize; the arrow's mid-handle bends it; `Delete` removes.
- `MarkupTextEditor.kt`: an inline `BasicTextField` at the shape's anchor, committed on blur or
  `Enter`, discarded when empty.
- `MarkupSelection.kt`: selection state and handle drawing, kept out of the overlay for line count.

**Files:** `.../editor/tools/image/markup/MarkupOverlay.kt`, `markup/MarkupTextEditor.kt`,
`markup/MarkupSelection.kt`

## Phase 15: Tests

- **Unit:** `ParamSet` typed get/with and default fallback; `EditorHistory` cap, redo-tail
  truncation, `preview`-does-not-push; `RasterFold` domain ordering (an op added last but ranked
  first applies first) and colour-matrix multiplication; `TransformMatrix`; `AdjustMatrix`
  neutrality; `FilterPresets` at intensity 0 = identity; `CropGeometry` aspect math;
  `MarkupHitTest`; `JpegExifSplice`; `EditableFormats.editPlan` over every extension in `MediaKind`.
- **Integration:** `EditBackup` creates the original exactly once across three saves; revert restores
  byte-identical content and drops the backup; `ImageSaveJob` writes atomically and leaves the source
  untouched when the encode fails; markup saved at native resolution lands on the expected pixel
  bounds; `ConvertJob` produces the target format, backs the source up and deletes it; an op from a
  disabled tool is skipped and reported rather than throwing.
- **UI:** the rail renders one button per enabled tool, grouped by domain, none for a disabled one;
  selecting a tool swaps the inspector; a `FloatParam` renders as a `DetentSlider` and a
  `ChoiceParam` as a `SegmentedControl` **without either tool contributing UI code** (the test that
  §1.3 actually holds); Undo/Redo enablement tracks the session; `Esc` while dirty shows the dialog;
  the Edit button opens `ConvertSheet` for a HEIC and edits directly for a PNG.

**Files:** `jvmTest/.../unit/ParamSetTest.kt`, `unit/EditorHistoryTest.kt`, `unit/RasterFoldTest.kt`,
`unit/TransformMatrixTest.kt`, `unit/AdjustMatrixTest.kt`, `unit/CropGeometryTest.kt`,
`unit/JpegExifSpliceTest.kt`, `unit/EditPlanTest.kt`, `integration/EditBackupTest.kt`,
`integration/ImageSaveJobTest.kt`, `integration/ConvertJobTest.kt`,
`integration/MarkupRasterTest.kt`, `ui/EditorShellUiTest.kt`, `ui/EditorToolRailUiTest.kt`,
`ui/ParamRowUiTest.kt`, `jvmTest/.../support/EditorHarness.kt`

## Phase 16: Housekeeping and follow-ons

- Orphan sweep on startup: drop `originals/` entries whose `sourcePath` no longer exists, and stale
  `.thumbnails` PNGs.
- Delete the backup when the source file is deleted from the grid.
- Update `roadmap.md` Phase 5 to "image half shipped, video half pending".
- Follow-ons, explicitly *not* in this plan: straighten / arbitrary-angle rotation, re-opening a
  saved document to adjust a previous crop (`edits/<key>.json` beside the backup), a tool that
  exports rather than overwrites ("Save a Copy").

**Files:** `core/data/WokaStorage.kt`, `app/App.kt`, `feature/folder/FolderFileOperations.kt`,
`ai/docs/roadmap.md`
