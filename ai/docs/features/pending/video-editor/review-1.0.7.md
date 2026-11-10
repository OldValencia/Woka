# Video editor — the 1.0.7 review of the base

`next_steps.md` §1.0.7, first item: before anything new, every step of the base 1.0.6 built is
checked against its tickets. **The result is a list of findings; what needs work becomes tickets
before the rest of 1.0.7.** Done 2026-11-10 in one sitting; F1 and F3 were fixed the same day
rather than ticketed, since the fix turned out to be one rule in the fold and two reads.

## Areas

| # | Area | State |
|---|------|-------|
| 1 | Architecture — session, layers (film / clip), recipe fold, engine, the shell ↔ video seam | **done** — F1, F2, F3. The shell names no tool id and branches on no `MediaKind`; the video engine names only Trim, Sound, Cover and Resize, whose meaning (time, sound, cover, render size) is the engine's |
| 2 | Tool isolation — manifest, schema, pure `stage()`; no tool knows another; no special cases by id in the shell | **done**, clean. Every `stage()` is pure. One tool knows another: Cover reads `TrimTool.clipsIn` to pin itself to a clip (1.0.6 review). The shell's film rules go by `ToolDomain`, never by id (`FilmScope.kt`, `EditorBottomPanel.kt`) |
| 3 | Undo — every user action one Ctrl+Z / Ctrl+Y step; nothing changes the document past history | **done**, clean. Every write goes through `record`; the strip's delete, duplicate, join, split, freeze, reorder and reset select inside their own step; a revert restarts the line on purpose (ME-107). One path skips history and is never called in the app — `EditorTimelineScope.select`, kept as the test fake's hook |
| 4 | Save and read — what is written reads back the same in editor, player, grid, hover preview, render; old edits open | **done** at the composition level: the editor, `RecipeVideoEdits` (player, PiP, grid, poster) and `VideoRender` all compose `shared + own` and fold it, and diverged only on the open clip — F1. The carriers themselves were not re-read; the 1.0.6 review covered them on 2026-11-05 |
| 5 | Join playback, sound, render — against their tickets | **done** for sound (F3) and for what the render composes. Join playback and the render's encode were not re-read; the 1.0.6 review covered them on 2026-11-05 |

## Findings

### F1 — a clip's own picture op was applied on top of the film's, but shown alone while the clip was open — **fixed 2026-11-10**
Film with Adjust exposure 0.5 for the whole film; select a clip, set its Adjust to 0.8.
- While the clip was open the document was `[adjust 0.8]`: `commitPending(Single)` drops every
  `adjust` op, the film's included.
- Deselected, in the player, the grid and the render, it was `[adjust 0.5, adjust 0.8]` —
  `base.ops + own` everywhere — and `fold` applied both. Reopened, the picture was the stacked one
  while the panel showed 0.8.
- And while that clip was open, **every other clip lost the film's Adjust**: `ClipLooks` took the
  shared layer as `document − own`, and the document no longer held the film's op.

Confirmed by a probe test (removed). **Decision (author, 2026-11-10): the clip's op replaces the
film's.** The trade-off accepted: a later change to the film's Adjust does not reach a clip with
its own.

**Fix.** `fold` keeps only the last op of each `Single` tool (`FoldLayers.kt`, `lastOfEachSingle`);
every composition puts the clip's half after the shared one, so the clip's wins wherever the two
meet — editor, player, grid, render, a GIF's frames alike. `ClipLooks` is handed the session's
shared layer (`EditorSession.sharedOps`) instead of subtracting, and shows a drag in flight on
other clips only when the shared layer is what is being dragged. Files written by 1.0.6 now render
as they were edited. Tests: `ClipLayeringTest`.

Markup is `Single` too — one op holds every shape — so a clip's own markup now replaces the
film's on that clip, as it already did on screen while the clip was open. Its op was seeded from
the film's shapes when the panel opened, so the film's shapes are in it; a film shape added after
that does not reach the clip.

### F3 — the same split broke a clip's own sound, the other way round — **fixed 2026-11-10**
Sound **multiplies**: the film's mute, volume and fades over the whole timeline, times the playing
clip's own (ME-240; `soundGain`, `VideoRender`, `MixClipSound`).
- Opening a clip under a film with Sound, the panel showed the **film's** values, and changing one
  copied all of them into the clip's op — film at 50 % and a clip nudged to 60 % played at 30 %, a
  2 s film fade-in was applied twice on the first clip.
- While the clip was open, `MixClipSound` found no film op (`document − own` again) and played the
  clip's alone.

**Fix.** `ToolManifest.multipliesShared` (true for Sound): on an open clip such a tool's panel
reads and writes from the clip's own work alone (`EditorSession.panelDocument`), so it starts at
the defaults. `MixClipSound` takes the film's half from `EditorTimelineScope.sharedOps`.

### F5 — the test JVM dies in native FFmpeg inside a render's decode — **open, intermittent**
Found 2026-11-10 in a full `:shared:build`: twice in about eight runs, once with a dump
(`shared/hs_err_pid30068.log`). `EXCEPTION_ACCESS_VIOLATION` at pc 0 in `avutil-60.dll`, under
`avcodec_send_packet` ← `FrameRender.recordClips` ← `FreshFrameGrabber.grabFrame`, in
`RenderSoundSyncTest` "cuts off keyframes keep the sound as long as the picture" — a cut render,
no area, no markup. The rerun passes. No native frames in the dump, so the cause is not known;
the next step is to run that test alone in a loop until it fails, then narrow it.

### F2 — `EditorSession.reset()` has no caller outside tests — minor, left
`EditorSessionResets.kt:19`. Only `EditorSessionTest` calls it.

## Observations (not defects)

- The session is two state machines in one class: the frame walk (a history per frame, for a GIF
  and a still) and the film's one linear line (`FilmLine`, `SessionShot`). The film rides on the
  frame structures — `FrameEdit.history` is kept but never read on a film — and branches on
  `film.linear` in `commit`, `moveTo`, `pasteOnClip`, `revertedToOriginal`.
- A layer's own half is recovered by list subtraction (`ops - base.ops.toSet()`), so an own op
  equal to a shared one vanishes from the delta. Harmless since F1: the two drew the same picture.
- Undoing the deselect a whole-film tool made on opening (Crop, Transform, Resize, Cover) leaves
  that tool open with the clip selected again. Its op still goes to the shared layer
  (`film.sharedTools`), so nothing is mis-written; only the "no selection under a whole-film tool"
  look is broken until the tool is reopened.
- `RenderTarget.kt:84` spells `"resize"` where everything else says `ResizeTool.manifest.id`.
- `rememberEditScope` makes the session linear in a `LaunchedEffect`, after the first composition;
  nothing can be edited before it runs, so it holds — but the rule lives in a comment.
