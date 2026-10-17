# SIDE-006 — Hovering a video plays five excerpts

| | |
|---|---|
| **Order** | sixth |
| **Depends on** | SIDE-004 |
| **Feature** | [Sidebar](../sidebar.md) |

**Goal.** Rest the pointer on a video's card or tile and, after 400 ms, it plays five excerpts of
2.5 s each, taken at 10, 30, 50, 70 and 90 % of the runtime. The excerpts are muted and tone-mapped
when the file is HDR. A five-segment bar across the top fills as they go, and they loop. Leaving
the card stops playback and the poster returns.

**Why now.** The author asked for it in these words: *«наводишь на видео и тебе показывает 5 секций
и там видео по 2-3 секунды показывается»*. A shelf of films is otherwise a shelf of stills.

## The measurement this was gated on — 2026-10-17

`probe/HoverExcerptProbe`, against two 2160p files on the author's disk. The grabber was opened as
`SeekPreviewSource` opens one: capped probe, no audio, RGBA, scaled down. It ran five positions per
file and 60 consecutive frames per position.

| file | threads | seek + first frame | 60 frames | sustained | film |
|---|---|---|---|---|---|
| UHD Blu-ray remux, HEVC, 63.6 GB | 2 | 287–567 ms | 1372–1587 ms | **38–44 fps** | 23.976 |
| same | auto | 145–295 ms | 323–387 ms | 155–186 fps | 23.976 |
| WEB-DL, HEVC DV/HDR, 9.1 GB | 2 | 231–786 ms | 922–994 ms | **60–65 fps** | 23.976 |
| same | auto | 122–210 ms | 208–220 ms | 273–289 fps | 23.976 |

**What it settles:**
- **Real motion, not stills.** At two threads, the seek preview's own budget, the worst file still
  decodes 1.6× faster than it plays. So the excerpts are moving pictures, not a slideshow of
  stills.
- **The seek is the cost.** Up to 0.8 s to land is too long to wait between excerpts. The next
  excerpt is therefore decoded while the current one plays: one excerpt is held ready, one is
  drawing.
- **2.5 s per excerpt.** A seek plus 60 frames at two threads takes at most about 2.15 s on the
  remux, so the producer keeps ahead of a 2.5 s excerpt with room to spare.
- **Two threads, not auto.** Auto is five to seven times faster and would take every core from
  whatever else is running. Two keeps up, which is all the excerpt needs.

## Steps

1. **The reel: `feature/viewer/video/engine/preview/HoverExcerptReel.kt` (new).**
   - **The grabber** is opened as `SeekPreviewSource` opens its own: 2 threads, output 320 px wide
     at the file's aspect, no audio stream.
   - **What it reads:** `readStreamHdrInfo()` for the tone curve, and the frame rate and length.
   - **The producer** runs on one confined thread, because a grabber is not thread-safe. It loops
     over the five positions, seeking and decoding `round(2.5 × fps)` frames into an `Excerpt`
     (its index and its frames). It sends each into a `Channel` with capacity 1, which holds one
     excerpt ready while the current one draws.
   - **The grabber is closed** on that same thread when the collector is cancelled.

2. **The drawing: `feature/viewer/VideoHoverExcerpts.kt`, an `expect` composable in `commonMain`
   with its `actual` in `jvmMain`.** `VideoHoverExcerpts(file, modifier)`:
   - waits 400 ms, so a pointer crossing the grid decodes nothing;
   - draws each excerpt's frames at the film's rate through `rememberFramePainter(moving = true)`,
     so HDR goes through the same curve the player uses;
   - draws the five-segment bar: 2.5.dp high, white at 30 %, filled white as it plays.
   
   It lives in `feature/viewer`, as `NativeMediaViewer` does, because it is the player's
   machinery.

3. **The slot.** `FolderCard`, `FolderCardBody` and `FolderCardTileBody` take
   `coverOverlay: (@Composable BoxScope.(hovered: Boolean) -> Unit)? = null`. It is drawn over the
   cover, under the caption and bar. `core` stays ignorant of the viewer; the folder feature fills
   the slot.

4. **Wiring.** `FolderGridItem` passes `VideoHoverExcerpts` as the overlay for a
   `MediaItem.Video` while it is hovered.

5. **The probe stays** as the evidence, in `jvmTest/probe/`, run by hand, with its files taken from
   `WOKA_PROBE_VIDEOS`.

## Files

- New:
  - `jvmMain/…/feature/viewer/video/engine/preview/HoverExcerptReel.kt`
  - `commonMain/…/feature/viewer/VideoHoverExcerpts.kt`
  - `jvmMain/…/feature/viewer/VideoHoverExcerpts.jvm.kt`
  - `jvmTest/probe/HoverExcerptProbe.kt`
- Edit:
  - `core/ui/components/FolderCard.kt`, `FolderCardBody.kt`, `FolderCardTile.kt`
  - `feature/folder/FolderGridItem.kt`

## Done when

- By hand, with the result dated in this file:
  - hovering an SDR and an HDR film plays five moving excerpts with the bar filling;
  - HDR looks like the player, not washed out;
  - the pointer leaving stops it at once;
  - sweeping the pointer across the grid starts nothing;
  - CPU use while one excerpt plays is visibly below playback's.
- No file over 150 lines; the anti-FQN grep is clean; `./gradlew.bat :shared:build` is green.

## Result

- 2026-10-17: built; `:shared:build` green with 1388 tests. The probe prints and asserts nothing
  without `WOKA_PROBE_VIDEOS`.
- The producer is a `flow` with `buffer(1)` ahead of `flowOn(Dispatchers.IO.limitedParallelism(1))`,
  which fuses into one channel of capacity 1: one excerpt drawing, one ready.
  `flowOn`'s default buffer of 64 would have decoded whole minutes ahead of a card that may be
  left the next moment.
- 2026-10-17: the by-hand check passed, run by the author, who asked for the commit.

