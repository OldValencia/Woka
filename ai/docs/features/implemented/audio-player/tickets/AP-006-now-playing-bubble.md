# AP-006 — Now Playing: the bubble when the sidebar is hidden

| | |
|---|---|
| **Order** | 3 — after AP-004 |
| **Depends on** | AP-004 (`NowPlaying`, `NowPlayingSource`, `NowPlayingCover`, `RoundIconButton`) |
| **Feature** | [Audio player](../audio-player.md) — §3.5; spec `ai/mockups/design/now-playing.html` |

**Goal.** With the sidebar hidden, the slot's source shows as a 48 dp bubble in one corner of the
window: cover, progress ring, at-rest glyph. On hover it opens into a capsule with name,
play/pause and `×`. It can be dragged to any of the four corners, and the corner is remembered.
**Why now.** The author, 2026-11-01, after AP-004: "Пилюли нет при закрытии сайдбара… для видео
она нужна". With the sidebar hidden, PiP is out of reach again.

Not in this ticket: the flight between row and corner (AP-008). The bubble fades and scales in
(200 ms) instead.

## Steps
1. **The corner setting.**
   - `core/data/SettingsEnums.kt`: `enum class WindowCorner { TopLeft, TopRight, BottomLeft,
     BottomRight }`.
   - `WokaSettings.kt`: `nowPlayingCorner: WindowCorner = WindowCorner.BottomLeft` beside
     `sidebarVisible`.
   - `SettingsRepository.kt` (`LibrarySettings`): `fun setNowPlayingCorner(corner: WindowCorner)`.
   - `LibrarySettingsImpl.kt`: `mutator.update { it.copy(nowPlayingCorner = corner) }`.
2. **Where the viewer is.** `NowPlaying` gains `var shownInViewer: String? by mutableStateOf(null)`
   — the path the viewer has open.
   - `MediaViewerActiveItemEffects` (`viewer/shell/MediaViewerScreenEffects.kt`, 130 lines) writes
     `state.activeItemPath` into it, keyed on that path.
   - A `DisposableEffect` clears it when the viewer leaves.
3. **Corner geometry** — `nowplaying/BubbleCorners.kt` (new). Pure functions, unit-tested:
   - `cornerOffset(corner, container: IntSize, bubblePx, edgePx, topPx): IntOffset`: 16 dp from
     the window's edges; a top corner starts `topPx` = 52 + 12 dp down, under the title bar.
   - `nearestCorner(center: Offset, container: IntSize): WindowCorner`: by the quadrant `center`
     falls in.
4. **Tokens** — `NowPlayingTokens.kt`:
   - `.bub`: `BubbleSize` 48, `BubbleEdge` 16, `BubbleTop` 64; `BubbleCover` 40; `RingStroke` 2.
   - `.more .t`: `CapsuleText` 150.
   - Durations: `CapsuleMs` 260, `SnapMs` 320, `FadeMs` 200.
5. **The pill** — `nowplaying/NowPlayingPill.kt` (new): `NowPlayingPill(source, toRight: Boolean)`.
   - Glass in a 24 dp-radius shape: `surface` at 0.85 alpha, a 1 dp `border`, a 12 dp shadow.
     Not `floatingPanel()`: it fixes a 20 dp radius and 6 dp padding.
   - A 46 dp ring box: a `Canvas` arc for `progress`, `border` under `accent`, 2 dp. The cover is
     40 dp and round, inside the ring.
   - At rest the cover carries a 28 % black scrim with the state glyph: three animated bars while
     playing, `Pause` while paused. On hover the scrim fades out.
   - On hover `AnimatedVisibility(expandHorizontally)` over 260 ms adds the title and subtitle
     (150 dp wide), play/pause and `×` (`RoundIconButton`). For a right corner (`toRight = false`)
     the row is reversed, so the cover stays at the edge.
   - A click on the cover or the text calls `reveal()`.
6. **The bubble** — `nowplaying/NowPlayingBubble.kt` (new): `NowPlayingBubble()`, filling the
   window.
   - **Visible** when all of these hold:
     - the slot holds a source;
     - `!sidebarVisible`;
     - `!EditorPresence.isOpen`;
     - `shownInViewer != source.path`.
     It fades and scales from 0.85.
   - **Anchor**: a 48 dp box placed by an `Animatable<IntOffset>` at `cornerOffset`. The pill is
     `wrapContentWidth(unbounded = true)` inside it, aligned Start in a left corner and End in a
     right one, so the capsule grows away from the edge.
   - **Drag**: `detectDragGestures` on the anchor.
     - `onDrag` calls `snapTo` with the offset plus the drag.
     - `onDragEnd` finds `nearestCorner` of the anchor's centre, animates there over 320 ms and
       saves it with `setNowPlayingCorner`.
     - While dragging, four 48 dp dashed circles mark the corners. The nearest one is drawn in
       `accent`.
     - Compose's own touch slop separates a drag from a click; the mockup's 4 dp was a browser
       stand-in.
   - **Resize**: when the container size or the saved corner changes, the anchor `snapTo`s the new
     `cornerOffset`.
7. **Mount** — `App.kt`: `NowPlayingBubble()` in the root `Box` after `StatusBanners()`, so it
   draws above the sidebar and all content.
8. **Tests**:
   - `jvmTest/.../unit/BubbleCornersTest.kt` (new): each corner's offset in a 1000×700 container,
     the top corners under the bar; `nearestCorner` for a point in each quadrant.
   - `NowPlayingTest`: `shownInViewer` is plain state, so nothing to add there.

## Files
`shared/src/commonMain/kotlin/com/woka/core/data/SettingsEnums.kt`, `.../core/data/WokaSettings.kt`,
`.../core/data/SettingsRepository.kt`,
`shared/src/jvmMain/kotlin/com/woka/core/data/LibrarySettingsImpl.kt`,
`.../app/shell/nowplaying/NowPlaying.kt`, `.../nowplaying/NowPlayingTokens.kt`,
`.../nowplaying/BubbleCorners.kt` (new), `.../nowplaying/NowPlayingPill.kt` (new),
`.../nowplaying/NowPlayingBubble.kt` (new), `.../app/App.kt`,
`shared/src/jvmMain/kotlin/com/woka/feature/viewer/shell/MediaViewerScreenEffects.kt`,
`shared/src/jvmTest/kotlin/com/woka/unit/BubbleCornersTest.kt` (new).

## Done when
- Hiding the sidebar while PiP plays shows the bubble bottom-left. Showing the sidebar removes it.
- Hover opens the capsule away from the edge in all four corners. Play/pause and `×` work, and a
  click on the cover brings PiP forward.
- The ring follows the video's position. The glyph shows bars while playing and pause while
  paused.
- Dragging snaps to the nearest corner, and the corner survives a restart.
- The bubble is absent in the editor, back on leaving it, and absent while the viewer shows the
  slot's own file.
- Every `.kt` is ≤ 150 lines, the anti-FQN grep is clean, and `:shared:build` is green.

## Result — 2026-11-01
- `:shared:build` green; `BubbleCornersTest` 3/3.
- **By hand, the author, 2026-11-01:** "Всё работает" — corners, capsule, drag, persistence, editor.
- Two more files than listed, for the size mandate: `NowPlayingGlyphs.kt` (ring, bars, corner
  targets) and `NowPlayingCapsule.kt` (split from `NowPlayingPill.kt` at 162 lines).
- The pill is placed by a `layout` modifier at the anchor, not inside a 48 dp box: a child drawn
  outside its parent's bounds would not be reliably hit-tested, and the capsule is exactly that.
- `scaleIn`/`scaleOut` pivot on the anchor — the layer is the whole window, so the default
  centre pivot would fly the bubble out of the middle of the screen.
