# ME-283 — A RIFF carrier for avi

| | |
|---|---|
| **Order** | after ME-282; promoted and built 2026-11-04 in the same sitting |
| **Depends on** | ME-282 (the shared tail helpers, `HEAD_PROBE`), ME-280 (the in-place write) |
| **Feature** | [Save](../save.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** An `avi` keeps its edit through `Done`, shows it on reopening, and `Revert` gives it back
byte for byte.
**Why now.** `next_steps.md` 1.0.6: *mkv/webm/avi без редактора*. ME-282 closed two of the three.

## Shape — written against the tree 2026-11-04

- **A chunk inside the `RIFF`, last, after `idx1`.** RIFF readers skip an unknown chunk by its
  size, which is the extension point the format has. Chunk id `WKED` — `WebpRecipe`'s name in the
  other RIFF — then the 16-byte `Woka-Edit-Recipe` identifier, then the JSON, then a pad byte when
  the size is odd (the size field does not count it).
- **A tail carrier with one header field.** The `RIFF` counts its own length at offset 4, so
  `TailRecipeCarrier` grows `lengthPatch(length)`, null by default. `RecipeInPlace` writes it
  **after** the append and **before** a truncate: a write that dies between leaves a size that is
  merely short, which readers tolerate, and the media data is never touched.
- **Refused unless the `RIFF` ends at EOF** — `TailRecipeCarrier.fits(head, length)`, true by
  default. An OpenDML file over 1 GB chains `RIFF AVIX` after the first, and appending to the last
  one is an unmeasured claim. Such a file answers `RenderOnly` (ME-289) at the door.

## Steps

1. `TailRecipeCarrier` moved out of `RecipeCarrier.kt` into its own file (that file would have
   passed 150); `fits`, `lengthPatch`, `LengthPatch`, and `carriesHere(file)` for the factory.
2. `RecipeInPlace`: `writeInPlace` patches after the append, `clearInPlace` before the truncate.
3. `AviRecipe.kt` (new), `"avi" -> AviRecipe` in `carrierFor`.
4. `EditRecipe.appendedInPlace` asks `fits` beside `accepts`.

## Files

`recipe/AviRecipe.kt` (new) · `recipe/TailRecipeCarrier.kt` (new, moved) · `recipe/TailBoxes.kt`
(new, shared with ME-282) · `recipe/RecipeCarrier.kt` · `recipe/RecipeInPlace.kt` ·
`recipe/EditRecipe.kt` · `jvmTest/unit/AviRecipeTest.kt` (new) ·
`jvmTest/integration/VideoRecipeTest.kt` · `jvmTest/probe/VideoCarrierProbe.kt`

## Done when

- `unit/AviRecipeTest`: round trip, pad byte, `RIFF` size, OpenDML refused, `boxIn` and the
  length patch agree with the byte path;
- `integration/VideoRecipeTest` runs its three cases on an `avi` encoded in the test (the corpus has
  none): save, reopen, second save does not grow the file, revert byte-identical;
- `./gradlew.bat :shared:build` green;
- **the by-hand pass is recorded in § Measured** — until then the row says *awaiting the by-hand pass*.

## Measured

**2026-11-04, by the author.** `build/carrier-probe/carrier.avi` against `reference_carrier.avi`:
plays identically, picture and sound, in **Woka and VLC**. ffmpeg re-opens it and our reader reads
the recipe back (`VideoCarrierProbe`). Windows Photos was not reported.
