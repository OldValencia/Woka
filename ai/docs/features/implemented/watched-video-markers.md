# Plan: Watched markers in the grid

**Status: complete, verified by hand.** Progress bars, dimming, hand-marking and the setting all
work. Three bugs surfaced while using it and were fixed (below).

Landed differently from the text that follows in four places:

1. **No migration.** `playbackPositions` was replaced outright rather than kept read-only for a
   version — Woka has no users, so a compatibility shim would have been dead code on arrival.
2. **No `expect`/`actual` for the context menu.** The concern raised in the plan was unfounded:
   `FolderCard` already takes `contextMenuContent` and `FolderCardContextMenu` already lives in
   `commonMain`. Checked before writing anything, which is why it cost nothing.
3. **The three settings reads became one `WatchMarks`.** They are always read together, from the
   same place, and bundling them kept `FolderGrid` inside the size limit.
4. **The dim setting ships on**, and sits in the Video Player panel under Resume playback — the
   setting that feeds it. `FolderGridItem` was split out to make room.

## Three bugs found by using it

- **Short videos were marked fully watched partway through.** `FINISHED_TAIL_MS` was a flat 30
  seconds subtracted from any duration: 0.4% of a film, half of a one-minute clip. The tail is now
  proportional with a 30-second cap, so "finished" lands at ~92% of a short file and ~99.6% of a long
  one. Pre-existing resume logic, made visible by hanging a marker on it.
- **Short videos would not resume.** Caused by that first fix: the resume floor stayed a flat ten
  seconds, so a 20-second clip stored a position, drew a bar, then ignored it on open — `Always`
  quietly not meaning always. One proportional floor now governs both storing and returning, because
  "far enough in to be worth keeping" and "far enough in to be worth seeking back to" are one
  judgement, and splitting them is what created the contradiction.
- **Marks written under `Always` survived a switch to `Long videos`.** The player stops writing short
  files in that mode but never erases what an earlier mode wrote, so cards kept claiming something
  playback no longer honoured. `WatchMarks` now checks each record's own duration against the mode's
  threshold — which is what the duration is stored for. Hidden rather than deleted: changing a
  setting should not destroy data, and switching back restores them.

Tests: `WatchRecordTest` (7), `PlaybackResumeThresholdTest` (6), `WatchMarksTest` (5). The first
pins the trap the feature turns on — a finished video re-opened at position 0 must not erase its own
mark. - Done

A video that has been played carries a progress bar along the bottom of its card, the way YouTube
marks a partly-watched video. Gated on the Resume playback setting, since that is what records the
position in the first place.

## What the code already has, and the three gaps

`WokaSettings.playbackPositions: Map<String, Long>` is already written every five seconds and on
close, gated by `resumeAppliesTo(durationMs)`. That is the foundation. Three things are missing, and
each one changes the shape of the feature:

1. **"Watched" is actively erased.** `saveResumePosition` *clears* the record within 30 s of the end
   (`FINISHED_TAIL_MS`) — the one state a watched-marker most needs to keep is the one state the
   current logic deliberately throws away.
2. **There is no duration to draw against.** The map holds a position in milliseconds;
   `MediaItem.Video` carries only `file`, `name`, `lastModified`, `isHidden`. A fraction cannot be
   computed from a position alone, and probing every video in a folder to find out would mean an
   FFmpeg open per card.
3. **Re-opening a finished video would erase its own mark.** After finishing, `resumePositionMs()`
   returns null, so the file opens at 0; the saver then sees a position under `MIN_RESUME_MS` and
   clears the record. Without care, the watched mark disappears the moment you open the file again.
   This is the trap in the whole feature and it gets its own test.

---

## Phase 1: A record that can hold all three states

- Add `core/data/WatchRecord.kt`:
  ```kotlin
  @Immutable @Serializable
  data class WatchRecord(
      val positionMs: Long = 0L,
      val durationMs: Long = 0L,
      val finished: Boolean = false
  )
  ```
  Shaped after `TrackPrefs`, which is already a `@Serializable` value in a settings map — this is the
  established pattern for per-file records, not a new one.
- `WokaSettings` gains `watchRecords: Map<String, WatchRecord>`. `playbackPositions` **stays** for one
  version, read-only, so existing resume points survive the upgrade: on first read, any path present
  in the old map and absent from the new one becomes `WatchRecord(positionMs = it)` with
  `durationMs = 0`. A zero duration draws no bar but still resumes, which is exactly the old
  behaviour and the honest thing to show when the length is unknown.
- Duration is written by the player, which knows it — nothing new is probed anywhere.

**Files:** `core/data/WatchRecord.kt`, `core/data/WokaSettings.kt`, `core/data/SettingsMutations.kt`,
`core/data/SettingsRepository.kt`

## Phase 2: Recording it correctly

Rework `PlaybackResume.kt` around the three states rather than one nullable position:

- Past `finishedFromMs()` → write `finished = true` and keep the record, instead of deleting it.
- Between `MIN_RESUME_MS` and that → write the position, `finished = false`, and the duration.
- **Below `MIN_RESUME_MS` → clear the position but preserve `finished`.** This is gap 3. Clearing the
  whole record here is what would wipe a watched mark on re-open.
- Watching a finished file again past `MIN_RESUME_MS` naturally clears `finished` — it is in progress
  once more, which is what the bar should say.
- `resumePositionMs()` keeps its current contract exactly: null for a finished file, so it opens at
  the beginning as it does today. Only the grid learns anything new.

**Files:** `video/engine/PlaybackResume.kt`

## Phase 3: Getting the record to the card

- Read the map **once per grid**, not once per card: `settingsRepo.collectField { it.watchRecords }`
  in `FolderGrid` and `HomeScreen`, then pass each card its own `WatchRecord?` as a parameter.
  `collectField` is already how `MediaViewerFullscreenSurface` reads a single setting.
- Every card collecting the repository itself would recompose every card in the folder on any
  settings change at all, which is the shape §10 of the code-quality work exists to prevent.
- `FolderCardData` is resolved from a path and does filesystem work behind `remember` — the record is
  **not** part of it. It changes for a different reason and on a different schedule, so it travels as
  its own parameter.

**Files:** `feature/folder/FolderGrid.kt`, `feature/home/HomeScreen.kt`,
`core/ui/components/FolderCard.kt`, `core/ui/components/FolderCardBody.kt`

## Phase 4: The bar

- New `core/ui/components/FolderCardProgress.kt`. `FolderCardOverlays.kt` is at 129 lines and this
  would push it past the limit, so it is its own file from the start rather than after the fact.
- 3.dp tall, flush to the bottom edge of the preview and inside the card's existing rounded clip, so
  it reads as part of the thumbnail rather than a bar under it.
- In progress: filled to `positionMs / durationMs` in the accent colour, over a 30%-opacity track.
  Finished: filled the whole width.
- Drawn when the record is `finished` **or** `durationMs > 0`. Finished needs no duration — the bar
  is full either way — which is what lets a hand-marked video (Phase 6) show one without ever having
  been played. A record with a position but no known length shows nothing rather than a wrong bar.
- Nothing at all when `resumeMode == ResumeMode.Off`: the feature is gated on the setting that feeds
  it, so turning that off hides the marks rather than leaving them to go stale.

**Files:** `core/ui/components/FolderCardProgress.kt`, `core/ui/components/FolderCardPreview.kt`

## Phase 5: The dim option

- `WokaSettings.dimWatchedVideos: Boolean = false`, with a `SettingsToggleRow` in the **Resume
  playback** section of `VideoPlayerSettingsPanel` — it belongs beside the setting it depends on, and
  that panel already reads the repository directly.
  Title: *"Dim videos you have started"*. Subtitle: *"Fade the thumbnail once a video has a saved
  position"*.
- Off by default. Changing the appearance of every thumbnail in a library is not something to do to
  someone who has not asked for it.
- Applied as a `ColorFilter` desaturation plus a small alpha reduction on the preview image only —
  the title, badge and progress bar keep full contrast, or the card stops being readable at a glance.

**Dimming and the bar answer different questions, deliberately.** Dimming fires from the first saved
position, so it means *touched* — anything you have opened and left. The bar then says how far:
part-width for two minutes in, full width for finished. Together they separate "not started" from
"started" from "done" with one glance and no text, and the setting only controls whether the first of
those distinctions is drawn at all.

**Files:** `core/data/WokaSettings.kt`, `core/data/SettingsMutations.kt`,
`core/data/SettingsRepository.kt`, `feature/settings/VideoPlayerSettingsPanel.kt`,
`core/ui/components/FolderCardPreview.kt`

## Phase 6: Marking it watched by hand

Right-click a video in the grid → **"Mark as watched"**, which writes exactly the record a finished
playthrough writes: `finished = true`, so it draws the same full bar and dims the same way. Nothing
about the display branches on how the record got there.

- The same entry flips to **"Mark as not watched"** when a record already exists, and clears it.
  Being able to assert the state without being able to withdraw it is a trap, not a feature — and
  un-marking is the only way back from a mis-click.
- Shown only for `MediaItem.Video`, and only while `resumeMode != ResumeMode.Off`: with resume off
  nothing is drawn, so the entry would silently do nothing.

**The architectural catch, which decides where the code goes.** `FolderCard` lives in `commonMain`,
`ContextMenuArea` is a desktop API, and the grid has no context menu anywhere today — the only one in
the app is in the viewer, which is `jvmMain`. Per the project's own mandate for desktop-only APIs,
this goes through `expect`/`actual`:

- `commonMain`: `expect @Composable fun MediaContextMenu(items: List<MediaMenuAction>, content: @Composable () -> Unit)`
  plus an `@Immutable data class MediaMenuAction(label, onClick)`.
- `jvmMain`: the `actual` wrapping `ContextMenuArea`, the same one `MediaViewerFrame` already uses.
- Both halves in the same package, as `VideoThumbnail` and the dialogs do.

Verify the desktop API resolves from a common-source call site **before** building the rest of the
phase; if it does not, the wrapper is the thing that absorbs it, which is why it exists.

**Files:** `core/ui/components/MediaContextMenu.kt` (+ `.jvm.kt`),
`core/ui/components/FolderCard.kt`, `feature/folder/FolderGrid.kt`, `feature/home/HomeScreen.kt`

## Phase 7: Tests

Unit, no player and no filesystem — the state machine is pure and it is where the traps are:

- Opening and closing a finished video at position 0 **keeps** `finished` (gap 3).
- Passing `FINISHED_TAIL_MS` sets `finished` and keeps the record.
- Re-watching a finished file past `MIN_RESUME_MS` clears `finished`.
- A record migrated from `playbackPositions` still resumes and reports no drawable fraction.
- `resumeAppliesTo` still gates everything: with `ResumeMode.Off` nothing is written.
- A hand-marked record — `finished` with `durationMs = 0` — draws a full bar, and playing the file
  afterwards replaces it with a real one rather than being refused.

**Files:** `jvmTest/.../WatchRecordTest.kt`

---

## Consequences worth stating before building

- **`LongOnly` means short videos are never marked.** That is the existing resume gate, inherited
  wholesale. A five-minute clip gets no bar under the default settings, and that will look like a bug
  unless it is expected.
- **The mark is per absolute path.** Moving or renaming a file loses it, exactly as it loses the
  resume point and the track preferences today.
- **`settings.json` grows by one record per video ever watched.** `TrackPrefs` already behaves this
  way and there is a "Forget saved tracks for all files" danger row for it; watch records deserve the
  same escape hatch, and it should be added in Phase 5 beside the toggle.

## Not doing

- **Marks in the viewer's carousel.** The same record would answer, but the carousel is for moving
  within a folder you are already in, where "have I seen this" is not the question being asked.
- **A separate "watched" state distinct from "played to the end".** Hand-marking writes the same
  record a finished playthrough writes, on purpose: two ways of reaching one state, not two states.
  Anything else would need the display to explain which is which, and there is nothing useful to say.
