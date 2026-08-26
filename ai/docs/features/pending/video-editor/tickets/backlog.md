# Video Editor — Backlog

The whole feature, four blocks. Each says what it is for and what shape it takes, and **names no
files** — those are written when the block is promoted into its own ticket file, against the tree as
it is then (`ai/docs/conventions/tickets.md`).

Design behind all of it: [the feature doc](../video-editor.md), and the shell it inherits,
[the image editor](../../image-editor/image-editor.md).

Order is dependency order. `ME-106` is the image editor's entry-points ticket, already done.

---

## 200 — Video editor

*Every ticket here is a claim on ME-001…ME-014: if one of them needs a change to the shell, the
foundation was wrong and the fix belongs there, not in a branch.*

### ME-201 — Video engine
An `EditorEngine` for `MediaKind.Video`: the existing player as the live surface, a timeline stage
folded from the document, and a saver that runs the Bytedeco-bundled ffmpeg as a background process.
The shell is not touched.
*Depends on [ME-106](../../image-editor/tickets/implemented/ME-106-entry-points.md).*

### ME-202 — Trim tool
`Geometry`, `Single`, `Canvas`: dual handles on the timeline, snapping to real keyframes read from the
container so the preview never lies about where the cut lands. Stream copy only — precise, re-encoding
trim is out of scope.
*Depends on ME-201.*

### ME-203 — Mute tool
One manifest, one boolean, no UI code. This is the acceptance test of the whole tool architecture: if it
takes more than a file and a Koin line, §3.4 of the feature doc is wrong.
*Depends on ME-201.*

### ME-204 — Video save
The same save targets, atomic write, cache invalidation and banner as images, over the ffmpeg job —
sharing the implementation, not a copy of it. `Save a Copy` is the default here too; an overwrite
invalidates the poster frame and the watch record with the file.
*Depends on ME-202, ME-203.*
