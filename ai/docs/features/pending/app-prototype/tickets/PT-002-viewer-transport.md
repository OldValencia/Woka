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

## Result — 2026-11-25

Built. New `woka/transport.js` (slot table, GIF clock, frame step, HDR menu) and `woka/frames.js`
(the strip); the static playback markup and the bottom `#frames` strip are gone from `woka.html`,
their bindings from `woka.js` and `app.js`, their rules from `editor.css`.

**Checked** (headless Edge, `shot.mjs`): `#viewer/gif` — Space pauses, `.` from frame 1 reads
`2 / 24`, `,` steps back, loop off stops at `24 / 24`, a click on the picture plays again. The
strip's first thumbnail sits 12 px / 23 px from the canvas's corner at both `#viewer/frames` and
`#editor/frames`. Play, time and seek start at the same x and y for video and GIF (280/322/418 at
1440 px). The HDR control shows at `#viewer/video` and `#editor/video`, not at `#viewer/photo`.
Computed styles against PT-001's commit, keyed from the nearest id: the photo editor scenes
(photo, crop, adjust, markup, focus) and the photo viewer differ only in hidden elements and the
timeline's play button; the frame-set canvas is 65 px taller (the bottom strip is gone); the video
pill is wider by the HDR control.

**Where the result differs from the steps.**
- The burst scene is `#viewer/frames`, not `#viewer/burst`: a scene id is the library item's id,
  and the burst item is `frames` in both sections. `gif` is in the editor's scenes too, or the bar
  could not follow Edit from the GIF.
- **The play button is one 32 px round knob in both places** — the editor's was a 36 px knob, the
  viewer's a bare glyph. Sharing geometry needed one of the two; the knob kept the editor's look.
  Author to confirm.
- A click on the picture also plays and pauses a video, as it does in the app.
- Subtitles opens a placeholder popover (Off / English); the app's subtitle menu is not drawn.
- The time label has a 48 px minimum, so the seek bar does not move between kinds or when the GIF
  label switches to frame numbers.
- `shot.mjs` keys an element by the nearest ancestor id: two buttons added to the pill renumbered
  every element after them under the old index path.

**Seen, not touched.** At 600 px wide the prototype draws nothing below its bar — already so in
`editor-next.html`. At 880 px the editor's pill overlaps `Original`; it did before, the HDR control
widens it.
