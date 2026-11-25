# PT-002 — One transport, GIF, frame strip on the left

| | |
|---|---|
| **Order** | second |
| **Depends on** | PT-001 |
| **Feature** | [App Prototype](../app-prototype.md) |

**Goal.** Draw the viewer's one transport with its video and GIF slot sets, add GIF and frame-set
scenes to the viewer, and stand the frame strip on the left in both viewer and editor.
**Why now.** These are the decisions that make viewer and editor agree (feature doc, "Viewer — the
target design"); the 1.0.9 Compose work cannot start from a prototype in which they still differ.
What the author sees: `#viewer/gif` playing with visible controls, `#viewer/burst` and
`#editor/frames` with the strip in the same place.

## Steps

1. **Media.** In `woka/woka.js` `libraryMedia`, add `{id:'gif', name:'Waves.gif', frames:24,
   fps:12, gif:true}` and give the existing `frames` item (`Seaside-burst.heic`) `frames:4`. A
   frame is drawn as the screenshot under a per-frame `viewBox`, as `frameView` does for video in
   `refine.js`. `SECTIONS.viewer` (from PT-001) gains `burst` and `gif`.
2. **Transport.** New `woka/transport.js`: `transportHtml(kind)` with `kind` ∈ `video | gif |
   audio`, built from one slot table (feature doc, "One transport, three slot sets"). Same DOM and
   classes for every kind; `audio` is defined in the table and first used by PT-101. It replaces
   the static `#viewer-playback` markup in `woka.html`; `syncViewerPlayback` in `woka.js` fills
   whichever kind is mounted. Styles in `viewer.css`, colours from `tokens.css`.
3. **GIF clock.** In `transport.js`: a `requestAnimationFrame` loop advancing `frameIndex` at
   `fps × speed`; Space and a click on the picture toggle it (the existing `play()` path routes on
   `currentMedia().gif`); seek moves by whole frames; `,` / `.` step one frame and pause; the loop
   button toggles stopping at the last frame; the speed popover reuses `chromePopover` with the
   `0.5 · 1 · 1.5 · 2` list the video settings use. Clicking the time toggles `0:01.2` ↔ `13 / 24`.
4. **Video frame step.** `,` / `.` step `1/30 s` while `playing` is false; no buttons.
5. **Frame strip.** New `framesStrip(item)` in `woka/frames.js`: a vertical column of 56×56
   thumbnails, 12 px from the stage's left edge, 72 px from its top, scrolling, the selected frame
   outlined in `--accent`, a dot on a frame with its own settings (today's `· edited` text). Mounted
   in the viewer for any item with `frames > 1`, and in the editor in place of the bottom `#frames`
   strip, which is deleted from `woka.html`, `app.js` (`render`) and `editor.css`. Selecting a frame
   pauses a GIF.
6. **Editor transport.** The play button and time in the editor's timeline `.transport`
   (`woka.html`) come from `transportHtml`'s left group, so the two share geometry.
7. **Pill.** Add the HDR control to the pill (`woka.html` header) for items with `hdr:true`; give
   the `video` item `hdr:true`. Visible in viewer and editor, as in the app since ME-179.

## Files

`ai/mockups/design/woka.html`, `woka/woka.js`, `woka/app.js`, `woka/viewer.css`,
`woka/editor.css`, `woka/sections.js` (edit); `woka/transport.js`, `woka/frames.js` (new).

## Done when

- `shot.mjs` at `#viewer/burst` and `#editor/frames`: the strip's first thumbnail has the same
  left/top offset relative to the picture's container in both.
- `#viewer/gif`: Space plays and pauses; `.` advances one frame and the time reads `2 / 24` after
  one press from frame 1; the loop toggle stops at frame 24 when off.
- `#viewer/video` and `#viewer/gif` screenshots: the play button, time and seek bar share x and y.
- The HDR control is in the pill at `#viewer/video` and `#editor/video`, absent at `#viewer/photo`.
- `#editor/photo` and `#editor/crop` PNGs unchanged against PT-001's (neither has a transport or
  a strip); `#editor/video` and `#editor/audio` differ only in the transport's left group.
