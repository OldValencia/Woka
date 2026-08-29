# Sound boost — volume past 100 %

**Priority.** Asked for 2026-08-29, ahead of the rest of the pending work.

**Goal.** An option in the video viewer's volume control that lets the slider go past 100 % — a quiet
recording made audible without reaching for the system mixer.

**Why.** The slider stops at 1.0 today, so a file mastered low is as loud as the app can make it.

## The question to answer first

**Where gain is applied.** `feature/viewer/video/engine/audio/VideoPlayerAudio.kt` sets the volume
through the line's own control today. A `FloatControl.MASTER_GAIN` cannot be trusted past its
reported maximum — it varies by device and silently clamps — so boost almost certainly means
**scaling the samples** before they are written to the line, with clipping handled deliberately
rather than by wraparound. That decision shapes everything below, and nothing should be written
until it has been read out of the code rather than assumed.

## The shape

- a persisted flag in `WokaSettings` (`core/data/`), off by default: boost is opt-in because it
  distorts, and a setting that silently clipped every file would be a defect rather than a feature;
- the slider's range becomes `0…1` or `0…N` from that flag — `MediaViewerVolumeControl` and the
  transient `MediaViewerVolumeOverlay`, which prints the percentage;
- a row in `PlayerSettings*` to turn it on, beside the existing playback rows;
- the same range in PiP, which draws its own controls;
- the ceiling is a decision, not a slider that goes on forever — 200 % is the usual choice.

## Done when

- with the option off, nothing about the volume control changes;
- with it on, the slider reaches the chosen ceiling and the sound is louder, with clipping bounded
  rather than wrapping;
- the level survives a restart, and PiP and the viewer agree on it;
- `./gradlew.bat :shared:build` green.
