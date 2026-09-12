# ME-280 — A tail carrier writes in place

| | |
|---|---|
| **Order** | first in `save/` |
| **Depends on** | nothing |
| **Feature** | [Video Editor — Save (`Done`)](../save.md) |

**Goal.** Write and remove an ISOBMFF `uuid` recipe box with a `setLength` and an append instead of
building a second copy of the whole file.

**Why now.** `save/` is folder 1 ([`../../video-editor.md`](../../video-editor.md) §1a) and this is
the wall in front of everything in it. `EditRecipe.write` and `clear` call `file.readBytes()` and
hand the array to `writeAtomically`, which builds another whole copy and moves it over the target: for
a 4 GB film that is an `OutOfMemoryError` **and** 4 GB of writes to change three hundred bytes. No
video can carry an edit until this is O(payload).

**Split from the backlog block on promotion.** That block also wired `mp4`/`mov` into `carrierFor`,
made the factory ask a video the carrier question, deleted `EditCapability.Preview` and taught
`VideoEditorEngine` to save — thirteen files and a mandated split of a 150-line file. That half is
**ME-288** in [`../../tickets/backlog.md`](../../backlog.md) §280 and depends on this one.
**This ticket adds no extension and changes no capability**, so nothing about a video moves yet; what
moves is how a `heic`, `heif` or `avif` is written, which is where the mechanism can be proved on
files that already exist.

---

## Design notes read against the tree, 2026-09-04

- **The read path is already right and was misdiagnosed once** (`save.md` §3.2). `RecipeSlices` reads
  the last 64 KB through a `RandomAccessFile` (ME-126). Nothing here touches reading.
- **Why abandoning atomicity is correct here, and only here.** A tail carrier's box sits after every
  media box in the file. Nothing before the append is read, moved or rewritten, so a write that dies
  halfway leaves the picture data byte-identical and at worst a truncated trailing box — which
  `RecipeCarrier`'s contract already reads as "no recipe", because nothing there throws. **Head
  carriers keep `writeAtomically` untouched**: a JPEG's segment sits *before* the image data and a
  half-written head is a destroyed photograph. This is not a general relaxation of §5.2's rule and the
  new code must not be reachable from a head carrier.
- **The box is always inside the read window, by construction.** `IsoBmffRecipe.with` already refuses
  a payload where `body.size + OVERHEAD > TAIL_WINDOW`, so an offset found in the 64 KB tail slice is
  enough to locate it in the file. That existing refusal is what makes the whole approach expressible
  in offsets rather than in a walk.
- **The one case that must not be truncated.** `ourBox()` finds our box wherever it is in the slice,
  and `without` splices it out of the middle correctly. If another program appended its own bytes
  *after* ours, truncating to our box's start would delete **their** data — a larger harm than the
  one this ticket removes. So the in-place path applies only when our box ends where the file does,
  and every other shape falls back to the existing byte path unchanged. On a 4 GB file that fallback
  will fail for memory; `runCatching` already catches `Throwable`, so it surfaces as a failed save
  with §4.5's banner rather than as a crash. Recorded, not fixed: it needs a foreign box appended
  after Woka's own, on a file too large to read.
- **A carrier still never opens a file.** `RecipeCarrier`'s own KDoc — *"Everything here works on
  bytes, not files… `EditRecipe` is the only thing that opens anything"* — is what keeps every case a
  unit test over an array literal. So the new members take byte arrays and the seeking lives beside
  `EditRecipe`.
- **`with` and `without` stay exactly as they are.** They are the head path, they are the fallback,
  and `IsoBmffRecipeTest` already covers them. `box(payload)` is extracted *out* of `with` so the two
  paths cannot build a different box.

---

## Steps

1. **`TailRecipeCarrier`, in `RecipeCarrier.kt` (89 lines).** An interface extending
   `RecipeCarrier`, defaulting `window` to `RecipeWindow.Tail`, with three members and no
   implementation:
   - `fun boxIn(tail: ByteArray): IntRange?` — where our box sits in the tail slice, null when there
     is none;
   - `fun accepts(head: ByteArray): Boolean` — whether this is a container the carrier can extend at
     all;
   - `fun box(payload: String): ByteArray?` — the bytes to append, null when the payload is too
     large for the read window.

   Its KDoc carries the atomicity argument above, in one paragraph, because that is the question
   anyone reading this interface will have.

2. **`IsoBmffRecipe` implements it** (133 lines). `object IsoBmffRecipe : TailRecipeCarrier`, the
   `override val window` line deleted because the interface now defaults it, and:
   - `boxIn` = `bytes.ourBox()?.let { it.at until it.end }` — one line over the private helper that
     already exists;
   - `accepts` = `head.isIsoBmff()`, the private extension already there;
   - `box` = the body of `with` from `val body =` down to `body.copyInto(box, OVERHEAD)`, moved
     verbatim, returning the box;
   - `with` becomes the `isIsoBmff` guard, a `box(payload) ?: return null`, and the existing
     concatenation. **No behaviour changes and `IsoBmffRecipeTest`'s existing cases must pass
     unedited** — that is what proves the extraction was an extraction.

3. **`File.leading`, in `RecipeSlices.kt` (59 lines).**
   `internal fun File.leading(count: Int): ByteArray?` — the first [count] bytes through the same
   `runCatching` the other two use. Eight bytes are enough for `accepts`, and reading
   `slice(RecipeWindow.Head)`'s 512 KB to check four of them would be silly.

4. **`RecipeInPlace.kt` (new, in `recipe/`).** Two functions, each returning **`Result<Unit>?` where
   null means "this file cannot be written in place, use the byte path"** — stated in the file's KDoc,
   because a nullable `Result` is otherwise a puzzle:
   - `internal fun writeInPlace(file: File, carrier: TailRecipeCarrier, box: ByteArray): Result<Unit>?`
     — resolves the offset (below), then one `RandomAccessFile(file, "rw")` doing `setLength(at)`,
     `seek(at)`, `write(box)`, inside `runCatching`;
   - `internal fun clearInPlace(file: File, carrier: TailRecipeCarrier): Result<Unit>?` — the same
     resolution; `Result.success(Unit)` and **no open at all** when there is no box, because
     `EditRecipe.clear`'s KDoc already forbids touching the mtime of a file that carries nothing;
     `setLength(at)` when there is one.
   - A private `tailOffset(file, carrier): Long?` holds the three-way answer both share: **no box** →
     `file.length()`; **our box ends where the file does** → its absolute start,
     `file.length() - tail.size + range.first`; **our box with something after it** → null.

5. **`EditRecipe` takes the new road when there is one** (104 lines). In `write`, after the payload is
   encoded and before `file.readBytes()`:
   - `if (carrier is TailRecipeCarrier)` → `carrier.accepts(file.leading(8) ?: …)` false gives the
     existing *"…files cannot carry an edit."*; `carrier.box(encoded)` null gives the existing
     *"This edit is too large for a … to carry."*; then `writeInPlace(...)?.let { return it }`.
   - In `clear`, the same shape with `clearInPlace(file, carrier)?.let { return it }` **before** the
     `readBytes`, so a file with nothing to remove is not read either.
   - The two message strings stay in this file, where they already are, rather than being copied into
     the new one.

6. **Tests.**
   - `jvmTest/unit/IsoBmffTailTest.kt` (new) covers the three new members over array literals, which
     is what the byte-level contract is for: `boxIn` is null on a file with no box, spans exactly the
     box `with` wrote, and does **not** reach the end when foreign bytes follow ours; `accepts` is
     true for `ftyp` and false for four other bytes; `box(payload)` is byte-identical to the tail of
     `with(bytes, payload)`, and null past `TAIL_WINDOW`.
     **A second file, not four more cases in `IsoBmffRecipeTest.kt`** — measured while writing this:
     the additions took that file to 159 lines, past the mandate, and its subject is different
     anyway. It carries its own byte builders so the shipped test stays byte-identical.
   - `jvmTest/integration/RecipeInPlaceTest.kt` (new) — the file-level behaviour, over a real temp
     `heic`-shaped fixture:
     1. a first write appends and `EditRecipe.read` round-trips it;
     2. a second write **leaves the file one box long, not two**, and the file's length returns to the
        first write's — the assertion that proves the truncate happened;
     3. `clear` returns the file byte-identical to before the first write;
     4. `clear` on a file carrying nothing **does not change its `lastModified`**;
     5. our box with four foreign bytes appended after it: the write still succeeds, the foreign bytes
        **survive**, and the recipe reads back — the fallback path, asserted rather than assumed.
   - `integration/HeicRecipeTest.kt` (162 lines, already over the mandate) is **not** touched: it
     covers the same round-trip through the byte path and must go on passing as the regression proof.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/recipe/RecipeCarrier.kt` (edit) ·
`.../recipe/IsoBmffRecipe.kt` (edit) · `.../recipe/RecipeSlices.kt` (edit) ·
`.../recipe/RecipeInPlace.kt` (new) · `.../recipe/EditRecipe.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/unit/IsoBmffTailTest.kt` (new) ·
`.../jvmTest/kotlin/com/woka/integration/RecipeInPlaceTest.kt` (new)

## Done when

- writing a recipe into an ISOBMFF file opens it once `"rw"` and never calls `readBytes` — readable
  in the diff, and the length assertion in the new test is what catches a regression;
- a second write leaves one box, and `clear` restores the original bytes exactly;
- a foreign box after ours is preserved, by falling back to the byte path;
- `clear` on a file carrying nothing still does not touch its mtime;
- **no head carrier reaches any of the new code** — `JpegRecipe`, `PngRecipe`, `TiffRecipe`,
  `GifRecipe` and `WebpRecipe` are untouched and still go through `writeAtomically`;
- `IsoBmffRecipeTest.kt` and `HeicRecipeTest.kt` are **byte-identical** — `git diff` on both is
  empty, which is what proves step 2 was an extraction and not a rewrite;
- no extension is added and no `EditCapability` changes — a video still answers `Preview` and
  `EditorEngineFactoryImpl` is untouched (that is ME-288);
- `wc -l` ≤ 150 for every file above;
- the anti-FQN grep (`mandates.md`) has been run on every edited `.kt` and is clean;
- `./gradlew.bat :shared:build` green.
