# Audio player — backlog

Written 2026-11-01 before the tree was read for them. Each block is promoted against the tree as it
then is, and **re-cut by what the author sees** (`ai/docs/conventions/tickets.md` §4).

**Order (the author, 2026-11-01):** Now Playing first — AP-004, AP-006 and AP-008, all specified and built,
with PiP video as the only source — then the audio player, AP-002 and AP-003, which add audio to a
slot that already exists. AP-004 and AP-005 were merged into
[AP-004](AP-004-now-playing-row.md); AP-005's id is retired.

## Audio — after

### AP-009 — Audio in the library
The `Music` grid filter beside Photos and Videos; `AUDIO_EXTENSIONS` joins
`ALL_SUPPORTED_MEDIA_EXTENSIONS`, `mediaAssociations` in `desktopApp/build.gradle.kts` and
`WindowsFileAssociationsTest`'s total. AP-003 is the part that plays; the Info panel's audio rows
went into it (2026-11-01).

## Media keys

### AP-007 — SMTC, second attempt
A time-boxed spike, measured before anything is built on it. The first attempt
(`implemented/media-viewer/system-media-controls.md`) got the flyout's title and poster working and
`add_ButtonPressed` refused with `CO_E_NOT_SUPPORTED` before touching the delegate. Leads, in order:
Woka's real top-level `HWND` instead of a hidden window (the one untried lead recorded there); a
`Windows.Media.Playback.MediaPlayer`'s own controls, which need no window; JMTC's prebuilt DLL last.
Success = the hardware Play/Pause key reaches a minimised Woka. Fails → the limitation stays, logged.
