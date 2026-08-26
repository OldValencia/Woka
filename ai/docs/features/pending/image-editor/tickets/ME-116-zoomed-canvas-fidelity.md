# ME-116 — Zoomed canvas fidelity

| | |
|---|---|
| **Order** | 48 — after ME-115 |
| **Depends on** | ME-103 (the proxy and `RasterSurface`), ME-109 (the zoom the canvas already has) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Draw the **native** bitmap on the editing canvas while it is zoomed past the proxy's own
resolution, and fall back to the proxy the moment it is not.

**Why now — and what this ticket no longer is.** The backlog block opens with "introduces canvas
zoom, which v1 deliberately ships without". That half is **done**: `EditorCanvasGeometry`,
`EditorCanvasHost` and `EditorCanvasGestures` landed with the crop work and were revised on
2026-08-21. Ctrl+wheel zooms about the cursor at the viewer's own `ZOOM_STEP_RATIO`, drag pans against
a clamp, the view resets to Fit on tool change, the floor is the viewer's `zoomBounds` and the ceiling
is 1000 %, and `PublishCanvasZoom` hands the whole thing to the top bar's pill. Checked against the
tree on 2026-08-26.

What is left is the reason the block waited, and it is untouched: `RasterSurface` draws
`EditorProxy`'s ≤2560 px copy at **every** zoom. Past 100 % the picture is visibly soft, and markup
precision — the thing zoom exists for — is being done against a blur. Both `EditorCanvasHost` and
`EditorCanvasGeometry` carry a comment naming this ticket as the fix.

## The one thing that is not obvious

**No region decoder exists, and none is needed.** `StillDecoder` answers `frameSet`, `nativeSize` and
`frame` — a whole picture, never a rectangle of one. Skia's `Codec` cannot subset, so "draw the native
bitmap for the visible region" cannot mean "decode that region".

It does not have to. `RasterSurface` already draws with `srcSize = the bitmap's own` and `dstSize =
sourceSize`, so **the bitmap it is handed is interchangeable** — that is the whole point of the
comment in it about the same recipe running against a 2560 px copy and against the native one with no
branch between them. Hand it the native bitmap and the picture sharpens; the canvas is `clipToBounds`,
so Skia rasterises only the visible destination pixels regardless of how large the source is. **The
work is deciding when to hold that bitmap, not how to draw it.**

**The decision has to arrive from above.** `EditorEngine.Surface(document, modifier)` carries no zoom
— by design (§3.3), and the same `ImageCanvas` draws the filter strip's thumbnails, which must never
ask for a native decode. So the host publishes the drawn scale through a `CompositionLocal` defaulting
to `0f`, meaning "not the editing canvas". `EditorBottomPanel` is a **sibling** of
`EditorCanvasHost` in `EditorScreen`, so the thumbnails read the default and nothing has to exclude
them by name.

**Two refusals, both measured, both permanent.** A native hold is `w × h × 4` bytes: 96 MB for 24 MP,
**384 MB** for the 100 MP case ME-010 measured. And an animation is already held frame by frame at
proxy size under a 96 MB budget (`EditorAnimation`); a native-resolution animation is not a thing this
canvas will ever hold. So: no native draw above `NATIVE_HOLD_MAX_PX` (40 MP, ~160 MB) or above an
8192 px edge, and none at all while an animation is playing. Refusing is invisible — the picture stays
as sharp as it is today.

## Steps

1. `core/EditorCanvasScale.kt` (new, `commonMain`): `val LocalCanvasScale = compositionLocalOf { 0f }`
   — canvas pixels per source pixel, or 0 for "not on the editing canvas". In `core/` rather than
   `shell/` because it is media-agnostic and the video engine will read it for the same reason.
2. `shell/EditorCanvasHost.kt` (edit): wrap the drawn `Box` in
   `CompositionLocalProvider(LocalCanvasScale provides geometry.scale)`. One line plus the import;
   nothing else in the host changes and no geometry is recomputed.
3. `engine/image/EditorNativeFrame.kt` (new): `@Composable internal fun rememberNativeFrame(file:
   File, frameIndex: Int, sourceSize: IntSize, wanted: Boolean): ImageBitmap?`.
   - `produceState(null, file, frameIndex, wanted)`: when `wanted` first turns true and the source
     passes the two budgets, decode on `ImageBitmapCache.ioDispatcher` with
     `stillDecoderFor(file).frame(file, frameIndex)` and publish it; otherwise stay null.
   - **Decoded once and kept** for as long as the composition lives, so zooming out and in again does
     not re-read the file — and dropped with it, never entering `ImageBitmapCache`. That is
     `EditorProxy`'s rule and the guard rail §10 asks for, and ME-123 is where the cache side of it
     is finished.
   - `NATIVE_HOLD_MAX_PX = 40_000_000` and `NATIVE_MAX_EDGE = 8192` live here with the arithmetic in
     the doc comment.
   - Never throws: a decode that fails answers null and the canvas goes on drawing the proxy.
4. `engine/image/ImageEditorCanvas.kt` (edit): read `LocalCanvasScale`; the proxy's own scale is
   `proxy.width.toFloat() / sourceSize.width`. Ask for the native frame when
   `canvasScale > proxyScale * SHARPEN_MARGIN` (1.05, so a canvas sitting exactly at the proxy's
   resolution does not flicker between the two on a one-pixel resize) **and** the frame source is not
   animating. Pass whichever bitmap is in hand to `RasterSurface`.
5. `engine/image/EditorAnimatedProxy.kt` (edit): `rememberEditorFrame` returns the still proxy or an
   animation frame and the caller cannot tell which. Return that fact — an `EditorFrame(bitmap,
   isAnimating)` value, or a second composable answering it — because step 4 must not swap a native
   still in underneath a playing GIF.
6. `engine/image/RasterSurface.kt` (edit): rename the `proxy` parameter to `source` and say in the doc
   comment that it is the proxy at Fit and the native bitmap while zoomed. **No logic changes** — if
   any is needed, step 4 put the wrong thing in the parameter.

## Files

`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorCanvasScale.kt` (new) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/EditorNativeFrame.kt` (new) ·
`.../engine/image/ImageEditorCanvas.kt` (edit) · `.../engine/image/EditorAnimatedProxy.kt` (edit) ·
`.../engine/image/RasterSurface.kt` (edit) · `.../shell/EditorCanvasHost.kt` (edit)

## Done when

- `jvmTest/unit/EditorNativeFrameTest.kt` (new): the two budgets — a 40 MP source is allowed, a
  100 MP one is refused, a 12000 × 900 panorama is refused on its edge rather than its area — asserted
  against the pure predicate, without decoding anything.
- `jvmTest/ui/EditorZoomFidelityUiTest.kt` (new): composed over a fixture image whose native
  resolution is above the proxy cap, the canvas at Fit draws the proxy and the canvas zoomed past it
  draws the native bitmap — asserted on which bitmap `RasterSurface` was handed, not on pixels.
- Same file: a filter strip thumbnail composed outside the canvas host reads `LocalCanvasScale` as 0
  and **never** asks for a native frame, however far the canvas behind it is zoomed. This is the
  regression that matters: eight thumbnails each holding a native decode is the eviction storm the
  proxy exists to avoid.
- `jvmTest/integration/ImageCacheEvictionTest.kt` (edit): zooming the editor to 800 % on a large
  photograph leaves `ImageBitmapCache` no larger than it started.
- By hand: open a 24 MP photograph, zoom to 400 %, and confirm the picture sharpens within a moment
  of the wheel stopping rather than staying soft; draw an arrow at that zoom and confirm it lands
  where the cursor is; zoom back to Fit and confirm nothing flickers. Repeat on an animated GIF and
  confirm it keeps playing, soft, without stalling.
- `./gradlew.bat :shared:build` green.
