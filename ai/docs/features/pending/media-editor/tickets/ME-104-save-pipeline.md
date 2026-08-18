# ME-104 — Save pipeline

> **Superseded 2026-08-19 by ME-132.** Shipped as written and then overtaken: §5 no longer writes
> pixels for a format that can carry a recipe, so `SaveTarget`, the copy naming and the overwrite
> consent this ticket built are deleted. What it built that **survives** is the render-encode-splice
> path itself — `NativeRender`, `ExifSplice`, `AtomicWrite`, `SourceStamp` — which becomes bucket B's
> in-place re-encode and is reused unchanged by ME-133's read-side fold. Kept for the record; the id is
> not reused.

| | |
|---|---|
| **Order** | 18 |
| **Depends on** | ME-009, ME-103 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Turn a document into a file on disk — render at native scale, encode, keep the metadata, and
write without ever putting the source at risk.

**Why now.** `ImageEditorEngine.save` is `TODO("ME-104")`. Nothing above it can be built or judged: the
`Done` menu has nothing to call, the banner has no outcome to report, and the entry points cannot ship a
`Done` that does nothing.

## Split from the backlog block, deliberately

The block described eleven things. This ticket is **the write**: render, encode, metadata, the stale
guard, the atomic move, cache invalidation. Everything the user *sees* around it — the `Done` menu with
the generated name, the overwrite confirmation and its session consent, the banner, the folder refresh —
is **ME-130**, written next.

The seam is real rather than arithmetic: everything here is testable end to end with no window open, and
nothing here needs a decision about what a dialog says.

## What is already built and must not be rewritten

`AtomicWrite` (retry, no fallback, `Result<Unit>`), `SourceStamp` (length + mtime), `copyName`
(`-edited`, then `-2`), `OverwriteConsent`, `SaveTarget`, `EditSaveResult`. ME-009 built all of them;
this ticket is the first caller of every one.

## The four things ME-010 measured, which this ticket has to honour

1. **The decode path already applies EXIF `Orientation`** — a 640×480 source tagged `6` arrives
   480×640. So the pixels are already rotated, and the save must write the tag as **1**. Splicing the
   source `APP1` back unchanged double-rotates on the next open.
2. **A Skia JPEG re-encode drops `APP1` entirely.** Preservation is never automatic; the segment
   survives only because this ticket splices it in.
3. **PNG round-trips losslessly** (Δ0), JPEG and WebP do not. Nothing here should pretend otherwise.
4. **A 100 MP source is 384 MB native.** It is decoded here, once, and released — never cached, never
   held past the write (§9, and the reason ME-103's proxy exists at all).

## Steps

1. `core/ui/image/ImageBitmapCache.kt`: add `fun remove(key: String)`, decrementing `retainedBytes` by
   the removed entry's size. The cache has had no removal API at all (recorded 2026-08-17), which is
   why an overwrite could not invalidate anything.
2. `save/SaveFormat.kt`: `internal enum class SaveFormat(val extension: String, val skia:
   EncodedImageFormat, val quality: Int)` and `fun formatFor(file: File): SaveFormat?`.

   **Which of the eleven formats `MediaKind` lists can be saved, and what happens to the rest.** Skia
   encodes three, so three save in place and eight do not:

   | Source | Saved as | Path |
   |---|---|---|
   | `jpg`, `jpeg` | JPEG **q95** | in place, `APP1` spliced back with `Orientation = 1` |
   | `png` | PNG, lossless | in place, Δ0 round trip (ME-010) |
   | `webp` | WebP q90 | in place |
   | `bmp`, `gif`, `tif`, `tiff` | — | `formatFor` returns **null** → ME-108's conversion sheet |
   | `heic`, `heif` | — | null. `libx265` **cannot open a HEIF output at all** (ME-010), so this is not a Skia gap that ffmpeg could close later — HEIF output is impossible, full stop |
   | `avif` | — | null here. AVIF is the one format with a second road: ffmpeg's `libaom-av1` writes PQ AVIF correctly (ME-010), which is what ME-118's `Keep` uses. Not this ticket's job |

   **This ticket does not convert anything.** `formatFor` returning null is a refusal, and
   `ImageSaveJob` turns it into `Failed`. ME-108 is what turns that refusal into a sheet offering a
   target — which is why the Edit button is never dead even though eight of eleven formats cannot be
   written back.
3. `save/NativeRender.kt`: `internal fun renderAtNativeScale(source: ImageBitmap, sourceSize: IntSize,
   recipe: RasterRecipe): Image` — a Skia surface of `recipe.outputSize`, the same matrix, colour
   filter and paint list ME-103's `RasterSurface` draws, at **scale 1.0**. Uses `CanvasDrawScope` over
   the surface's canvas so `RasterStage.Paint`'s `DrawScope.() -> Unit` runs unchanged — one draw
   routine for the screen and for the file, which is what makes "markup rasterised at source
   resolution" fall out rather than be implemented twice.
4. `save/ExifSplice.kt`: `internal fun spliceExif(encoded: ByteArray, source: File): ByteArray` — for a
   JPEG target only, copy the source's `APP1` segment in after `SOI` with the `Orientation` value
   **forced to 1**, and return `encoded` untouched when the source carries no `APP1`, when either file
   is not a JPEG, or when the segment cannot be parsed. Never throws: losing metadata is a smaller harm
   than losing the save, and §6 says preservation is best-effort with a stated fallback.
5. `save/ImageSaveJob.kt`: the orchestration, in one order that is written down once — **stale check
   first**, then decode native, render, encode, splice, resolve the target path, atomic write, and on
   `Overwrite` only, invalidate. Returns `EditSaveResult`. Every failure returns `Failed` with a reason
   phrased about the user's file rather than about an exception, and **no failure path writes to the
   source**.
6. `engine/image/ImageEditorEngine.kt`: `save` delegates to `ImageSaveJob`, on `Dispatchers.IO`. The
   stamp is taken when the engine is constructed, not when the save starts — the window this guard
   exists to cover is *the editing session*, and a stamp read at save time would compare the file to
   itself.
7. Invalidation on overwrite, and only there: `ImageBitmapCache.remove(key(path, 0))` and
   `remove(previewKey(path))` — the exact list ME-010 measured, no more. The `.thumbnails` poster is
   keyed on length + mtime so it orphans rather than going stale, and a copy is a path nothing has ever
   seen.

## Files

`jvmMain feature/editor/save/SaveFormat.kt`, `save/NativeRender.kt`, `save/ExifSplice.kt`,
`save/ImageSaveJob.kt` (new), `engine/image/ImageEditorEngine.kt`,
`core/ui/image/ImageBitmapCache.kt` (edit), `jvmTest integration/ImageSaveJobTest.kt`,
`jvmTest unit/SaveFormatTest.kt`, `jvmTest integration/ExifSpliceTest.kt` (new)

## Verification

`integration/ImageSaveJobTest.kt` — the behaviours, not the plumbing:

- an empty document returns `NothingToDo` and **writes no file at all**;
- a copy lands beside the source at the generated name, and the source's bytes are byte-identical
  afterwards;
- a document with a colour operation produces a file whose pixels actually differ from the source —
  otherwise the recipe was never applied and every other assertion here would still pass;
- **a source modified on disk after the engine opened it returns `Failed` and leaves both files
  untouched** — the stale guard, on the path where it matters;
- an unwritable target returns `Failed` and leaves the source intact;
- `Overwrite` removes exactly the two cache entries and no others — asserted by putting a third,
  unrelated entry in first and checking it survives.

`integration/ExifSpliceTest.kt` — `exif_rot90.jpg` (`Orientation=6`) saved as a copy comes back with
`Orientation=1` and its other IFD0 entries intact; a PNG target is returned unspliced; a source with no
`APP1` produces a valid JPEG rather than a corrupt one.

`unit/SaveFormatTest.kt` — every extension `MediaKind` lists maps to a format or to null, and the three
formats carry the qualities §9 settled.

## Done when

- `ImageEditorEngine.save` has no `TODO` and returns a real `EditSaveResult`;
- no test writes to a path outside its own temp directory;
- the native bitmap is not in `ImageBitmapCache` after a save — same `retainedBytes` assertion ME-103
  uses;
- `./gradlew.bat :shared:build` green, with the tests above passing.

## Result — 2026-08-18

`:shared:build` green, **611 tests, 0 failures** (598 before). `ImageEditorEngine.save` has no `TODO`.
Largest new file 116 lines.

### The test found a bug that was never about the editor

`ExifSpliceTest`'s dimension assertion failed: a rotated JPEG saved as 640×480 when it should have been
480×640. Tracing it turned up a disagreement that predates this ticket entirely.

**`Codec.width/height` reports the raster as stored; `Image.makeFromEncoded` applies `Orientation`.**
`SkiaFrameDecoder` uses the first for `nativeSize` and the second for `frame(0)`, so for any portrait
photograph the two answers differed — 640×480 from one, 480×640 from the other. The file's own KDoc
already documented that `makeFromEncoded` applies the tag; `nativeSize` had simply never been reconciled
with it.

The editor's symptom was the visible one: `save` drew the oriented pixels into a surface sized from the
stored dimensions, **stretching every rotated photograph into a landscape**. But the defect is the
viewer's too — `nativeSize` is what "Actual size" and every zoom percentage are computed against, so
those were wrong for rotated photos as well.

Fixed at the source rather than worked around here: `nativeSize` now asks `codec.encodedOrigin` and
swaps the axes for the four quarter-turn origins. The four that only mirror are deliberately excluded —
"anything but `TOP_LEFT`" would have been wrong for `TOP_RIGHT`. Every viewer zoom, scale and navigation
test still passes, and `ImageLoaderTest` gained the guard that should have existed: **`nativeSize` must
agree with the bitmap it describes**.

### Two things about the splice

`spliceExif` rewrites `Orientation` to 1 **in place**, one 16-bit field, leaving every other entry and
every offset untouched. Rebuilding the IFD would mean rebuilding the offsets inside it, which is a
different and much larger risk for no gain — §6 wants the metadata carried across, not reorganised.

It **never throws**. A malformed segment, a non-JPEG source, an absent `APP1` all return the encoded
bytes unchanged. Losing metadata is a far smaller harm than losing the save, and §6 states that
fallback openly rather than discovering it.

### One thing corrected while writing it

`copyNameFor` first used the *format's* extension, which turns `photo.jpeg` into `photo-edited.jpg`.
Nothing in this ticket converts anything: a `.jpeg` re-encodes as JPEG and stays a `.jpeg`. Extensions
change in ME-108, and they change because the user agreed to it.

### What the tests are actually for

`the recipe actually reaches the pixels` compares the saved copy against the source pixel by pixel.
Without it the whole file would pass against a save that copied the source and never applied the
document — every other assertion here is about paths, names and bytes surviving, and all of them would
still be true.
