# Audio player — backlog

Written 2026-11-01 before the tree was read for them. Each block is promoted against the tree as it
then is, and **re-cut by what the author sees** (`ai/docs/conventions/tickets.md` §4).

**Order (the author, 2026-11-01):** Now Playing first — AP-004, AP-006 and AP-008, all specified and built,
with PiP video as the only source — then the audio player, AP-002 and AP-003, which add audio to a
slot that already exists. AP-004 and AP-005 were merged into
[AP-004](AP-004-now-playing-row.md); AP-005's id is retired.

## Audio — after

## Media keys

### AP-011 — macOS media keys and Now Playing
`MPRemoteCommandCenter` for the keys, `MPNowPlayingInfoCenter` for Control Centre, over FFM into
`libobjc` — the design in `implemented/media-viewer/system-media-controls.md` Phase 4, behind the
same `SystemMediaControls` seam AP-007 fills on Windows. Built when there is a Mac to verify it on,
run from `createDistributable`.

### AP-012 — Linux MPRIS
The `org.mpris.MediaPlayer2` D-Bus interface for the keys and the desktop's media widget. Only if
Linux becomes a target; today the project does not test on it.
