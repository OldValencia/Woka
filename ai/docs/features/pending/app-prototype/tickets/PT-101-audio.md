# PT-101 — Audio in the viewer and Now Playing

| | |
|---|---|
| **Order** | third |
| **Depends on** | PT-002 |
| **Feature** | [App Prototype](../app-prototype.md) |

**Goal.** Draw a track in the viewer — cover, title, artist, the transport's audio slots — and the
Now Playing slot that keeps it after the viewer closes: the sidebar row, and the corner bubble when
the sidebar is hidden.
**Why now.** The audio row of the viewer's capability table is the last kind not drawn; PT-002
left its slot set defined and unused. What the author sees: `#viewer/track` and the row/bubble.

Source of truth for the look: the app (`AudioStage.kt`) and the accepted `now-playing.html`
(audio-player §3.5): cover `min(300 px, 46 % of the height)`, radius 12, title 20 px semibold,
artist 13 px secondary; row 40 px with a 32 px cover; bubble 48 px with a 40 px round cover and a
progress ring, a capsule with name, play/pause and `×` on hover.

## Steps

1. **Media.** `woka/woka.js`: add `{id:'track', name:'Sea breeze.m4a', title:'Sea breeze',
   artist:'Coastline', cover:'<square viewBox>', audioOnly:true}`; a `Music` folder in the sidebar
   holding audio only; `Pictures`/`Screenshots`/`Vacation` leave audio out. `openMedia` switches
   the sidebar to a folder that holds the item when the active one does not. `app.js setScene`
   puts an `audioOnly` item in `video` mode, so the clock and `duration()` serve it.
   `SECTIONS.viewer` gains `track`.
2. **Stage.** `#audio-stage` beside `#picture` in `woka.html`: cover (the screenshot under the
   item's square `cover` viewBox), title, artist. Shown instead of `#picture` for an `audioOnly`
   item. Styles in `viewer.css`.
3. **Pill.** For an `audioOnly` item the size control and its divider are hidden, and so is Edit
   (there is no audio editor). Info and the carousel toggle stay.
4. **Now Playing.** New `woka/nowplaying.js`: a slot `{id, time, playing}` with its own clock.
   Leaving a playing track hands its time to the slot (called at the top of `setScene`); opening the
   track again takes it back and keeps playing (end of `openMedia`). Row in the sidebar foot above
   `Link folder…` (cover, title, artist, play/pause); a click opens the track. Bubble bottom-left
   when the sidebar is hidden: cover, ring, pause state; capsule on hover with name, play/pause,
   `×` that empties the slot. Hidden in the editor and while the viewer shows the slot's own file.
   Corner dragging is not drawn.

## Files

`ai/mockups/design/woka.html`, `woka/woka.js`, `woka/app.js`, `woka/sections.js`,
`woka/viewer.css`, `woka/shell.css` (edit); `woka/nowplaying.js` (new).

## Done when

- `#viewer/track`: cover, title and artist centred above the transport; the transport has the
  audio slots (no subtitles, no fullscreen); the pill shows Info and the carousel toggle only.
- Play the track, open `#viewer/photo`: the row shows the track with a pause button; hide the
  sidebar: the bubble is in the bottom-left corner; open the editor: it is gone; `×` empties it.
- Opening the track from the row continues from the slot's time.

## Result — 2026-11-25

Built: `woka/nowplaying.js` (new); `woka.html`, `woka.js`, `app.js`, `transport.js`, `sections.js`,
`viewer.css`, `shell.css`. `#viewer/track` shows the cover, `Sea breeze`, `Coastline` and the audio
slots (play, −5 s, seek, +5 s, duration, volume, settings); the pill keeps Info and the carousel
toggle; the sidebar switches to `Music`. Played and left, the row shows the track with pause; the
row's button pauses and resumes the slot; hidden sidebar → bubble 16 px from the bottom-left corner;
editor → bubble gone; `×` empties the slot; opening the track from the row continues from the
slot's time and keeps playing. Checked in headless Edge.

**Where the result differs from the steps.** Who runs the sound is explicit (`owner`: the scene
while the viewer is on the track, the slot once it left): without it the viewer re-synced the slot
to "paused" while reopening, before the track took its time back. `setScene` now builds a scene's
document in `sceneState(scene)` — PT-102 needed the same document without kept edits. Hover on the
bubble is styled, not checked: the headless run moves no pointer.
