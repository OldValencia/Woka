# ME-282 — A Matroska carrier

| | |
|---|---|
| **Order** | after ME-281 opened the gate — **done 2026-11-04** |
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

## Measured

**2026-11-04, by the author.** `build/carrier-probe/carrier.mkv` against `reference_carrier.mkv`:
plays identically, picture and sound, in **Woka and VLC**. ffmpeg re-opens it and our reader reads
the recipe back (`VideoCarrierProbe`). Windows Photos was not reported.
