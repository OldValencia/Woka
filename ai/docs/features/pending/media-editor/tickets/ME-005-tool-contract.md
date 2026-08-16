# ME-005 — Tool contract

| | |
|---|---|
| **Order** | 6 |
| **Depends on** | ME-004 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** `ToolManifest`, `EditorTool`, `CanvasTool`, `ToolStage`, `EditorToolScope`.

**Why now.** It is the interface every later ticket implements, and getting `ToolStage` media-agnostic
here is what makes the video editor a tool set rather than a second application (§3.3).

## Steps

1. `ToolDomain.kt` (commonMain): `enum class ToolDomain { Geometry, Color, Annotate, Output }` with
   KDoc stating both of its jobs — rail grouping *and* application order — plus `ToolCardinality`,
   `ToolInteraction` and `EditorShortcut` alongside it.
2. `ToolManifest.kt` (commonMain): the data class from §3.4. `icon: DrawableResource`
   (`org.jetbrains.compose.resources`), `supports: Set<MediaKind>` reusing the existing
   `core/data/MediaKind.kt` — not a private list of extensions.
3. `ToolStage.kt` (commonMain): `sealed interface ToolStage`, an empty marker. `RasterStage`
   implements it in `jvmMain` (ME-007) because its cases carry graphics types; the video stage will
   implement it later. The marker is what keeps `EditorTool` in `commonMain`.
4. `EditorTool.kt` (commonMain): `manifest`, `params: List<ParamSpec<*>>`, `fun stage(params:
   ParamSet): ToolStage?`, plus `defaults(): ParamSet` derived from `params` so a tool never writes
   its defaults twice.
5. `EditorToolScope.kt` (commonMain): what a `CanvasTool` overlay is handed — `document`,
   `preview(op)`, `commit()`, `sourceSize`, `canvasToSource` / `sourceToCanvas` matrices, and the
   selection slot. `androidx.compose.ui.graphics.Matrix` is available in `commonMain`; use it, so a
   video overlay can take the same scope.
6. `CanvasTool.kt` (commonMain): `@Composable fun Overlay(scope: EditorToolScope)`.

## Files

`feature/editor/core/ToolDomain.kt`, `core/ToolManifest.kt`, `core/ToolStage.kt`, `core/EditorTool.kt`,
`core/EditorToolScope.kt`, `core/CanvasTool.kt` (all commonMain, new)

## Done when

- compiles;
- `jvmTest/unit/ToolContractTest.kt` asserts `defaults()` returns every spec's default and nothing
  else, and that `ToolDomain.entries` is in application order — a test that fails loudly the day
  someone reorders the enum for cosmetic reasons.
