# ME-302 — The HDR tool leaves an SDR picture's rail

| | |
|---|---|
| **Order** | first in `shell/` |
| **Depends on** | nothing |
| **Feature** | [The shared editor shell](../shell.md) |

**Goal.** Give the editor a per-file question — what colour the source was written in — and use it to
drop the HDR tool from the rail of a picture that has no high dynamic range.

**Why now.** Reported 2026-09-03: an **SDR** photograph offers the HDR tool, and nothing it contains
can move anything. Detection is not the hard part (`TransferKind` is read at the decode); what is
missing is a channel from the file to the shell. `tools(kind)` filters on `manifest.supports` — the
*medium* — and on Settings — the *preference*. This is a third question, asked of the **file**, and
the registry has not got it. Every later per-file hide reuses whatever this builds.

**Split from the backlog block on promotion.** That block named three surfaces; this ticket is the
rail alone. The pill's dropdown — the viewer's and the editor's own — became **ME-303** in
[`../../tickets/backlog.md`](../../backlog.md) §300 and depends on step 2 below.

---

## Design notes read against the tree, 2026-09-04

- **Hidden, not greyed.** The author's decision (backlog §300): *"скрывать только на SDR картинках…
  Поэтому скрыть во вьювере и редакторе."* `EditorRail`'s `unavailable` map already draws a
  struck-through button with a reason, and it is the wrong shape here — it says "this tool cannot
  apply *here*", and a greyed HDR entry on every photograph in a library that is almost all SDR is
  permanent furniture explaining an absence.
- **The predicate is `needsToneCurve(transfer)`**, which already exists in `core/data/TransferKind.kt`
  and is exactly `transfer != TransferKind.SDR`. **Wide-gamut SDR therefore keeps the hide**, even
  though `displayable` gamut-maps it: that function's own KDoc records that BT.2020 primaries with an
  ordinary transfer are *not* HDR, and a control labelled `HDR` has nothing to say about such a file.
- **Not on `SourceStats`.** It is the obvious place and it is wrong: stats are measured off the proxy
  and are null until the canvas has decoded one, so a rail filtered on them would show the HDR button
  and then take it away under the cursor.
- **`registry.tools(kind)` is not widened.** `EditorSkippedOps` and `RasterFold.resolve` both read it
  to tell "no such tool" from "tool switched off, its work was not applied". A third meaning inside
  that one answer makes the skipped-ops notice claim a hidden tool's operations were dropped — a
  notice that lies, which §3.4 says is worse than none. The filter therefore sits over what the
  registry returns.
- **Two files are at the mandate's ceiling and each carries a split as its own step**:
  `EditorEngine.kt` 148 (step 1) and `ImageEditorEngine.kt` **155, already over** (step 3).
  `EditorScreen.kt` is 150 and step 7 swaps one line for one line, so it neither grows nor is split —
  that file's split is still nobody's ticket.

**Known consequence, recorded rather than fixed.** A stored `ToSdr` operation on a file that now
reads SDR stays in the document and still bakes on `Done`: `bakesToSdr` reads the document, never the
registry, and this ticket hides a button rather than filtering operations. It cannot arise through
Woka's own path — a bake rewrites the picture and `Revert` removes the record — and it is written
here so nobody later reads the hide as an op filter.

---

## Steps

1. **Split `EditorEngine.kt` (148 lines).** New `EditorEnginePicture.kt` in the same package, with
   `sourceSize`, `stats`, `drawnSize`, `previewMatrix` and `frames` moved across **with their KDoc
   verbatim**; its header states the cut — *what the picture is*, as against
   `EditorEngineSurface`'s *what it draws* (ME-156's precedent) — and repeats that it names no
   bitmap, player or recipe. `EditorEngine` becomes
   `interface EditorEngine : EditorEngineSurface, EditorEnginePicture` and loses its now-unused
   `Matrix` and `IntSize` imports. **No implementation and no call site changes**, exactly as ME-156's
   split did not: an override of a grandparent's property is still an override.

2. **`sourceColor` on `EditorEnginePicture`.**
   `val sourceColor: StillColorInfo get() = StillColorInfo.Sdr`, importing
   `com.woka.core.data.StillColorInfo`. Its KDoc says what the property is for — the question asked of
   the **file**, where `manifest.supports` asks it of the medium and Settings of the preference — and
   why the default is SDR: `VideoEditorEngine` and every test fake inherit it without a line, so a
   video keeps the answer ME-180 gave it and ME-301 is the ticket that changes that.

3. **Make room in `ImageEditorEngine.kt` (155 lines).** Move the private `reEncode` and **its whole
   KDoc** into `ImageEditWrites.kt` (125 lines, whose header already covers "every write the image
   engine has") as
   `internal fun reEncodeStill(file, frameIndex, sourceSize, stamp, recipeOf, document): EditSaveResult`,
   a single `ImageSaveJob(...).save(document)`. The engine keeps a two-line private `reEncode` that
   calls it, so `::reEncode` goes on pointing at the same thing and `writeFor`'s signature is
   untouched. Net −14 lines.

4. **`ImageEditorEngine` answers step 2.**
   `override val sourceColor: StillColorInfo by lazy { readStillColor(file) }`, importing
   `com.woka.feature.viewer.image.readStillColor`. **`by lazy` for `sourceSize`'s stated reason**: the
   factory decides editability from the format alone and must not touch the disk to grey out a menu
   item. Forced during composition like `sourceSize` already is, and one demuxer open is the same
   class of read `nativeFrameSize` does there today.

5. **`EditorTool.appliesTo`.** Add
   `fun appliesTo(color: StillColorInfo): Boolean = true` to `EditorTool`, beside `rows` and
   `resolve` — the two existing hooks that ask a tool about the picture rather than about its
   parameters. Defaulted, so no tool but one says anything. Its KDoc states that the parameter is a
   colour because colour is the only per-file question anything asks today, and that it widens when a
   second one appears rather than being replaced.

6. **`HdrTool` refuses an SDR picture.**
   `override fun appliesTo(color: StillColorInfo) = needsToneCurve(color.transfer)`, importing
   `com.woka.core.data.needsToneCurve`. One comment: this is the second override in the manifest's
   neighbourhood and the reason is the same one `supports = setOf(MediaKind.Image)` gives — a control
   that cannot move what it names is worse than no control.

7. **The shell filters.** New `EditorToolsForPicture.kt` in `feature/editor/shell`:
   - `internal fun List<EditorTool>.forPicture(color: StillColorInfo) = filter { it.appliesTo(color) }`
     — pure, so it is unit-testable without a window;
   - `@Composable internal fun toolsForPicture(registry: EditorToolRegistry, engine: EditorEngine): List<EditorTool>`,
     collecting `registry.tools(engine.kind)` and returning
     `remember(enabled, engine) { enabled.forPicture(engine.sourceColor) }`.

   **The `remember` is load-bearing**, and the KDoc must say so: `EditorScreen` has
   `LaunchedEffect(tools)` to clear a vanished tool, and a fresh `filter` per recomposition changes
   the list's identity and restarts that effect several times a second.

   `EditorScreen.kt:58` swaps one line for one line —
   `val tools by registry.tools(engine.kind).collectAsState()` becomes
   `val tools = toolsForPicture(registry, engine)`. `collectAsState` and `getValue` stay imported;
   lines 55–57 still use both.

8. **Tests.**
   - `jvmTest/unit/ToolsForPictureTest.kt` — `HdrTool.appliesTo` is false for `StillColorInfo.Sdr`,
     false for wide-gamut SDR (the rule `TransferKind.kt` states), true for `PQ` and for `HLG`; and
     `listOf(HdrTool, FixtureTool()).forPicture(StillColorInfo.Sdr)` drops `hdr` and keeps the other,
     which is what proves the filter is a filter and not a special case.
   - `jvmTest/ui/EditorRailHdrUiTest.kt` — `onEditor(listOf(HdrTool))` and `EditorScreen` over
     `FixtureEngine()`: no `editor-tool-hdr` node. The same over
     `FixtureEngine(sourceColor = StillColorInfo(TransferKind.PQ))`: the node is displayed. The tags
     are the ones `EditorRail` already writes.
   - `FixtureEngine.kt` (148 lines) gains its constructor line and one import —
     `override val sourceColor: StillColorInfo = StillColorInfo.Sdr,` with no KDoc, because the name
     is the documentation and two lines is exactly the room the file has.
   - `jvmTest/unit/VideoRailToolsTest.kt` is **not** touched: it asserts `manifest.supports`, which is
     a different question and still has the same answer.

## Files

`shared/src/commonMain/kotlin/com/woka/feature/editor/core/EditorEnginePicture.kt` (new) ·
`.../core/EditorEngine.kt` (edit) · `.../core/EditorTool.kt` (edit) ·
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/ImageEditorEngine.kt` (edit) ·
`.../engine/image/ImageEditWrites.kt` (edit) · `.../tools/hdr/HdrTool.kt` (edit) ·
`.../shell/EditorToolsForPicture.kt` (new) · `.../shell/EditorScreen.kt` (edit) ·
`shared/src/jvmTest/kotlin/com/woka/support/FixtureEngine.kt` (edit) ·
`.../jvmTest/kotlin/com/woka/unit/ToolsForPictureTest.kt` (new) ·
`.../jvmTest/kotlin/com/woka/ui/EditorRailHdrUiTest.kt` (new)

## Done when

- an SDR photograph's rail draws no HDR button, and a PQ or HLG one draws it — the two UI tests;
- nothing else about the rail moves: `EditorRail`, its `unavailable` map and its dividers are
  untouched, and a tool switched off in Settings still leaves the registry rather than this filter;
- `EditorSkippedOps` and `RasterFold` are untouched, so the skipped-ops notice still means "switched
  off" and never "hidden for this file";
- the video rail is unchanged — `VideoRailToolsTest` green with no edit, `VideoEditorEngine`
  untouched, and `sourceColor` inherited as SDR;
- `wc -l` reports ≤ 150 for `EditorEngine.kt`, `ImageEditorEngine.kt`, `EditorScreen.kt`,
  `ImageEditWrites.kt`, `EditorTool.kt`, `HdrTool.kt` and `FixtureEngine.kt`;
- the anti-FQN grep (`mandates.md`) has been run on every edited `.kt` and is clean;
- `./gradlew.bat :shared:build` green.
