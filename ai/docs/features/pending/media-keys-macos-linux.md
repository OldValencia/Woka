# Media keys on macOS and Linux

**Scope.** The hardware media keys and the system's media panel, on the two platforms AP-007 left
out. Windows has them through SMTC since 2026-11-01
([`implemented/audio-player/`](../implemented/audio-player/audio-player.md), AP-007). Both backends
fill the same `SystemMediaControls` seam, and the Now Playing slot owns the session, so neither
touches the viewer or the slot.

**Why waiting.** Neither platform can be checked here: there is no Mac, and the project does not
test on Linux. Native code that cannot be run is not shipped.

## Backlog

### AP-011 — macOS media keys and Now Playing
`MPRemoteCommandCenter` for the keys, and `MPNowPlayingInfoCenter` for Control Centre: title,
artist, artwork. Both over FFM into `libobjc`: one `objc_msgSend` downcall per signature, and a
target class built with `objc_allocateClassPair` from upcall stubs. The design is Phase 4 of
[`implemented/media-viewer/system-media-controls.md`](../implemented/media-viewer/system-media-controls.md).
On macOS the keys arrive only through these APIs, since AWT never sees them. It is checked from
`createDistributable`, not `gradlew run`.

### AP-012 — Linux MPRIS
The `org.mpris.MediaPlayer2` and `.Player` D-Bus interfaces, for the keys and the desktop's media
widget. Only if Linux becomes a platform the project tests on.

## Decision log

| Date | Question | Choice | Why |
|---|---|---|---|
| 2026-11-01 | macOS and Linux in AP-007 | no — their own file, built when they can be checked | the author; Windows is the platform tested on, and code nobody can run is not shipped |
