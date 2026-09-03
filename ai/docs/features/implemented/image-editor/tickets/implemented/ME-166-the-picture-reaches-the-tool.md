# ME-166 — The picture reaches the tool

| | |
|---|---|
| **Order** | after ME-162; before ME-167, the only thing that asks for it |
| **Depends on** | ME-005, ME-008 |
| **Feature** | [Image Editor](../../image-editor.md) |

**Goal.** Give a tool a way to read a statistic of the picture — a histogram, a black point — without
giving it the pixels, and without `stage()` ever seeing a frame.

**Why now.** `AdjustTool.kt` carries this comment today:

> *Auto is deliberately absent. An automatic correction reads the picture — a histogram, a black
> point — and a tool receives parameters and nothing else; `EditorTool.resolve` hands over the frame,
> never the pixels. It waits in the backlog for a hook that something actually asks for.*

ME-167 is that ask. The hook is written now, on its own, because it is a contract change and the tool
that uses it is not.

## Steps
1. Add `SourceStats` to `feature/editor/core/` (commonMain): a `data class` of `luma: IntArray` (256
   buckets, normalised), `blackPoint: Float`, `whitePoint: Float`, `meanLuma: Float`. Plain numbers —
   `commonMain` must not name a raster type, which is why `EditorTool` lives there at all (§3.3).
2. Widen `EditorTool.resolve` to `fun resolve(spec: ParamSpec<*>, params: ParamSet, frame: IntSize, stats: SourceStats?): ParamSet? = null`. It already receives the frame and is already the
   documented place where "the shell knows the picture"; this is the same seam carrying one more fact.
   `stats` is nullable because the video engine has no still to measure and passes null.
3. `stage()` is **unchanged**. It stays pure — params in, stage out — which is what lets the same fold
   run at preview scale and at scale 1.0. A tool reads statistics at the moment of a click and writes
   the answer into params; it never reads them while rendering.
4. Add `val stats: SourceStats?` to `EditorEngine` (`EditorEngine.kt`), computed lazily. In
   `ImageEditorEngine`, compute it from the **proxy**, not the native bitmap — a histogram of a
   2560 px downscale is the histogram of the picture, and ME-168 already put that decode on a
   background thread. Return null in the video engine.
5. Thread `stats` from the engine into `ParamWriter` beside the `sourceSize` it already carries, and
   through `ParamRows`'s existing `paramsOf(document, tool, sourceSize)` call path in
   `feature/editor/shell`. One more argument on a call that already exists — no new plumbing.
6. Update the four existing `resolve` overrides (Resize's is the only non-default one) for the new
   signature, changing no behaviour.
7. Delete the "Auto is deliberately absent" paragraph from `AdjustTool.kt` only when ME-167 lands, not
   here — until then it is still true.

## Files
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/SourceStats.kt` (new)
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorTool.kt` (edit, 146 lines — at the mandate, budget a split)
`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorEngine.kt` (edit, 126 lines)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/ImageEditorEngine.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/shell/ParamWriter.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/tools/resize/ResizeTool.kt` (edit — signature only)

## Done when
- `stage(params: ParamSet): ToolStage?` is byte-identical to what it is today — grep it and confirm;
- `jvmTest/unit/` asserts `SourceStats` off a known fixture: `still.png`'s histogram sums to its pixel
  count, and a black-clipped fixture reports a black point above zero;
- the video engine compiles with `stats = null` and no branch anywhere in the shell;
- `./gradlew.bat :shared:build` green.
