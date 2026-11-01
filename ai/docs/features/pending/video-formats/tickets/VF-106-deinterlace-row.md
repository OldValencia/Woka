# VF-106 — "Deinterlace: Auto / Off" in the player's ⚙ popover

| | |
|---|---|
| **Order** | 2 of 2 |
| **Depends on** | [VF-102](VF-102-deinterlace-in-the-player.md) — `Deinterlace.isInterlaced` and the re-open |
| **Feature** | [Video formats](../video-formats.md) §7 |

**Goal.** An interlaced file shows a "Deinterlace" row (Auto / Off) in the player's ⚙ popover; Off
plays it raw, for every interlaced file from then on. A progressive file never shows the row.
**Why now.** VF-102 deinterlaces unconditionally; the author wants a way out, scoped to the files it
concerns (2026-11-01): **not** in the app's Settings screen, and hidden where it would do nothing.

## Steps
1. **Split `core/data/WokaSettings.kt` (148)** — the six enums at `:6–14` move to a new
   `core/data/SettingsEnums.kt`, same package, so no import changes.
2. `WokaSettings`: `val deinterlace: Boolean = true` — true is Auto. A Boolean, not an enum, for the
   reason `showRemainingTime` gives: the row is a toggle. Global, like `repeatMode`.
3. `PlaybackSettings.setDeinterlace(enabled: Boolean)` (`core/data/SettingsRepository.kt`); `PlaybackSettingsImpl` overrides it with
   `mutator.update { it.copy(deinterlace = enabled) }`. Not offered by any Settings-screen section.
4. `Deinterlace.enabled` (`@Volatile`, default true) and `VideoPlayerState.setDeinterlace(enabled)` in
   `decode/Deinterlace.kt`: stores it, and when `isInterlaced` and the value changed, requests the
   video re-open with the spinner (`isReloadingVideo = true`, `pendingVideoReopenIndex` — as
   `selectVideoTrack`). `observe` keeps setting `isInterlaced` when disabled, but asks for no re-open;
   `active` becomes `enabled && topFieldFirst != null`.
5. `shell/MediaViewerPlaylist.kt`, `PlaybackModeEffects` — a third `LaunchedEffect(videoState,
   settings.deinterlace) { videoState?.setDeinterlace(settings.deinterlace) }` beside `repeatMode` and
   `playbackSpeed`.
6. `video/PlayerSettingsPlayback.kt` — `ValueRow` from `private` to `internal`.
   `video/PlayerSettingsMenu.kt`, "Picture" section, above Ambient fill:
   `if (videoState.deinterlace.isInterlaced) ValueRow("Deinterlace", if (on) "Auto" else "Off", on) {
   repo.setDeinterlace(!on) }`.
7. Tests: `unit/` — `copy(deinterlace = false)` round-trips through the settings JSON and an old
   `settings.json` without the field decodes to true; `DeinterlaceTest` — with `enabled = false`,
   `interlaced.ts` sets `isInterlaced` and leaves `active` false.

## Files
`core/data/SettingsEnums.kt` (new), `core/data/WokaSettings.kt`, `core/data/SettingsRepository.kt`,
`jvmMain/.../core/data/PlaybackSettingsImpl.kt`, `decode/Deinterlace.kt`,
`shell/MediaViewerPlaylist.kt`, `video/PlayerSettingsPlayback.kt`, `video/PlayerSettingsMenu.kt`,
tests.

## Done when
- `interlaced.ts`: the row reads Auto; Off → the comb is back after the spinner, Auto → gone; the
  choice survives a restart; `chapters.mkv` and `sample.evo`: no row;
- `./gradlew.bat :shared:build` green; anti-FQN clean; no `.kt` over 150 lines.

## Result — 2026-11-01
**Built** with VF-102. `WokaSettings.deinterlace` (enums split to `SettingsEnums.kt`),
`PlaybackSettings.setDeinterlace`, `VideoPlayerState.setDeinterlace` (in `decode/Deinterlace.kt`),
the effect in `PlaybackModeEffects`, the row in the ⚙ "Picture" section shown on
`deinterlace.isInterlaced`. Tests: `WokaSettingsTest` (Off round-trips, an old file reads Auto),
`DeinterlaceTest` (Off: flagged interlaced, no graph). The row itself was not looked at on screen.
