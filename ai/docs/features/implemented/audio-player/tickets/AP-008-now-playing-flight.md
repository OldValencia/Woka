# AP-008 — Now Playing: the cover flies between row and corner

| | |
|---|---|
| **Order** | 4 — after AP-006, last of the Now Playing tickets |
| **Depends on** | AP-004 (row), AP-006 (bubble) |
| **Feature** | [Audio player](../audio-player.md) — §3.5; spec `now-playing.html` (`fly()`, `.ghost`) |

**Goal.** Hiding the sidebar flies the row's cover into the bubble's corner, and the bubble
appears when it lands. Showing the sidebar flies the bubble's cover back into the row.
**Why now.** The author, 2026-11-01: "Го давай дальше". It is the last part of the accepted mockup.

## Steps
1. **Where the row's cover is** — `NowPlaying` gains `var rowCover: Rect? by mutableStateOf(null)`.
   - `NowPlayingCover` takes a `modifier` parameter.
   - `NowPlayingRow` passes `Modifier.onGloballyPositioned { rowCover = it.boundsInRoot() }`.
2. **The flight** — `nowplaying/NowPlayingFlight.kt` (new).
   - `class CoverFlight` holds an `Animatable` from 0 to 1, the two root rects, the two corner
     radii and `active`.
   - `rememberCoverFlight(sidebarVisible, eligible, disc: () -> Rect)`:
     - **The rested row.** It keeps the last `rowCover` whose left edge is ≥ 0. The sidebar hides
       by sliding its content left under a clip, so a negative left edge means a row that is not
       fully on screen, and the one kept is where the row will rest once shown again.
     - **The trigger.** On a change of `sidebarVisible` — not on first composition — and only while
       `eligible`, it runs 0 → 1 over 300 ms.
     - **Hiding:** from the rested row (radius 6) to `disc()` (radius 20).
     - **Showing:** from `disc()` to the rested row.
   - `FlightGhost(flight, source, layerOrigin)` draws `NowPlayingCover` at the interpolated rect,
     moved from root into the layer's coordinates.
3. **Bubble** — `NowPlayingBubble.kt`:
   - `eligible` = a source, no editor, and not shown in the viewer.
   - `disc()` = the anchor plus 4 dp, 40 dp square (`.bub .cover`), converted to root coordinates
     with the layer's `positionInRoot`.
   - `visible` also needs `!flight.active`. While a flight is pending, the exit is
     `ExitTransition.None`, so the bubble does not fade under the ghost that replaces it.
4. **Tests** — `unit/CoverFlightTest.kt` (new): `lerpRect` at 0, ½ and 1 (the interpolation is
   pure, so it is extracted as `internal fun lerpRect`). Nothing else here is checkable without a
   window; it is checked by hand.

## Files
`.../app/shell/nowplaying/NowPlaying.kt`, `NowPlayingCover.kt`, `NowPlayingRow.kt`,
`NowPlayingBubble.kt`, `NowPlayingFlight.kt` (new),
`shared/src/jvmTest/kotlin/com/woka/unit/CoverFlightTest.kt` (new).

## Done when
- Hiding the sidebar while PiP plays: the cover leaves the row, lands in the corner, and the
  bubble is there. Showing the sidebar: the bubble's cover flies into the row.
- No flight while the editor is open, while the viewer shows the slot's file, or with the slot
  empty.
- Files ≤ 150 lines, the anti-FQN grep is clean, and `:shared:build` is green.

## Result — 2026-11-01
- `:shared:build` green; `CoverFlightTest` 2/2.
- **By hand, the author, 2026-11-01:** "Всё работает".
- The row publishes `positionInRoot` + size, not `boundsInRoot`: the latter is clipped, and the
  sidebar hides by clipping, so a hidden row would never read left of zero.
- `NowPlayingParts.kt` (new) holds `RowText` and `RoundIconButton`, split from `NowPlayingRow.kt`
  at 154 lines.
- `active` is cleared in `finally`: a second toggle mid-flight cancels the first, and a flag left
  set would keep the bubble hidden for good.
- The bubble arrives without its 200 ms fade after a flight in (`landed`), and leaves without one
  when the sidebar opens — the ghost is the transition both times.
