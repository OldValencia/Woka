# ME-177 — One tone map, at the decode

| | |
|---|---|
| **Order** | before ME-119, which mirrors a mode the editor cannot honour today |
| **Depends on** | ME-120 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Tone map an HDR still **once, where it is decoded**, so the viewer and the editor draw the
same pixels and neither owns a colour decision.

**Why now.** ME-120 made the viewer's HDR stills correct and the author checked by hand: the same
file is still dull in the editor. `feature/editor/` never reads `StillColorInfo` at all, and
`RasterImageDraw.kt:46` draws the decoded bitmap through `drawImage`, so a PQ file's codes are
painted as sRGB.

Nothing recorded this, because §6.1 opens by asserting the opposite — *"Woka already tone-maps HDR
stills at draw time"* — which is true of the viewer only. Read as settled, it meant no ticket was
written for the editor half, and **ME-119 presupposes an HDR path there that does not exist**.

## Why at the decode and not in the editor's fold

The obvious repair is to give the editor its own tone map, appended to `recipe.shaders`. It was
rejected, and the reason is worth keeping:

- The viewer applies edits **before** mapping — `ImageLoader.load` folds
  `AppliedStillEdits.apply` over the raw decode and the painter maps the result. Matching that in the
  editor would compute every Adjust value against PQ codes in a second place.
- It would still not agree. The editor draws `paints` after `shaders`; the viewer bakes paints into
  the bitmap and maps the lot. Markup would come out a different colour in the two surfaces, and
  closing that gap needs an offscreen layer — which §3.2 spends its one performance claim ruling out.

Mapping at the decode makes an HDR still an **ordinary sRGB bitmap** before anything sees it. Edits
and markup then act on displayable colour, and the two surfaces cannot disagree because neither has a
tone map left to disagree with.

**Not a decoder decorator.** Wrapping what `stillDecoderFor` returns looked cleanest and is wrong:
`EditorProxy:49` and `StillPreview:83` branch on `is FfmpegStillDecoder` to assemble a tiled HEIF *at*
its target size, `StillColorProbe:34` compares by identity to decide how deep to probe — it is the
function that answers *"is this HDR"* in the first place — and `StillDecoderRoutingTest` asserts the
identity outright. A wrapper breaks all four, and the first one silently: a 100 MP HEIF would decode
whole again, which is precisely ME-168.

So the seam is a function every decode site calls on what it just decoded.

## Steps
1. `HdrToneMap.kt` beside the shader: `toneMapped(bitmap, colour): ImageBitmap` — a raster surface
   drawn through `HdrImagePainter` at 1:1, white point from `measuredWhitePoint`. Reusing the painter
   rather than the effect directly keeps one copy of the uniform packing.
2. `StillForDisplay.kt` in `feature/viewer/image/`: `displayable(file, decoded): ImageBitmap`, which
   maps when `readStillColor(file).needsColorManagement` and **returns the same instance otherwise** —
   the contract `StillEdits.apply` already sets, so an SDR picture never pays a surface to learn it
   had nothing to do.
3. The six decode sites call it: `ImageLoader.load`, `EditorProxy.load`, `StillPreview.decodeSmall`,
   `EditorNativeFrame`, `EditorAnimation`'s forward walk, `FrameExport`. Each is one line, and each
   maps the bitmap it actually holds — after the proxy and preview caps, not before, so the cost lands
   on the small copy.
4. `rememberFramePainter`'s HDR branch narrows to `moving`: a still now arrives mapped and would be
   mapped **twice**. ME-120 added that flag for the white point and this is its second use.
5. `StillPreview`'s disk entry is keyed on path, size and mtime, none of which this change touches, so
   an HDR file's existing thumbnail would survive it. The key gains a version segment.
6. §6.1's opening paragraph is corrected — it is the sentence that hid this.

## Files
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/video/HdrToneMap.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/image/StillForDisplay.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/image/ImageLoader.kt` (edit, 35 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/image/StillPreview.kt` (edit, 110 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/ViewerFramePainter.kt` (edit, 82 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/EditorProxy.kt` (edit, 79 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/EditorNativeFrame.kt` (edit, 78 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/EditorAnimation.kt` (edit, 76 lines)
`shared/src/jvmMain/kotlin/com/woka/core/utils/FrameExport.jvm.kt` (edit, 55 lines)

Nine, against the ~8 `tickets.md` §4 sets. Six of them are the one-line call in step 3; splitting the
seam from its callers would leave a tone map nothing invokes.

## Done when
- `jvmTest/unit/`: `displayable` returns a **different** bitmap for `hdr_real_pq.avif` and the **same
  instance** for an SDR file;
- `jvmTest/integration/`: `ImageLoader.load` and `EditorProxy.load` agree on the same HDR file —
  the claim the whole ticket exists to make, and the one that was false;
- `jvmTest/integration/`: `StillDecoderRoutingTest` still passes unchanged, proving the routing
  identities this ticket chose not to disturb;
- `grep` finds no `HdrImagePainter` reachable from a still path;
- confirmed by hand: `5-REAL-hdr-frame.avif` is the same brightness in the viewer and the editor.
