# ME-137 — The RIFF carrier: WebP

| | |
|---|---|
| **Order** | 29 |
| **Depends on** | ME-131, ME-108 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Give WebP a recipe chunk, so the one format that is still re-encoded stops being.

**Why now.** `webp` is the whole of bucket B (§5.1) — the only format where `Done` rewrites the
picture, loses the original and leaves nothing to revert to. A carrier moves it to bucket A and
**empties bucket B**, which is what `EditCapability` predicted in its own doc: `Destructive` is a
transitional state expected to hold nothing.

## The container, and the promotion it needs

A WebP is `RIFF` + size(4, LE, counting everything after it) + `WEBP` + chunks. A **simple** file
holds exactly one — `VP8 ` (lossy) or `VP8L` (lossless) — and the format does not allow anything
beside it. Ancillary chunks are legal only in the **extended** form, which is announced by a `VP8X`
chunk placed first: 10 bytes — flags(1) + reserved(3) + canvas width−1 (3, LE) + canvas height−1
(3, LE).

So the write is two cases:
- **already extended** (`VP8X` first, which every animated file is): append our chunk;
- **simple**: build the `VP8X` from the dimensions read out of the `VP8 `/`VP8L` bitstream header,
  put it first, then the original chunk, then ours.

Every chunk is FourCC(4) + size(4, LE) + payload + **a pad byte when the size is odd**, and the
`RIFF` size at offset 4 is rewritten on every insertion and removal. Both are the mistakes this
ticket exists to get right once.

## Steps

1. `recipe/WebpRecipe.kt` (new): `object WebpRecipe : RecipeCarrier`, `window = Head` (our chunk sits
   near the front, unlike ME-108's box). FourCC `WKED`, chosen for the same reason PNG's `wkEd` was:
   private, and nothing else writes it.
   - `scan(bytes)`: walk chunks from offset 12, honouring the pad byte, and return ours. `truncated`
     when the walk runs out of array before finding either ours or the end.
   - `without(bytes)`: drop our chunk, fix the `RIFF` size. **Leaves a promoted `VP8X` in place** —
     removing it would need proof no other chunk relies on it, and an extended WebP is a valid WebP.
     Say so in the doc comment; it is the one thing here that is not a perfect round trip.
   - `with(bytes, payload)`: the two cases above. Refuse (null) anything whose `RIFF`/`WEBP` header
     does not check out, and anything that would push the file past `Int.MAX_VALUE`.
2. `recipe/WebpDimensions.kt` (new), pure: canvas width and height out of a `VP8 ` frame header (the
   3-byte start code, then 14-bit width and height) and out of a `VP8L` header (the `0x2F`
   signature, then 14-bit width−1 and height−1). Unit-tested against both fixtures; this is the part
   a wrong guess would corrupt a file with.
3. `recipe/RecipeCarrier.kt`, `carrierFor`: `"webp" -> WebpRecipe`, and cut `webp` from the note that
   lists what is left.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/recipe/WebpRecipe.kt` (new) ·
`.../recipe/WebpDimensions.kt` (new) · `.../recipe/RecipeCarrier.kt` (edit)

## What this leaves behind

With bucket B empty, `EditCapability.Destructive` becomes unreachable through the UI and the whole
re-encode path behind it — `ImageSaveJob`, `formatFor`, `EditorNoHistoryNotice` — is dead code.
**Do not delete it here.** It is the fallback for any format that ever gains an encoder before a
carrier, and removing it is a decision about the model rather than a step in a carrier ticket. Raise
it in ME-117 (housekeeping) with the measurement this ticket produces: nothing reaches it.

## Done when

- `jvmTest/unit/WebpRecipeTest.kt`: a payload round-trips through a **simple** file and the result
  starts with `VP8X` carrying the source's dimensions; a round-trip through an **already extended**
  file adds no second `VP8X`; the `RIFF` size matches the file's real length after both a write and
  a clear; an odd-length payload is padded and still reads back; a file that is not RIFF, and one
  truncated mid-chunk, read as "no recipe" without throwing.
- `jvmTest/unit/WebpDimensionsTest.kt`: the dimensions read out of `still.webp` and `anim.webp` match
  what the decoder reports for them.
- `jvmTest/integration/EditRecipeTest.kt`: a document round-trips through `still.webp`, and
  `stillDecoderFor(file).frame(file, 0)` is **pixel-identical** before and after — the promotion to
  `VP8X` must not touch the picture.
- `jvmTest/unit/EditCapabilityTest.kt`: the existing `a webp can be re-encoded but keeps no history`
  becomes `Recorded`, and the test names bucket B as empty.
- `jvmTest/integration/EditRecipeTest.kt`: `a format with no carrier answers nothing rather than
  failing` currently uses `still.webp` and must move — there is no `bmp` in the corpus, so copy the
  JPEG fixture's bytes to a `.bmp` name, which is exactly what `carrierFor` keys on.
- By hand: edit a WebP twice; the second `Done` continues the first instead of re-encoding, and
  Revert brings back the original picture.
- `./gradlew.bat :shared:build` green.
