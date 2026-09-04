# Feature: Video Editor — Save (`Done`)

The instant, lossless half of `Done ▾`: the `EditDocument` goes into the video file's own private
metadata slot, the media data is never rewritten, and every reader in Woka folds it. It is §5 of the
image editor's design applied to a container that is a thousand times larger — which changes the
mechanics and none of the reasoning.

Umbrella: [`../video-editor.md`](../video-editor.md) §3.5 and §5. Ticket ids: **ME-280…ME-289**.

---

## 1. Status

| Ticket | Title | State |
|---|---|---|
| [ME-280](tickets/ME-280-a-tail-carrier-writes-in-place.md) | A tail carrier writes in place | **done 2026-09-04** |
| [ME-288](tickets/ME-288-mp4-and-mov-carry-an-edit.md) | mp4 and mov carry an edit, and `Preview` is deleted | **done 2026-09-04** — and **invisible outside the editor until ME-284**: reported by the author the same day, who edited an `mp4`, saw `Done` succeed, and found the change in the editor and in no other part of Woka |
| [ME-281](tickets/ME-281-does-anything-else-tolerate-the-box.md) | Probe: does anything else tolerate the box? | **done 2026-09-04** — the gate opened: VLC, Windows Photos and Woka all play an edited `mp4` and `mov` unchanged |

Everything else is a block in [the shared backlog](../tickets/backlog.md) §280, where no state is
recorded. `save/` is **folder 1** of the build order ([`../video-editor.md`](../video-editor.md) §1a).

`EditCapability.Preview` — the temporary case
[`foundation/`](../../../implemented/video-editor/foundation/foundation.md) §3.5 introduces — is
deleted by **ME-288**, not by ME-280: promoting the first block split the write from the wiring, and
the capability belongs with the wiring.

---

## 2. Scope

**In.** A carrier per video container. A write that does not copy the file. Every reader folding what
was written — the viewer's frame, its duration and its slider, the grid poster, PiP, the seek
preview. Revert. Cache eviction. The capability that drives the `Done ▾` menu.

**Out.** `Render video…` ([`render/`](../render/render.md)). Overwriting the source: there is nothing
to overwrite, because nothing is encoded.

---

## 3. Architecture

### 3.1 Three container families, three answers

| Format | Container | The slot |
|---|---|---|
| `mp4`, `mov` | ISOBMFF | **`IsoBmffRecipe`, already written and shipped** for `heic`/`heif`/`avif`. A top-level `uuid` box appended after the last box; an unknown top-level box is skipped by its size, which is the extension point the specification provides. Wiring the two extensions into `carrierFor` is close to free |
| `mkv`, `webm` | Matroska / EBML | **No `uuid` box at all.** A second carrier — §3.3 |
| `avi` | RIFF | A trailing chunk, plus the 4-byte `RIFF` size at offset 4. **`WebpRecipe` already does RIFF surgery** (ME-137) and is the precedent to read before writing this |

The library is mixed (author, 2026-09-03), so Matroska is the **second** ticket here and not a
someday. Until it exists, an `mkv` collapses `Done ▾` to `Render video…` alone and says why.

### 3.2 The write must stop copying the file — and it is safe here in a way it was not for a JPEG

`EditRecipe.write` and `clear` call `file.readBytes()` and hand the result to `writeAtomically`,
which builds a whole second copy and moves it over the target. For a 4 GB film that is an OOM *and*
4 GB of writes to change three hundred bytes.

**Read is already fine and was misdiagnosed once** — `RecipeSlices` (ME-126) reads the last 64 KB of
a tail carrier through a `RandomAccessFile`, so `carries(file)` per grid tile costs nothing on an
`mp4`.

The write becomes: seek, `setLength` back over the old box, append the new one. `RandomAccessFile`,
O(payload).

**Why abandoning atomicity is correct for this one case, and only this one.** A tail carrier's box is
after every media box in the file. Nothing before the append is read, moved or rewritten, so a write
that dies halfway leaves the video data byte-identical and, at worst, a truncated trailing box —
which `RecipeCarrier`'s contract already handles as "no recipe", because nothing there throws. **The
picture cannot be damaged by a failed write.** That is the opposite of a JPEG, where the segment sits
*before* the image data and a half-written head is a destroyed photograph; head carriers therefore
keep `writeAtomically` exactly as they are, and this is not a general relaxation of §5.2's rule.

### 3.3 Matroska has no `uuid`, and the shape of its slot is a probe

Three candidates, in the order they should be tried:

1. **An unknown top-level EBML element appended after the `Segment`.** EBML requires a reader to skip
   an element it does not recognise, and this is the structural analogue of the `uuid` box: nothing
   existing moves, and no size field needs rewriting. **Probe first.**
2. **A `Tags` element carrying a `SimpleTag`** whose `TagName` is ours. It is the sanctioned place for
   arbitrary metadata, and it is *inside* the `Segment` — so the `Segment`'s size VINT has to be
   rewritten, which only works if its declared width has room.
3. **A `Void` element** already present as padding. Universal in principle, unreliable in practice:
   there is no guarantee any given file has enough of it.

The probe is not "does it round-trip through our own carrier" — that is a unit test. It is **what
mpv, VLC and Windows do with the file afterwards**, and it wants a fresh head: ME-181's probe took
four wrong turns before it read correctly.

### 3.4 A save changes the duration, and that is the part with teeth

Every other recipe in this app changes how a file *looks*. This one changes how long it *is*, and
three things read a duration:

- **The viewer's slider and readout**, which must show the edited length, not the source's;
- **the watch record**, which stores `positionMs` against `durationMs` — a trim can leave a resume
  position past the end of the video it belongs to, and something has to decide whether that clamps
  or clears;
- **chapters**, which are probed from the container in source time and now point into a timeline
  that may have re-ordered or dropped them.

### 3.5 Every reader folds, or `Done` is a half-truth

The still answer is `RecipeStillEdits`: readers decode the original and fold the recipe. The video
readers are the viewer's frame path, the grid poster (`VideoThumbnail`), PiP and the seek preview —
and the *cuts* are as much part of the fold as the colour is. **A viewer that applied the crop and
ignored the cut would be worse than one that ignored both**, so the viewer plays the clip list, and
it does that by reusing [`playback/`](../playback/playback.md)'s clip walk rather than growing a
second one.

Caches take care of themselves: `SaveEviction`'s poster key is path, length **and** mtime, all three
of which an append changes, so the poster re-derives itself rather than going stale.

### 3.6 The cover is chosen, and it is folded

Accepted 2026-09-03: the author wants to pick the video's cover in Woka rather than take
`VideoThumbnailHeuristics`' guess. It is one field in the recipe, and two properties make it worth
stating here rather than in a ticket:

- **The position is in timeline time, not source time.** A cover is a frame of the *result*; after a
  reorder, source `00:31` may be the second thing you see.
- **The poster is drawn through the fold.** That is the point rather than a detail: freeze the first
  frame and put a title on it (§950 of the backlog) and the grid tile shows the title card. An
  unfolded poster would show the bare source frame and the feature would be pointless.

It also **answers §4's last open question**: a trim can remove the very frame a poster was taken
from, and once the cover is a chosen timeline position that cannot happen — the position is inside
the result by construction, and clamped if a later edit shortens it.

---

## 4. Open questions

- ~~Does anything else tolerate a `uuid` box on an `mp4`'s tail?~~ — **answered by ME-281,
  2026-09-04: yes.** It was the gate on this whole folder, and it opened: VLC, Windows Photos and
  Woka all play an edited `mp4` and `mov` unchanged, and the bundled ffmpeg re-decodes both. `Done`
  keeps its place as the default save and the `Done ▾` menu does not collapse (§5).
- **What a resume position does after a trim.** Clamp, clear, or map through the clip list. The third
  is correct and the most work.
- **Whether the grid marks an edited video.** Stills do not, and a cut is a bigger claim about a file
  than a colour tweak is.
- ~~Whether a trim can orphan the frame the poster was taken from~~ — **answered by ME-287**: the
  cover becomes a chosen timeline position, so it is inside the result by construction (§3.6).

---

## 5. Decision log

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-09-04 | Does anything else tolerate a `uuid` box on an `mp4`'s tail? | **Yes — the gate is open and `save/` proceeds as designed** | ME-281, measured. Our reader and the bundled ffmpeg both re-open the written files, and by hand VLC, Windows Photos and Woka play an edited `mp4` and `mov` exactly as before. This was §4's first open question and the one that could have collapsed `Done ▾` permanently; it did not, so ME-282 writes the Matroska carrier rather than re-planning the folder |
| 2026-09-03 | Where does a video's recipe go? | **`mp4`/`mov` into the existing `uuid` box carrier; Matroska gets its own; `avi` follows `WebpRecipe`'s RIFF surgery** | Two of the five containers already have a shipped, tested carrier; the library is mixed, so Matroska is second and not later (§3.1) |
| 2026-09-03 | Is a whole-file atomic replace acceptable? | **No — an in-place `setLength` plus append, for tail carriers only** | A whole copy of a 4 GB film to change 300 bytes is an OOM and 4 GB of writes. It is safe *only* because a tail box sits after all media data, so a failed write cannot damage the picture (§3.2) |
| 2026-09-03 | Does the viewer honour the cuts? | **Yes, reusing the editor's clip walk** | A viewer that applied the crop and ignored the cut would be worse than one that ignored both (§3.5) |
| 2026-09-03 | Which frame is the cover, and is it folded? | **A chosen *timeline* position, drawn through the fold** | A cover is a frame of the result, not of the source; and folding it is what makes a frozen title frame appear on the grid tile (§3.6) |
| 2026-09-03 | Is the read path a problem? | **No, and the old doc said it was** | `RecipeSlices` already reads a 64 KB tail with a `RandomAccessFile` (ME-126) |
