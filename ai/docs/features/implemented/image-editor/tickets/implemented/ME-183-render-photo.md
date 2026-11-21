# ME-183 — Render photo

| | |
|---|---|
| **Order** | 1.0.8, before ME-184 |
| **Depends on** | Existing native image save pipeline |
| **Feature** | [Image editor](../../image-editor.md) |

**Goal.** Export the current photo and its working edits into a new file beside the source.
**Why now.** Recorded edits are visible only in Woka; the export seam and raster renderer already exist.

## Steps
1. Extract native rendering and encoding from `ImageSaveJob` into `BakeStill`.
2. Move image engine writes into `ImageEditorOutputs` to keep the engine below 150 lines.
3. Offer `RenderPhoto` through the existing export menu and status surface.
4. Encode JPEG/PNG/WebP/AVIF in their own format; otherwise use JPEG for opaque pixels and PNG for transparency.
5. Reserve a numbered sibling name, write atomically, check source changes and cancellation, and retain JPEG EXIF.
6. Verify decoded output, source preservation, naming, formats and cancellation in integration tests.

## Files
`save/BakeStill.kt`, `save/RenderStill.kt`, `save/ImageSaveJob.kt`,
`engine/image/ImageEditorEngine.kt`, `engine/image/ImageEditorOutputs.kt` under the JVM editor;
`jvmTest/kotlin/com/woka/integration/RenderPhotoTest.kt`.

## Done when
- Render photo exports the current frame with edits and without the recipe; Done still records edits.
- Original bytes remain unchanged and repeated exports use distinct names.
- Shared build and native image audit pass.

**Status:** implemented, 2026-11-21. Author manually verified: everything appears to work. Focused render/copy/safety tests passed; strict native audit clean over 11 runs. Full-suite run encountered an existing FolderSelectionTest assertion; no further reruns at the author's request.
