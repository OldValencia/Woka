# ME-127 — Output quality is q100, and there is nothing to configure

| | |
|---|---|
| **Order** | 60 — first of the four promoted 2026-08-30; it blocks nothing and nothing blocks it |
| **Depends on** | nothing |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Encode every re-encoded save at **q100** and **delete the idea of a quality setting** — no
`WokaSettings` field, no Settings row, no argument at the call site.

**Why now.** The backlog block was "make q95 adjustable" and the answer, given 2026-08-30, is the other
one: quality is not a preference, it is a promise. A photograph that has already been compressed once
must not lose a second generation because a number in Settings was left where somebody put it, and one
more row against a §5 that is already dense buys a number most people never touch. Fixing the constant
at its ceiling makes the promise checkable instead of configurable.

## What this actually reaches, stated honestly

**Almost nothing today, and that is the point.** `SaveFormat.quality` is read in exactly one place —
`ImageSaveJob.encode` — and that job runs only for `EditCapability.Destructive`, which is §5.1's
bucket B. Bucket B is **empty**: `carrierFor` answers for `jpg`, `png`, `heic`, `heif`, `avif`, `webp`,
`gif`, `tif` and `tiff`, so every editable format is `Recorded` and the pixels are never rewritten at
all. The only live exerciser is `ImageSaveJobTest`.

So this ticket is a **policy written into the one constant that would decide it**, plus the assertion
that keeps it there, plus the deletion of the backlog block that proposed the opposite. It is small on
purpose and is not pretending otherwise. What it prevents is the day a new format lands in bucket B —
or §7's conversion sheet is ever built — and the number nobody remembered choosing turns out to be 90.

**Lossless is not on the table.** WebP's encoder takes quality 100 as *lossy at maximum*, not as
lossless; Skia exposes no lossless-WebP flag through `encodeToData`. q100 is the ceiling this build can
reach, and the doc says so rather than implying a round trip that does not happen. PNG is already 100
and is genuinely lossless (ME-010 measured a zero-delta round trip).

## Steps

1. **`save/SaveFormat.kt` (edit)** — `JPEG("jpg", EncodedImageFormat.JPEG, 100)` and
   `WEBP("webp", EncodedImageFormat.WEBP, 100)`. `PNG` is unchanged at 100.

2. **`save/SaveFormat.kt` (edit)** — replace the `95, not 90` paragraph on `JPEG` with the rule as it
   now stands, on the **enum** rather than on one member, because it is one rule for all three:
   editing is a second compression laid over the source's first, the file is the user's photograph, and
   the ceiling is the only value that never has to be defended. Say in the same place that WebP's 100
   is lossy-at-maximum and that there is no lossless road through Skia.

3. **`save/SaveFormat.kt` (edit)** — `val quality: Int` keeps its name and stays a constructor
   property. It is **not** collapsed into a single top-level `const`: a format-specific number is what
   the type is for, and the day AVIF gets a Skia encoder it will not be 100.

4. **`../image-editor.md` §7 (edit)** — "photographic sources default to JPEG **q95**" becomes **q100**,
   and the sentence gains the clause that there is no setting to change it.

5. **`../image-editor.md` §9 (edit)** — strike the 2026-08-18 row `JPEG quality on save? → q95
   everywhere` in the `~~…~~` **Superseded** style the file already uses for the four rows of
   2026-08-19, and add one dated 2026-08-30: *Configurable output quality? → **No. q100, fixed.***,
   with the reason above. The old row's "Configurable quality is ME-127, not MVP" is now answered by
   ME-127 saying no.

6. **`backlog.md` (edit)** — remove the ME-127 block. The id is spent, not returned to the pool
   (`conventions/tickets.md` §2).

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/save/SaveFormat.kt` (edit);
`ai/docs/features/pending/image-editor/image-editor.md` (edit);
`ai/docs/features/pending/image-editor/tickets/backlog.md` (edit).

## Verification

- `jvmTest/unit/SaveFormatTest.kt` (new) — every `SaveFormat` entry answers `quality == 100`, written as
  a loop over `SaveFormat.entries` so a fourth format added later fails the test rather than slipping
  past it; and `formatFor` still routes `jpg`, `jpeg`, `png`, `webp` and refuses `bmp`, `heic`, `tif`;
- `jvmTest/integration/ImageSaveJobTest.kt` — unchanged and still green, which is what says the encode
  path still produces a file at the new number;
- `grep -rn "q95" ai/docs/features/pending/image-editor/` returns nothing.

## Done when

- `SaveFormat.JPEG.quality`, `SaveFormat.WEBP.quality` and `SaveFormat.PNG.quality` are all `100`;
- no `WokaSettings` field, no `SettingsMutations` function and no Settings row was added — the diff
  touches one Kotlin file;
- §7 and §9 say q100, and §9 records the refusal with its date;
- the ME-127 block is gone from `backlog.md` and the row is in the status table;
- `./gradlew.bat :shared:build` green.
