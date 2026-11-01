# AP-003 — An audio file opens in the viewer, plays, and carries on in Now Playing

| | |
|---|---|
| **Order** | 5 — first of the audio tickets |
| **Depends on** | AP-001 (formats, fixtures), AP-004…AP-008 (Now Playing) |
| **Feature** | [Audio player](../audio-player.md) — §3.1–§3.4; spec `ai/mockups/design/audio-player.html` |

**Goal.** An audio file in a linked folder shows as a cover tile, opens in the viewer as the mockup
draws it, plays, seeks, repeats and shuffles over the folder's audio files, and keeps playing in
Now Playing after the viewer closes.
**Why now.** The author's order: audio after Now Playing, which is done.

**Re-cut from the backlog.** It absorbs AP-002. The engine change shows nothing on its own
(`tickets.md` §4), and AP-002's id is retired. What stays for a later ticket (AP-009): the `Music`
filter, file associations, and the Info panel's audio rows.

## Steps

### Kind
1. `core/data/MediaKind.kt`:
   - `MediaKind.Audio`;
   - `AUDIO_EXTENSIONS` — the list kept in the decision log (AP-001), plus `mlp`;
   - `ALL_SUPPORTED_MEDIA_EXTENSIONS` stays image + video until AP-009, which owns associations;
   - `mediaKind()` and `mediaItemOrNull` gain the audio branch.
2. `LocalMediaRepository.kt`: `MediaItem.Audio(file)`.
3. Decide each exhaustive `when` and each `MediaItem.Video` site (§2's 19 files) in the code. Audio
   behaves like video where the site is about a **player** (playlist, system controls, PiP
   conflict). Audio behaves like nothing where the site is about a **picture** (frame strip, hover
   reel, size modes, editor). The editor rejects audio.

### Engine — `VideoPlayerState` without a picture
4. **The clock** — `engine/decode/AudioClockLoop.kt` (new): `startAudioClockLoop()`, run instead
   of the decoder and the presenter when `file.mediaKind() == MediaKind.Audio`.
   - It opens one grabber with video disabled, writes `durationMs` (`lengthMs`) and runs
     `probeTracksAndChapters`. An open failure sets `openFailed`.
   - It then advances `currentPositionMs` by wall time × `playbackSpeed` while playing.
   - On a seek-generation change it moves to `targetMs` and calls `videoSeekComplete`, so the audio
     loop's gate opens.
   - It does not advance until the audio loop has fed the line under the current generation.
   - When the audio loop reports EOF for this generation and the clock has reached it: Repeat File
     → seek 0; otherwise the presenter's end — `isPlaying = false`, `reachedEnd = true`.

   **Why a wall clock.** The audio loop is already built to follow an external clock: a 200 ms
   lead, a 400 ms lag resync, and a seek gate. A sample-counting clock would mean rewriting the
   writer and its `atempo` accounting. This replaces the 2026-11-01 "samples handed to the line"
   decision (logged).
5. `PlaybackGeneration`:
   - `markAudioFed(gen)` and `isAudioFed`;
   - `markAudioEnd(gen, ms)` and `audioEndFor(gen): Long?`.
   Both are keyed by the generation, so a stale EOF from before a seek is ignored.
6. `VideoPlayerAudioDecoder.kt` (150 lines): the `frame.samples?.let` block moves into
   `VideoPlayerAudioSamples.kt` as `deliverAudioFrame(...)`, for the size mandate. The loop then
   reports fed and end.
7. `startPlayback()` (`VideoPlayerPresenter.kt`): the audio branch starts the clock and the audio
   loop, and nothing else. An audio file never opens the video grabber, so an embedded cover never
   reaches the clock.
8. Resume points and "watched" are not recorded for audio. `saveResumePosition` and
   `resumePositionMs` are only called from the video decoder, which does not run for audio.

### Cover
9. **Cover loading** — `MediaThumbnails` gains an audio branch, `AudioCover.kt` (new):
   - first the embedded `attached_pic` (decode its one packet);
   - else `cover.jpg` / `folder.jpg` / `front.jpg` beside the file;
   - else null.
   It is cached the way video posters are. The grid tile, the carousel and Now Playing read it.

### Viewer
10. `ViewerMedia` gains `Audio(player, cover, title, subtitle)`. Title and subtitle come from the
    tags (`title`, `artist — album`), falling back to the file name and the folder name.
11. `AudioStage.kt` (new), per `audio-player.html`:
    - the cover up to 300 dp, radius 12, a click toggles play;
    - paused: scale 0.94, brightness 0.88, 350 ms;
    - title 20 sp, the second line 13 sp;
    - the blurred cover as the backdrop.
12. The controls bar for audio: `MediaViewerVideoControls` without subtitles and fullscreen. The
    seek slider has no picture preview. ⚙ shows Repeat, Shuffle, Speed and the time label only.
13. `MediaViewerPlaylist` and the arrows walk the same kind as the current file.
14. **Now Playing.** `AudioNowPlaying` (new, jvm) is the source.
    - The viewer claims the slot when an audio player starts.
    - `releaseState` keeps a player the slot owns.
    - `reveal()` navigates to the file's folder with the viewer open on it, and the viewer reuses
      the slot's player for that path.
    - `stop()` disposes the player and releases the slot.
    - A video starting in the viewer claims nothing, but stops a slot that holds audio — "one sound
      source".

### Grid
15. The tile: the cover cropped square with a note glyph and the length. Without a cover, the
    placeholder with the file name. No hover reel.

### Tests
16. Tests:
    - `integration/AudioPlaybackTest.kt` (new), through `PlayerHarness` on `tone.mp3`,
      `tone-cover.mp3` and `tone.flac`:
      - plays to the end and raises `reachedEnd`;
      - position advances;
      - a seek lands;
      - Repeat File loops;
      - the cover file never ends after one frame.
    - `unit/MediaKindTest.kt`: audio extensions route to `Audio`.
    - `unit/AudioCoverTest.kt`: embedded cover found on `tone-cover.mp3`; none on `tone.mp3`.

## Done when
- An `.mp3` and a `.flac` play in the running app with the mockup's centre, seek and pause. They
  carry on in Now Playing after Esc, and return to the viewer from the row.
- Arrows and auto-advance skip non-audio files.
- Files ≤ 150 lines, the anti-FQN grep is clean, and `:shared:build` is green.

## Result — 2026-11-01
- `:shared:build`: 1491 tests, one failure — `EditorSettingsPersistenceTest`, the known
  `settings.json` lock race — which passes rerun alone. `AudioPlaybackTest` 4/4,
  `AudioCoverTest` 4/4, `MediaKindTest` with the audio case. By-hand check pending.
- **Built as specified, with these differences:**
  - The viewer is handed **one kind**. Opened on a track it gets the folder's audio files only; on
    a picture it gets pictures and films only (`FolderScreen`). The arrows, carousel, playlist and
    media keys follow from that, with no per-site check.
  - `ViewerMedia` gains `AudioMedia` (beside the sealed interface, same file). `MediaViewerFrame`
    hands it to `AudioStage`. A tap already plays and pauses in the viewer, so the cover needed no
    handler of its own.
  - `AudioSession` (jvm) owns the one audio player; `AudioNowPlaying` is its slot source.
    Double-click / F → fullscreen is refused for a track.
  - The grid tile shows the cover or a note glyph, **without the length badge**: the card's
    caption already reads `Audio · <size>`, as a film's reads `Video · <size>`, and a length
    would mean opening every file in the folder.
  - New icon `music_note.svg`.
- **Decided after the build (the author):**
  - The folder walk for music moved from the viewer into `AudioSession`: a track that ends
    starts the next one with the viewer closed, and an open viewer follows it.
  - Repeat Off plays the folder through and stops after the last track; Folder wraps; File
    loops. `MediaViewerPlaylist.nextPath(playThrough = true)`.
  - The tile stays without the length.
  - `FolderItemContextMenu.kt` (152) split: `WatchedMenuItem` moved to its own file.
  - `:shared:build` after these: 1495 tests, one failure — `DirectoryWatchTest`'s known
    sub-folder flake, 4/4 rerun alone. `AudioPlaylistWalkTest` 3/3, `AudioSessionWalkTest` 1/1.
