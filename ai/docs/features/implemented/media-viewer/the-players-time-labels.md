# Plan: The player's time labels

**Status: complete, verified by hand.** The hours, the total/remaining flip from all three
entry points, and the hand cursor across the whole seeker pill all work.

`formatTime` knew only minutes and seconds, so a 95-minute film read `95:03` — minutes that never
rolled over into hours. The right-hand label was also dead text: always the total, where every other
player lets you flip it to what is left.

Decided with the user:

- **Hours are automatic. No setting.** Under an hour `25:05`, over it `1:24:34`. A switch for this
  would only have preserved the behaviour being complained about.
- The hour is the **largest unit and is not capped** — a three-day recording reads `74:12:05`. Days
  would be a fourth field and a unit nobody reads off a seek bar.
- **The right label flips between total and remaining**, by clicking it *and* from two settings
  surfaces. The click **is** the setting, so they cannot drift apart.
- **No animation on the label.** The affordance is the cursor — and the whole seeker pill was
  missing it, which got fixed in the same pass.

## Phase 1: The formatter

**New** `jvmMain/feature/viewer/video/MediaViewerTimeLabel.kt`. `MediaViewerTimeSlider.kt` was at 149
lines and could hold none of this, so the formatter and both labels moved out and the slider kept
the row (149 → 131).

```kotlin
internal fun usesHours(durationMs: Long): Boolean
internal fun formatTime(ms: Long): String                      // the shape a lone value picks
internal fun formatTime(ms: Long, withHours: Boolean): String  // the shape the file dictates
```

**Both labels take their shape from the *duration*, never from their own value.** Four minutes into a
95-minute film the left label reads `0:04:12`, not `4:12`; otherwise it gains two characters as the
film crosses an hour and shoves the seek bar sideways mid-playback. The seek tooltip is passed the
same flag for the same reason.

The one-argument form keeps the two existing callers working and simply gains hours: the seek
tooltip, and the video editor's band (`VideoEditorBandParts.kt:88`) — the only place this reaches
outside the player.

Nothing can overflow: the labels size to their content and the slider between them carries
`weight(1f)`, so a freakishly long file costs the bar a couple of characters and clips nothing.
`maxLines = 1` says so explicitly, and tabular figures (`fontFeatureSettings = "tnum"`) stop the
digits breathing as the seconds tick. A face with no `tnum` table ignores it, which is today's
behaviour.

**Files:** `video/MediaViewerTimeLabel.kt` (new), `video/MediaViewerTimeSlider.kt`

## Phase 2: Total or remaining

- `WokaSettings.showRemainingTime: Boolean = false`. A `Boolean`, not a two-value enum: the label is
  a toggle, and an enum would turn every read into "if it is Total then Remaining else Total".
- `setShowRemainingTime` on `PlaybackSettings`.
- `TrailingTimeLabel` renders `−` + `formatTime(durationMs - position(), withHours)` with a real
  minus (U+2212, not a hyphen: it sits on the digits' baseline and carries their width), and its
  `clickable` writes the setting. No local state at all.
- `position()` is read **only** in the remaining branch, so showing the total still costs no
  recomposition at the presenter's frame rate (§10.5).
- Remaining counts down the file's timeline, not wall-clock at the current speed — it has to agree
  with the bar underneath it.

**Files:** `core/data/WokaSettings.kt`, `core/data/SettingsRepository.kt`,
`jvmMain/core/data/PlaybackSettingsImpl.kt`, `video/MediaViewerTimeLabel.kt`

## Phase 3: Where the setting is offered

- Player ⚙ — a `ValueRow` beside Repeat and Shuffle: `Time left` → `Total` / `Remaining`.
- Settings → Video Player — a `Time` section with a two-option `SegmentedControl`.

**Files:** `video/PlayerSettingsPlayback.kt`, `video/PlayerSettingsMenu.kt`,
`feature/settings/VideoPlayerSettingsPanel.kt`

## Phase 4: The hand cursor, across the whole seeker pill

59 files in the repo already called `pointerHoverIcon(PointerIcon.Hand)` and the pill — the `Row` at
`MediaViewerVideoControls.kt:41` — was the hole. Every interactive thing in it now has it: play /
pause, ±5 s, **the seek bar**, the right time label, CC, speaker, **the volume bar**, ⚙ and
fullscreen.

`Modifier.sliderCursor()` in `slider/MediaSliderHover.kt` serves both bars from one line. Kept out
of `sliderHover` itself: that reports *where* the pointer is, and a caller wanting the position
without claiming to be clickable would have had no way to ask.

The ⚙ is the odd one — it owns no `clickable` at all, its popover opens on hover — but it reads as a
button and has to feel like the rest of the pill.

Deliberately untouched: the left label (clicking it does nothing), the pill's background, and
`MediaViewerTapGestures` / `MediaViewerFrame` / `MediaViewerFullscreenSurface`. Clicking the picture
pauses it, but the picture is not a button and a hand cursor over the whole film is wrong.

**Files:** `slider/MediaSliderHover.kt`, `slider/MediaSlider.kt`, `slider/MediaVerticalSlider.kt`,
`video/MediaViewerSkipButton.kt`, `video/MediaViewerVideoControls.kt`,
`video/MediaViewerVolumeControl.kt`, `video/SubtitleControl.kt`, `video/PlayerSettingsControl.kt`

## Phase 5: Tests

`jvmTest/unit/TimeFormatTest.kt` (6): clip vs film, the forced shape, 74 hours uncapped, zero and
negative clamped, the hour boundary rolling over rather than counting on, and the forced shape
agreeing with the auto one past an hour.

## Decisions

| Date | Question | Choice |
|---|---|---|
| 2026-09-20 | Is the time format a setting? | No. Hours appear when the file has them; a switch would only preserve the complaint. |
| 2026-09-20 | Largest unit? | The hour, uncapped. |
| 2026-09-20 | Does the position label follow its own value or the duration's? | The duration's, or it grows mid-playback and pushes the bar. |
| 2026-09-20 | Remaining against playback speed? | No. The label must agree with the bar beneath it. |
| 2026-09-20 | Setting or click for total/remaining? | Both, one state: the click writes the setting. |
| 2026-09-20 | Animation to advertise the click? | None — the user rejected it outright. The hand cursor, everywhere in the pill that lacked it. |
