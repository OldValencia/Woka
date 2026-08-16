# ME-010 — Capability probes

| | |
|---|---|
| **Order** | 11 |
| **Depends on** | ME-001, ME-013 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Answer, on this machine, what the decoders and the caches actually do — instead of assuming
it in eight later tickets.

**Why now.** Every save-path decision (EXIF, formats, HDR, cache staleness, memory) is currently a
guess.

**Where the output goes.** Into this ticket, dated, per `ai/docs/conventions/feature-docs.md` §7.
There is no `ai/docs/testing/` tree to recreate — the earlier one was read, acted on and deleted
deliberately, and this ticket used to open by restoring it. It does not any more.

## Steps

1. `EditorFormatProbe.kt` (`jvmTest/probe/`, following `DecoderCapabilityProbe` + `CapabilityReport`):
   for every extension in `MediaKind`, decode through the existing `StillDecoders` path, re-encode via
   Skia to PNG and JPEG, re-decode, and print dimensions, max channel delta and round-trip time. This
   is the conversion matrix §7 needs.
2. `EditorExifProbe.kt`: (a) open a JPEG carrying `Orientation=6` through the viewer's decode path and
   record whether it displays upright; (b) dump the marker segments before and after a Skia re-encode
   and verify that splicing the source `APP1` with `Orientation` forced to 1 produces a file both Woka
   and Explorer read correctly.
3. `EditorCacheProbe.kt`: overwrite a file the grid has already drawn, then record which of
   `ImageBitmapCache`, `.thumbnails` and Coil still serve the stale picture. That is the exact
   invalidation list ME-104's overwrite path has to implement.
4. `EditorHdrProbe.kt`: the question §6.1 is gated on. Can the bundled ffmpeg **write** a PQ and an
   HLG file — AVIF via libaom, HEIF via libx265 — that (a) Woka's own `StillColorProbe` reads back
   with the right transfer, and (b) Windows Photos opens as HDR? Then the reverse: tone-map a PQ
   source to SDR through the existing `HdrShader` path and record the round-trip delta. The answer
   decides whether the HDR tool ships with `To HDR` or with `Keep` + `To SDR` only.
5. `EditorMemoryProbe.kt`: decode a 24 MP and a ~100 MP source, build the ≤ 2560 px proxy, render a
   full-size recipe into a Skia surface, and record peak native bytes and wall time against
   `ImageBitmapCache.retainedBytes`. This is the number the "very large sources" gap in §10 is waiting
   on — it decides whether the editor refuses, warns, or tiles.
6. Probes print a table and assert nothing about the machine (`code-style/test.md`). Paste the output
   into this ticket under a dated heading that names the ffmpeg version it came from, and write the
   decisions they settle into the feature's decision log (§9).

## Files

`jvmTest/probe/EditorFormatProbe.kt`, `probe/EditorExifProbe.kt`, `probe/EditorCacheProbe.kt`,
`probe/EditorHdrProbe.kt`, `probe/EditorMemoryProbe.kt` (new), this ticket,
`ai/docs/features/pending/media-editor/media-editor.md` (decision log)

## Done when

- the five probes run green and their output is recorded here with a date and the ffmpeg version it
  came from;
- §6 of the feature doc has no "probe first" left unanswered;
- the memory numbers are recorded even if the guard rail they imply is left to a later ticket.
