# ME-138 — The GIF carrier: Application Extension

| | |
|---|---|
| **Order** | **done 2026-08-21** — built and confirmed by hand; the deferral ended when the clock landed |
| **Depends on** | ME-131, ME-108, and [animated stills](../../../implemented/animated-stills.md) |
| **Feature** | [Image Editor](../image-editor.md) |

> **Deferred 2026-08-20, unblocked 2026-08-21.** The study below is what deferred it: Woka did not
> animate a GIF anywhere, so an edit would have applied to every frame the app drew and the app drew
> one. [Animated stills](../../../implemented/animated-stills.md) shipped that day; the block was claimed the next.
> What the study said about *scope* still holds and is why this ticket is small — one recipe covers
> the file and every reader folds it, so nothing here is per-frame.

**Goal.** Give GIF a recipe block, so an animation can be turned and flipped like every other
picture.

**Why now.** It was the simplest of the three writers left — a block the format was built to carry —
and it is the one people have most of: a folder of GIFs was a folder where `Edit` was greyed out for
no reason the user could see.

## What "editing an animated GIF" actually means here

Asked before writing a line, and worth writing down because the answer is not obvious.
**Measured in the tree on 2026-08-20, when Woka animated a GIF nowhere.**

> **Read this as history, and read the answer as still true.** The viewer plays a GIF since
> 2026-08-20, so the first bullet below is out of date. What it was establishing is not: an edit
> applies to **every frame the app draws**, because one recipe covers the file and every reader
> folds it. The clock made that visible rather than changing it.

**One edit, all the frames, and that is deliberate** (ME-126). `RecipeStillEdits.apply(file,
frameIndex, decoded)` is handed the frame index and does not read it — the file's one document is
folded over whatever bitmap a reader decoded (ME-133). The viewer's clock, the strip's thumbnails,
the grid tile and `Export Frames` therefore all draw the same turn.

The day a format wants a *different* edit per frame — HEIC, where the frames are separate
photographs rather than one moving picture — the seam is that ignored parameter and a per-frame key
in `EditDocument`. It is not a carrier's business, and no carrier has to change for it.

- **The viewer** shows it as a *frame set*: `SkiaFrameDecoder` says so in its own doc — an animated
  GIF "has always sat still in the viewer" — and `mayHoldFrames` puts `gif` in the frame-strip family,
  so what the user sees is eight still pictures and the strip down the left edge to step through them.
- **The grid** draws it through Coil, and only `coil-compose` is on the classpath: no `coil-gif`, no
  animated decoder registered anywhere. The tile is frame 0.

**Nothing regresses.** And one limit, stated rather than discovered later: outside Woka the file is
untouched (§5.3), so it still plays its original animation unrotated. **Inside** Woka it plays
edited, and for free — the clock advances `frameIndex` and the decode effect keyed on it goes
through `ImageLoader`, which folds the recipe like every other reader. That was the whole bet behind
deferring this ticket, and it paid: animated playback cost this one no work at all.

## The block

`0x21 0xFF 0x0B` + an 8-byte application identifier + a 3-byte authentication code + data sub-blocks
+ `0x00`. This is the same mechanism the Netscape looping extension uses, which is why every decoder
in existence already skips the ones it does not know.

Identifier and code together are 11 bytes: **`WokaEdit` + `Rec`**, fixed forever, for the reason
ME-108's UUID is.

A sub-block is one length byte (1–255) then that many bytes, so a payload is written in 255-byte
runs and read back by concatenating them. That framing is the whole of the fiddliness here.

**Placed after the leading run of application extensions**, before the first graphic block — *not*
immediately after the Global Color Table, which is what this ticket said before it was built.

The corpus is why. `anim.gif` puts `NETSCAPE2.0` in the very first position after the table, and
decoders exist that honour the loop count only when its block sits there; inserting in front of it
is a change to how the file loops, made by a carrier that has no business having an opinion. Ours
goes after that run instead. It is still the head of the file, which is all `window = Head` needs.

The same choice makes the read cheap: **the walk stops at the first non-application block, and that
is the answer.** Nothing of ours is ever written past that point, so reaching the picture data
decides "no recipe" rather than reporting a window that ran out — which matters because the grid
asks this per visible tile.

## Steps

1. `recipe/GifBlocks.kt` (new): the block walk and the sub-block framing.
   - the walk from the end of the header — signature (6) + logical screen descriptor (7) + the
     Global Color Table when its flag is set (`3 * 2^(n+1)` bytes) — over the leading run of
     `0x21 0xFF` application extensions, and no further.
   - it answers three things: where ours is, where one would go, and whether the bytes ran out.
   - sub-block framing both ways: a chain read back concatenated, and a payload written in runs of
     at most 255.
2. `recipe/GifRecipe.kt` (new): `object GifRecipe : RecipeCarrier` over that walk.
   - `scan`: our block's payload chain as UTF-8. `truncated` only when the chain ran off the end.
   - `without`: splice ours out; null when there is none.
   - `with`: insert at the walk's insertion point. **Only `GIF89a` gets a write** — the Application
     Extension does not exist in `GIF87a` and promoting the version is a change to the file's own
     claim about itself, so such a file stays in bucket C where it already is.
3. `recipe/RecipeCarrier.kt`, `carrierFor`: `"gif" -> GifRecipe`, and the note now says `bmp` is
   bucket C entire.
4. `jvmTest/resources/media/still.bmp` (new fixture) + `STILL_BMP`: bucket C had no file in the
   corpus, so after this ticket the "neither a slot nor an encoder" tests had nothing to be asked
   about. 160×120 — a BMP is uncompressed and the full-sized one was 900 KB of a 3.3 MB corpus.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/recipe/GifRecipe.kt` (new) ·
`.../recipe/GifBlocks.kt` (new) · `.../recipe/RecipeCarrier.kt` (edit) ·
`jvmTest/resources/media/still.bmp` (new) · `jvmTest/kotlin/com/woka/fixtures/SampleMedia.kt` (edit)

## Done when

- `jvmTest/unit/GifRecipeTest.kt`, against blocks built by hand: a round-trip; a payload longer than
  255 bytes split across sub-blocks and read back whole; a Netscape loop block left exactly where it
  was by both `with` and `without`, with ours landing after it; a file with no Global Color Table; a
  `GIF87a` refused; something that is not a GIF refusing everything; a truncated sub-block chain read
  as "no recipe" without throwing.
- `jvmTest/integration/GifRecipeFramesTest.kt`: a document round-trips through `anim.gif`; **every
  frame decodes identically** before and after the write — the frame count and the pixels of frame 0
  and of the last frame; the timings and the repeat count survive; a clear restores the file byte for
  byte. An animation is where a botched block walk shows up as a broken file, not as a missing
  recipe, so it gets its own file rather than another case in `EditRecipeTest`.
- `jvmTest/unit/EditCapabilityTest.kt`: `anim.gif` is `Recorded`, and the "neither a slot nor an
  encoder" case holds `still.bmp` — bucket C is one format wide.
- By hand: turn a GIF, `Done`, and it still animates in the viewer with every frame turned.
- `./gradlew.bat :shared:build` green.
