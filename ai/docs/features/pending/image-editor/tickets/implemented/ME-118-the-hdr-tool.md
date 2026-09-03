# ME-118 — The HDR tool

| | |
|---|---|
| **Order** | last of the 500 track |
| **Depends on** | ME-013, ME-132, ME-177, ME-181, ME-182 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** `Keep` and `To SDR` as an `Output` tool, and a save that honours the second.

**Both halves moved before a line was written**, and in opposite directions.

`Keep` **had nothing to build.** It was specified as needing the ffmpeg encode path because Skia
cannot write PQ. It needs no encoder at all: every format Woka lists resolves to
`EditCapability.Recorded`, which splices a recipe and never touches the pixels — bucket B has been
empty since ME-137. An HDR photograph already keeps its transfer, its primaries and its PQ codes
through an edit. What `Keep` owed was a name in the UI.

`To SDR` **was bigger than the block admitted**, and grew two tickets of its own before it could
exist: ME-181, because nothing here had ever encoded with anything but Skia, and ME-182, because the
first attempt to test that encoder uncovered a decode defect that had nothing to do with it.

## The decision that shaped it

`To SDR` is the **first irreversible operation in this editor**. Everything else records a recipe
`Revert` removes; this rewrites the photograph and the PQ codes are gone — a Revert afterwards finds
an SDR picture and hands it back unchanged. Put to the author 2026-09-03 with three roads (in place
with a warning / a sibling file / drop the tool); the answer was **in place, with a warning**, so
`BakeToSdrDialog` is part of the work rather than a nicety.

It is also worth saying what the feature is *for*: since ME-177 Woka draws these files correctly, so
baking buys nothing here. It is for an application that mishandles PQ.

## Steps
1. `HdrTool` in `tools/hdr/`, following `ResizeTool`: `ToolDomain.Output`, `Single`, one
   `ChoiceParam`, `order = 1` behind Resize — a resize is about the picture, this is about the file
   it becomes.
2. **`stage()` returns null always**, and that is the design. ME-177 tone maps at the decode, so the
   canvas already shows SDR whichever option is chosen and there is nothing for a raster stage to
   add. A test pins it: if it ever stops being null the editor is mapping twice.
3. `bakesToSdr(document)` reads the choice off the document — the road every op already takes to the
   save, since `writeFor` is handed `EditLayers` and nothing else.
4. `writeFor` branches on it **ahead of the capability switch**: a recipe spliced beside baked pixels
   would replay the edit on top of itself.
5. `ImageSaveJob` learns AVIF (null `SaveFormat` → ME-181) and — separately important — decodes
   through **`displayable`** rather than the bare decoder. It read the raw decode, which for an HDR
   source is PQ codes; baking those would have written into the file the very defect ME-177 took off
   the screen. Bucket B being empty is the only reason that was never wrong in practice.
6. The dialog, raised by `Done` and only when the document asks to bake.

## Files
`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/hdr/HdrTool.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/BakeToSdrDialog.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/EditorHostControls.kt` · `EditorHostDialogs.kt`
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/ImageEditWrites.kt`
`shared/src/jvmMain/kotlin/com/woka/feature/editor/save/ImageSaveJob.kt`
`shared/src/jvmMain/kotlin/com/woka/feature/editor/di/EditorModule.kt` — one line
`shared/src/jvmTest/kotlin/com/woka/integration/HdrBakeTest.kt` (new)

## Done when
- `jvmTest/integration/`: baking `hdr_real_pq.avif` leaves it reading `TransferKind.SDR`, and its
  **brightness does not move** — the picture on screen was already the tone-mapped one, so a bake
  that changed how it looks would mean the map ran twice;
- `stage()` is null for both options;
- `./gradlew.bat :shared:build` green — 1307 tests.

## Limits, stated rather than discovered later
- **HEIC/HEIF cannot be baked.** ME-010: `libx265` cannot open a HEIF output at all, so there is no
  road for those pixels. The save refuses rather than half-writing.
- A **video frame** still assumes the white point (ME-120), which does not reach the image editor —
  it never opens a video — but does reach the 200 track when it reuses these tools.
