# ME-101 — Editor shell

| | |
|---|---|
| **Order** | 15 — first ticket with pixels |
| **Depends on** | ME-008, ME-011, ME-012 |
| **Feature** | [Media Editor](../media-editor.md) |

**Goal.** Build the editor screen — top bar, canvas host, inspector container, tool rail — driven by
the registry and the session, and provable against `FixtureTool` before any real tool exists.

**Why now.** Everything under it is built and nothing above it can start: ME-102's row renderers need a
container to sit in, ME-103's surface needs a canvas host to measure it, and ME-105's first real tool
needs a rail to appear in.

## Scope

**This ticket does not mount the editor and does not save.** `EditorScreen` is a composable taking its
collaborators as parameters; ME-106 mounts it in `FolderScreen` and owns the keyboard takeover, and
ME-104 owns what `onDone` does. Nothing here touches `GlobalShortcutState`, `FolderScreen` or
`SaveTarget`.

**The shell is the video editor's shell too.** Nothing here may branch on media: `EditorScreen` takes
an `EditorEngine` and reads `engine.kind` where a kind is needed, never a literal `MediaKind.Image`.
The rail, the inspector, the top bar and the canvas host are the *same files* ME-201 inherits — a trim
tool differs from a crop tool in its manifest and its `stage()`, and in nothing the shell can see. If
building the video editor later requires editing any file in this ticket, §3.3 was wrong and the fix
belongs there rather than in a branch (`ToolManifest.supports` is what makes a tool appear or not).

**The canvas is `Fit` only.** No wheel zoom, no pan, no gestures — decided 2026-08-18 (§9): above 100 %
the canvas draws a ≤2560 px proxy, so zoom without ME-116's native-region draw ships a feature that
looks broken. `EditorCanvasHost` computes a `Fit` matrix and nothing else.

## Two things the tree forces, found while writing this

- **The shell cannot read `RasterRecipe.skipped`.** `EditorEngine` deliberately exposes no `recipe()`
  (§3.3) — the moment the shell can name `RasterRecipe`, every file touching it becomes a video ticket.
  So the "N operations were not applied" notice is **derived by the shell**, from what it already has:
  an op is skipped when `registry.toolFor(op)` is null or the tool is absent from
  `registry.tools(kind).value`. Same rule `RasterFold.resolve` applies, computed independently rather
  than plumbed out of the engine.
- **The UI test needs a fake `EditorEngine`.** `ImageEditorEngine.Surface` is `TODO("ME-103")`, so
  ME-011's harness cannot drive a shell on its own. `FixtureEngine` is added beside `FixtureTool`.

`floatingPanel()` is deliberately **not** reused for the rail and inspector: it paints
`Theme.colors.surface`, which in the light theme is white at 80 % — over a photograph that leaves the
`onScrim*` foregrounds nothing to sit on. Both take `Theme.viewer.popoverScrim` and
`Theme.viewer.onScrimBorder`, matching the viewer's own popovers and the mockup.

## Steps

1. `support/FixtureEngine.kt` (`jvmTest`): an `EditorEngine` with `kind = MediaKind.Image`, a
   constructor-supplied `sourceSize`, a `Surface` drawing a `Box` with a solid colour and a
   `testTag("editor-surface")`, and a `save` returning a constructor-supplied `EditSaveResult`. Records
   the documents it was asked to draw, so a test can assert the canvas re-renders on commit.
2. `shell/EditorCanvasHost.kt`: `@Composable internal fun EditorCanvasHost(engine, document, modifier)`.
   Measures itself with `onSizeChanged`, computes the `Fit` scale from `engine.sourceSize` against the
   measured size, and composes `engine.Surface(document, Modifier)` centred at that scale. Exposes the
   two matrices as an `EditorToolScope` implementation for the active tool's overlay — `canvasToSource`
   and `sourceToCanvas` built from the same scale and offset, never measured separately, so they cannot
   disagree.
3. `shell/EditorTopBar.kt`: `Cancel` on the left; centre shows the file name plus `· Edited` when
   `session.isDirty` collects true; right holds undo and redo enabled from `session.canUndo` /
   `session.canRedo` and a `Done` pill. Geometry from `EditorTokens.TopBar`, colours from
   `Theme.viewer.onScrim` / `onScrimSecondary` / `onScrimDisabled`. `Done` and `Cancel` are
   `() -> Unit` parameters; the save-target menu belongs to ME-104 and a menu whose `Overwrite` entry
   did nothing would be worse than a button.
4. `shell/EditorRail.kt`: collects `registry.tools(engine.kind)`, groups consecutive tools by
   `manifest.domain`, and draws one 1.dp × 20.dp divider at each boundary — from the grouping, never
   from a hardcoded count, so a domain with no enabled tools contributes no divider. States: selected
   (`onScrimSelected` fill, `accentEditor` icon), plain (`onScrimSecondary`), and disabled
   (`onScrimDisabled`, not clickable, tooltip carrying the reason). Reads `EditorTokens.Rail`.
5. `shell/EditorInspector.kt`: the card. Header shows the active tool's `manifest.title` and a `Reset`
   action calling `session.reset()`; body is a `content: @Composable () -> Unit` slot ME-102 fills;
   footer draws the skipped-ops notice when the derived list is non-empty, naming the distinct tool
   titles. Hidden entirely when `activeToolId` is null. Reads `EditorTokens.Inspector`.
6. `shell/EditorSkippedOps.kt`: `internal fun skippedOps(document: EditDocument, registry:
   EditorToolRegistry, kind: MediaKind): List<ToolOp>` — the derivation named above, pure and unit
   tested, so the notice cannot drift from what the fold actually skips.
7. `shell/EditorScreen.kt`: composes the four above over `ViewerChrome`'s scrim. Owns
   `activeToolId: String?` in a `remember` **and nothing else** — every other piece of state is the
   session's or the registry's. Selecting a tool that has left the registry clears it, so a tool
   switched off in Settings while open does not leave an inspector for a tool that is gone.

## Files

`jvmMain feature/editor/shell/EditorScreen.kt`, `shell/EditorTopBar.kt`, `shell/EditorRail.kt`,
`shell/EditorInspector.kt`, `shell/EditorCanvasHost.kt`, `shell/EditorSkippedOps.kt` (all new),
`jvmTest support/FixtureEngine.kt` (new), `jvmTest unit/EditorSkippedOpsTest.kt` (new),
`jvmTest ui/EditorShellUiTest.kt` (new)

## Verification

`unit/EditorSkippedOpsTest.kt` — an op from an unregistered id is skipped; an op from a registered but
disabled tool is skipped; an op from an enabled tool is not; the list keeps document order.

`ui/EditorShellUiTest.kt`, on `onEditor` + `FixtureTool` + `FixtureEngine`:

- the rail lists one fixture per `ToolDomain`, in domain order, with three dividers;
- clicking a rail item opens the inspector with that tool's title; clicking it again is idempotent;
- a disabled fixture is present, not clickable, and does not open an inspector;
- disabling the active tool through `settings.setEditorToolEnabled` closes its inspector;
- `· Edited` appears only after a commit, and undo/redo enablement tracks `session.canUndo/canRedo`;
- `Cancel` and `Done` invoke their callbacks exactly once;
- the skipped-ops notice appears when a committed op's tool is switched off, and names it.

## Done when

- the seven files above exist, none over 150 lines;
- `EditorScreen` references no `RasterRecipe`, no `SaveTarget`, no `GlobalShortcutState` and no
  `FolderScreen` — grep proves it;
- **no file in `shell/` names `MediaKind.Image` or `MediaKind.Video`** — grep proves it. The one place a
  kind appears is `engine.kind`, passed through to the registry. This is the mechanical form of "the
  video editor inherits the shell";
- `./gradlew.bat :shared:build` green, with the tests above passing.

## Result — 2026-08-18

Eight files in `feature/editor/shell/` plus three in `jvmTest`. `:shared:build` green, **575 tests, 0
failures** (563 before, +12: six unit, six UI). Largest file 121 lines.

Every Done-when condition checked mechanically, not by eye:

| Condition | |
|---|---|
| no file in `shell/` names `MediaKind.Image`/`.Video` | ✅ grep clean — the only kind spoken about is `engine.kind` |
| `EditorScreen` names no `RasterRecipe`, `SaveTarget`, `GlobalShortcutState`, `FolderScreen` | ✅ grep clean |
| every file ≤ 150 lines | ✅ max 121 |
| anti-FQN | ✅ clean across all eleven files |

### The shell test found a design bug, in the ticket rather than in the code

Step 5 said the inspector's **footer** draws the skipped-ops notice, and the mockup drew it inside the
card. Implemented that way, `the skipped notice appears when a committed op's tool is switched off`
failed — and it was right to.

**The inspector only exists while a tool is open.** So a user who switches a tool off in Settings and
comes back to the editor is shown *nothing*: the warning is reachable only by opening some unrelated
tool and noticing it at the bottom of that tool's panel. §3.4 promises a document "never crashes and
never silently loses work", and the notice is the entire mechanism of the second half — one that
appears by accident does not keep the promise.

Fixed in the design, not in the test: `EditorSkippedNotice` is its own composable, stacked **above**
the inspector and shown whenever `skippedOps` is non-empty, tool open or not. `EditorInspector` lost two
parameters and got smaller. `ai/mockups/design/editor.html` §4 is redrawn to match — the mockup is the
spec, so it changes first.

### Two smaller things the tree decided

- **`EditorCanvasGeometry` is a separate file from the host.** The matrices are the part with a
  correctness claim — `canvasToSource` is computed by inverting `sourceToCanvas` rather than written
  out, so the two cannot drift — and that claim is testable without a composition. The host is layout.
- **`EditorTopBar` split at 152 lines** into the bar and `EditorTopBarControls`. The seam is real rather
  than arithmetic: the bar is *what sits where*, the controls are *what a button looks like*, and
  ME-104's save menu asks the second question again.

`EditorCanvasHost` takes `overlay` as a `@Composable (EditorCanvasGeometry) -> Unit` rather than
handing the geometry out through a callback — a callback fired from composition is a side effect, and
the overlay needs the matrices synchronously anyway.
