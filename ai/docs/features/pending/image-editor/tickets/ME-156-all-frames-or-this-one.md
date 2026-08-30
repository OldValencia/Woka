# ME-156 — All Frames or This Frame: the switch that says who the edit is for

| | |
|---|---|
| **Order** | 64 — after ME-126, which is the only thing that can store its answer |
| **Depends on** | ME-126 (the keyed payload and `EditScope`), ME-130 (`EditorHost.done`, which this also edits) |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Give a multi-frame still a **segmented control under the top bar** — `All Frames` /
`This Frame` — that decides which slot `Done` writes into, seeded from what the file already carries.

**Why now.** ME-126 made the payload able to hold a per-frame override and deliberately shipped nothing
that could write one. This is the control that writes one. It is the smaller half of what the backlog
asked for, and it is shippable alone because the editor already **opens on a frame**: the viewer's
frame strip picks it and `onEdit(id, frame)` carries it into `EditorTarget.frameIndex`. Walking to
another frame without leaving the editor is [ME-158](ME-158-the-editors-frame-strip.md).

## Where it is, and where it is not

**Its own strip, centred under the top bar** (decided 2026-08-30). A `SegmentedControl` from
`core/ui/components`, `SegmentedControlDefaults.onScrim` like the inspector's rows, shown **only** when
the file is one it can mean something for.

The two placements that were not chosen: **in the top bar** — which is at 135 lines and already carries
Cancel, the title, Revert, HDR, undo, redo and Done, and where a two-segment control would be the
seventh thing in a row of six; and **at the head of a frame strip**, which reads well but does not exist
until ME-157 and would have made this ticket depend on that one.

**It is not offered for an animation.** A GIF or an animated WebP is *watched*: the clock advances
`frameIndex` and every frame goes through the same fold (ME-138, ME-140), so an edit applied to one
frame of eight is a flicker rather than an edit. `EditorFrames.allowsPerFrame` is
`count > 1 && !playsAsAnimation`, and a burst, a Live Photo's still and a TIFF page chain are what is
left — every one of which is a *set of pictures*, not a moving one.

**It is not offered for a single picture**, which is the overwhelmingly common case and must cost
nothing: one `if` in `EditorScreen`, no strip, no measurement, no extra decode.

## What the switch means, said where the user is

`with(document, AllFrames, i)` **clears every per-frame override** (ME-126) — it has to, or "every
frame draws this" would be false wherever a key wins. That is destructive of work the user may have
done frames ago, so the strip carries a secondary line that states the consequence before the click:

```
        ╭──────────────┬──────────────╮
        │  All Frames  │  This Frame  │
        ╰──────────────┴──────────────╯
     Replaces the separate edits on 2 frames
```

- nothing overridden, `All Frames` selected → "Applies to all 5 frames"
- `This Frame` selected → "Applies to frame 3 only"
- `All Frames` selected while other frames carry overrides → the replacement line above

The count comes from the payload the engine already read when it opened; it is not a second disk hit.

## Steps

1. **`core/EditorFrames.kt` (new, `commonMain`)** — everything the shell needs to know about frames
   inside one file, as one value so the interface grows by one member:

   ```kotlin
   @Immutable
   data class EditorFrames(
       val count: Int = 1,
       val playsAsAnimation: Boolean = false,
       val storedScope: EditScope = EditScope.AllFrames,
       /** Frame indices the file carries an override for. */
       val overridden: Set<Int> = emptySet()
   ) {
       val allowsPerFrame: Boolean get() = count > 1 && !playsAsAnimation
   }
   ```

   The four travel together because they come from the same two reads the engine already makes — the
   decoder's frame set and the stored payload — and because ME-157's marks want `overridden` too.
   `allowsPerFrame` is the rule, in the one place a later medium changes it.

2. **`core/EditorEngine.kt` (edit)** — `val frames: EditorFrames get() = EditorFrames()`. Defaulted, so
   the video engine and every test fake compile untouched, and the default says "one picture" — which
   is the truth for a video timeline as far as this control is concerned. The file is at **134 lines**
   before ME-126's `save` parameter; if `wc -l` reports over 150 after both, the split is the three
   *drawing* members (`Surface`, `Thumbnail`, `togglePlayback`) and their docs into a
   `core/EditorEngineSurface.kt` sibling interface that `EditorEngine` extends, as its own step.

3. **`engine/image/ImageEditorEngine.kt` (edit)** — implement `frames` as a `by lazy`, for the same
   reason `sourceSize` is lazy: the factory must decide editability from the format alone and must not
   touch the disk to grey out a menu item. `count` and `playsAsAnimation` from
   `stillDecoderFor(file).frameSet(file)` — `viewable.size` and `frames.isAnimation`, the two the
   viewer's own strip already uses — and `storedScope`/`overridden` from `EditRecipe.read(file)`.

4. **`shell/EditorFrameScopeStrip.kt` (new)** — the control and its line. `SegmentedControl` with the
   two labels, `Theme.viewer.onScrimSecondary` for the 11.5.sp footnote, `testTag("editor-frame-scope")`.
   It takes `EditorFrames`, the selected `EditScope`, the open frame index and a callback; it computes
   nothing else and reads nothing global.

5. **`shell/EditorScreen.kt` (edit)** — new parameters `frames: EditorFrames`, `editScope: EditScope`,
   `onScopeChange: (EditScope) -> Unit`, and inside the `Box`:

   ```kotlin
   if (frames.allowsPerFrame) {
       EditorFrameScopeStrip(…, modifier = Modifier.align(Alignment.TopCenter))
   }
   ```

   **The file is at 143 lines**, so this step is also the split: the `overlay = { geometry -> … }`
   lambda handed to `EditorCanvasHost` — eighteen lines with its two comments — moves to
   `shell/EditorCanvasOverlay.kt` as `EditorCanvasOverlay(tools, activeTool, session, engine, document,
   canvasKeys, selection)`. Net negative before the addition.

6. **`shell/EditorHost.kt` (edit)** — hold the choice for the session and spend it at `Done`:

   ```kotlin
   var editScope by remember(target) { mutableStateOf(engine.frames.storedScope) }
   …
   engine.save(document, editScope)
   ```

   **Seeded from the file, never from a constant.** Reopening frame 3 of a burst that already carries
   a frame-3 override must come back in `This Frame`; opening it in `All Frames` and pressing `Done`
   would clear every override the file had, which is a silent loss on a reopen-and-save.

   `editScope` is read into the closure `done` builds, so if ME-130's conditional split produced
   `shell/EditorActions.kt` it is a parameter of that builder rather than a capture in this file.

7. **`../image-editor.md` §4.2 (edit)** — the layout sketch gains the strip, marked as appearing only
   for multi-frame stills.

8. **`../image-editor.md` §9 (edit)** — a row dated 2026-08-30: *Where does the frame scope live, and
   for which files?* → *its own strip under the top bar; multi-frame stills only, never an animation*,
   with the flicker argument and the two rejected placements.

## Files

`core/EditorFrames.kt` (new), `core/EditorEngine.kt` (edit) under
`shared/src/commonMain/kotlin/com/woka/feature/editor/`; `shell/EditorFrameScopeStrip.kt` (new),
`shell/EditorCanvasOverlay.kt` (new), `shell/EditorScreen.kt` (edit), `shell/EditorHost.kt` (edit),
`engine/image/ImageEditorEngine.kt` (edit) under `shared/src/jvmMain/kotlin/com/woka/feature/editor/`;
`ai/docs/features/pending/image-editor/image-editor.md` (edit).

## Verification

- `jvmTest/unit/EditorFramesTest.kt` (new) — `allowsPerFrame` is true for `count = 5`, false for
  `count = 1`, and false for `count = 8, playsAsAnimation = true`; the defaults describe a single
  picture;
- `jvmTest/ui/EditorFrameScopeUiTest.kt` (new), the ticket's real test, under `EditorUnderShell`:
  - `editor-frame-scope` does not exist for `STILL_JPG` and does not exist for `ANIM_GIF`;
  - it exists for `BURST_HEIC` and reads "Applies to all 5 frames";
  - opened on frame 3, selecting `This Frame` changes the line to "Applies to frame 3 only";
  - with a payload already carrying overrides for two other frames, `All Frames` shows the replacement
    line naming **2**;
  - the strip is absent while the file is a single picture even after a tool is opened — a regression
    guard for step 5's `if` being in the wrong branch of the `Box`;
- `jvmTest/integration/FrameScopeSaveTest.kt` (new), over a copy of `BURST_HEIC`:
  - `engine.save(document, EditScope.ThisFrame)` on frame 3 writes a keyed payload and leaves the
    un-keyed ops alone; a second engine opened on frame 3 reports `storedScope == ThisFrame`;
  - `engine.save(document, EditScope.AllFrames)` clears the keys;
  - `engine.frames` for `STILL_JPG` is `count = 1`, for `ANIM_GIF` is `playsAsAnimation = true`, for
    `MULTIPAGE_TIFF` is `count = 3` and not animated;
- `jvmTest/ui/EditorShellUiTest.kt`, `EditorTopBarUiTest.kt`, `EditorRevertUiTest.kt`,
  `CropToolSwitchUiTest.kt` — unchanged and still green.

## Done when

- a single picture and an animation show no strip and behave exactly as they do today;
- a burst, a Live Photo still and a multi-page TIFF show the strip;
- `This Frame` + `Done` changes that frame and no other, verified by reading the other frames back;
- `All Frames` + `Done` changes every frame, and the strip said so before the click;
- reopening a frame that carries an override comes back in `This Frame`;
- Revert is unchanged and still removes everything from the file;
- no file touched exceeds 150 lines (`wc -l`);
- `./gradlew.bat :shared:build` green.
