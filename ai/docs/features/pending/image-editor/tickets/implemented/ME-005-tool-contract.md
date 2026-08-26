# ME-005 — Tool contract

| | |
|---|---|
| **Order** | 6 |
| **Depends on** | ME-004 |
| **Feature** | [Image Editor](../../image-editor.md) |

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

## Result — 2026-08-16

`./gradlew.bat :shared:build` green. **509 tests, 0 failures, 0 errors** — 506 before, plus three
here. Six new files, 18–84 lines.

**`ToolCardinality` was not redeclared.** Step 1 lists it among the types to create beside
`ToolDomain`, but ME-004 already needed it for `EditDocument.commitPending` and put it in
`core/ToolCardinality.kt`, whose KDoc says the manifest would name it rather than redeclare it.
`ToolManifest` names it. `ToolDomain.kt` holds `ToolDomain`, `ToolInteraction` and `EditorShortcut`.

**Two questions carried from earlier tickets, both now closed:**

- *Does `ActionParam` need a press count?* No. ME-003 flagged that its `Boolean` might have to become
  a counter if a tool needed repeated presses distinguished. `ToolCardinality.Repeating` in the
  manifest is what makes two presses two operations, exactly as that KDoc predicted, so the spec is
  unchanged and the question is settled rather than deferred again.
- *Is `androidx.compose.ui.graphics.Matrix` reachable from `commonMain`?* Yes — step 5's claim holds,
  and `EditorToolScope` uses it, so a video overlay can take the same scope an image overlay does.

**One thing not yet proven.** `ToolStage` compiles as an empty sealed interface, but it has no
implementor, so the claim that `jvmMain` can implement a `commonMain` sealed interface across source
sets is still an argument rather than a fact. ME-007's `RasterStage` is what tests it. If it does not
hold, the marker becomes a plain interface and nothing else about the design changes.

**A trap for the next test file in `com.woka.unit`.** `ToolContractTest` first declared
`private enum class Fit`, which collided with the identically named one in `ParamSetTest` — top-level
`private` in Kotlin does not prevent a package-level redeclaration clash. Renamed to `FakeFit`. Test
helper types in this package need names that are unique across the package, not merely across the
file.
