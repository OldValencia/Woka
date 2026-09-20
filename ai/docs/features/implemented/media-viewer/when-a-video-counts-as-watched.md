# Plan: When a video counts as watched

**Status: complete, verified by hand.** The threshold setting, the credits-chapter signal and the
grid bar all work; one bug surfaced while using it and is written up below.

A film watched to its credits and closed there was not recorded as watched. `saveResumePosition`
only wrote `finished` once the play head passed `finishedFromMs` — `duration − min(30 s, 8 %)`, the
last half-minute of a film. Stopping during a closing title sequence never reaches that, so the
record stayed "in progress" and the grid drew a part-filled bar for a film that was over.

The threshold becomes a setting — **Most of it** (default) or **The very end** — refined per file by
the container's own credits chapter when it names one.

Decided with the user:

- "Most of it" = **95 %** of the duration. Measured against a real file: 95:03 long, end credits from
  88:15 — 92.8 %. Both shares are wrong on it, and 95 % is wrong in the safe direction, marking the
  film two minutes into its credits rather than three minutes before its last scene has played.
- Crossing the threshold **clears the resume point**, exactly as finishing does. Watched is watched;
  anyone who wants the last minutes back switches the setting.
- **No migration.** Existing records keep whatever `finished` they hold; the new threshold governs
  writes from here on. Woka runs on one machine.
- **Marking is not stopping.** Crossing the threshold writes a record and nothing else — the file
  keeps playing. See the section below, which is why it is a section and not a line.

## Marking must not touch playback

- The player is `remember(playedItem, itemReady)` (`MediaViewerVideoSource.kt`). `WokaSettings` is
  not a key, so a settings write cannot rebuild or restart the decoder.
- Every settings-driven effect in the viewer keys on one field, never the whole object:
  `settings.repeatMode`, `settings.playbackSpeed` (`MediaViewerPlaylist.kt`),
  `settings.systemMediaControls`, `settings.hdrViewMode` (`MediaViewerScreenEffects.kt`). Nothing in
  the viewer reads `watchRecords` at all — only the grid does, through `rememberWatchMarks`.
- `resumePositionMs()` is read once, when the decoder starts (`VideoPlayerDecoder.kt`). Rewriting the
  record mid-file changes nothing about the file already open.
- Playback stops only on `reachedEnd`, raised by the presenter at real EOF. This feature never
  touches that path.
- The five-second saver does not thrash after the mark: the finish branch is guarded on
  `existing?.finished != true`, so it writes once and every later tick is a no-op.

One consequence that is **not** new but is far easier to reach at 95 %: rewinding well back after the
mark clears it, because `withWatchPosition` sets `finished = false` for any position above the floor.
Re-watching the middle of a film has always meant "in progress again". Left alone.

## Bug found by using it: the bar was under the caption

Reported as "watched full and watched partly look the same, the bar under the preview is gone", with
the dimming still visible. Found by reading the live `settings.json` rather than by guessing: every
record in `D:\Movies` had a drawable fraction (`Mutiny` finished, Spider-Man at 19 %, `МАМА В 16` at
16 %), so `WatchRecord.progress` was not the problem and neither was `WatchMarks.forVideo` — the dim
is drawn from the same record and it was showing.

`FolderCardProgress` was called from inside `FolderCardPreview`, whose `Box` is composed **before**
`FolderCardCaption` in `FolderCardBody`. The caption's scrim is a sibling aligned `BottomCenter`,
`fillMaxWidth`, `fillMaxHeight(0.52f)`, ending at `Color.Black` alpha **0.86** — exactly along the
edge the 3 dp bar occupies. A later sibling paints over an earlier one, so the bar was there and
invisible, and a full bar and a 19 % bar looked identical: both buried. `zIndex` cannot fix it —
the bar's parent *is* the preview `Box`, a sibling of the caption, so ordering inside it changes
nothing.

Fixed by moving the call up one level, into `FolderCardBody` after the caption. No kind check goes
with it: only a video ever gets a record (`WatchMarks.forVideo`), so a folder or a photograph passes
null and draws nothing.

Predates this feature — the caption moved onto the cover after the markers shipped — but the 95 %
threshold is what made it visible, by finally putting a finished mark next to a partial one.

## Phase 1: State

- **New** `core/data/ResumeMode.kt` — `ResumeMode` and `resumeAppliesTo` move out of
  `WokaSettings.kt` (150 lines, at the mandate ceiling), joined by `enum class WatchedWhen { MostOfIt,
  TheVeryEnd }`.
- `WokaSettings`: `val watchedWhen: WatchedWhen = WatchedWhen.MostOfIt`.

**Files:** `core/data/ResumeMode.kt` (new), `core/data/WokaSettings.kt`

## Phase 2: Contract

- `SettingsRepository`: `fun setWatchedWhen(mode: WatchedWhen)` on `PlaybackSettings`.
- `PlaybackSettingsImpl`: one `mutator.update`.

`SettingsMutations` and `WatchRecord` are untouched — because the mark still clears the position,
`withWatchFinished` already writes the exact record this needs.

**Files:** `core/data/SettingsRepository.kt`, `jvmMain/core/data/PlaybackSettingsImpl.kt`

## Phase 3: Thresholds

- **New** `jvmMain/feature/viewer/video/engine/WatchThresholds.kt` takes the constants plus
  `finishedFromMs` and `watchFloorMs` out of `PlaybackResume.kt` (113 lines before this), which keeps
  the saver and the resume lookup.
- `WATCHED_SHARE = 0.95f` and
  `watchedFromMs(durationMs, mode: WatchedWhen, creditsStartMs: Long?)`:
  - `durationMs <= 0` or `TheVeryEnd` → `finishedFromMs(durationMs)`, today's behaviour exactly.
  - `MostOfIt` → `minOf(share, creditsStartMs ?: share, finishedFromMs(…))`. A credits chapter can
    only move the mark **earlier**: it is precision on top of the share, not a replacement, and a
    container that starts its credits at 98 % must not make `MostOfIt` stricter than it promises.
    The `finishedFromMs` term is load-bearing at this share — 95 % of a one-minute clip is 0:57, past
    the 0:55.2 the tail ratio puts it at — so without it the default would be stricter than
    `TheVeryEnd` on short files.
- `creditsStartMs(chapters, durationMs)`: a title containing `credit`, `outro` or `титр`,
  case-insensitively, **and three guards, any one of which returns null and drops back to the flat
  share**. The chapter list is metadata someone typed; a hint, never an instruction.
  - **Last chapter only** — a series that opens with its titles (chapter 1 `Credits`, chapter 2 the
    episode) is never looked at.
  - **Starts at or after 60 % of the duration** — a `Credits` chapter at 0, or at 5 %, describes
    something that is not a film's closing credits whatever it is called. Rejected outright rather
    than clamped: a wrong early mark is the failure this feature exists to avoid.
  - **Two chapters or more**, already enforced by `probeChapters` — a single chapter spanning the
    whole file is not a segmentation and never reaches this code.

  It fires only when the container names the chapter, and nothing more: `smart-chapters.md` records
  two files from this library that do (`End Credits` at 89.9 % of an Avatar episode, `End credits` at
  96.3 % of a Yanni concert), while a scene-numbered rip (`Chapter 01…12`) says nothing and falls
  back to the share. Detecting credits from the picture was considered and rejected — it helps only
  where credits start before 95 %, its thresholds cannot be justified without a corpus to measure
  against, and a false positive marks a film watched fifteen minutes early.

**Files:** `jvmMain/…/engine/WatchThresholds.kt` (new), `jvmMain/…/engine/PlaybackResume.kt`

## Phase 4: Engine

`PlaybackResume.kt`: a private `watchedFrom()` resolves the threshold for the open file —
the setting's share, pulled earlier by `creditsStartMs(tracks.chapters, durationMs)` — and both
`saveResumePosition()` and `resumePositionMs()` read it, so the watched window and the resumable
window stay one judgement.

**Files:** `jvmMain/…/engine/PlaybackResume.kt`

## Phase 5: UI

`VideoPlayerSettingsPanel.kt` was 125 lines, so the whole Resume playback section moves out the way
`AmbientSettingsSection` did before it.

- **New** `feature/settings/ResumeSettingsSection.kt`: the mode control, the minutes stepper, the new
  `Count a video as watched after` segmented control, the dim toggle and the danger row — all inside
  the existing `resumeMode != Off` guard, because with resume off nothing is recorded to mark.
  A segmented control rather than a percentage stepper: the choice is between two intentions, and
  exposing 95 % as a number would promise a precision the threshold does not have.

**Files:** `feature/settings/ResumeSettingsSection.kt` (new), `feature/settings/VideoPlayerSettingsPanel.kt`

## Phase 6: Tests

**New** `jvmTest/unit/WatchedThresholdTest.kt` (8). `PlaybackResumeThresholdTest` is untouched and
must keep passing — it pins `TheVeryEnd`'s definition.

- `TheVeryEnd` equals `finishedFromMs` at every length.
- A 2 h film marks at 1:54:00 under `MostOfIt`, 1:59:30 under `TheVeryEnd`.
- The film that prompted this marks at 88:15 with its chapter, 90:17.850 without.
- `mostOfIt(d) <= finishedFromMs(d)` at every length; a one-minute clip is the case that needs the
  clamp.
- A credits chapter at 80 % pulls the mark to 80 %; one at 98 % leaves it at 95 %.
- Duration 0 marks nothing in either mode.
- `creditsStartMs` believes `End Credits` and `ТИТРЫ` late in the file; returns null for opening
  credits, a credits chapter at 0, one at 5 %, `Chapter 12`, an empty list and a single chapter.

**Files:** `jvmTest/unit/WatchedThresholdTest.kt`

## Decisions

| Date | Question | Choice |
|---|---|---|
| 2026-09-20 | How much is "most of it"? | 95 % of the duration, clamped never to exceed the existing tail. Measured against a real 95:03 film whose credits start at 92.8 %: 90 % would have marked it watched three minutes before its last scene finished. A share cannot find the credits, so it errs late and lets the chapter data be the precise signal. |
| 2026-09-20 | Does a file marked watched early keep its resume point? | No — it clears, exactly as finishing does. The setting is the escape hatch; two kinds of "finished" would need the card to explain a difference that means nothing to the person looking at it. |
| 2026-09-20 | Detect credits? | By chapter title only, and only ever to move the mark earlier. Picture analysis was considered and dropped — unverifiable thresholds, and a false positive is far more costly than a miss. |
| 2026-09-20 | Trust the chapter title? | No. Last chapter, starting at ≥ 60 % of the file, or the whole signal is dropped for the flat share. Badly named chapters exist. |
| 2026-09-20 | Migrate existing records? | No. One machine, no users. |
