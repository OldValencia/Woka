# ME-108 — The ISOBMFF carrier: HEIC, HEIF, AVIF

| | |
|---|---|
| **Order** | 28 |
| **Depends on** | ME-131 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Give the three ISOBMFF formats a recipe slot, so a phone photograph becomes editable
without an encoder and without a converted copy.

**Why now.** These are the formats a camera actually produces, and they are the whole of bucket C
that matters: `Edit` is greyed on every HEIC in the library. §5.1 says a new carrier moves a format
from C **straight to A** — no re-encode, no conversion, pixels never touched — and this is the first
of the four writers that proves it on a format Skia cannot write at all.

**One writer, one ticket.** The backlog block held four (ISOBMFF, RIFF, GIF, TIFF); the other three
are ME-137 and land one at a time. Nothing outside `recipe/` changes for any of them: `carrierFor`
decides the bucket, so adding a row here flips `EditCapability` for those extensions and both doors
open on their own.

## The box, and why it goes at the end

A top-level `uuid` box — the slot ISOBMFF provides for private data — **appended after the last
box**. Appending is not a stylistic choice: HEIF item offsets in `iloc` are absolute file offsets, so
inserting anywhere earlier rewrites the meaning of every one of them.

That costs the head-window optimisation `EditRecipe` was built around. A `uuid` box at the end of a
4 MB HEIC is not in the first 512 KB, so a head read answers "no recipe" for every edited file, and
`carries` is asked **per visible grid tile** (ME-133). The carrier therefore says which end of the
file it lives at, and `EditRecipe` reads that end.

## Steps

1. `recipe/RecipeCarrier.kt`: add `enum class RecipeWindow { Head, Tail }` and
   `val window: RecipeWindow get() = RecipeWindow.Head` on the interface. `JpegRecipe` and
   `PngRecipe` are unchanged by the default.
2. `recipe/EditRecipe.kt`: `payloadOf` slices by `carrier.window` — the existing `head()`, or a new
   `tail()` reading the last `TAIL_BYTES` (64 KB; our box is the last thing in the file). The
   whole-file fallback on `truncated` stays exactly as it is for both.
3. `recipe/IsoBmffRecipe.kt` (new): `object IsoBmffRecipe : RecipeCarrier` with
   `window = RecipeWindow.Tail` and a fixed 16-byte UUID constant — **written once and never
   changed**, since it is the identity of every recipe already in a file.
   - `scan(bytes)`: find the UUID, validate the box header immediately before it (32-bit size, type
     `uuid`), return the payload after it. Matching the UUID rather than walking from the start,
     because on a tail slice there is no start to walk from; `truncated = true` when the slice holds
     no `ftyp` **and** no match, so `EditRecipe` retries whole.
   - `without(bytes)`: walk top-level boxes from the start — `size(4) + type(4)`, `size == 1` reads a
     64-bit largesize, `size == 0` runs to EOF — and return the array minus our box, or null when
     there is none.
   - `with(bytes, payload)`: append `size + 'uuid' + UUID + payload`, refusing (null) anything that
     is not ISOBMFF (no `ftyp` in the first box) or a payload past `Int.MAX_VALUE` framing.
4. `recipe/EditRecipe.kt`, `carrierFor`: `"heic", "heif", "avif" -> IsoBmffRecipe`, and cut those
   three from the "not gaps to be filled here" note so it names only what is left.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/recipe/IsoBmffRecipe.kt` (new) ·
`.../recipe/RecipeCarrier.kt` (edit) · `.../recipe/EditRecipe.kt` (edit)

## What this does not do

A HEIC holding a burst gets **one** recipe for the file, not one per frame. Editing frame 3 and
seeing frame 0 draw edited is ME-126's question, and it is unchanged by this ticket — it simply
becomes reachable, where before the format could not be edited at all.

## Done when

- `jvmTest/unit/RecipeCarrierTest.kt`: synthetic boxes only — a `uuid` round-trip, a second write
  replacing rather than appending twice, a `size == 1` largesize box walked correctly, a box whose
  size runs past the array, and a file that is not ISOBMFF. Every one reads as "no recipe" and
  nothing throws.
- `jvmTest/integration/EditRecipeTest.kt`: round-trip through `livephoto.heic` and `still.avif` from
  the corpus, and **`stillDecoderFor(file).frame(file, 0)` is pixel-identical before and after the
  write** — the claim the whole model rests on.
- `jvmTest/unit/EditorEngineFactoryTest.kt`: `capability` of a `.heic` is `Recorded`, not
  `Unavailable`.
- `jvmTest/integration/`: a HEIC edited through `ImageEditorEngine.save` and read back through
  `ImageLoader` comes out turned — the format is editable end to end, with no encoder anywhere in the
  path.
- By hand: `Edit` is no longer greyed on a HEIC in the grid, and a quarter turn survives closing and
  reopening the folder.
- `./gradlew.bat :shared:build` green.
