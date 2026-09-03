# ME-180 — The switch reaches the picture, and leaves video alone

| | |
|---|---|
| **Order** | immediately after ME-179 |
| **Depends on** | ME-177, ME-179 |
| **Feature** | [Image Editor](../image-editor.md) |

**Goal.** Make flipping the HDR mode redraw the picture, and take the control off the video viewer.

## 1. Nothing redrew

Reported the moment the pill shipped: *"При переключении не обновляется картинка."*

ME-119 put the mode into both cache keys and argued that this made invalidation unnecessary —
switching misses, switching back hits. That half was right and is **not** the bug. The bug is that
**nobody asked for the new key**. The bitmap on screen is held in state filled by an effect:

```
LaunchedEffect(activeItem, showConflictDialog, state.frameIndex, reloadKey)
```

The mode is not among the keys, so the effect never re-ran, and the state kept the bitmap decoded
under the old mode. The same in the editor, whose proxy is `produceState(null, file, frameIndex)`.

A cache key and a recomposition key are different mechanisms and this change needed both — the first
so a stale entry is not served, the second so anything goes looking. ME-119 wrote a Done-when for the
first and none for the second, which is why the tests passed and the app did not.

**The viewer needs no new plumbing**: `MediaViewerActiveItemEffects` already receives the whole
`WokaSettings`. The editor has no settings near its proxy, so `HdrModeAccess` gives it the mode as
Compose state — `HdrView.mode` is a plain getter, correct for `displayable` on a decode thread and
useless as a key, since reading it inside `remember` is precisely the mistake being fixed.

## 2. The control does not belong over a video

*"Зачем в Видео вьювере этот переключатель? Скрой, не удаляй."*

Right, and for a reason the ticket should record: **everything ME-177 built runs at the still decode**.
A video frame is still tone mapped by `HdrImagePainter` with `ASSUMED_WHITE_POINT`, and the switch
reaches none of it. Offering a control that moves nothing is worse than offering none.

**Hidden, not removed.** `showHdrMode` stays a parameter and only the argument is `false`, because
whether video gets the same treatment is genuinely open — it needs a white point that does not pump
between frames, which is ME-120's stated limit and not a small thing.

## Steps
1. `settings.hdrViewMode` joins the keys of `MediaViewerActiveItemEffects`' decode.
2. `HdrModeAccess` in `feature/viewer/image/` — the mode as Compose state, for callers with no
   settings in reach. `rememberEditorFrame` keys both its `produceState` calls on it.
3. `showHdrMode` on `MediaViewerNormalControls`, through `MediaViewerTopBarSetup` and
   `MediaViewerLayoutWrapper`, argued `videoState == null` at the screen.
4. `MediaViewerScreenEffects.kt` went to 158 lines, so its ambient half — volume, auto-hide, focus —
   moves to `MediaViewerAmbient.kt`. The two never shared a line.

## Files
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/image/HdrModeAccess.kt` (new)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/MediaViewerAmbient.kt` (new, extracted)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/MediaViewerScreenEffects.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/MediaViewerScreen.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/MediaViewerEffects.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/MediaViewerLayoutWrapper.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/MediaViewerGlobalControls.kt` (edit)
`shared/src/jvmMain/kotlin/com/woka/feature/editor/engine/image/EditorAnimatedProxy.kt` (edit)

## Done when
- `jvmTest/ui/`: the HDR control is in the pill and **stays there while the editor owns the canvas**,
  unlike the eye and Edit;
- `jvmTest/ui/`: it is absent when `showHdrMode` is false;
- confirmed by hand — the part no test here reaches: flipping the mode redraws the picture in the
  viewer and in the editor without reopening the file, and the pill carries no HDR control over a
  video.
