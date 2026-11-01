# AP-009 — Audio in the library

| | |
|---|---|
| **Order** | 6 — after AP-003 |
| **Depends on** | AP-003 (audio plays), AP-001 (formats) |
| **Feature** | [Audio player](../audio-player.md) — §1a, §5 |

**Goal.** A folder can be narrowed to its music, and Windows offers Woka for audio files.
**Why now.** AP-003 plays a track opened from the grid, but nothing outside the grid reaches it,
and the grid cannot show only the music.

Written against the tree on 2026-11-01. `isMedia` already resolves through `mediaKind()`, so the
startup route, window drops, search and the sidebar's counts have seen audio since AP-003. Only the
associations and the Settings count read `ALL_SUPPORTED_MEDIA_EXTENSIONS`.

## Steps
1. Measure the nine formats AP-001 left open, on samples downloaded from `samples.ffmpeg.org`
   (`AudioFormatProbe` with `WOKA_AUDIO_SAMPLES`). Keep `vqf`, `oma`, `aob` and `mp1`; cut the rest
   (§1a). `ape` and `mpc` are cut for good (decision log).
2. `core/data/MediaKind.kt`:
   - the four join `AUDIO_EXTENSIONS`;
   - `ALL_SUPPORTED_MEDIA_EXTENSIONS` becomes image + video + audio (153).
3. `feature/folder/FolderViewOptions.kt`: `Music` joins `FILTERS`.
   `feature/folder/FolderStateMapper.kt`: `"Music"` keeps folders and `MediaItem.Audio`.
   `core/data/WokaSettings.kt`: the comment on `mediaFilter` lists it.
4. `desktopApp/build.gradle.kts`: 31 audio entries in `mediaAssociations` — one per
   `AUDIO_EXTENSIONS`, each with a MIME type and an Explorer description.
5. Tests:
   - `WindowsFileAssociationsTest`: 153, with audio among the key formats;
   - `MediaKindTest`: the four new extensions are audio, and `ape` and `mpc` are not media;
   - `FolderMusicFilterTest` (new): Music keeps folders and tracks, and Photos and Videos leave
     tracks out.

**Files:** `shared/src/commonMain/kotlin/com/woka/core/data/MediaKind.kt`,
`shared/src/commonMain/kotlin/com/woka/core/data/WokaSettings.kt`,
`shared/src/commonMain/kotlin/com/woka/feature/folder/FolderViewOptions.kt`,
`shared/src/commonMain/kotlin/com/woka/feature/folder/FolderStateMapper.kt`,
`desktopApp/build.gradle.kts`, `shared/src/jvmTest/kotlin/com/woka/unit/WindowsFileAssociationsTest.kt`,
`shared/src/jvmTest/kotlin/com/woka/unit/MediaKindTest.kt`,
`shared/src/jvmTest/kotlin/com/woka/unit/FolderMusicFilterTest.kt`.

**Done when:** View › Show offers Music and it shows the folder's tracks and sub-folders. Settings ›
OS Integration registers 153 formats, and an `.mp3` offers Woka in Explorer's "Open with".
`:shared:build` is green.

## Result — 2026-11-01
- Built as specified. `:shared:build` green.
- `FolderStateMapperTest` is at 148 lines, so the Music case went into a file of its own rather
  than over the mandate.
- `desktopApp/build.gradle.kts` is 285 lines: it was 254 before, and the 150-line mandate is for
  `.kt` sources.
- By-hand check pending.
