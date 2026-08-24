# ME-103 — Image surface

| | |
|---|---|
| **Order** | 17 |
| **Depends on** | ME-007, ME-101 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Draw a real photograph through the folded recipe, so the editor stops being a shell over a
coloured rectangle.

**Why now.** `ImageEditorEngine.Surface` is `TODO("ME-103")`, which is the single reason the `Edit`
entries cannot be switched on. Every ticket after this one — the save pipeline, the first tool, the
entry points — needs something on screen to be judged against.

## The proxy, and why it is not `StillPreview`

The canvas draws a copy capped at **2560 px on the long edge**, built in the editor's own scope and
**never put in `ImageBitmapCache`** (§9, 2026-08-18). Measured in ME-010: a 100 MP source is 384 MB of
native pixels against a 256 MB cache budget, so caching one evicts everything else and still does not
fit. The proxy costs 6–8 ms to build, which is cheaper than the eviction storm by a wide margin, and
keeping it out of the shared cache is also the whole of the guard rail §10 asked for.

`StillPreview` is the same shape at 400 px and is **not reused**: it writes to the disk thumbnail cache
*and* to `ImageBitmapCache` under `previewKey`, and both are exactly what the editor must not do. What
is worth lifting from it is the technique — a tiled HEIF is assembled *at* the target size rather than
assembled whole and shrunk, the difference between 0.5 MB and 138 MB — so the proxy asks
`FfmpegStillDecoder` for a capped decode where it can and scales down where it cannot.

Native is decoded **once, at save, by ME-104**, and is not held here at all.

## The coordinate problem, stated before it is coded

`RasterRecipe.matrix` is in **source** pixels. The proxy is source × *proxyScale*. The box
`EditorCanvasHost` hands the engine is source × *fitScale*. Three spaces, and getting the composition
order wrong produces a picture that looks right until the first crop.

The draw is therefore: scale by `fitScale`, apply `recipe.matrix` (source space), draw the proxy
stretched from proxy size back to source size. Written once, in one function, with the three scales
named — a second place computing any of them is the bug this paragraph exists to prevent.

## `EditorCanvasHost` measures the wrong thing after a crop

ME-101 sizes the surface from `engine.sourceSize`. That is correct until an operation changes the frame:
after a crop to a different aspect, the drawn picture is `recipe.outputSize` and the box around it is
still the source's shape, so the picture letterboxes inside a box of the wrong proportions.

`sourceSize` cannot simply become the recipe's output — it is what *operations address*, and a crop tool
dragging against a shrinking coordinate space would fight itself.

So `EditorEngine` gains **`fun drawnSize(document: EditDocument): IntSize`** — media-agnostic, no raster
type in the signature, defaulting to `sourceSize` — and the host measures with that. The video engine
will answer with its frame size and never think about it again.

## Steps

1. `engine/image/EditorProxy.kt` (jvmMain): `internal object EditorProxy { fun load(file: File,
   frameIndex: Int): ImageBitmap }` — capped decode through `FfmpegStillDecoder.frame(file, index,
   PROXY_MAX_PX)` when that decoder is the one routed to, a native decode plus a Skia downscale
   otherwise, and `PROXY_MAX_PX = 2560`. **No cache write of any kind.** A source already at or below
   the cap is returned unscaled rather than round-tripped through a surface.
2. `core/EditorEngine.kt`: add `fun drawnSize(document: EditDocument): IntSize = sourceSize`. Defaulted,
   so `FixtureEngine` and the future video engine need no change.
3. `shell/EditorCanvasHost.kt`: measure from `engine.drawnSize(document)` instead of `engine.sourceSize`,
   and key the remembered geometry on it. `EditorCanvasGeometry.sourceSize` keeps its name and meaning —
   it is the size of what is drawn — so nothing else moves.
4. `engine/image/RasterSurface.kt`: `@Composable internal fun RasterSurface(proxy: ImageBitmap,
   sourceSize: IntSize, recipe: RasterRecipe, modifier: Modifier)`. One `Canvas`, one
   `withTransform`, `drawImage` with `recipe.colorFilter` applied at the draw call — never as a
   `ColorFilter` wrapped around a layer — then `recipe.paints` in source space. **No bitmap is allocated
   per frame**, which is §3.2's whole claim and is what this file has to keep true.
5. `engine/image/ImageEditorEngine.kt`: `drawnSize` returns `recipe(document).outputSize`; `Surface`
   remembers the proxy on `file` + `frameIndex`, folds the document, and composes `RasterSurface`. The
   fold stays behind `recipe()`, which is already `internal` and already absent from `EditorEngine`.

## Files

`jvmMain feature/editor/engine/image/EditorProxy.kt`, `engine/image/RasterSurface.kt` (new),
`engine/image/ImageEditorEngine.kt`, `commonMain feature/editor/core/EditorEngine.kt`,
`jvmMain feature/editor/shell/EditorCanvasHost.kt` (edit),
`jvmTest integration/EditorProxyTest.kt`, `jvmTest unit/EditorCanvasGeometryTest.kt` (new)

## Verification

`integration/EditorProxyTest.kt`, against the corpus:

- a 640×480 source comes back **unscaled** — under the cap, so no surface round trip;
- a source over the cap comes back with its long edge at 2560 and its aspect ratio intact;
- **the proxy is absent from `ImageBitmapCache` afterwards** — the guard rail, asserted rather than
  assumed, by comparing `retainedBytes` before and after;
- a tiled HEIF (`burst.heic`) builds without decoding the whole picture first.

`unit/EditorCanvasGeometryTest.kt` — fit scale for a wide source in a tall box and the reverse;
`canvasToSource` composed with `sourceToCanvas` is the identity within a float epsilon, at three
different canvas sizes; a zero canvas yields scale 1 rather than a division by zero.

## Done when

- opening the editor on a photograph draws that photograph, at `Fit`, letterboxed correctly;
- `EditorEngine` still names no raster type — `drawnSize` takes a document and returns an `IntSize`;
- `RasterSurface` allocates no bitmap inside its draw scope — read the diff;
- `./gradlew.bat :shared:build` green, with the tests above passing.

## Result — 2026-08-18

`:shared:build` green, **598 tests, 0 failures** (588 before, +10). Largest new file 72 lines.
`EditorEngine` still names no raster type — the only `Raster` in it is the comment saying why.

### The corpus could not test the thing this ticket is about

Every still in it is 640×480 or smaller — deliberately, it is 3.3 MB of fixtures — so **the downscale
branch had no file that reaches it**. A proxy test against `still.png` alone would pass with the
capping code deleted.

So `EditorProxyTest` synthesises its oversized sources: a 4000×1000 and a 1000×4000 PNG written to a
temp directory, asserting the long edge lands on 2560 and the aspect survives in both orientations.
Nothing is committed to the corpus for it — a generated solid colour proves the arithmetic, and 16 MB
of fixture would prove the same thing more slowly.

### The guard rail is asserted, not promised

`the proxy never lands in the shared bitmap cache` compares `ImageBitmapCache.retainedBytes` before and
after, and demands they are **equal** rather than close. That is the whole of §10's "keep a huge source
out of the shared cache": it is implemented by this object not writing, and a guarantee of absence
holds only while something checks the absence.

### Found while building: the proxy decodes on the composition thread

`Surface` does `remember(file, frameIndex) { EditorProxy.load(...) }`, which runs **synchronously
during composition**. ME-010 measured a 100 MP source at 667 ms to decode; a 24 MP one is nearer 150.
So opening the editor on a large photograph freezes the UI for that long.

Not fixed here, and deliberately not papered over with a `LaunchedEffect` invented on the spot — the
placeholder, the ordering against the frozen viewer underneath, and what `Cancel` does mid-decode are a
design, not a one-liner. Recorded on **ME-123**, which is already the ticket about large sources.

### Two things the implementation had to get right

- **`RasterSurface` names all three scales in one function.** `recipe.matrix` is in source pixels, the
  proxy is source × proxyScale, the box is `outputSize` × fitScale. The failure mode of getting this
  wrong is not a crash: it is a picture that looks perfect **until the first crop**, because until then
  every matrix is the identity and the errors cancel.
- **Markup draws outside the matrix.** `recipe.paints` are applied after the transform block, not
  inside it, because a crop must *clip* an arrow rather than move it — which is `ToolDomain`'s
  application order showing up in the draw call.

`EditorCanvasGeometryTest` pins the claim ME-101 made: `canvasToSource` composed with `sourceToCanvas`
round-trips three points to within 0.01 px at three canvas/source pairs, including a 137×991 canvas
against a 4000×1000 source where a hand-written inverse would drift visibly.
