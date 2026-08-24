# Feature: Video Editor (Roadmap Phase 5b)

The second half of Phase 5. Everything structural already exists: the document, the history, the tool
contract, the schema-driven inspector, the shell, the shortcut table, the save banner —
[the image editor](../image-editor/image-editor.md) built all of it as ME-001…ME-014, deliberately
media-agnostic, and this feature is a tool set, an engine and a saver plugged into it.

Split out of `pending/media-editor/` on 2026-08-24, so that a folder holds one shipping unit. **The
ticket IDs are unchanged** — ME-201…ME-204, on the ME prefix the shared foundation is numbered in —
because an ID is stable and never reused (`conventions/feature-docs.md` §3).

```
video-editor/
  video-editor.md   ← you are here: what the thing is and why. The feature file.
  tickets/          backlog.md, plus one file per ticket once one is promoted
```

Conventions: `ai/docs/conventions/feature-docs.md` · `tickets.md` · `code-style/README.md`.
Architecture, in full and not repeated here: [`../image-editor/image-editor.md`](../image-editor/image-editor.md)
§3 (the document, the fold, the engine seam, the tool contract) and §4 (the shell this inherits
unchanged).

---

## 1. Status

Nothing is built. This table is the only place a ticket's state is recorded.

| Ticket | Title | State |
|---|---|---|
| [ME-201](tickets/backlog.md) | Video engine | backlog |
| [ME-202](tickets/backlog.md) | Trim tool | backlog |
| [ME-203](tickets/backlog.md) | Mute tool | backlog |
| [ME-204](tickets/backlog.md) | Video save | backlog |

The foundation these claim is done: ME-001…ME-014, and ME-106 for the entry points, all in the image
editor's table. Nothing here is blocked by an unshipped image ticket — 5a's remaining work is tools
and polish, not shell.

---

## 2. Scope

**In:** Trim by keyframe-snapped stream copy, and Mute. Same shell, same history, same undo, same
`Done`.

**Out:** re-encoding filters of any kind, precise (non-keyframe) trim, speed changes, concatenation,
audio editing beyond the mute switch, subtitles, and anything that touches the picture's pixels.

**Formats:** whatever the player already opens and ffmpeg can stream-copy. The list is written when
ME-201 is promoted, against the probe results of the day, not guessed here.

---

## 3. What this feature actually adds

Three things, and no fourth:

- **An `EditorEngine` for `MediaKind.Video`.** The existing player is the live surface; the fold
  produces a timeline edit rather than a raster recipe; the saver runs the Bytedeco-bundled ffmpeg as
  a background process. This is the seam §3.3 of the image editor's doc exists for — the shell
  composes `engine.Surface(...)` and never branches on media kind.
- **Two tools**, both on the ordinary contract: Trim is a `CanvasTool` (dual handles over the
  timeline), Mute is a manifest and one `BoolParam` with no UI code at all.
- **A saver** that reuses the image save targets, the atomic write, the cache invalidation and the
  banner — sharing the implementation, not copying it.

**Mute is the acceptance test of the whole design.** If adding it takes more than a file and a Koin
line, §3.4 of the image editor's doc is wrong. Likewise the reverse: if any ticket here needs the
shell changed, the foundation was wrong and the fix belongs in the foundation, not in a branch.

---

## 4. Open questions — answered when a ticket is promoted, not before

These are the things this document deliberately has no answer for. Each is a real fork, and guessing
one now would put a wrong sentence into a doc that is supposed to be true.

- **Does the recipe model reach video at all?** §5 of the image editor is "the `EditDocument` goes
  into the file's private metadata slot and pixels are never rewritten". MP4 and MOV are ISOBMFF, so
  the `uuid` box carrier ME-108 built for HEIC and AVIF is *structurally* available — but a recipe is
  only worth writing if every reader folds it, and the reader here is a decoding player, not a
  bitmap. A trim that is a recipe means the player skips a range on every open; a trim that is a save
  means ffmpeg writes a new file. **The backlog blocks were written before §5 was rewritten
  (2026-08-19) and still say `Save a Copy` is the default — that phrase no longer describes anything
  in the app**, and ME-204 has to be re-read against §5 when it is promoted rather than implemented
  as written.
- **What a keyframe-snapped trim tells the user** when the cut they asked for is 400 ms from the cut
  they get. Snapping to real keyframes is what keeps the preview honest; showing the snap is a UI
  decision nobody has made.
- **Whether the trim strip is the player's scrubber or a second timeline.** The viewer already has a
  slider; the editor is a mode over the viewer, and two timelines stacked would be the submenu problem
  in a different costume.
- **What an overwrite invalidates.** For an image it is `ImageBitmapCache` plus the preview key
  (2026-08-17). A video also has a poster frame and a watch record, both keyed on the file.
- **Playback while editing.** `EditorEngine.togglePlayback()` already exists (ME-140, added for
  animated stills on the image canvas) and the video engine is its obvious second implementation —
  but "the canvas plays" and "the canvas plays *the trimmed range*" are not the same feature.

---

## 5. Decision log

| Date | Question | Decision | Why |
|---|---|---|---|
| 2026-08-24 | Own folder, or stay under `media-editor/`? | **Own folder, `pending/video-editor/`** | A feature folder is a shipping unit; the image half is nearly done and the video half is unstarted, and one status table covering both said "backlog" for a quarter of its rows indefinitely |
| 2026-08-24 | Renumber the tickets to a `VE-` prefix? | **No — ME-201…ME-204 stand** | IDs are stable and never reused (`feature-docs.md` §3). The foundation these depend on is ME-numbered and shared; a rename would break every citation to buy a nicer prefix |
| 2026-08-16 | Does `stage()` return `RasterStage`? | No — a `ToolStage` marker | A trim stage is not a matrix; without this the video editor cannot reuse the tool contract *(carried from the image editor's log — it is the decision this feature rests on)* |
| 2026-08-16 | How does video reuse the shell? | An `EditorEngine` per `MediaKind` | The shell composes `engine.Surface(...)` and never branches on media *(carried, same reason)* |
