# ME-233 — Sound across a join

| | |
|---|---|
| **Order** | after ME-232, built the same day it was promoted (2026-11-05) |
| **Depends on** | ME-232 (the player walks the kept ranges) |
| **Feature** | [Playback](../playback.md) · umbrella [Video Editor](../../video-editor.md) |

**Goal.** A cut film's sound runs straight through every join, cut to the sample, with no
gap and no click.
**Why now.** After ME-232 the picture joined without a stall but the sound re-seeked when it did
and flushed the line, dropping up to ~300 ms of queued sound — the author heard a blip at every
join (2026-11-05).

## Shape — measured 2026-11-04

- **No audio lanes.** The backlog block planned pre-rolled audio decoders beside the video ones.
  `AudioJoinProbe` measured an audio-only seek at **1–11 ms** on S02E01's and Green Book's AC-3,
  landing within one 32 ms frame of the target — against the ~200 ms the audio loop already
  decodes ahead of the picture. One grabber seeking at the splice is enough.
- **The sound walks the ranges itself, ahead of the picture**, and is never told about a join:
  ME-232's `audioJoin` re-seek — the click — is gone.
- **A 5 ms ramp each side of a splice**: two different waveforms butted together step by up to
  twice their amplitude, which is the click even when no sample is missing.
- **A freeze is silence for its hold**, as the render already writes it (ME-217).

## Built — 2026-11-05

- `audio/AudioRanges.kt` (on `EditPlayback.audio`): the range the sound is in; `deliver` keeps a
  frame's part inside its range, ramps in after a splice, ramps out and splices when a frame runs
  past the range's end — silence for any freezes, a seek 100 ms before the next range so the head
  can be trimmed to the sample, and the next range's `atempo` built at user speed × its pace.
  `follow` re-places it after any audio reseek; an edit of the list with no seek re-places it from
  its own position. Arithmetic in `audio/AudioSplice.kt`.
- The audio loop compares sound and picture **on the result** while ranges are set (`aheadMs`),
  since source time jumps at a join; a lag past 400 ms still resyncs. A speed change that is only
  the picture reaching a pace the spliced sound already has (`alreadyAt`) no longer re-seeks.
  Past the last range the loop parks (`ended`).
- `integration/AudioSpliceTest` reads the PCM the line would get: three ranges cut off frame
  boundaries come out as long as the result ± 25 ms with no step over 1000 between samples (the
  clip's 440 Hz tone steps ~345 at most; with the ramp set to 0 the test fails on a step of 4837);
  a 500 ms freeze is 500 ± 15 ms of silence. `probe/AudioJoinProbe.kt` holds the measurement.

**Not yet by hand.** Not covered: a join between ranges at different speeds — the old `atempo`
graph's last few milliseconds are dropped when the new one is built; and the edit's gain (ME-240)
still follows the picture, so a clip's own volume reaches the line ~200 ms after its sound does.
