# Sound boost — volume past 100 %

**Shipped 2026-08-29.** Asked for and built the same day; the ceiling and the detent were judged by
ear on a real file before this moved here.

**Goal.** An option in the video viewer's volume control that lets the slider go past 100 % — a quiet
recording made audible without reaching for the system mixer.

**Why.** The slider stops at 1.0 today, so a file mastered low is as loud as the app can make it.

**The switch is a glyph, not a word.** "Sound boost" set the popover's width for a row that is only a
switch, so the label became `speaker.fill` and `bolt.fill` side by side at one height
(`drawable/speaker_bolt_fill.svg`), dimmed while off, with the name in a tooltip.

**What the work also turned up.** Two Compose snapshot defects in the player, unrelated to boost and
found because the volume popover was on screen while they bit: a track probe whose write was dropped
by the recomposer, and — after that was fixed with a mutable snapshot — a correct model the screen
never re-read. Both are now `writeUiState { }`, and the account is in `code-style/state.md`.

## Where gain is applied — answered

`setVolumeInternal` (`engine/decode/VideoPlayerExt.kt`) turns the level into decibels and clamps it
into `MASTER_GAIN`'s **reported** `[minimum, maximum]`. Past unity that clamp belongs to the device,
not to us: it differs by machine and fails silently. So the level is **split at unity** —

- the line keeps `min(volume, 1f)`, which with boost off is byte-for-byte what ran before;
- above unity the line sits at 0 dB and the samples are multiplied and hard-clamped to the Int16
  range (`engine/audio/SoundBoost.kt`). Hard clipping only; a limiter is a different feature.

The buffers are 16-bit signed little-endian PCM, so this is a pure `ByteArray → ByteArray` and is
tested without a sound card (`jvmTest/unit/SoundBoostTest.kt`).

## The shape

- **The switch lives in the volume popover itself**, not in player settings: "Включить Sound boost",
  read where the slider is. Off by default, because boost distorts and a control that silently
  clipped every file would be a defect rather than a feature;
- until it is on, the slider stops at 100 % exactly as it does today. Turning it on is what
  *permits* going higher — it does not raise the level by itself;
- above 100 % the track is drawn in another colour, running **green → red** with the amount of
  boost, so the range that distorts never looks like the range that does not;
- a persisted flag in `WokaSettings` (`core/data/`) — and the same treatment in PiP, which draws its
  own controls.

## The two open questions — decided 2026-08-29

- **The ceiling is 300 %** (`MAX_MEDIA_VOLUME`, `core/data/VolumeRange.kt`).
- **One slider, not two**, its range growing to the ceiling — with a **magnetic detent at 100 %**, so
  a track that reaches three times as high does not make the ordinary setting harder to land on. That
  detent is what paid for the shared control; without it the second slider would have been right.

Every way the level moves clamps against `volumeCeiling(soundBoost)` — the slider, both scroll
wheels and the arrow keys — so none of them can stop somewhere the others do not.

## Done — all of it

- with the option off, nothing about the volume control changes;
- with it on, the slider reaches 300 % and the sound is louder, clipping bounded rather than wrapping;
- the level survives a restart, and turning the switch off pulls a boosted level back to 100 % so it
  cannot strand above a slider that no longer reaches it;
- covered by `jvmTest/unit/SoundBoostTest.kt`, `SoundBoostSettingsTest.kt` and
  `jvmTest/ui/VolumeBoostUiTest.kt`;
- `./gradlew.bat :shared:build` green.
