# AP-004 — Now Playing: the slot and its sidebar row, with PiP video as the source

| | |
|---|---|
| **Order** | 2 — after AP-001, first of the Now Playing tickets |
| **Depends on** | nothing (AP-001 is measurement and touches none of this) |
| **Feature** | [Audio player](../audio-player.md) — §3.3, §3.5; spec `ai/mockups/design/now-playing.html` |

**Goal.** While a video is in PiP, the sidebar shows a Now Playing row above `Link Folder…` —
poster, name, `Picture in Picture`, play/pause, `×` on hover — and a click on it brings the PiP
window to the front.
**Why now.** The author's order (2026-11-01): Now Playing before the audio player. The PiP window
is hard to find today, so this slice is useful before audio exists.

**Re-cut from the backlog.** It merges AP-004 (the slot) and AP-005 (the row): the slot alone draws
nothing (`tickets.md` §4). AP-005's id is retired and never reused.

## Steps
1. **The slot** — `commonMain/.../app/shell/nowplaying/NowPlaying.kt` (new). Shell state that
   two features read, beside `EditorPresence` for the same reason (its KDoc):
   - `interface NowPlayingSource { val path: String; val title: String; val subtitle: String;
     val isPlaying: Boolean; val progress: Float; suspend fun cover(): ImageBitmap?;
     fun togglePlayPause(); fun stop(); fun reveal() }`. `isPlaying` and `progress` must read
     snapshot state, so a composable reading them recomposes. `progress` is 0..1, 0 while the
     length is unknown. It is not drawn here; AP-006's ring reads it.
   - `class NowPlaying { var source: NowPlayingSource? by mutableStateOf(null); private set;
     fun claim(next: NowPlayingSource); fun release(path: String) }`.
     - `claim` stops the held source when its `path` differs from `next`'s, then holds `next`.
       This is "one sound source" (§3.3).
     - `release` empties the slot only when it holds `path`.
2. **Koin** — `app/di/Koin.kt`: `single { NowPlaying() }` in `appModule`. `App.kt`:
   `val nowPlaying: NowPlaying by inject()` on `AppDependencies`, beside `router`.
3. **The PiP source** — `jvmMain/.../feature/viewer/pip/PipNowPlaying.kt` (new):
   `class PipNowPlaying(item: MediaItem, player: VideoPlayerState) : NowPlayingSource`.
   - `path` = the file's absolute path; `title` = its name; `subtitle` = `"Picture in Picture"`.
   - `isPlaying` = `player.isPlaying`; `progress` = `currentPositionMs / durationMs`, clamped,
     0 when `durationMs <= 0`.
   - `cover()` = `generateVideoThumbnail(item.file)`.
   - `togglePlayPause()` = `player.togglePlayPause()`; `stop()` = `PipManager.closePip()`;
     `reveal()` = `PipManager.requestFront()`.
4. **`PipManager.kt`** (118 lines):
   - The end of `openPip` claims `PipNowPlaying(item, state)`.
   - Every branch that nulls `activeItem` releases its path first. That is `closePip` in both
     cases — a video returned to the viewer is not in the slot (§3.3) — and `releaseState`.
   - Add `var frontRequests by mutableStateOf(0); private set` and `fun requestFront()` that
     increments it.
5. **Bring to front** — `viewer/shell/MediaViewerFullscreenWindow.kt` (141 lines): after the
   opening `LaunchedEffect(Unit)`, add `LaunchedEffect(PipManager.frontRequests) { if (> 0) {
   window.toFront(); window.requestFocus() } }`. It is the only place the AWT window exists, so
   the request is a counter the window observes, not a call into it.
6. **Tokens** — `nowplaying/NowPlayingTokens.kt` (new). Each value is named after its rule in
   `now-playing.html`, as `SidebarTokens` is:
   - `.np` padding: 8 dp vertical, 10 dp horizontal;
   - `.nprow`: height 44, radius 9, inner padding 6, gap 9;
   - `.cover`: 32, radius 6;
   - `.rb`: 26 circle; play icon 13; `×` icon 10;
   - text: title 13 sp SemiBold, subtitle 11 sp.
7. **Cover** — `nowplaying/NowPlayingCover.kt` (new): `NowPlayingCover(source, size, shape)`.
   It loads `source.cover()` in `produceState` keyed on `source.path`. Until that returns, or if
   it returns null, it draws `Icons.Static.Video` tinted `textTertiary` on `surfaceElevated`, as
   `VideoCover`'s fallback does. A 1 dp `border` ring is drawn around it (`.cover`
   `box-shadow`).
8. **Row** — `nowplaying/NowPlayingRow.kt` (new): `NowPlayingRow()` reads
   `AppDependencies.nowPlaying.source` and draws nothing when it is null. Otherwise:
   - a 1 dp `border` hairline on top, then the 44 dp row: cover, a title/subtitle column (one line
     each, ellipsis), play/pause, `×`;
   - the `×` alpha is 1 only while the row is hovered;
   - **no hover fill on the row** — the author, 2026-11-01;
   - a click on the row calls `reveal()`, and the two buttons consume their own clicks.
   For the buttons, reuse `ToolbarIconButton` if it renders a 26 dp circle with a 13 dp icon.
   Otherwise write a private `RoundIconButton` in this file and say in its KDoc why
   `ToolbarIconButton` did not fit.
9. **Sidebar** — `app/shell/sidebar/Sidebar.kt` (135 lines): call `NowPlayingRow()` between the
   scrolling column and `SidebarFoot`. `SidebarFoot` keeps its own top hairline, so the row sits
   between two separators with 8 dp above and below it (`.np`).
10. **Tests**:
    - `jvmTest/.../unit/NowPlayingTest.kt` (new), with a fake source:
      - claiming a second path stops the first;
      - re-claiming the same path does not stop it;
      - `release` of another path leaves the slot alone;
      - `release` of the held path empties it.
    - `jvmTest/.../integration/PipNowPlayingTest.kt` (new), on the `PipReturnTest` pattern
      (`startKoinOnce`, the same fixture player):
      - `openPip` puts the file in the slot;
      - `closePip()` and `closePip(returnToMain = true)` each empty it;
      - `requestFront` increments `frontRequests`.

## Files
`shared/src/commonMain/kotlin/com/woka/app/shell/nowplaying/NowPlaying.kt` (new),
`.../app/shell/nowplaying/NowPlayingTokens.kt` (new), `.../nowplaying/NowPlayingCover.kt` (new),
`.../nowplaying/NowPlayingRow.kt` (new), `.../app/di/Koin.kt` (edit), `.../app/App.kt` (edit),
`.../app/shell/sidebar/Sidebar.kt` (edit),
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/pip/PipNowPlaying.kt` (new),
`.../feature/viewer/pip/PipManager.kt` (edit),
`.../feature/viewer/shell/MediaViewerFullscreenWindow.kt` (edit),
`shared/src/jvmTest/kotlin/com/woka/unit/NowPlayingTest.kt` (new),
`shared/src/jvmTest/kotlin/com/woka/integration/PipNowPlayingTest.kt` (new).

## Done when
- A video sent to PiP adds the row, and closing PiP — by its own close, by `×`, or by returning
  to the viewer — removes it.
- Play/pause in the row pauses and resumes the PiP video, and its icon follows the player.
- A click on the row with the PiP window behind the main window brings PiP to the front.
- The row matches `now-playing.html` in both themes: no hover fill, `×` only on hover, the same
  8 dp above and below it. Checked by hand and dated in this ticket.
- Every edited `.kt` stays ≤ 150 lines, and the anti-FQN grep is clean.
- `./gradlew.bat :shared:build` is green. Read a lone `SettingsPersistenceTest` or
  `SettingsRecordsTest` failure as the known lock race (2026-11-01): rerun them alone.

## Result — 2026-11-01
- `:shared:build` green. `NowPlayingTest` 4/4, `PipNowPlayingTest` 6/6, `PipReturnTest` 3/3.
- **By hand, the author, 2026-11-01:** the row appears and leaves with PiP, play/pause follows the
  player, the click brings PiP forward, the row matches the mockup — "всё работает".
- Built as specified, with one addition: `PipNowPlaying.stop()` closes PiP only while PiP still
  holds that player. Without it, `claim` stopping the old source would close the film `openPip`
  had just installed (`PipNowPlayingTest`, "a second film in PiP…").
