# AP-007 — Media keys and the Windows media panel through SMTC

| | |
|---|---|
| **Order** | 7 — the last of 1.0.5 |
| **Depends on** | AP-004 (the Now Playing slot), AP-003 (`AudioSession`) |
| **Feature** | [Audio player](../audio-player.md) — §7, §8 |

**Goal.** The hardware media keys reach Woka while it is minimised or unfocused, and the Windows
media panel shows what Woka plays.
**Why now.** Music keeps playing after the viewer closes, and the keys only worked with a Woka
window focused.

Written 2026-11-01, after the author chose SMTC over `RegisterHotKey`. The spike came first.

## Steps
1. **Spike** — `core/system/windows/`:
   - `WinRt.kt`: `combase` over FFM — `RoInitialize`, HSTRINGs, `RoGetActivationFactory`, GUIDs,
     calls by vtable slot.
   - `SmtcButtonHandler.kt`: the `ButtonPressed` delegate as an in-memory COM object of upcall stubs.
   - `WindowsSmtcControls.kt`: `GetForWindow` on the main `ComposeWindow`'s `windowHandle`, the
     buttons, the playback status, `add_ButtonPressed`.

   Measured by the author: `add_ButtonPressed` returns `S_OK`; Pause/Play arrive as buttons 1/0
   focused and minimised; the panel shows the session. The first attempt's message-only window
   was the cause of `CO_E_NOT_SUPPORTED`.
2. **The panel** — `SmtcDisplay.kt`, `SmtcThumbnail.kt`:
   - a track as music, with title and artist;
   - a film as video, with title and subtitle;
   - the cover or poster as a thumbnail, built from PNG bytes in memory
     (`SHCreateMemStream` → `CreateRandomAccessStreamOverStream` → `CreateFromStream`).
   - `MediaSessionInfo` gains `artist`, `isVideo` and `cover`.
3. **Ownership** — `NowPlayingSystemSession` (`expect` in commonMain, the actual in jvmMain),
   mounted in `App.kt`. It attaches while the slot holds a source, and detaches when the slot
   empties or Settings turns system media controls off.
   - `NowPlayingSource` gains `hasNext`, `hasPrevious`, `next()` and `previous()`. The defaults are
     none, which is what PiP uses.
   - `AudioNowPlaying` answers them through `AudioSession`: Next is the walk's own next track,
     Previous the folder's track before.
   - `MediaViewerSystemControlsEffect` keeps the session only for a film in the viewer.
   - `FullscreenSystemControlsEffect` is deleted: that window holds the slot.
4. `SystemMediaControlsFactory` picks `WindowsSmtcControls` on Windows; `AwtMediaKeyControls` is
   deleted, since SMTC also takes the focused case. The OS Integration toggle's text says
   "playback", not "video playback".

**Files:** `shared/src/jvmMain/kotlin/com/woka/core/system/windows/` (`WinRt.kt`,
`SmtcButtonHandler.kt`, `WindowsSmtcControls.kt`, `SmtcDisplay.kt`, `SmtcThumbnail.kt`),
`core/system/SystemMediaControls.kt`, `core/system/SystemMediaControlsFactory.kt`,
`core/system/AwtMediaKeyControls.kt` (deleted),
`app/shell/nowplaying/NowPlaying.kt`, `app/shell/nowplaying/NowPlayingSystemSession.kt` (+ `.jvm.kt`),
`app/App.kt`, `feature/viewer/audio/AudioSession.kt`, `feature/viewer/audio/AudioNowPlaying.kt`,
`feature/viewer/video/MediaViewerSystemControlsEffect.kt`,
`feature/viewer/shell/MediaViewerFullscreenWindow.kt`, `feature/viewer/shell/MediaViewerPlaylist.kt`
(comment), `feature/settings/OsIntegrationSettingsPanel.kt`.

**Done when:**
- Play/Pause, Next and Previous work with Woka minimised, with the viewer open or closed.
- The panel shows title, artist and cover for a track, and the name and poster for a PiP film.
- `×` in Now Playing removes Woka from the panel, and the keys go back to other players.
- `:shared:build` is green.

## Result — 2026-11-01
- Spike: as above, checked by the author. The rest is built; `:shared:build` result in the
  status table.
- `Unknown app` under `gradlew run` is the process name `java.exe`. The packaged `Woka.exe` carries
  `FileDescription: Woka` (read from `createDistributable`'s output). The packaged name is to be
  checked by hand.
