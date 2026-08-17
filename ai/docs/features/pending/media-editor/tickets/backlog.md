# Media Editor — Backlog

Everything after the foundation (ME-001…ME-014, one file each in this directory). Each block says what
it is for and what shape it takes, and **names no files** — those are written when the block is
promoted into its own ticket file, against the tree as it is then
(`ai/docs/conventions/tickets.md`).

Design behind all of it: [the feature doc](../media-editor.md).

Order inside a track is dependency order. Between tracks, only the marked dependencies matter.

---

## 100 — Image editor

### ME-104 — Save pipeline
Replay the recipe onto a Skia surface at native scale, encode (PNG / JPEG **q95** / WebP q90), splice EXIF
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
The Edit button stops being a stub, and the grid's `Edit` entry — **already present since ME-101, sitting
beside Rename/Copy/Delete with `enabled = false`** — becomes live. Replacing that literal with
`engineFactory.create(target) != null` is the whole of the editability decision on that side. Both
routes then call one entry function with a path and a frame index, both greyed with a reason when the
media is not editable, neither deciding editability on its own. Plus: the
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
`Geometry`, `Single`, `Canvas`. A rect in source coordinates plus the aspect presets settled in §9 —
Free, Original, 1:1, 4:3, 3:2, 16:9 as a chip row (six exceeds `SegmentedControl`), and an
`ActionParam` that flips the ratio rather than doubling the list with portrait variants; an
overlay with dimming, thirds grid, eight handles, hover cursors and a minimum size. Drags preview,
pointer-up commits. The aspect and hit-test math is a separate pure file, unit-tested.
*Depends on ME-105.*

### ME-110 — Resize tool
`Output`, `Single`, `Continuous`, pure data: two `IntParam` (width, height), a `BoolParam` aspect lock,
**100/75/50/25 % as a `SegmentedControl` and a long-edge chip row — 4K, 2560, 1920, 1280** (§9), and a
footnote row showing source → target dimensions. The footnote is a shell addition every later tool
inherits.
*Depends on ME-105.*

### ME-111 — Adjustments tool
`Color`, `Single`, `Continuous`, pure data: **Exposure, Brightness, Contrast, Saturation, Warmth,
Tint** (§9) composing into **one** `ColorMatrix`, neutral at all-zero, plus an auto action. No Compose
code in the tool at all. Highlights/Shadows are deliberately absent: they need a per-luminance curve,
which is a fourth fold primitive against §3.2's three.
*Depends on ME-105.*

### ME-112 — Filter presets
`Color`, `Single`, `Continuous`, pure data: **Original, Vivid, Dramatic, Warm, Cool, Mono, Noir,
Silvertone** (§9) as colour-matrix constants, an intensity that lerps toward identity, and the
thumbnail strip rendered by the schema's preview-choice row — one texture, one filter per thumbnail,
no extra decode.

**Also carries the `PreviewChoiceParam` renderer itself**, deferred here from ME-102: it draws each
option through that option's own `stage()`, so it needs filters to preview and cannot be tested without
them.
*Depends on ME-111.*

### ME-113 — Markup engine
`Annotate`, `Repeating`, `Canvas`. The shape hierarchy in source coordinates for **arrow, rectangle,
ellipse and freehand** — text is ME-122 — one draw routine shared by the overlay, the recipe and the
save path, which is what makes native-resolution rasterisation automatic, and hit-testing with handle
positions. Style parameters come from the schema.
*Depends on ME-105.*

### ME-114 — Markup interaction
The overlay for the four geometric shapes: draw, select, move, resize, bend an arrow, Delete removes.
Text and its inline editing are ME-122. Selection state lives outside the history — selecting is not
an edit.
*Depends on ME-113.*

### ME-115 — Editor settings panel
Settings → Editor, with one row per manifest: icon, title, summary, switch. No per-tool settings code
now or ever; a tool disabled here disappears from the rail, the shortcut table and the fold, and its
ops are reported as skipped rather than dropped.
*Depends on ME-106.*

### ME-116 — Zoomed canvas fidelity
**Introduces canvas zoom, which v1 deliberately ships without** (§9, 2026-08-18): wheel 100–800 % about
the cursor reusing `ScaleMath`, middle-drag or Space+drag panning clamped by `PanBounds`, reset on tool
change — *and* the reason it waited, which is that above 100 % the proxy is not enough for markup
precision, so the canvas draws the native bitmap for the visible region while zoomed and drops back to
the proxy when it is not. Shipping the gesture without the native draw would look broken.
*Depends on ME-114.*

### ME-117 — Housekeeping
`roadmap.md` Phase 5 updated to "image half shipped, video half pending"; the editor exercised by
hand once and the result dated in this ticket; the `-edited` naming checked against a folder
that already holds a dozen of them.
*Depends on ME-108, ME-115.*

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

### ME-122 — Markup text
The fifth markup shape, split from ME-113/114 because it is the only one with focus, a caret and an
IME (§9, 2026-08-18). A text shape in source coordinates carrying its string, size and colour; inline
editing on the canvas committed on blur or Enter and **discarded when empty**, so an accidental
double-click leaves no invisible empty shape in the document. Rasterised at source resolution by the
same draw routine as every other shape.
*Depends on ME-114.*

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
*Depends on ME-104.*

### ME-124 — Editor and picture-in-picture
Undefined today, and the reason it matters is concrete: `GlobalShortcutState.onPreviewKey` is a
**single mutable slot**, so whoever registers last wins the keyboard. With a PiP window open and the
editor mounted, that is decided by composition order rather than by intent.

Decide and enforce one rule — most likely "the editor takes the keyboard and PiP keeps playing without
shortcuts", the alternative being to refuse opening the editor while PiP is up — and make the handler
hand-off explicit rather than incidental. ME-106 saves and restores the viewer's handler; this ticket
is what happens when there are three claimants, not two.
*Depends on ME-106.*

### ME-125 — Save cancellation and job lifetime
What happens to an in-flight save when the app closes. No file is ever half-written — the atomic move
is the last step — so this is about the **job and the banner**, not corruption: which scope the save
runs in, whether closing the window waits for it, and what the banner says if the process goes away
mid-write. Today the answer is "whatever the coroutine scope happens to do", which is not an answer.
*Depends on ME-104.*

### ME-126 — Frame sets after an edit
Editing frame N of a burst produces a **new single-frame file** beside the container (ME-108's
conversion). What the carousel then shows is unstated: the container it came from, the new file, both,
and where the selection lands. A small ticket, but the kind that reads as a bug when it is left to
fall out of the listing order.
*Depends on ME-108.*

### ME-127 — Configurable output quality
JPEG quality is fixed at **q95** in MVP (§9, 2026-08-18). This is the field that makes it adjustable:
a `WokaSettings` entry, a row in Settings → Editor, and the save pipeline reading it instead of the
constant. Parked deliberately — one more field and one more row against a §5 that is already dense,
for a number most users never touch.
*Depends on ME-115.*

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

### ME-129 — Rename `IosSwitch`
The components are Woka's own, inspired by that design language rather than branded with it
(2026-08-18). `IosSwitch` is the only file carrying a vendor name; the type added in ME-102 is
`SwitchColors` with no prefix, so this is the composable and its call sites in Settings and elsewhere.
Deliberately not folded into ME-102 — a rename touching every call site is not the same change as
making one control readable on a scrim.
*Depends on nothing.*

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
