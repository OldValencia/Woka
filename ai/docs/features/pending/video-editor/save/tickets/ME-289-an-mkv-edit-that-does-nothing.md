# ME-289 — An mkv's Edit that does nothing

| | |
|---|---|
| **Order** | before ME-282 — **done 2026-11-04**, with ME-282 and ME-283 |
| **Depends on** | nothing for step 1; ME-282 for `Done` itself |
| **Feature** | [Save](../save.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** An `mkv` (and `webm`, `avi`) either opens the editor or says why it cannot — never a
click with no answer.

**Why now.** Reported by the author 2026-11-02 on `Movies/2160p Green Book.mkv` and
`Yanni Live the Concert.mkv`: Edit pressed, nothing happens; an `mp4` opens.

## Analysis — read against the tree 2026-11-02

1. `EditorEngineFactoryImpl.videoCapability` answers `Unavailable` for a container with no
   `carrierFor` — every Matroska and RIFF file until ME-282 / ME-283.
2. `EditorHost` gets `null` from `factory.create`, runs `LaunchedEffect(target) { onClose() }`
   and returns: the editor opens and closes in the same frame. That is the "nothing happens".
3. The **grid's** menu asks `isEditable` and greys Edit. The **viewer's** pill and right-click do
   not: `ViewerContext.editActionOf(isVideo, edit)` checks only `VIDEO_EDITING_ENABLED`. Two doors,
   one of them not asking — the mistake `isEditable`'s own KDoc names.

## Steps

1. `editActionOf` also takes `isEditable(factory, path)` and returns null when it is false, so the
   viewer hides Edit exactly where the grid greys it. Test: an `mkv` gets no action, an `mp4` does.
2. **The real fix, for the author's mixed library (§0):** a video with no carrier still gets an
   engine — a new `EditCapability.RenderOnly` — so `Done ▾` shows `Render video…` alone, with
   mockup state 9's notice ("MKV cannot keep an edit yet"). `VideoEditorWrites.save` already
   answers a sentence for that case. ME-282 then turns `mkv`/`webm` into `Recorded` and `Done`
   appears.
3. `EditorHost`: when `create` returns null, post the reason through `EditorNoticeStatus` instead
   of closing silently — the defensive half, for whatever the next unsupported case is.

## Done when
- An `mkv` opened from the viewer opens the editor with `Render video…`, or shows no Edit at all.
- No path closes the editor without a message.
- `./gradlew.bat :shared:build` green.

## Built — 2026-11-04

- **Step 1 superseded by step 2.** With `RenderOnly` every existing video file is editable, so the
  viewer asking `isEditable` would hide nothing it does not already hide. Not built.
- **Step 2.** `EditCapability.RenderOnly`; `videoCapability` answers it for a container with no
  carrier *or* a carrier that refuses this file (`carriesHere`: a streamed `mkv`, an OpenDML `avi`).
  `DoneChoices.keeps` turns the button into `Render video…` with no caret, and the same action is
  `Done`'s key. `EditorRenderOnlyNotice` — mockup state 9's words — sits where
  `EditorNoHistoryNotice` does. Since every video now reaches the render, a copy writes the
  source's own container only for `mp4 mov m4v mkv webm avi` (`copiesInto`); the rest encode to `mp4`.
- **Step 3.** `EditorRefusedEffect`: a refused target posts *"XYZ files cannot be edited."* through
  `EditorNoticeStatus`, then closes.
- The quit dialog's `Save` on a `RenderOnly` film still answers *"cannot keep an edit yet"* in a
  banner — the one road left to `VideoEditorWrites.save`'s `else`.
