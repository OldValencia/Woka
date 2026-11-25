# PT-102 — Carousel badges and Original in the viewer

| | |
|---|---|
| **Order** | fourth, built with PT-101 |
| **Depends on** | PT-002 |
| **Feature** | [App Prototype](../app-prototype.md) |

**Goal.** Say on the carousel what each file is and whether it carries an edit, and let the viewer
show the source of an edited file without opening the editor.
**Why now.** Edits are kept inside the file and the original is untouched, but nothing in the
viewer says a picture is shown with an edit. What the author sees: badges on the carousel and
`Original` in the pill on `Palms.jpg`.

The caption under the carousel (`name` and `3 of 7`) is already drawn by `editor-next`; this ticket
leaves it as it is.

## Steps

1. **Edited files.** `woka/woka.js`: an `editedIds` set. `Palms.jpg` starts edited — its scene opens
   with the Warm style kept, so the prototype has an edited file without a round trip through the
   editor. `finishEditing` (`experience.js`) adds the scene when its document differs from the
   scene's blank one and removes it when it does not; Discard leaves the set alone.
2. **Badges.** The carousel item gets a chip, bottom-left: a video's duration with a play glyph,
   `GIF`, a frame set's count, a track's music glyph; and a dot, top-right, on an edited file.
   Replaces the centred play badge. Styles in `viewer.css`, over-media colours from `tokens.css`.
3. **Original.** `#viewer-original` in the pill's viewer group, shown only for an edited file:
   click toggles the comparison, press-and-hold shows it while held, `\` held does the same — the
   editor's `toggleComparison`, with `initComparison` binding both buttons and the `\` handler
   (`refine.js`) no longer refusing the viewer when the file is edited. The `Original` label on the
   canvas appears in the viewer too.

## Files

`ai/mockups/design/woka.html`, `woka/woka.js`, `woka/experience.js`, `woka/refine.js`,
`woka/viewer.css` (edit).

## Done when

- The carousel shows a duration on the video, `GIF` on the GIF, `4` on the frame set, a dot on
  `Palms.jpg`.
- `#viewer/palm`: the picture is warm; `Original` is in the pill; clicking it shows the
  unfiltered picture and the canvas label; `#viewer/photo` has no `Original`.
- Edit `Seaside.jpg`, Done: its thumbnail gets the dot and the viewer pill gets `Original`; Revert
  to original + Done removes both.

## Result — 2026-11-25

Built with PT-101: `woka.js`, `app.js` (`sceneState`), `experience.js`, `refine.js`, `woka.html`,
`viewer.css`. The Vacation carousel reads: nothing on the plain photos, a dot on Palms, `4`,
`GIF`, `▶ 00:32` on both videos; in `Music` the track carries a music glyph. `#viewer/palm` is warm with `Original` in the pill; a click shows the
unfiltered picture and the canvas label; `#viewer/photo` has no `Original`. Mono on `Seaside.jpg`
+ Done → dot and `Original`; Revert to original + Done → both gone. Checked in headless Edge.

The scenes neither ticket touches — every editor scene, the video and the GIF in the viewer, both
themes — match PT-002's commit in computed style, the sidebar and the carousel aside.

**Seen, not touched.** Revert to original on a video sets one `Original video` clip, which is not
the scene's blank three-clip document, so a reverted video still counts as edited. That is the
existing revert, not this ticket's.
