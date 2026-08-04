# Plan: Smart Chapters (Step 12 of `video-player-enhancements.md`, §4)

Container chapters (MKV) drawn as YouTube-style segments on the seek bar, plus a hover pill naming
the segment, and a chapter list in the ⚙ menu.

## ✅ Shipped — where the build differs from the plan below

The plan is kept as written; these five points override it, so read them first.

1. **`probeChapters` lives in its own `MediaChapterProbe.kt`,** not in `MediaTrackProbe.kt` — the
   normalisation would have put that file at ~165 lines, over the §2 mandate.
2. **The probe survives `durationMs == 0`.** It is occasionally still 0 right after `start()`, and
   clamping to it would have dropped every chapter; the ceiling falls back to the largest chapter
   end instead.
3. **The hovered segment lightens by blending towards white (`lerp`, 0.30), not by raising alpha
   0.2 → 0.35.** The plan assumed a translucent inactive colour, but the real caller passes an
   opaque `Theme.colors.textSecondary` — lowering its alpha darkens it against the panel.
4. **Added on request after the first live test: a YouTube-style hover preview fill** — everything
   up to the cursor drawn in a lighter shade (`lerp` 0.50), under the played fill so the accent
   still reads as the real position. Not drawn during a drag, where the accent already follows.
5. **The pill is positioned by a `PopupPositionProvider`, not `Popup(alignment, offset)`.** Phase 4
   as written shipped broken and is the one real defect this step produced — see the Step 12 entry
   in `video-player-enhancements.md` for the diagnosis. Two extra files came out of it:
   `MediaSliderTooltip.kt` (the pill, split off because `MediaSlider.kt` would have passed 150) and
   `MediaSliderHover.kt` (`Modifier.sliderHover`, one `pointerInput(Unit)` loop instead of three
   event-keyed `onPointerEvent` modifiers).

## Decisions taken up front (do not re-litigate)

- **The hover pill is shown always** — with or without chapters — carrying the time, and the chapter
  title as a second line when there is one. This is a deliberate change to shipped behaviour: today
  `MediaSlider` only draws its pill while dragging, and **no caller passes `tooltipText` at all**, so
  the pill has never appeared in the app. Precise scrubbing wants it regardless of chapters.
- **No persistent current-chapter label** in the control bar. The name is available on hover; a
  permanent line is noise against the HIG minimalism the panel is built on.
- **No keyboard shortcuts** for chapter skip. `Alt`+arrows are already ±5 s (Step 7) and the
  remaining combinations are not discoverable.
- **The chapter list lives in the ⚙ menu**, not on the panel — same reasoning as Video Track (§2.3):
  secondary navigation belongs in the settings drawer.
- **Clicking a segment seeks to the clicked position, not to the chapter start.** Segmentation is
  purely visual; the tap/drag logic is untouched.
- **In the resting state the bar looks exactly as it does today.** Segments, gaps and the taller
  track appear on hover only.

## Measured facts this plan is built on

Chapters are read from the **already-open** `AVFormatContext` the track probe uses, so this costs no
extra I/O — unlike subtitles, which needed a full walk of the container.

Test corpus already on disk, nothing to build:

| File | Chapters |
|---|---|
| `Yanni.Live.The.Concert.Event.mkv` | **15**, named and contiguous: `Creating` 0–88 s, `Standing in Motion` 88–178, `Rainmaker` 178–564 … `End credits` 4698–4877 |
| `Avatar…S02E01.mkv` | **2**: `Part 01` 0–3666 s, `End Credits` 3666–4077 |
| `_TEST bitmap subtitles/_TEST.bitmap-subs.mkv` | **0** — the no-chapters case |

`AVChapter.time_base` is `1/1000000000` in both files, so the conversion must go through the
chapter's own timebase rather than assume milliseconds.

## Phase 0: Split `MediaSlider.kt` (§2 file-size mandate)

`MediaSlider.kt` is **199 lines** and has been first on the §2.7 watch list since Step 7. It cannot
be edited before it is split. `MediaVerticalSlider` is an unrelated control (used only by the volume
overlay) and is the natural cut.

- Move `MediaVerticalSlider` verbatim into `MediaVerticalSlider.kt`. No behaviour change.
- Verify `MediaSlider.kt` lands at ~140 lines.

**Files:** `MediaSlider.kt`, new `MediaVerticalSlider.kt`

## Phase 1: Model and probe

- Add `MediaChapter(startMs: Long, endMs: Long, title: String)` in commonMain, next to `MediaTrack`.
- Add `probeChapters(grabber: FFmpegFrameGrabber, durationMs: Long): List<MediaChapter>` in
  `MediaTrackProbe.kt` (105 lines, room for it): walk `formatContext.nb_chapters()` /
  `chapters(i)`, convert `start`/`end` through `time_base`, read the title from
  `av_dict_get(metadata, "title")`.
- Normalise inside the probe, so no consumer has to:
  - `end <= start` → end at the next chapter's `start`; the last one ends at `durationMs`.
  - Empty or missing title → `"Chapter N"` (1-based).
  - Clamp both ends to `0..durationMs`; drop entries left empty by the clamp.
  - Return `emptyList()` when the container reports fewer than 2 chapters — one chapter is not a
    segmentation, and the UI then behaves exactly as it does for a file with none.
  - Gaps between chapters are **kept as gaps**, not absorbed into the neighbour: stretching a
    segment over a hole would draw a boundary the file does not claim.

**Files:** new `player/MediaChapter.kt` (commonMain), `player/MediaTrackProbe.kt`

## Phase 2: State wiring

- Add `var chapters: List<MediaChapter>` to `VideoPlayerTracks` (it already holds the probe result;
  `VideoPlayerState.kt` is at 151 lines and must not grow).
- In `VideoPlayerDecoder.startDecoderLoop`, call `probeChapters` next to `probeMediaTracks` — after
  `durationMs` is assigned, since the last chapter's end depends on it — and assign the result.
- Add `val chapters: List<MediaChapter>` passthrough on `VideoPlayerState` only if a call site needs
  it; otherwise read `videoState.tracks.chapters` directly.

**Files:** `player/VideoPlayerTracks.kt`, `player/VideoPlayerDecoder.kt`

## Phase 3: Segments on the track

- Add `chapters: List<MediaChapter> = emptyList()` and `durationMs: Long = 0L` parameters to
  `MediaSlider`. Both default to "no chapters", so the single existing caller is unaffected until
  Phase 5 wires them.
- Track hover in `MediaSlider`: `hoverX: Float?` via `onPointerEvent(Move/Enter/Exit)`, held
  separately from `dragX` — the pill follows the cursor, while `displayValue` keeps following the
  drag.
- New `MediaSliderChapters.kt` drawing the segmented track:
  - Segment rects come from `startMs/durationMs` … `endMs/durationMs`, minus a **2 dp** gap on each
    inner boundary.
  - Each segment draws the inactive colour, then the played part of that segment in the active
    colour, so the fill still reads as one continuous progress bar.
  - Heights: **3 dp** at rest, **5 dp** while the slider row is hovered, **7 dp** for the segment
    under the cursor, animated with `animateDpAsState` like the existing thumb growth.
  - The hovered segment also lightens its inactive colour (alpha 0.2 → 0.35).
  - With `chapters` empty the composable draws the current single-piece track, so `MediaSlider` has
    one drawing path rather than two.
- Derive the hovered index with `derivedStateOf` over `hoverX`: the pointer moves at frame rate and
  must not recompose the whole control bar.

**Files:** `MediaSlider.kt`, new `MediaSliderChapters.kt`

## Phase 4: The hover pill

- Change the pill's condition from `dragX != null` to `hoverX != null || dragX != null`.
- Position it from the cursor (`hoverX`), not from `displayValue`, and **clamp** the offset so the
  pill stays inside the panel at both ends — at 0 % and 100 % it currently runs off the edge.
- Two-line content: the chapter title (12 sp, `FontWeight.SemiBold`, white) above the time (12 sp,
  white at 0.7 alpha). With no chapter under the cursor only the time line is drawn, which is the
  no-chapters case as well.
- Time comes from `formatTime` in `MediaViewerTimeSlider.kt`, so the pill and the readouts on either
  side of the bar agree. *(Note: `formatTime` prints `mm:ss` with unbounded minutes — an 81-minute
  concert reads `81:16`. Changing that is out of scope here.)*
- `MediaViewerTimeSlider` passes `chapters`, `durationMs` and a `tooltipText` lambda into
  `MediaSlider`.

**Files:** `MediaSlider.kt`, `MediaViewerTimeSlider.kt`

## Phase 5: Chapter list in the ⚙ menu

- Add a `PlayerChapterSection(videoState)` composable: a `PopoverSectionLabel("Chapters")` plus one
  `PopoverListRow` per chapter, labelled `"$title"` and selected when
  `currentPositionMs` falls inside it. Clicking calls `videoState.seek(chapter.startMs)`.
- Hidden entirely when `chapters` is empty, like the Video Track section.
- Call it from `PlayerSettingsMenuList` above `PlayerSyncSection`.
- `PlayerSettingsMenu.kt` is 79 lines; if the section is written inline it would pass the limit, so
  it goes in its own file.

**Files:** new `PlayerChapterSection.kt`, `PlayerSettingsMenu.kt`

## Verification

`./gradlew.bat :shared:compileKotlinJvm -q` after each phase, the anti-FQN Grep over every edited
`.kt`, then live:

1. **Yanni concert** — 15 segments appear on hover with correct proportions (the 88 s opener is a
   sliver, `Rainmaker` is wide); the segment under the cursor is taller and lighter; the pill names
   it and shows the time; the played fill crosses segment boundaries continuously.
2. **Avatar E01** — two segments, boundary at 61:06.
3. **`_TEST.bitmap-subs.mkv`** — no segments, and the pill still appears on hover with the time
   alone.
4. The resting bar is pixel-identical to before on all three.
5. Clicking mid-segment seeks to that point, not to the chapter start; dragging still works and the
   pill follows the drag.
6. The pill stays inside the window at 0 % and 100 %.
7. ⚙ lists the 15 chapters, marks the current one, and jumps on click; the section is absent on the
   chapter-free file.
8. Windowed and fullscreen behave identically (both route through `MediaViewerVideoControls`).

## Out of scope

- Chapter thumbnails in the pill (needs a preview-frame pipeline that does not exist).
- Editing or authoring chapters.
- Chapter support for formats FFmpeg does not expose chapters for.
