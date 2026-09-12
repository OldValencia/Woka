# ME-282 — A Matroska carrier

| | |
|---|---|
| **Order** | **next** in `save/`, after ME-281 opened the gate |
| **Depends on** | ME-281 (the gate), ME-280 (the in-place write it inherits) |
| **Feature** | [Video Editor — Save (`Done`)](../save.md) |

**Goal.** Give `mkv` and `webm` a slot of their own, so a Matroska video keeps its edit and gets its
`Edit` entry back.

**Why now.** The library is mixed (author, 2026-09-03), which is why this was sequenced second in the
folder rather than left as a someday. ME-288 narrowed `Edit` to the two ISOBMFF containers, and the
corpus is almost all `mkv` — so this is the ticket that makes the feature true of the author's own
files. ME-281 has just said the approach is sound on a container that everything still plays.

**Visible outcome** (`tickets.md` §4): a Matroska video offers `Edit` again, keeps a Markup or Adjust
through `Done`, shows it on reopening, and `Revert` gives the file back byte for byte.

---

## Measured against the corpus, 2026-09-04 — and it settles the shape

The three corpus `mkv` files have byte-identical headers. Decoded:

```
1a 45 df a3  a3   …35 bytes…   18 53 80 67  01 00 00 00 00 00 52 a5   …
^ EBML id    ^ size=35         ^ Segment id ^ 8-byte VINT = 21157
```

`Segment`'s payload starts at offset 52, and 52 + 21157 = **21209 = the file's exact length**. So:

- **The `Segment` declares a known size and ends precisely at EOF.** Appending after it is genuinely
  a *new top-level element*: nothing moves and **no size field is rewritten**, which is the whole
  reason candidate 1 in `save.md` §3.3 was ranked first. Candidates 2 (`Tags`/`SimpleTag`, inside the
  Segment, needs the Segment's VINT widened) and 3 (`Void` padding, not guaranteed to exist) are
  **not attempted** — they are the fallbacks if this fails, and it does not have to.
- **This makes it a tail carrier**, so it implements `TailRecipeCarrier` and inherits ME-280's
  `setLength`-and-append for nothing. Which is the point of having built that first.
- **The unknown-size Segment is the case that must be refused.** A live or streamed Matroska writes
  its Segment size as an all-ones VINT (`01 ff ff ff ff ff ff ff`), meaning "to the end of the file" —
  and then bytes appended after it are *inside* the Segment as far as any reader is concerned, which
  is a different and unmeasured claim. None of the corpus files does this. `accepts` refuses it, and
  the file answers `Unavailable` rather than being written to on a guess.

**`EditRecipe`'s `HEAD_PROBE` is 8 bytes and that is not enough.** ISOBMFF needs four bytes at
offset 4; reading a Segment's size VINT means walking a variable-length EBML header first — 35 bytes
in the corpus, but the length is the file's own business. It has to widen.

---

## Steps

1. **Widen the head probe.** `HEAD_PROBE` in `EditRecipe.kt` (139 lines) becomes `4096`, with the
   reason in its comment: an ISOBMFF magic is at a fixed offset and a Matroska Segment's size sits
   past a variable-length EBML header. 4 KB is one `read` and covers any header a muxer writes.

2. **`MatroskaRecipe.kt` (new, in `recipe/`)** — `internal object MatroskaRecipe : TailRecipeCarrier`,
   in `WebpRecipe`'s house style: private constants at the top, the byte helpers at the bottom, and
   the two mistakes-to-make-once stated in the KDoc.
   - **The element.** A 4-byte class-D EBML id, then an 8-byte VINT length, then the payload.
     `0x1E 0x57 0x6B 0x45` — first byte in `0x10…0x1F` so it is a well-formed 4-byte id, and
     `57 6b 45` reads `WkE` in a hex dump, which is the habit `Woka-Edit-Recipe` and `WKED` already
     keep. Checked against every assigned top-level id (EBML, Segment, SeekHead, Info, Cluster,
     Tracks, Cues, Attachments, Chapters, Tags) — it collides with none.
   - **A fixed-width 8-byte VINT** (marker `0x01`, then seven big-endian length bytes) rather than
     the shortest legal encoding. Legal, deterministic, and it means the element's header is a
     constant 12 bytes — so `boxIn` is arithmetic rather than a second VINT decoder.
   - **The 16-byte `Woka-Edit-Recipe` identifier goes at the start of our payload**, and `scan`
     verifies it. A 4-byte id we assigned ourselves is a weaker claim than ISOBMFF's UUID; putting
     the shipped identifier inside makes the check exactly as strong, and it is the same constant so
     the two carriers cannot disagree about what "ours" means.
   - `boxIn`, `accepts`, `box` as `TailRecipeCarrier` requires; `scan`, `with`, `without` as
     `RecipeCarrier` does — `with`/`without` being the byte fallback for the foreign-bytes-after-ours
     shape, exactly as `IsoBmffRecipe` keeps them.
   - `accepts(head)`: `1a 45 df a3` at offset 0, **and** a `Segment` whose size VINT is not
     all-ones. Both, or the file is refused.

3. **Two extensions.** `"mkv", "webm" -> MatroskaRecipe` in `carrierFor` (`RecipeCarrier.kt`,
   139 lines), and the bucket-C sentence above it loses `mkv`/`webm` and keeps `avi`.
   **Nothing in `EditorEngineFactoryImpl` changes** — it already asks `carrierFor`, which is what
   makes a Matroska answer `Recorded` and get its `Edit` entry back with no branch anywhere.

4. **Tests.**
   - `jvmTest/unit/MatroskaRecipeTest.kt` (new) — the byte contract over hand-built EBML, which is
     what array literals are for: a payload round-trips; the bytes in front of our element are
     untouched; a file carrying nothing has nothing to remove; **an unknown-size Segment is refused**;
     a non-EBML container is refused; `boxIn` spans the element and reaches EOF; `box` refuses past
     `TAIL_WINDOW`; and an element carrying our id but *not* our identifier is neither read nor
     removed — the `theirs()` case, which is the one this design is most exposed on.
   - `jvmTest/integration/VideoRecipeTest.kt` (104 lines) — its three cases parameterised over the
     `mov` it has and an `mkv`: save, reopen through a second engine, a second save that does not
     grow the file, and a revert that is byte-identical.
   - `jvmTest/unit/EditorEntryTest.kt` and `jvmTest/unit/VideoEditorEngineTest.kt` — their
     "a container without a carrier" subject stops being the `mkv` and becomes an **`avi`**. There is
     no `avi` in the corpus and none is needed: `capability` stats the file and reads the extension,
     so a zero-byte temp `x.avi` answers `Unavailable` truthfully. The comment says ME-283 is what
     changes that answer.
   - `jvmTest/probe/VideoCarrierProbe.kt` (129 lines) — a third subject, `carrier.mkv`, copied from
     the corpus. **The by-hand pass is part of this ticket**, not a follow-up: ME-281 proved the
     ISOBMFF box is tolerated and says nothing about an EBML element. VLC, Windows Photos and Woka
     at minimum, recorded in § *Measured* with the date, exactly as ME-281's was.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/recipe/MatroskaRecipe.kt` (new) ·
`.../recipe/RecipeCarrier.kt` (edit) · `.../recipe/EditRecipe.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/unit/MatroskaRecipeTest.kt` (new) ·
`.../jvmTest/kotlin/com/woka/integration/VideoRecipeTest.kt` (edit) ·
`.../jvmTest/kotlin/com/woka/unit/EditorEntryTest.kt` (edit) ·
`.../jvmTest/kotlin/com/woka/unit/VideoEditorEngineTest.kt` (edit) ·
`.../jvmTest/kotlin/com/woka/probe/VideoCarrierProbe.kt` (edit)

## Done when

- an `mkv` offers `Edit` again, keeps an edit through `Done`, shows it on reopening, and `Revert`
  leaves the file byte-identical — the visible outcome, and the integration test asserts each part;
- a **streamed** Matroska (unknown-size Segment) answers `Unavailable` and is never written to;
- the write is in place: one `RandomAccessFile`, no `readBytes`, and a second save does not grow the
  file — inherited from ME-280 by implementing `TailRecipeCarrier`, and asserted rather than assumed;
- `IsoBmffRecipe`, `IsoBmffTailTest`, `RecipeInPlaceTest` and `HeicRecipeTest` are **byte-identical**:
  a second tail carrier must need no change to the first;
- the head carriers are untouched and still go through `writeAtomically`;
- **the by-hand pass is recorded in § Measured with a date** — until then the row says
  *awaiting the by-hand pass*, per `tickets.md` §4's last rule;
- `wc -l` ≤ 150 for every file above;
- the anti-FQN grep (`mandates.md`) is clean;
- `./gradlew.bat :shared:build` green.

## Measured

**Not yet** — the probe's `mkv` subject has to be opened by hand once the carrier writes it.
