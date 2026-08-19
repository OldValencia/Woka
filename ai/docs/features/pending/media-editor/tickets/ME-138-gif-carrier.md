# ME-138 — The GIF carrier: Application Extension

| | |
|---|---|
| **Order** | 30 |
| **Depends on** | ME-131, ME-108 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Give GIF a recipe block, so an animation can be turned and flipped like every other
picture.

**Why now.** It is the simplest of the three writers left — a block the format was built to carry —
and it is the one people have most of: a folder of GIFs is currently a folder where `Edit` is greyed
out for no reason the user can see.

## The block

`0x21 0xFF 0x0B` + an 8-byte application identifier + a 3-byte authentication code + data sub-blocks
+ `0x00`. This is the same mechanism the Netscape looping extension uses, which is why every decoder
in existence already skips the ones it does not know.

Identifier and code together are 11 bytes: **`WokaEdit` + `Rec`**, fixed forever, for the reason
ME-108's UUID is.

A sub-block is one length byte (1–255) then that many bytes, so a payload is written in 255-byte
runs and read back by concatenating them. That framing is the whole of the fiddliness here.

**Placed immediately after the Global Color Table**, before the first graphic block. Extensions are
legal anywhere in the data stream, and the front is where a head read finds it (`window = Head`).

## Steps

1. `recipe/GifRecipe.kt` (new): `object GifRecipe : RecipeCarrier`.
   - a private walk over the data stream from the end of the header — signature (6) + logical screen
     descriptor (7) + the Global Color Table when its flag is set (`3 * 2^(n+1)` bytes) — then blocks:
     `0x21` extension (label, then sub-blocks), `0x2C` image descriptor (descriptor, optional local
     colour table, LZW minimum code size, then sub-blocks), `0x3B` trailer.
   - `scan`: our extension's sub-blocks, concatenated, as UTF-8. `truncated` when the walk runs out.
   - `without`: splice ours out; null when there is none.
   - `with`: insert after the Global Color Table, in 255-byte sub-blocks. Refuse a file whose header
     is not `GIF87a`/`GIF89a`.
2. `recipe/GifRecipe.kt`: **only `GIF89a` gets a write.** The extension block does not exist in
   `GIF87a`, and promoting the version is a change to the file's own claim about itself — refuse
   instead, which leaves such a file in bucket C where it already is.
3. `recipe/RecipeCarrier.kt`, `carrierFor`: `"gif" -> GifRecipe`, and cut `gif` from the note.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/recipe/GifRecipe.kt` (new) ·
`.../recipe/RecipeCarrier.kt` (edit)

## Done when

- `jvmTest/unit/GifRecipeTest.kt`, against blocks built by hand: a round-trip; a payload longer than
  255 bytes split across sub-blocks and read back whole; another application extension (a Netscape
  loop block) left exactly where it was by both `scan` and `without`; a file with no Global Color
  Table; a `GIF87a` refused; a truncated sub-block chain read as "no recipe" without throwing.
- `jvmTest/integration/EditRecipeTest.kt`: a document round-trips through `anim.gif`, and **every
  frame decodes identically** before and after the write — `frameSet(…).frames.size` and the pixels
  of frame 0 and of the last frame. An animation is where a botched block walk shows up as a broken
  file, not as a missing recipe.
- `jvmTest/unit/EditCapabilityTest.kt`: `anim.gif` is `Recorded`; the "neither a slot nor an encoder"
  case is left holding `multipage.tif` alone.
- By hand: turn a GIF, `Done`, and it still animates in the viewer with every frame turned.
- `./gradlew.bat :shared:build` green.
