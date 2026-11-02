# ME-215 — This Clip: a tool on one stretch of the film

| | |
|---|---|
| **Order** | next in `timeline/`, before ME-263 and ME-268 (both are keyed on it) |
| **Depends on** | ME-210 (stable clip ids), ME-284 (the readers fold) |
| **Feature** | [Timeline](../timeline.md) · umbrella [Video Editor](../../video-editor.md) §3.3 |

**Goal.** Split twice, select the middle clip, switch to **This Clip**, and Adjust / Styles / Crop
/ Markup act on that stretch only — in the editor, the viewer, the grid poster and the render.

**Why now.** The author, 2026-11-02: *"это ж видео, у него есть длина, а мы делаем вид, что это
картинка"* — when, and for how long, a look applies. The design's answer (§3.3): the time range
**is** the clip.

## Shape — written against the tree 2026-11-02

- **No new mechanism.** `EditLayers(base, frames, touched)` and `EditScope` carry frame-set edits
  since ME-156…ME-176. A film keys the same `Map<Int, EditDocument>` on a **per-clip key**, never
  a position: add `key: Int` to `TrimClip` (default 0, the implicit whole-file clip); `split` and
  `duplicate` mint the next free key, `delete` drops that key's layer, a reorder touches nothing.
- `VideoEditorEngine.frames` returns `EditorFrames(count = clips, overridden, storedScope)`;
  `allowsPerFrame` is true for a film with more than one clip. The switch reads "All Clips / This
  Clip" — words supplied by the engine, not a `MediaKind` branch in the shell.
- "This Clip" follows the strip's selection (`EditorTimelineScope.selectedId`); the session moves
  to that clip's layer as `moveToFrame` does for a burst.
- A clip carrying its own layer draws ME-157's dot on its `ClipTile`.

## Steps
1. `TrimClip.key` and key minting in `ClipEdits`; `ClipEditsTest`.
2. Engine: `frames`, `storedDocumentIn(key)`, `storedBase()` from `EditRecipe.read(file).frames`.
3. Shell: scope labels from the engine; strip selection → `session.moveToFrame(key, scope)`.
4. **Every reader folds per clip** — the bulk of the work:
   - editor canvas: `base + layer(current clip)`, the current clip being `ClipWalk.index`;
   - viewer: `AppliedVideoEdit.painter` takes the clip (from `EditedClock.index`);
     `RecipeVideoEdits` folds one recipe per key, cached;
   - render: `encodeClips` draws each clip through its own `RecipePicture`; any per-clip layer is
     pixel work, so the copy path is off;
   - poster: the first clip until ME-287 chooses a cover.
5. Tests: a render of two clips, one tinted — the pixels differ by clip; the reader returns two
   painters.

## Done when
- Split, split, select, This Clip, Adjust: only that stretch changes — in the editor, in the
  viewer after `Done`, and in a render.
- Deleting the clip removes its layer, duplicating copies it, reordering keeps it.
- `./gradlew.bat :shared:build` green.
