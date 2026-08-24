# ME-139 — The TIFF carrier: a private IFD tag

| | |
|---|---|
| **Order** | 31 |
| **Depends on** | ME-131, ME-108 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Give TIFF a recipe tag, and with it close bucket C down to the one format that can never
leave it.

**Why now.** It is the last container with a slot. After it, `bmp` is the only format Woka lists
that cannot record an edit, and §5.1's staged rollout is finished — every other picture in the
library can be edited, reverted and edited again.

## The tag, and the offset problem

TIFF is a header — `II`/`MM` + `42` + a 4-byte offset to IFD0 — and a chain of IFDs, each a count(2)
plus 12-byte entries plus a 4-byte link to the next. Tag numbers **≥ 32768** are reserved for private
use; ours is `0xA057` (41047), fixed forever.

An entry is tag(2) + type(2) + count(4) + value-or-offset(4). A payload longer than four bytes lives
elsewhere in the file and the entry points at it — which is why this is the fiddliest of the four
writers: **adding a 12-byte entry to an IFD in place shifts every byte after it**, and every value
offset already in the file points at an absolute position.

The way through is not to shift anything:

- **write**: append our payload at the end of the file, then append a *rebuilt* IFD0 — the original
  entries plus ours, in ascending tag order as the specification requires — and patch the header's
  IFD0 offset to point at the copy. Nothing before the end of the original file moves, so every
  existing offset still means what it meant.
- **clear**: put the header's offset back and truncate. Which requires knowing where IFD0 was, so
  **our payload carries it**: four bytes of original offset in front of the JSON. That is what makes
  a revert byte-identical rather than approximately identical, which is the promise ME-107 tests.

## Steps

1. `recipe/TiffRecipe.kt` (new): `object TiffRecipe : RecipeCarrier`, `window = Tail` — a rebuilt
   IFD0 and the payload both live at the end (ME-108's seam, used a second time).
   - a private reader for the header: byte order from `II`/`MM`, the `42` magic, the IFD0 offset.
     **Both byte orders**, everywhere; a big-endian TIFF read as little-endian is a corrupted file.
   - `scan`: follow the header's offset to IFD0, find tag `0xA057`, read its value from where the
     entry points. `truncated` when the offset lies outside the slice — the tail window is the common
     case, and a whole-file re-read is the answer.
   - `without`: read the original offset out of our payload's first four bytes, write it back into
     the header, and cut the file at the point our block started.
   - `with`: as above. Refuse a file whose magic is not `42`, and one whose IFD0 offset is outside
     the file.
2. `recipe/TiffRecipe.kt`: **IFD0 only.** A multi-page TIFF is a chain, and every page after the
   first is untouched — the recipe belongs to the file (ME-126), so one tag on the first IFD says it
   for all of them.
3. `recipe/RecipeCarrier.kt`, `carrierFor`: `"tif", "tiff" -> TiffRecipe`, and reduce the note to
   `bmp` alone.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/recipe/TiffRecipe.kt` (new) ·
`.../recipe/RecipeCarrier.kt` (edit)

## Done when

- `jvmTest/unit/TiffRecipeTest.kt`, against headers built by hand in **both** byte orders: a
  round-trip; entries left in ascending tag order; a second write replacing rather than chaining; a
  clear that returns the exact original bytes; an IFD0 offset pointing past the end of the file, a
  magic that is not 42, and a truncated IFD — each "no recipe", none throwing.
- `jvmTest/integration/EditRecipeTest.kt`: a document round-trips through `multipage.tif`; **all
  three pages still decode**, with `frameSet(…)` reporting three and page 0 and page 2 pixel-identical
  to before the write; and `clear` gives back a byte-identical file.
- `jvmTest/unit/EditCapabilityTest.kt`: `multipage.tif` is `Recorded`, and the "neither a slot nor an
  encoder" case is down to a `.bmp`.
- The feature doc's §5.1 table and the carrier table in it: bucket C is `bmp` and nothing else.
- By hand: turn a TIFF, `Done`, reopen, Revert — and the file is the size it was to the byte.
- `./gradlew.bat :shared:build` green.
