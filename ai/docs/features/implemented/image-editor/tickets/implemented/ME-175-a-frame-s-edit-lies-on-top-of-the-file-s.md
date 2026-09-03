# ME-175 — A frame's edit lies on top of the file's

| | |
|---|---|
| **Order** | before ME-169, whose merge depends on which model wins |
| **Depends on** | ME-126, ME-156, ME-133 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Make a per-frame edit **compose with** the file's own rather than replace it, so
"all frames get this, and frame 3 also gets that" is expressible.

**Why now.** ME-169 asks `Done` to write every frame it touched in one merge, and that
cannot be done under today's rule: `EditScope.AllFrames` writes `EditPayload(ops = …)`
and **discards `frames` entirely**, so a sitting that edited frame 0 per-frame and then
frame 2 for all frames destroys the first inside one `Done`. Three ways of catching that
were considered on 2026-09-02 and the author proposed a fourth that removes it instead:
if a frame's edit lies *on top of* the file's, the two writes do not compete.

**It also answers what people actually do.** Warm every frame of a burst, then straighten
one of them. Today that is two mutually exclusive choices; after this it is a base and a
delta.

## The rule

`EditPayload.documentFor` is a choice today — `frames[frameIndex] ?: EditDocument(ops)`.
It becomes a composition: the file's ops, then the frame's, in that order.

**The fold already does the right thing with the result**, which is why this is a small
change rather than a new engine. Two `Pixels` stages multiply, so a base exposure and a
per-frame contrast both apply. Two `Paint` stages append, so markup adds. `orderedStages`
sorts by domain, not by which document an op came from, so ordering is unaffected.

**Geometry is the case that does not commute.** A base crop and a per-frame crop concat
into a crop of a crop, which is nobody's intent — that is the collision ME-176 warns
about, and this ticket makes it *possible* rather than fixing it.

## Compatibility, which is not free this time

A file already written with both `ops` and a `frames` entry means "frame 3 draws its own
edit **instead**". Read under the new rule it would draw both, and a photograph the author
edited last week would change on opening.

So the payload gains one field: `val layered: Boolean = false`. Absent — every file
written before this ticket — reads the old way; present and true, the new. `editorJson`
leaves defaults out, so nothing is written into a file that does not need it, and an older
build ignores the unknown key exactly as it ignores `frames` (ME-003's
`ignoreUnknownKeys`).

## Steps
1. `EditPayload` gains `layered: Boolean = false`, with the KDoc saying what the flag
   distinguishes and that it exists because files already on disk mean the older thing.
2. `documentFor(frameIndex)` becomes: legacy → `frames[i] ?: EditDocument(ops)`; layered →
   `EditDocument(ops + (frames[i]?.ops ?: emptyList()))`. Not `ops` when the key is
   absent — an absent key is "this frame adds nothing", which is the same document.
3. `with(document, scope, frameIndex)` stops discarding. `AllFrames` writes
   `copy(ops = document.ops, layered = true)` and **keeps** `frames`; `ThisFrame` writes
   the key and `layered = true` as it does now.
4. **What `ThisFrame` stores is the delta**, not the whole document. The editor opens on
   the composed document, so `Done` must subtract the base before writing the key, or the
   base is stored twice and applied twice. `EditDocument.without(base)` — drop the ops the
   base already carries, by identity of `toolId` *and* params.
5. `storedScope` keeps its meaning: `ThisFrame` exactly where a key exists.
6. Everything else is untouched. No carrier changes, no reader changes — every reader goes
   through `documentIn`, which goes through `documentFor`.

## Files
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditPayload.kt` (edit, 81 lines)
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditDocument.kt` (edit, 68 lines)

## Done when
- `jvmTest/unit/`: a legacy payload (`layered = false`) with both an `ops` list and a
  `frames` key draws **only** the key's document, exactly as it does today;
- `jvmTest/unit/`: a layered payload draws the base then the frame's, in that order;
- `jvmTest/unit/`: a frame with no key draws the base under both rules;
- `jvmTest/integration/`: a burst saved All Frames, then frame 2 saved This Frame, draws
  the base on frames 0 and 1 and base-plus-delta on frame 2 — the scenario that loses work
  today;
- `jvmTest/unit/`: `with(…, AllFrames, …)` no longer empties `frames`;
- `./gradlew.bat :shared:build` green.

## Note
Confirmed by hand belongs to **ME-176**, which puts the behaviour on screen. Nothing here
is visible on its own except that a per-frame edit stops discarding the file's.
