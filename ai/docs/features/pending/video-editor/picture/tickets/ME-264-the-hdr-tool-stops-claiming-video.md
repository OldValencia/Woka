# ME-264 — The HDR tool stops claiming video

| | |
|---|---|
| **Order** | out of band, on the author's word: it is one manifest line and the rail was visibly wrong |
| **Depends on** | ME-201 — there was no video rail to be wrong until it landed |
| **Feature** | [Picture tools](../picture.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** Take the HDR tool out of a video's rail, and leave a photograph's untouched.

**Why now.** ME-201 made every declared tool appear on a video, because `ToolManifest.supports`
defaults to both media kinds — which is exactly what makes reusing the rail free. The author's first
run reported the one entry that default gets wrong.

**Why it is the tool's decision and not the rail's.** What HDR decides is *what a save writes*. For a
still that is a pixel operation this tool can perform — `To SDR` bakes the tone map and re-encodes.
For a video it is a transfer characteristic on an encoded stream, which belongs to
[`render/`](../../render/render.md) in a vocabulary this tool does not speak. A control that cannot
move what it names is worse than no control, which is the same line ME-180 held when it hid the
pill's HDR switch for video.

---

## Steps

1. `HdrTool.manifest`: add `supports = setOf(MediaKind.Image)`, with a comment saying it is **the
   first `supports` override in the app** and why this tool cannot take the default. Import
   `MediaKind`; the file goes from 95 to 96 lines.
2. Add `VideoRailToolsTest` in `jvmTest/kotlin/com/woka/unit/`, against the **app's own registry
   resolved from Koin** rather than a fixture: a fixture would only prove `EditorToolRegistry` can
   filter, which `EditorToolRegistryTest` already proves. Two assertions — HDR's `supports` is stills
   alone, and a video's rail excludes `hdr` while still offering `crop`, `transform`, `adjust`,
   `filter`, `markup` and `resize`. It names the **exception** and not the whole rail: every tool
   reused on video is reused *because* it said nothing, and asserting the full list would turn adding
   an image-and-video tool into a test edit.
3. Nothing else. `bakesToSdr` is read by `EditorHostControls` and `ImageEditWrites`, and both become
   unreachable for a video by construction — no HDR op can enter a document whose rail never offered
   the tool. `EditorSkippedOps` would report one that somehow existed, which is the behaviour already
   specified for a missing tool.

## Files

`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/hdr/HdrTool.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/unit/VideoRailToolsTest.kt` (new)

## Done when

- A video's rail has no HDR entry; a photograph's still does, and its Output group is unchanged.
- `VideoRailToolsTest` passes, and no existing HDR test changes — `HdrBakeTest` and the bake dialog
  are about stills and were never asked about video.
- The anti-FQN grep over both files is clean.
- `./gradlew.bat :shared:build` green.

---

## Done — 2026-09-03

Both files as planned, `:shared:build` green. `Resize` deliberately **stayed** on video: it is
ME-262's job to give it a meaning there — the render's output resolution — rather than to hide it,
because the author asked for it by name and "how big is the file I get" has nowhere else to live.
