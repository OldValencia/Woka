# Audio player — backlog

Written 2026-11-01 before the tree was read for them. Each block is promoted against the tree as it
then is, and **re-cut by what the author sees** (`ai/docs/conventions/tickets.md` §4).

**Order (the author, 2026-11-01):** Now Playing first — AP-004 (specified), then AP-006 and AP-008,
with PiP video as the only source — then the audio player, AP-002 and AP-003, which add audio to a
slot that already exists. AP-004 and AP-005 were merged into
[AP-004](AP-004-now-playing-row.md); AP-005's id is retired.

## Now Playing — first

### AP-006 — The bubble
With the sidebar hidden, per `now-playing.html`: a fixed 48 dp anchor in one of four corners, 16 dp
from the window's edges, the top corners under the 52 dp title bar; the pill inside it grows away
from the edge. Drawn in `App.kt`'s root `Box` above `MainShell`. Glass as `floatingPanel()` draws
it, but radius 24 and no padding — that modifier fixes radius 20 and 6 dp padding. Cover 40 dp
round, the at-rest glyph (bars / pause), 2 dp progress ring from `NowPlayingSource.progress`.
Hover → the capsule over 260 ms: name, subtitle, play/pause, `×`. Drag with a 4 dp click threshold,
snap to the nearest corner over 320 ms, dashed targets while dragging. The corner is a new
`WokaSettings` field, default bottom-left. Hidden when the slot is empty, when the sidebar is
shown, while `EditorPresence.isOpen`, and when the viewer is on the slot's file. The viewer
publishes its active path from `MediaViewerActiveItemEffects`. The fullscreen video is a separate
window, so nothing in the main window needs hiding there.

### AP-008 — The flight between row and corner
Hiding the sidebar flies the row's cover to the bubble's corner, and showing it flies the cover
back: 300 ms, alongside the sidebar's 250 ms width animation. The row publishes its cover's window
bounds, and a ghost cover animates between those bounds and the corner's. Split from AP-006 for
size; the bubble without it still appears and disappears correctly.

## Audio — after

### AP-002 — The engine plays a file with no picture
`VideoPlayerState` opens an audio-only file: no video loop, no HDR/rotation/picture-filter reads on
a missing stream, the clock advanced by the audio writer from samples handed to the line. Seek,
speed, repeat-file and EOF behave as on video. Shape decided by AP-001's answer on `attached_pic`.
Integration tests through `PlayerHarness` on the AP-001 fixtures. Shows nothing alone — likely
merged into AP-003.

### AP-003 — An audio file opens in the viewer and plays
`MediaKind.Audio` / `MediaItem.Audio` / `AUDIO_EXTENSIONS` (the list AP-001 measured), every
exhaustive `when` and the 19 `MediaItem.Video` sites decided one by one. Grid tile: cover or
placeholder, no hover reel. Viewer: cover, title/artist from tags, the bar without picture-only
controls; arrows and auto-advance over audio files only; no resume or watched marks. `Music` in the
grid filter; editor entry hidden. Joins the Now Playing slot. File associations +
`WindowsFileAssociationsTest` total. Needs `audio-player.html` first.

## Media keys

### AP-007 — SMTC, second attempt
A time-boxed spike, measured before anything is built on it. The first attempt
(`implemented/media-viewer/system-media-controls.md`) got the flyout's title and poster working and
`add_ButtonPressed` refused with `CO_E_NOT_SUPPORTED` before touching the delegate. Leads, in order:
Woka's real top-level `HWND` instead of a hidden window (the one untried lead recorded there); a
`Windows.Media.Playback.MediaPlayer`'s own controls, which need no window; JMTC's prebuilt DLL last.
Success = the hardware Play/Pause key reaches a minimised Woka. Fails → the limitation stays, logged.
